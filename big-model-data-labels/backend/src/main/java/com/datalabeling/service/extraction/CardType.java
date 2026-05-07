package com.datalabeling.service.extraction;

import lombok.Data;

import java.util.regex.Pattern;

/**
 * 中国卡号类型枚举
 *
 * 定义各种号码类型的特征和识别规则
 */
public enum CardType {
    /**
     * 银行卡号
     *
     * 标准特征：
     * - 长度：通常16位（信用卡）或16-19位（借记卡）
     * - BIN码：开头6位代表发卡行
     * - Luhn算法校验
     *
     * 中国银联BIN码：
     * - 62xxxx：中国银联
     * - 621-625：各大银行
     */
    BANK_CARD("银行卡号", 16, 19, CardPattern.BANK_CARD, Priority.HIGHEST),

    /**
     * 身份证号
     *
     * 标准特征：
     * - 一代身份证：15位纯数字（已停用）
     * - 二代身份证：18位，17位数字+1位数字或X
     * - 前6位：地区码
     * - 第7-14位：出生日期YYYYMMDD
     * - 第15-17位：顺序码
     * - 第18位：校验码（0-9或X）
     *
     * 扩展范围说明：
     * - 最小14位：捕获少1位的错误身份证号
     * - 最大22位：捕获多1-4位的错误身份证号（19-22位）
     * - 标准格式验证会严格过滤，扩展范围仅用于识别候选号码
     */
    ID_CARD("身份证号", 14, 22, CardPattern.ID_CARD, Priority.HIGH),

    /**
     * 手机号
     *
     * 标准特征：
     * - 长度：固定11位
     * - 格式：1开头，第二位3-9
     * - 号段：134-139, 150-152, 157-159, 182-184, 187-188, 178等
     */
    PHONE_NUMBER("手机号", 11, 11, CardPattern.PHONE_NUMBER, Priority.MEDIUM),

    /**
     * 社保卡号
     *
     * 标准特征：
     * - 长度：通常9-18位
     * - 格式：各地区不统一
     */
    SOCIAL_CARD("社保卡号", 9, 18, CardPattern.SOCIAL_CARD, Priority.LOW);

    private final String displayName;
    private final int minLength;
    private final int maxLength;
    private final CardPattern pattern;
    private final Priority priority;

    CardType(String displayName, int minLength, int maxLength, CardPattern pattern, Priority priority) {
        this.displayName = displayName;
        this.minLength = minLength;
        this.maxLength = maxLength;
        this.pattern = pattern;
        this.priority = priority;
    }

    public String getDisplayName() {
        return displayName;
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public Pattern getPattern() {
        return pattern.getPattern();
    }

    public Priority getPriority() {
        return priority;
    }

    /**
     * 卡号模式定义
     */
    private enum CardPattern {
        BANK_CARD("^(?:4[0-9]{12}(?:[0-9]{3})?|5[1-5][0-9]{14}|6[2-5]\\d{13,17})$"),
        ID_CARD("^[1-9]\\d{5}(19|20)\\d{2}(0[1-9]|1[0-2])\\d{3}[\\dXx]$|^\\d{15}$"),
        PHONE_NUMBER("^1[3-9]\\d{9}$"),
        SOCIAL_CARD("^\\d{9,18}$");

        private final Pattern pattern;

        CardPattern(String regex) {
            this.pattern = Pattern.compile(regex);
        }

        public Pattern getPattern() {
            return pattern;
        }
    }

    /**
     * 优先级枚举
     */
    public enum Priority {
        HIGHEST,  // 最高优先级（银行卡号）
        HIGH,     // 高优先级（身份证号）
        MEDIUM,   // 中等优先级（手机号）
        LOW       // 低优先级（其他）
    }

    /**
     * 号码识别结果
     */
    @Data
    public static class IdentificationResult {
        private final CardType type;
        private final String number;
        private final boolean isValidFormat;
        private final boolean isPartialMatch;

        public IdentificationResult(CardType type, String number, boolean isValidFormat, boolean isPartialMatch) {
            this.type = type;
            this.number = number;
            this.isValidFormat = isValidFormat;
            this.isPartialMatch = isPartialMatch;
        }

        public String getReason() {
            if (isValidFormat) {
                return String.format("%s格式正确", type.getDisplayName());
            }
            return String.format("%s格式错误", type.getDisplayName());
        }
    }
}
