package com.datalabeling.service;

import com.datalabeling.entity.Label;
import com.datalabeling.service.extraction.ExtractionOrchestrator;
import com.datalabeling.service.extraction.ExtractedNumber;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 提示词模板引擎
 * 负责将模板中的变量替换为实际值
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateEngine {

    private final ExtractionOrchestrator extractionOrchestrator;
    private final ObjectMapper objectMapper;

    /**
     * 变量匹配正则：{{variable_name}}
     */
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(#\\w+\\s+)?(\\w+)(?:\\s+[^}]*?)?}}");

    /**
     * 条件块匹配正则：{{#if condition}}...{{/if}}
     */
    private static final Pattern CONDITION_PATTERN = Pattern.compile("\\{\\{#if\\s+(\\w+)\\}}(.*?)\\{\\{/if\\}}", Pattern.DOTALL);

    /**
     * 渲染提示词模板
     *
     * @param prompt 提示词模板
     * @param label  标签
     * @param rowData 数据行
     * @param preprocessorResult 预处理结果（可选）
     * @param extractedNumbers 提取的号码（可选）
     * @return 渲染后的提示词
     */
    public String renderTemplate(String prompt, Label label, Map<String, Object> rowData,
                                  String preprocessorResult, List<ExtractedNumber> extractedNumbers) {
        try {
            // 构建变量上下文
            Map<String, Object> context = buildContext(label, rowData, preprocessorResult, extractedNumbers);

            // 处理条件块
            String processed = processConditions(prompt, context);

            // 替换变量
            processed = replaceVariables(processed, context);

            log.debug("模板渲染完成，结果长度: {}", processed.length());
            return processed;

        } catch (Exception e) {
            log.error("模板渲染失败", e);
            throw new RuntimeException("模板渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 渲染二次强化提示词模板
     */
    public String renderEnhancementTemplate(String prompt, Label label, Map<String, Object> rowData,
                                            String initialResult, int initialConfidence,
                                            String initialReasoning, String validationResult) {
        try {
            Map<String, Object> context = new HashMap<>();
            context.put("label_name", label.getName());
            context.put("label_description", label.getDescription());
            context.put("row_data_json", objectMapper.writeValueAsString(rowData));
            context.put("initial_result", initialResult);
            context.put("initial_confidence", initialConfidence);
            context.put("initial_reasoning", initialReasoning);
            context.put("validation_result", validationResult);

            // 处理条件块
            String processed = processConditions(prompt, context);

            // 替换变量
            processed = replaceVariables(processed, context);

            return processed;

        } catch (Exception e) {
            log.error("强化模板渲染失败", e);
            throw new RuntimeException("强化模板渲染失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建变量上下文
     */
    private Map<String, Object> buildContext(Label label, Map<String, Object> rowData,
                                              String preprocessorResult, List<ExtractedNumber> extractedNumbers) {
        Map<String, Object> context = new HashMap<>();

        // 基础变量
        context.put("label_name", label.getName());
        context.put("label_description", label.getDescription());

        // 关注列
        List<String> focusColumns = label.getFocusColumns();
        context.put("focus_columns", focusColumns != null && !focusColumns.isEmpty()
            ? String.join(", ", focusColumns) : "全部");

        // 提取字段
        List<String> extractFields = label.getExtractFields();
        context.put("extract_fields", extractFields != null && !extractFields.isEmpty()
            ? String.join(", ", extractFields) : "全部");

        // 原始数据 JSON
        try {
            context.put("row_data_json", objectMapper.writeValueAsString(rowData));
        } catch (Exception e) {
            context.put("row_data_json", rowData.toString());
        }

        // 预处理结果
        context.put("preprocessor_result", preprocessorResult != null ? preprocessorResult : "");

        // 提取的号码
        if (extractedNumbers != null && !extractedNumbers.isEmpty()) {
            StringBuilder sb = new StringBuilder();
            for (ExtractedNumber num : extractedNumbers) {
                if (sb.length() > 0) sb.append("\n");
                sb.append("- ").append(num.getField())
                  .append(": ").append(num.getValue())
                  .append(" (").append(num.getValidation()).append(")");
            }
            context.put("extracted_numbers", sb.toString());
        } else {
            context.put("extracted_numbers", "");
        }

        return context;
    }

    /**
     * 处理条件块 {{#if condition}}...{{/if}}
     */
    private String processConditions(String template, Map<String, Object> context) {
        String result = template;
        Matcher matcher = CONDITION_PATTERN.matcher(template);

        while (matcher.find()) {
            String variable = matcher.group(1);
            String content = matcher.group(2);
            Object value = context.get(variable);

            // 判断条件：非空、非false、非空字符串
            boolean conditionMet = value != null
                && !Boolean.FALSE.equals(value)
                && !"".equals(value);

            String replacement = conditionMet ? content : "";
            result = result.substring(0, matcher.start()) + replacement + result.substring(matcher.end());

            // 重新匹配，因为内容已变化
            matcher = CONDITION_PATTERN.matcher(result);
        }

        return result;
    }

    /**
     * 替换变量 {{variable}}
     */
    private String replaceVariables(String template, Map<String, Object> context) {
        String result = template;
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        while (matcher.find()) {
            String fullMatch = matcher.group(0);  // {{#if xxx}} 或 {{variable}}
            String variable = matcher.group(2);   // variable_name

            Object value = context.get(variable);
            String replacement = value != null ? value.toString() : "";

            result = result.replace(fullMatch, replacement);

            // 重新匹配
            matcher = VARIABLE_PATTERN.matcher(result);
        }

        return result;
    }

    /**
     * 获取默认的分类提示词
     */
    public static String getDefaultClassificationPrompt() {
        return "你是数据标注助手。请根据以下规则判断这行数据是否符合标签定义。\n" +
            "\n" +
            "标签名称: {{label_name}}\n" +
            "标签规则: {{label_description}}\n" +
            "关注列: {{focus_columns}}\n" +
            "\n" +
            "{{#if preprocessor_result}}\n" +
            "=== 规则预处理结果 ===\n" +
            "{{preprocessor_result}}\n" +
            "{{/if}}\n" +
            "\n" +
            "=== 原始数据 ===\n" +
            "{{row_data_json}}\n" +
            "\n" +
            "请仅回答\"是\"或\"否\"，不要有任何额外解释。";
    }

    /**
     * 获取默认的强化提示词
     */
    public static String getDefaultEnhancementPrompt() {
        return "你是数据质量审核专家。请对以下初步分析结果进行二次验证和强化。\n" +
            "\n" +
            "=== 任务信息 ===\n" +
            "标签名称: {{label_name}}\n" +
            "标签规则: {{label_description}}\n" +
            "\n" +
            "=== 原始数据 ===\n" +
            "{{row_data_json}}\n" +
            "\n" +
            "=== 初步分析结果 ===\n" +
            "判断: {{initial_result}}\n" +
            "置信度: {{initial_confidence}}%\n" +
            "推理: {{initial_reasoning}}\n" +
            "\n" +
            "{{#if validation_result}}\n" +
            "=== 规则验证结果 ===\n" +
            "{{validation_result}}\n" +
            "{{/if}}\n" +
            "\n" +
            "=== 二次分析要求 ===\n" +
            "请重新审视初步分析结果，重点关注：\n" +
            "1. 初步结论是否基于充分的证据？\n" +
            "2. 推理逻辑是否存在漏洞？\n" +
            "3. 是否被号码格式等因素误导？\n" +
            "4. 置信度是否合理？\n" +
            "\n" +
            "请输出JSON格式：\n" +
            "{\n" +
            "  \"final_result\": \"维持原判\"或\"修正为是/否\",\n" +
            "  \"final_confidence\": 0-100,\n" +
            "  \"validation_notes\": \"二次审核发现的问题或确认的理由\",\n" +
            "  \"should_adjust\": true/false,\n" +
            "  \"adjustment_reason\": \"如果需要修正，说明原因\"\n" +
            "}";
    }
}
