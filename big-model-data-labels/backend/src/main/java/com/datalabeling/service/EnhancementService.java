package com.datalabeling.service;

import com.datalabeling.dto.EnhancementConfig;
import com.datalabeling.entity.Label;
import com.datalabeling.entity.SystemPrompt;
import com.datalabeling.util.PostProcessValidator;
import com.datalabeling.service.extraction.ExtractedNumber;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 二次强化分析服务（优化版 v2）
 * 负责对初步分析结果进行二次验证和强化
 *
 * 优化内容：
 * - 使用增强的shouldTrigger方法，支持强制触发条件
 * - 集成后置验证器，规则层面的最终保障
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancementService {

    private final SystemPromptService systemPromptService;
    private final DeepSeekService deepSeekService;
    private final PromptTemplateEngine promptTemplateEngine;
    private final ObjectMapper objectMapper;
    private final PostProcessValidator postProcessValidator; // L5: 注入后置验证器

    /**
     * 强化分析结果
     */
    @lombok.Data
    @lombok.Builder
    public static class EnhancementResult {
        private String finalResult;           // 最终结果
        private int finalConfidence;          // 最终置信度
        private String validationNotes;       // 验证笔记
        private boolean wasAdjusted;          // 是否修正了结论
        private String adjustmentReason;      // 修正原因
        private int rounds;                   // 分析轮数
    }

    /**
     * 执行二次强化分析（优化版 v2）
     *
     * @param label 标签
     * @param rowData 数据行
     * @param initialResult 初步结果
     * @param initialConfidence 初步置信度
     * @param initialReasoning 初步推理
     * @param validationResult 规则验证结果（可选）
     * @param config 强化配置
     * @param extractedNumbers 提取的号码（用于后置验证）
     * @return 强化结果
     */
    public EnhancementResult enhance(Label label, Map<String, Object> rowData,
                                      String initialResult, int initialConfidence,
                                      String initialReasoning, String validationResult,
                                      EnhancementConfig config,
                                      List<ExtractedNumber> extractedNumbers) {

        // ========== L4: 使用增强的触发条件判断 ==========
        boolean shouldTrigger = config.shouldTrigger(initialConfidence, validationResult);

        if (!shouldTrigger) {
            log.debug("初始置信度 {} 且无强制触发条件，跳过强化分析", initialConfidence);

            // ========== L5: 即使跳过二次强化，仍执行后置验证 ==========
            return performPostValidation(
                initialResult, initialConfidence, rowData, extractedNumbers, validationResult, label != null ? label.getName() : null);
        }

        try {
            // 获取强化提示词
            SystemPrompt prompt = config.getEnhancementPromptId() != null
                ? systemPromptService.findById(config.getEnhancementPromptId())
                    .orElseThrow(() -> new IllegalArgumentException("指定的强化提示词不存在"))
                : systemPromptService.getEnhancementPrompt(null);

            // 渲染提示词
            String renderedPrompt = promptTemplateEngine.renderEnhancementTemplate(
                prompt.getTemplate(),
                label,
                rowData,
                initialResult,
                initialConfidence,
                initialReasoning,
                validationResult
            );

            // 调用 LLM 进行强化分析
            String llmResponse = deepSeekService.callForEnhancement(renderedPrompt);

            // 解析响应
            EnhancementResult result = parseEnhancementResponse(llmResponse, initialConfidence);
            result.setRounds(1);

            log.info("强化分析完成: 初步={}, 最终={}, 置信度: {} -> {}",
                initialResult, result.getFinalResult(),
                initialConfidence, result.getFinalConfidence());

            // ========== L5: 二次强化后，再次执行后置验证 ==========
            String finalResultToCheck = result.getFinalResult() != null
                ? result.getFinalResult()
                : initialResult;

            EnhancementResult validatedResult = performPostValidation(
                finalResultToCheck,
                result.getFinalConfidence() > 0 ? result.getFinalConfidence() : initialConfidence,
                rowData,
                extractedNumbers,
                validationResult,
                label != null ? label.getName() : null);

            // 保留二次强化的信息
            validatedResult.setRounds(result.getRounds());
            if (result.getFinalResult() != null) {
                validatedResult.setAdjustmentReason(
                    (validatedResult.getAdjustmentReason() != null
                        ? validatedResult.getAdjustmentReason() + "; " : "") +
                    "二次强化: " + result.getAdjustmentReason());
            }

            return validatedResult;

        } catch (Exception e) {
            log.error("强化分析失败", e);
            // 失败时返回原始结果 + 后置验证
            return performPostValidation(
                initialResult, initialConfidence, rowData, extractedNumbers, validationResult, label != null ? label.getName() : null);
        }
    }

    /**
     * 向后兼容的方法（旧签名）
     * 不包含 extractedNumbers 参数，默认传 null
     *
     * @deprecated 请使用包含 extractedNumbers 参数的新方法
     */
    @Deprecated
    public EnhancementResult enhance(Label label, Map<String, Object> rowData,
                                      String initialResult, int initialConfidence,
                                      String initialReasoning, String validationResult,
                                      EnhancementConfig config) {
        return enhance(label, rowData, initialResult, initialConfidence,
                      initialReasoning, validationResult, config, null);
    }

    /**
     * 执行后置规则验证（L5）
     */
    private EnhancementResult performPostValidation(String result, int confidence,
                                                     Map<String, Object> rowData,
                                                     List<ExtractedNumber> extractedNumbers,
                                                     String validationResult,
                                                     String labelName) {
        try {
            // 执行后置验证
            PostProcessValidator.ValidationResult validation =
                postProcessValidator.validate(result, rowData, extractedNumbers, labelName);

            // ✅ vNext：允许后置规则“纠偏”为[是]（当模型判否但规则证据表明合格）
            if (validation.getSuggestedResult() != null
                    && !validation.getSuggestedResult().trim().isEmpty()
                    && !validation.getSuggestedResult().equals(result)) {
                String suggested = validation.getSuggestedResult();
                log.warn("后置验证建议修正判断结果: {} -> {} ({})", result, suggested, validation.getMessage());

                int suggestedConfidence = confidence;
                if ("是".equals(suggested)) {
                    // 规则证据充分时给一个保守下限，避免“是”置信度过低
                    suggestedConfidence = Math.min(100, Math.max(confidence, 60));
                } else if ("否".equals(suggested)) {
                    suggestedConfidence = Math.min(confidence, 50);
                }

                return EnhancementResult.builder()
                    .finalResult(suggested)
                    .finalConfidence(suggestedConfidence)
                    .validationNotes("后置规则验证: " + validation.getMessage())
                    .wasAdjusted(true)
                    .adjustmentReason("后置规则验证证据充分，修正为[" + suggested + "]")
                    .rounds(0)
                    .build();
            }

            if (!validation.isValid()) {
                // 后置验证失败，修正结果
                log.warn("后置验证失败，修正判断结果: {}", validation.getMessage());

                return EnhancementResult.builder()
                    .finalResult("否")
                    .finalConfidence(Math.min(confidence, 50))
                    .validationNotes("后置规则验证: " + validation.getMessage())
                    .wasAdjusted(true)
                    .adjustmentReason("后置规则验证检测到问题，强制修正为[否]")
                    .rounds(0)
                    .build();
            } else if (validation.getLevel() == PostProcessValidator.ValidationLevel.WARNING) {
                // 警告级别，记录但不修正
                log.info("后置验证警告: {}", validation.getMessage());

                return EnhancementResult.builder()
                    .finalResult(result)
                    .finalConfidence(confidence)
                    .validationNotes("后置规则验证: " + validation.getMessage())
                    .wasAdjusted(false)
                    .rounds(0)
                    .build();
            } else {
                // 验证通过
                return EnhancementResult.builder()
                    .finalResult(result)
                    .finalConfidence(confidence)
                    .validationNotes("后置规则验证通过")
                    .wasAdjusted(false)
                    .rounds(0)
                    .build();
            }

        } catch (Exception e) {
            log.error("后置验证失败", e);
            // 后置验证失败时，返回原始结果
            return EnhancementResult.builder()
                .finalResult(result)
                .finalConfidence(confidence)
                .validationNotes("后置验证失败: " + e.getMessage())
                .wasAdjusted(false)
                .rounds(0)
                .build();
        }
    }

    /**
     * 解析强化分析响应
     */
    private EnhancementResult parseEnhancementResponse(String response, int initialConfidence) {
        try {
            JsonNode root = objectMapper.readTree(response);

            String finalResult = root.path("final_result").asText();
            int finalConfidence = root.path("final_confidence").asInt();
            String validationNotes = root.path("validation_notes").asText();
            boolean shouldAdjust = root.path("should_adjust").asBoolean();
            String adjustmentReason = root.path("adjustment_reason").asText();

            // 处理 final_result
            if ("维持原判".equals(finalResult)) {
                // 保持原始结果
                finalResult = null;  // 标记为保持原判
            }

            return EnhancementResult.builder()
                .finalResult(finalResult)
                .finalConfidence(finalConfidence > 0 ? finalConfidence : initialConfidence)
                .validationNotes(validationNotes)
                .wasAdjusted(shouldAdjust)
                .adjustmentReason(adjustmentReason)
                .rounds(1)
                .build();

        } catch (Exception e) {
            log.warn("解析强化响应失败，使用默认值: {}", e.getMessage());
            return EnhancementResult.builder()
                .finalResult(null)
                .finalConfidence(initialConfidence)
                .validationNotes("无法解析强化响应")
                .wasAdjusted(false)
                .rounds(1)
                .build();
        }
    }

}
