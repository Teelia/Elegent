package com.datalabeling.service.extraction;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * 增强型提取结果
 * 包含丰富的上下文信息，便于大模型理解和分析
 */
@Data
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
public class EnhancedExtractedResult extends ExtractedNumber {

    /**
     * 原始值（提取的原始字符串）
     */
    private String rawValue;

    /**
     * 标准化值（经过格式转换、清洗的值）
     * 例如：日期统一为ISO格式、金额统一为数字等
     */
    private Object normalizedValue;

    /**
     * 数据类型（string/integer/decimal/date/email/url等）
     */
    private String dataType;

    /**
     * 业务含义
     * 解释这个数据在业务场景中的含义
     */
    private String businessMeaning;

    /**
     * 验证状态（valid/invalid/unknown）
     */
    private String validationStatus;

    /**
     * 验证详情
     */
    private String validationDetail;

    /**
     * 额外属性
     * 存储提取的附加信息（如：邮箱域名、银行卡类型、手机号运营商等）
     */
    private Map<String, Object> attributes;

    /**
     * 上下文文本
     * 提取值周围的上下文（前后各N个字符）
     */
    private String context;

    /**
     * 源字段
     * 如果是从特定字段提取的，记录字段名
     */
    private String sourceField;

    /**
     * 提取器版本
     * 记录使用的提取器版本
     */
    private String extractorVersion;

    /**
     * 创建高置信度增强结果
     */
    public static EnhancedExtractedResult highConfidence(String field, String value, String validation, String businessMeaning) {
        return EnhancedExtractedResult.builder()
            .field(field)
            .value(value)
            .rawValue(value)
            .confidence(0.95f)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning(businessMeaning)
            .build();
    }

    /**
     * 创建中等置信度增强结果
     */
    public static EnhancedExtractedResult mediumConfidence(String field, String value, String validation, String businessMeaning) {
        return EnhancedExtractedResult.builder()
            .field(field)
            .value(value)
            .rawValue(value)
            .confidence(0.75f)
            .validation(validation)
            .validationStatus("unknown")
            .businessMeaning(businessMeaning)
            .build();
    }

    /**
     * 转换为JSON格式用于大模型提示词
     */
    public String toLLMJSON() {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"field\": \"").append(escapeJson(getField() != null ? getField() : "")).append("\",\n");
        sb.append("  \"value\": \"").append(escapeJson(getValue() != null ? getValue() : "")).append("\",\n");
        sb.append("  \"dataType\": \"").append(dataType != null ? dataType : "string").append("\",\n");
        sb.append("  \"confidence\": ").append(Math.round(getConfidence() * 100)).append(",\n");
        sb.append("  \"validation\": \"").append(escapeJson(getValidation() != null ? getValidation() : "")).append("\"");

        // 添加业务含义（如果有）
        if (businessMeaning != null && !businessMeaning.isEmpty()) {
            sb.append(",\n  \"businessMeaning\": \"").append(escapeJson(businessMeaning)).append("\"");
        }

        // 添加附加属性（如果有）
        if (attributes != null && !attributes.isEmpty()) {
            sb.append(",\n  \"attributes\": {");
            boolean first = true;
            for (Map.Entry<String, Object> entry : attributes.entrySet()) {
                if (!first) {
                    sb.append(", ");
                }
                String attrValue = entry.getValue() != null ? entry.getValue().toString() : "";
                sb.append("\"").append(escapeJson(entry.getKey())).append("\": \"")
                  .append(escapeJson(attrValue)).append("\"");
                first = false;
            }
            sb.append("}");
        }

        sb.append("\n}");
        return sb.toString();
    }

    /**
     * JSON字符串转义
     */
    private String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
