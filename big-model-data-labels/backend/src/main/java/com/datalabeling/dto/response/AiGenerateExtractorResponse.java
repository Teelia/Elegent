package com.datalabeling.dto.response;

import lombok.Data;

import java.util.List;

/**
 * AI生成提取器响应
 */
@Data
public class AiGenerateExtractorResponse {

    /**
     * 提取器代码建议
     */
    private String suggestedCode;

    /**
     * 提取器描述
     */
    private String description;

    /**
     * 生成的正则规则列表
     */
    private List<PatternSuggestion> patterns;

    /**
     * AI建议说明
     */
    private String explanation;

    /**
     * 正则规则建议
     */
    @Data
    public static class PatternSuggestion {
        /**
         * 规则名称
         */
        private String name;

        /**
         * 正则表达式
         */
        private String pattern;

        /**
         * 规则描述
         */
        private String description;

        /**
         * 优先级
         */
        private Integer priority;

        /**
         * 信心度
         */
        private Double confidence;

        /**
         * 验证类型
         */
        private String validationType;

        /**
         * 匹配示例
         */
        private String example;

        /**
         * 不匹配示例
         */
        private String negativeExample;
    }
}
