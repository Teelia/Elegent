package com.datalabeling.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 预处理器配置
 * 用于分类标签的规则预处理
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PreprocessorConfig {

    /**
     * 启用的提取器列表
     * 例如: ["id_card", "phone", "bank_card"]
     */
    @JsonProperty("extractors")
    private List<String> extractors;

    /**
     * 动态提取器选项（前端通用结构）
     *
     * <p>结构示例：</p>
     * <pre>
     * {
     *   "extractorOptions": {
     *     "id_card": {"include18Digit": true},
     *     "keyword_match": {"keywords": ["请求撤警"], "matchType": "any"}
     *   }
     * }
     * </pre>
     */
    @JsonProperty("extractorOptions")
    private Map<String, Map<String, Object>> extractorOptions;

    /**
     * 身份证提取器选项
     */
    @JsonProperty("idCardOptions")
    private IdCardOptions idCardOptions;

    /**
     * 手机号提取器选项
     */
    @JsonProperty("phoneOptions")
    private PhoneOptions phoneOptions;

    /**
     * 银行卡提取器选项
     */
    @JsonProperty("bankCardOptions")
    private BankCardOptions bankCardOptions;

    /**
     * 号码类标签意图配置（JSON 扩展字段，显式表达实体/任务/输出策略）。
     * 存储字段名：number_intent
     */
    @JsonProperty("number_intent")
    private NumberIntentConfig numberIntent;

    /**
     * 身份证提取器选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IdCardOptions {
        /**
         * 包含18位标准身份证号
         */
        @JsonProperty("include18Digit")
        @Builder.Default
        private boolean include18Digit = true;

        /**
         * 包含15位旧版身份证号
         */
        @JsonProperty("include15Digit")
        @Builder.Default
        private boolean include15Digit = true;

        /**
         * 包含疑似身份证号
         */
        @JsonProperty("includeLoose")
        @Builder.Default
        private boolean includeLoose = true;
    }

    /**
     * 手机号提取器选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PhoneOptions {
        /**
         * 包含中国移动号段
         */
        @JsonProperty("includeChinaMobile")
        @Builder.Default
        private boolean includeChinaMobile = true;

        /**
         * 包含中国联通号段
         */
        @JsonProperty("includeChinaUnicom")
        @Builder.Default
        private boolean includeChinaUnicom = true;

        /**
         * 包含中国电信号段
         */
        @JsonProperty("includeChinaTelecom")
        @Builder.Default
        private boolean includeChinaTelecom = true;

        /**
         * 包含疑似手机号
         */
        @JsonProperty("includeLoose")
        @Builder.Default
        private boolean includeLoose = false;
    }

    /**
     * 银行卡提取器选项
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class BankCardOptions {
        /**
         * 包含16位银行卡号
         */
        @JsonProperty("include16Digit")
        @Builder.Default
        private boolean include16Digit = true;

        /**
         * 包含17-19位银行卡号
         */
        @JsonProperty("includeOtherLength")
        @Builder.Default
        private boolean includeOtherLength = true;

        /**
         * 验证BIN码（发卡行识别）
         */
        @JsonProperty("validateBin")
        @Builder.Default
        private boolean validateBin = false;

        /**
         * 使用Luhn算法校验
         */
        @JsonProperty("useLuhnValidation")
        @Builder.Default
        private boolean useLuhnValidation = true;
    }

    /**
     * 从JSON字符串解析配置
     */
    public static PreprocessorConfig fromJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            // 返回默认配置
            return PreprocessorConfig.builder()
                .extractors(new java.util.ArrayList<>())
                .idCardOptions(IdCardOptions.builder().build())
                .phoneOptions(PhoneOptions.builder().build())
                .bankCardOptions(BankCardOptions.builder().build())
                .extractorOptions(new java.util.HashMap<>())
                .numberIntent(null)
                .build();
        }

        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.readValue(json, PreprocessorConfig.class);
        } catch (Exception e) {
            throw new IllegalArgumentException("解析预处理器配置失败: " + e.getMessage(), e);
        }
    }

    /**
     * 转换为JSON字符串
     */
    public String toJson() {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            return mapper.writeValueAsString(this);
        } catch (Exception e) {
            throw new RuntimeException("转换预处理器配置为JSON失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查是否启用了特定提取器
     */
    public boolean isExtractorEnabled(String extractorType) {
        return extractors != null && extractors.contains(extractorType);
    }

    /**
     * 获取某个提取器的动态选项（不存在则返回空 Map）
     */
    public Map<String, Object> getExtractorOptions(String extractorType) {
        if (extractorOptions == null || extractorType == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> options = extractorOptions.get(extractorType);
        return options != null ? options : Collections.emptyMap();
    }
}
