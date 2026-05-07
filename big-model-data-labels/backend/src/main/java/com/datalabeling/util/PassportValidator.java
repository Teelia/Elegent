package com.datalabeling.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 护照号验证器
 * <p>
 * 验证中国护照和外籍护照的格式
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
@Component
public class PassportValidator {

    /**
     * 中国护照：G/E/P + 8位数字
     * - G: 普通护照
     * - E: 电子护照
     * - P: 公务护照
     */
    private static final Pattern CN_PASSPORT = Pattern.compile("\\b[GEPM]\\d{8}\\b");

    /**
     * 外籍护照：1-2个大写字母开头 + 6-9位数字
     * 常见格式：XX1234567, X123456789
     */
    private static final Pattern FOREIGN_PASSPORT = Pattern.compile("\\b[A-Z]{1,2}\\d{6,9}\\b");

    /**
     * 香港特区护照：H/K + 7位数字
     */
    private static final Pattern HK_PASSPORT = Pattern.compile("\\b[HK]\\d{7}\\b");

    /**
     * 澳门特区护照：M + 7位数字
     */
    private static final Pattern MO_PASSPORT = Pattern.compile("\\bM\\d{7}\\b");

    /**
     * 台湾地区通行证：8位数字
     */
    private static final Pattern TW_PASSPORT = Pattern.compile("\\b\\d{8}\\b");

    /**
     * 验证护照号格式
     *
     * @param passport 护照号
     * @return 验证结果
     */
    public ValidationResult validate(String passport) {
        if (passport == null || passport.trim().isEmpty()) {
            return ValidationResult.invalid("护照号为空");
        }

        String trimmed = passport.trim();

        // 检查中国护照
        if (CN_PASSPORT.matcher(trimmed).matches()) {
            return ValidationResult.valid("中国护照格式正确");
        }

        // 检查港澳特区护照
        if (HK_PASSPORT.matcher(trimmed).matches() || MO_PASSPORT.matcher(trimmed).matches()) {
            return ValidationResult.valid("港澳特区护照格式正确");
        }

        // 检查外籍护照（更宽松的校验）
        if (FOREIGN_PASSPORT.matcher(trimmed).matches()) {
            // 外籍护照格式较多，标记为需要人工确认
            return ValidationResult.valid("外籍护照格式正确（建议人工确认）");
        }

        // 检查台湾通行证
        if (TW_PASSPORT.matcher(trimmed).matches()) {
            return ValidationResult.valid("台湾通行证格式正确");
        }

        return ValidationResult.invalid(
                String.format("护照号格式错误：%s（应为G/E/P+8位数字或其他标准格式）", trimmed));
    }

    /**
     * 从文本中提取并验证护照号
     *
     * @param text 文本
     * @return 提取和验证结果
     */
    public ExtractResult extractAndValidate(String text) {
        ExtractResult result = new ExtractResult();

        // 合并匹配所有护照格式
        String patternStr = "[GEPM]\\d{8}\\b|[HK]\\d{7}\\b|M\\d{7}\\b|\\d{8}\\b|[A-Z]{1,2}\\d{6,9}\\b";
        Pattern pattern = Pattern.compile(patternStr);
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String passport = matcher.group();
            ValidationResult vr = validate(passport);

            ExtractedItem item = new ExtractedItem();
            item.setValue(passport);
            item.setValid(vr.isValid());
            item.setReason(vr.getReason());
            item.setPassportType(determinePassportType(passport));

            result.getItems().add(item);

            if (vr.isValid()) {
                result.setValidCount(result.getValidCount() + 1);
            } else {
                result.setInvalidCount(result.getInvalidCount() + 1);
                result.getInvalidItems().add(item);
            }
        }

        return result;
    }

    /**
     * 检测文本中是否有不符合标准的护照号
     * <p>
     * 例如：C789056321（9位，非标准格式）
     * </p>
     *
     * @param text 文本
     * @return 检测结果
     */
    public DetectionResult detectSuspiciousPassports(String text) {
        DetectionResult result = new DetectionResult();

        // 查找可能的护照号格式但不符合标准的情况
        // 模式：单个大写字母 + 9位数字（非标准格式，应该是G/E/P + 8位）
        // 使用负向前瞻确保后面不是字母或数字，避免使用\b导致标点符号后匹配失败
        Pattern suspiciousPattern = Pattern.compile("[A-Z]\\d{9}(?=[^A-Za-z0-9]|$)");
        Matcher matcher = suspiciousPattern.matcher(text);

        while (matcher.find()) {
            String passport = matcher.group();
            result.addSuspiciousPassport(passport, "以" + passport.charAt(0) + "开头，9位数字，非标准护照格式（标准应为G/E/P+8位）");
            log.debug("检测到可疑护照号: {}", passport);
        }

        // 检查数字开头的护照号（异常）
        // 排除18位身份证号和15位营业执照号
        Pattern numberStartPattern = Pattern.compile("(?<![A-Za-z0-9])\\d{8,10}(?=[^A-Za-z0-9]|$)");
        Matcher numberMatcher = numberStartPattern.matcher(text);

        while (numberMatcher.find()) {
            String num = numberMatcher.group();
            // 排除身份证号（18位）和营业执照号（15位）
            if (num.length() != 18 && num.length() != 15) {
                result.addSuspiciousPassport(num, "数字开头，可能是未识别的护照号格式");
            }
        }

        return result;
    }

    /**
     * 判断护照类型
     */
    private String determinePassportType(String passport) {
        char firstChar = passport.charAt(0);
        switch (Character.toUpperCase(firstChar)) {
            case 'G':
            case 'E':
            case 'P':
            case 'M':
                return "中国普通/电子护照";
            case 'H':
            case 'K':
                return "香港特区护照";
            default:
                if (passport.length() == 8 && passport.matches("\\d+")) {
                    return "台湾通行证";
                }
                return "外籍护照";
        }
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        public static ValidationResult valid() {
            return new ValidationResult(true, "格式正确");
        }

        public static ValidationResult valid(String reason) {
            return new ValidationResult(true, reason);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }

    /**
     * 提取结果
     */
    @Data
    public static class ExtractResult {
        private List<ExtractedItem> items = new ArrayList<>();
        private int validCount = 0;
        private int invalidCount = 0;
        private List<ExtractedItem> invalidItems = new ArrayList<>();

        public boolean hasInvalid() {
            return invalidCount > 0;
        }

        public String getInvalidReasons() {
            return items.stream()
                    .filter(item -> !item.isValid())
                    .map(item -> String.format("%s: %s", item.getValue(), item.getReason()))
                    .collect(Collectors.joining("; "));
        }

        /**
         * 获取所有有效的护照号列表
         */
        public List<String> getValidPassports() {
            return items.stream()
                    .filter(ExtractedItem::isValid)
                    .map(ExtractedItem::getValue)
                    .collect(Collectors.toList());
        }

        /**
         * 检查是否存在无效护照号
         */
        public boolean hasSuspiciousPassports() {
            return !invalidItems.isEmpty();
        }
    }

    /**
     * 提取项
     */
    @Data
    public static class ExtractedItem {
        private String value;
        private boolean valid;
        private String reason;
        private String passportType;
    }

    /**
     * 可疑护照号检测结果
     */
    @Data
    public static class DetectionResult {
        private List<SuspiciousItem> suspiciousItems = new ArrayList<>();

        public void addSuspiciousPassport(String passport, String reason) {
            suspiciousItems.add(new SuspiciousItem(passport, reason));
        }

        public boolean hasSuspicious() {
            return !suspiciousItems.isEmpty();
        }

        public List<String> getPassports() {
            return suspiciousItems.stream()
                    .map(SuspiciousItem::getPassport)
                    .collect(Collectors.toList());
        }

        @Data
        public static class SuspiciousItem {
            private final String passport;
            private final String reason;
        }
    }
}
