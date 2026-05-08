package com.datalabeling.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 号码类标签意图配置（存储于 labels.preprocessor_config 的 JSON 扩展字段 number_intent）。
 *
 * <p>目标：显式表达“实体 + 任务 + 输出策略”，避免依赖 description 的自然语言解析。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NumberIntentConfig {

    /**
     * 实体：phone / bank_card / id_card（字符串形式，后端会做容错归一化）。
     */
    @JsonProperty("entity")
    private String entity;

    /**
     * 任务：exists / extract / invalid / masked / invalid_length_masked（字符串形式，后端会做容错归一化）。
     */
    @JsonProperty("task")
    private String task;

    /**
     * 提取输出包含哪些类别：valid / invalid / masked（当 task=extract 时生效）。
     */
    @JsonProperty("include")
    private List<String> include;

    /**
     * 输出策略（可选）。
     */
    @JsonProperty("output")
    private Output output;

    /**
     * 业务口径策略（可选）。
     */
    @JsonProperty("policy")
    private Policy policy;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Output {
        /**
         * 输出格式：list / text（默认 list）。
         */
        @JsonProperty("format")
        private String format;

        /**
         * 最大输出条数（默认 50）。
         */
        @JsonProperty("maxItems")
        private Integer maxItems;

        /**
         * list 格式的拼接符（默认 "，"）。
         */
        @JsonProperty("joiner")
        private String joiner;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Policy {
        /**
         * 15位（一代身份证）是否视为有效（默认 true）。
         */
        @JsonProperty("id15_is_valid")
        @JsonAlias({"id15IsValid"})
        private Boolean id15IsValid;

        /**
         * 默认输出脱敏值（maskedValue）（默认 true）。
         */
        @JsonProperty("default_masked_output")
        @JsonAlias({"defaultMaskedOutput"})
        private Boolean defaultMaskedOutput;

        /**
         * 无效银行卡的弱信号是否需要关键词窗命中（默认 true，降低误判）。
         */
        @JsonProperty("require_keyword_for_invalid_bank")
        @JsonAlias({"requireKeywordForInvalidBank"})
        private Boolean requireKeywordForInvalidBank;

        /**
         * 身份证校验位不通过是否视为“无效”（默认 false；保持历史口径：invalid 仅包含长度错误）。
         */
        @JsonProperty("id_checksum_invalid_is_invalid")
        @JsonAlias({"idChecksumInvalidIsInvalid"})
        private Boolean idChecksumInvalidIsInvalid;

        /**
         * 18位身份证末位为 X/x 是否视为“无效”（默认 false；国家标准允许 X 作为校验位）。
         *
         * <p>适用于“仅允许数字”的业务口径：将末位X视为不合格录入。
         */
        @JsonProperty("id18_x_is_invalid")
        @JsonAlias({"id18XIsInvalid"})
        private Boolean id18XIsInvalid;
    }
}
