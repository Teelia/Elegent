package com.datalabeling.service.extraction;

import lombok.Data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 号码证据建模结果（规则侧 SSOT）。
 *
 * <p>用于：
 * 1) 否定条件/号码类任务的确定性提取
 * 2) 给 LLM 提供“可引用的证据”，避免模型凭空生成号码
 *
 * <p>注意：对外展示/提示词建议使用 maskedValue，避免泄露完整号码。
 */
@Data
public class NumberEvidence {

    /**
     * 证据版本号，用于后续演进兼容。
     */
    private String evidenceVersion = "1.1";

    /**
     * 结构化候选列表（含校验与冲突信息）。
     */
    private List<NumberCandidate> numbers = new ArrayList<>();

    /**
     * 派生结论（便于直接用于标签判断）。
     */
    private Map<String, Object> derived = new HashMap<>();

    @Data
    public static class NumberCandidate {
        /**
         * 证据ID（用于 LLM 引用）。
         */
        private String id;

        /**
         * 类型（例如：ID_VALID_18 / ID_VALID_15 / ID_INVALID_LENGTH / ID_INVALID_CHECKSUM / BANK_CARD / PHONE 等）。
         */
        private String type;

        /**
         * 原始值（可能包含敏感信息；默认不建议用于日志/提示词）。
         */
        private String value;

        /**
         * 脱敏值（建议对外展示/提示词使用）。
         */
        private String maskedValue;

        private int length;
        private int startIndex;
        private int endIndex;

        /**
         * 是否为“遮挡/脱敏片段”（例如包含 *）。
         */
        private Boolean masked;

        /**
         * 遮挡统计：数字个数/遮挡符个数（用于审计与一致性校验）。
         */
        private Integer digitCount;
        private Integer maskCount;

        /**
         * 遮挡模式摘要（可选）：例如 "4+*3+4"（前4位+3个*+后4位）。
         */
        private String maskPattern;

        /**
         * 命中的上下文关键词（可选，用于“无效银行卡/手机号”弱信号判定时审计）。
         * 仅存关键词本身，不存原文片段，避免引入新的PII泄露面。
         */
        private String keywordHint;

        /**
         * 规则侧置信度（0-1）。
         */
        private float confidenceRule;

        /**
         * 校验信息（结构/日期/校验位/Luhn 等）。
         */
        private List<ValidationItem> validations = new ArrayList<>();

        /**
         * 冲突记录（例如同时满足 Luhn 与身份证结构特征）。
         */
        private List<ConflictItem> conflicts = new ArrayList<>();
    }

    @Data
    public static class ValidationItem {
        private String name;
        private boolean pass;
        private String detail;

        public static ValidationItem of(String name, boolean pass, String detail) {
            ValidationItem v = new ValidationItem();
            v.name = name;
            v.pass = pass;
            v.detail = detail;
            return v;
        }
    }

    @Data
    public static class ConflictItem {
        private String withType;
        private String reason;
        private String resolvedAs;

        public static ConflictItem of(String withType, String reason, String resolvedAs) {
            ConflictItem c = new ConflictItem();
            c.withType = withType;
            c.reason = reason;
            c.resolvedAs = resolvedAs;
            return c;
        }
    }
}
