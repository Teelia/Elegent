package com.datalabeling.service.extraction;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 增强型提取器协调服务
 * 负责协调所有增强型提取器，提供统一的提取接口
 *
 * 核心增强：
 * 1. 自动注册提取器（通过ExtractorRegistry）
 * 2. 支持增强型提取结果（包含上下文和业务含义）
 * 3. 大模型友好的提示词构建
 * 4. 更灵活的提取器配置
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedExtractionOrchestrator {

    private final ExtractorRegistry extractorRegistry;
    private final ObjectMapper objectMapper;

    /**
     * 统一提取入口（增强版）
     * 返回包含丰富上下文信息的提取结果
     *
     * @param label 标签配置
     * @param rowData 数据行
     * @return 增强型提取结果
     */
    public Map<String, Object> extract(Label label, Map<String, Object> rowData) {
        return extract(label, rowData, 0.80f);
    }

    /**
     * 统一提取入口（带信心度阈值）
     */
    public Map<String, Object> extract(Label label, Map<String, Object> rowData, float confidenceThreshold) {
        String type = label.getType();

        log.debug("开始增强提取，标签类型: {}, 标签名称: {}, 信心度阈值: {}%",
            type, label.getName(), Math.round(confidenceThreshold * 100));

        // 结构化提取
        if (Label.Type.STRUCTURED_EXTRACTION.equals(type)) {
            return extractStructured(label, rowData, confidenceThreshold);
        }

        throw new IllegalArgumentException("不支持的标签类型: " + type);
    }

    /**
     * 结构化提取（增强版）
     */
    private Map<String, Object> extractStructured(Label label, Map<String, Object> rowData, float confidenceThreshold) {
        long startTime = System.currentTimeMillis();

        try {
            // 解析提取器配置
            JsonNode extractorConfig = parseExtractorConfig(label);
            String extractorCode = extractorConfig.path("extractorType").asText();

            // 获取目标文本
            String text = getTargetText(label, rowData);
            if (text == null || text.trim().isEmpty()) {
                return createFailureResult("目标文本为空", startTime);
            }

            log.debug("目标文本长度: {} 字符", text.length());

            // 获取提取器
            IEnhancedExtractor extractor = extractorRegistry.getByCodeWithAlias(extractorCode);
            if (extractor == null) {
                return createFailureResult("未找到提取器: " + extractorCode, startTime);
            }

            // 解析选项
            Map<String, Object> options = parseOptions(extractorConfig.path("options"));

            // 合并默认选项
            Map<String, Object> defaultOptions = extractor.getDefaultOptions();
            if (defaultOptions != null) {
                Map<String, Object> mergedOptions = new HashMap<>(defaultOptions);
                mergedOptions.putAll(options);
                options = mergedOptions;
            }

            // 执行提取
            List<EnhancedExtractedResult> results = extractor.extractWithContext(text, options);

            // 设置字段名
            String fieldName = extractorConfig.path("fieldName").asText();
            if (fieldName != null && !fieldName.isEmpty()) {
                for (EnhancedExtractedResult result : results) {
                    result.setField(fieldName);
                }
            }

            // 构建返回结果
            return buildEnhancedResult(label, results, extractor, startTime, confidenceThreshold);

        } catch (Exception e) {
            log.error("增强型结构化提取失败", e);
            return createFailureResult("提取失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 构建增强型返回结果
     */
    private Map<String, Object> buildEnhancedResult(Label label, List<EnhancedExtractedResult> results,
                                                     IEnhancedExtractor extractor, long startTime, float confidenceThreshold) {
        if (results.isEmpty()) {
            return createEmptyResult(startTime);
        }

        // 去重：按值去重，保留信心度最高的
        Map<String, EnhancedExtractedResult> uniqueResults = new LinkedHashMap<>();
        for (EnhancedExtractedResult result : results) {
            String key = result.getField() + ":" + result.getValue();
            EnhancedExtractedResult existing = uniqueResults.get(key);
            if (existing == null || result.getConfidence() > existing.getConfidence()) {
                uniqueResults.put(key, result);
            }
        }

        // 过滤低置信度结果
        List<EnhancedExtractedResult> filteredResults = uniqueResults.values().stream()
            .filter(r -> r.getConfidence() >= confidenceThreshold)
            .collect(Collectors.toList());

        // 记录被过滤的数量
        int filteredOutCount = uniqueResults.size() - filteredResults.size();
        if (filteredOutCount > 0) {
            log.info("过滤掉 {} 个低信心度结果（阈值: {}%）", filteredOutCount, Math.round(confidenceThreshold * 100));
        }

        // 如果过滤后没有结果，返回空结果
        if (filteredResults.isEmpty()) {
            return createEmptyResult(startTime);
        }

        // 按字段分组
        Map<String, List<Object>> groupedResults = filteredResults.stream()
            .collect(Collectors.groupingBy(
                EnhancedExtractedResult::getField,
                LinkedHashMap::new,
                Collectors.mapping(r -> r.getNormalizedValue() != null ? r.getNormalizedValue() : r.getValue(),
                    Collectors.toList())
            ));

        // 计算平均置信度
        float avgConfidence = (float) filteredResults.stream()
            .mapToDouble(EnhancedExtractedResult::getConfidence)
            .average()
            .orElse(0);

        // 生成大模型提示词上下文
        String llmPromptContext = extractor.buildLLMPromptContext(filteredResults);

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("extractedData", groupedResults);
        result.put("confidence", Math.round(avgConfidence * 100));
        result.put("reasoning", buildReasoning(filteredResults));
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("totalCount", filteredResults.size());
        result.put("filteredOutCount", filteredOutCount);
        result.put("extractResults", filteredResults);

        // 增强字段
        result.put("llmPromptContext", llmPromptContext);
        result.put("extractorMetadata", extractor.getMetadata());

        log.info("增强提取完成: 提取到 {} 个结果，过滤掉 {} 个低信心度结果，平均置信度: {}%",
            filteredResults.size(), filteredOutCount, result.get("confidence"));

        return result;
    }

    /**
     * 构建推理说明
     */
    private String buildReasoning(List<EnhancedExtractedResult> results) {
        StringBuilder sb = new StringBuilder();
        sb.append("✓ 提取结果（").append(results.size()).append("个）：\n");
        for (EnhancedExtractedResult r : results) {
            sb.append("  • ").append(r.getField()).append(": ")
                .append(r.getValue())
                .append(" (信心度: ").append(Math.round(r.getConfidence() * 100)).append("%");
            if (r.getValidation() != null) {
                sb.append(", ").append(r.getValidation());
            }
            if (r.getBusinessMeaning() != null) {
                sb.append("\n    业务含义: ").append(r.getBusinessMeaning());
            }
            sb.append(")\n");
        }
        return sb.toString().trim();
    }

    /**
     * 解析提取器配置
     */
    private JsonNode parseExtractorConfig(Label label) {
        String configStr = label.getExtractorConfig();
        if (configStr == null || configStr.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结构化提取必须配置 extractorConfig");
        }

        try {
            return objectMapper.readTree(configStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "解析 extractorConfig 失败: " + e.getMessage());
        }
    }

    /**
     * 解析选项
     */
    private Map<String, Object> parseOptions(JsonNode optionsNode) {
        Map<String, Object> options = new HashMap<>();

        if (optionsNode != null && optionsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = optionsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isBoolean()) {
                    options.put(key, valueNode.asBoolean());
                } else if (valueNode.isInt()) {
                    options.put(key, valueNode.asInt());
                } else if (valueNode.isDouble()) {
                    options.put(key, valueNode.asDouble());
                } else if (valueNode.isTextual()) {
                    options.put(key, valueNode.asText());
                }
            }
        }

        return options;
    }

    /**
     * 获取目标文本
     */
    private String getTargetText(Label label, Map<String, Object> rowData) {
        List<String> focusColumns = label.getFocusColumns();

        if (focusColumns != null && !focusColumns.isEmpty()) {
            // 只关注指定列
            StringBuilder sb = new StringBuilder();
            for (String column : focusColumns) {
                Object value = rowData.get(column);
                if (value != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(value.toString());
                }
            }
            return sb.toString();
        } else {
            // 使用所有数据
            StringBuilder sb = new StringBuilder();
            for (Object value : rowData.values()) {
                if (value != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(value.toString());
                }
            }
            return sb.toString();
        }
    }

    /**
     * 创建空结果
     */
    private Map<String, Object> createEmptyResult(long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("extractedData", new HashMap<>());
        result.put("confidence", 0);
        result.put("reasoning", "未提取到符合条件的信息");
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("totalCount", 0);
        result.put("extractResults", new ArrayList<>());
        return result;
    }

    /**
     * 创建失败结果
     */
    private Map<String, Object> createFailureResult(String errorMessage, long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("extractedData", null);
        result.put("confidence", 0);
        result.put("error", errorMessage);
        result.put("durationMs", System.currentTimeMillis() - startTime);
        return result;
    }

    /**
     * 获取所有可用的提取器元数据
     * 用于前端展示和用户选择
     */
    public List<ExtractorMetadata> getAvailableExtractors() {
        return extractorRegistry.getAllMetadata();
    }

    /**
     * 获取提取器注册中心
     */
    public ExtractorRegistry getRegistry() {
        return extractorRegistry;
    }

    /**
     * 批量提取（使用多个提取器）
     */
    public Map<String, Object> extractMultiple(Label label, Map<String, Object> rowData,
                                               List<String> extractorCodes, float confidenceThreshold) {
        long startTime = System.currentTimeMillis();
        String text = getTargetText(label, rowData);

        if (text == null || text.trim().isEmpty()) {
            return createFailureResult("目标文本为空", startTime);
        }

        Map<String, List<EnhancedExtractedResult>> allResults = new LinkedHashMap<>();

        // 使用每个提取器进行提取
        for (String code : extractorCodes) {
            IEnhancedExtractor extractor = extractorRegistry.getByCodeWithAlias(code);
            if (extractor == null) {
                log.warn("未找到提取器: {}", code);
                continue;
            }

            Map<String, Object> options = extractor.getDefaultOptions();
            List<EnhancedExtractedResult> results = extractor.extractWithContext(text, options);
            allResults.put(code, results);
        }

        // 合并结果
        List<EnhancedExtractedResult> mergedResults = allResults.values().stream()
            .flatMap(List::stream)
            .collect(Collectors.toList());

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("extractorCount", extractorCodes.size());
        result.put("resultCount", mergedResults.size());
        result.put("resultsByExtractor", allResults);
        result.put("durationMs", System.currentTimeMillis() - startTime);

        return result;
    }
}
