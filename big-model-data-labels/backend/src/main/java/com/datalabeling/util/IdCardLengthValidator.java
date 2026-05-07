package com.datalabeling.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 身份证号长度严格验证器
 * <p>
 * 仅接受18位身份证号（前17位为数字，第18位为数字或X）
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
public class IdCardLengthValidator {

    /**
     * 18位身份证号正则：前17位数字 + 第18位数字或X/x
     */
    private static final Pattern VALID_ID_PATTERN = Pattern.compile("^\\d{17}[0-9Xx]$");

    /**
     * 15位身份证号正则（兼容老身份证，某些场景可能需要）
     */
    private static final Pattern LEGACY_ID_PATTERN = Pattern.compile("^\\d{15}$");

    /**
     * 验证身份证号格式
     *
     * @param idNumber 身份证号
     * @param allowLegacy 是否允许15位老身份证号
     * @return 验证结果
     */
    public static ValidationResult validate(String idNumber, boolean allowLegacy) {
        if (idNumber == null || idNumber.trim().isEmpty()) {
            return ValidationResult.invalid("身份证号为空");
        }

        String trimmed = idNumber.trim();

        // 检查长度
        if (trimmed.length() == 18) {
            return validate18Bit(trimmed);
        } else if (trimmed.length() == 15 && allowLegacy) {
            return validate15Bit(trimmed);
        } else {
            return ValidationResult.invalid(
                    String.format("身份证号长度错误：%d位（必须18位%s）",
                            trimmed.length(),
                            allowLegacy ? "或15位" : "")
            );
        }
    }

    /**
     * 验证18位身份证号（严格模式）
     */
    private static ValidationResult validate18Bit(String idNumber) {
        if (!VALID_ID_PATTERN.matcher(idNumber).matches()) {
            return ValidationResult.invalid(
                    "身份证号格式错误：前17位必须为数字，第18位为数字或X"
            );
        }

        // 可选：校验位验证
        // 注意：根据业务需求，可能不需要严格校验位
        // String checksumResult = validateChecksum(idNumber);
        // if (checksumResult != null) {
        //     return ValidationResult.invalid(checksumResult);
        // }

        return ValidationResult.valid();
    }

    /**
     * 验证15位身份证号（兼容模式）
     */
    private static ValidationResult validate15Bit(String idNumber) {
        if (!LEGACY_ID_PATTERN.matcher(idNumber).matches()) {
            return ValidationResult.invalid("15位身份证号格式错误：必须全为数字");
        }
        return ValidationResult.valid();
    }

    /**
     * 校验位验证（可选实现）
     * ISO 7064:1983.MOD 11-2 校验算法
     */
    private static String validateChecksum(String idNumber) {
        try {
            int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
            char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

            int sum = 0;
            for (int i = 0; i < 17; i++) {
                sum += (idNumber.charAt(i) - '0') * weights[i];
            }

            int checkIndex = sum % 11;
            char expectedCheckChar = checkChars[checkIndex];
            char actualCheckChar = Character.toUpperCase(idNumber.charAt(17));

            if (expectedCheckChar != actualCheckChar) {
                return String.format("校验位错误：应为%c，实际为%c", expectedCheckChar, actualCheckChar);
            }

        } catch (Exception e) {
            return "校验位计算失败";
        }

        return null;
    }

    /**
     * 快速验证（仅检查格式，不检查校验位）
     */
    public static boolean quickValidate(String idNumber) {
        return validate(idNumber, false).isValid();
    }

    /**
     * 从文本中提取并验证身份证号（增强版，检测前缀字符）
     * <p>
     * 增强功能：
     * - 检测身份证号前的特殊字符（? ？ # 等）
     * - 返回原始值和清理后的值
     * </p>
     *
     * @param text      文本
     * @param allowLegacy 是否允许15位
     * @return 提取和验证结果
     */
    public static ExtractResult extractAndValidate(String text, boolean allowLegacy) {
        ExtractResult result = new ExtractResult();

        // 匹配可能带前缀的身份证号：前缀字符 + 18位数字
        // 模式：0-3个前缀字符 + 17位数字 + 校验位
        Pattern pattern = Pattern.compile("[?？#＊\\s]{0,3}(\\d{17}[0-9Xx])");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String rawId = matcher.group(0);  // 完整匹配（包含前缀字符）
            String cleanId = rawId.replaceAll("[^0-9Xx]", "");  // 清理后纯数字（移除前缀）

            ExtractedItem item = new ExtractedItem();
            item.setRawValue(rawId);  // 保留原始值
            item.setValue(cleanId);   // 清理后的值

            // 检测是否有“非空白”的前缀字符（空白仅作为分隔符，不应触发前缀错误）
            // 典型误判来源：LLM/格式化输出在身份证前多了空格或换行，rawId 会以空白开头。
            String specialPrefix = rawId.replaceAll("[0-9Xx\\s]", "");
            if (!specialPrefix.isEmpty()) {
                item.setValid(false);
                item.setReason(String.format("身份证号前存在前缀字符（如?#*等），导致格式错误：%s", rawId));
                log.debug("检测到带前缀字符的身份证号: raw={}, clean={}", rawId, cleanId);
            } else {
                // 正常验证（允许 rawId 含空白分隔）
                ValidationResult vr = validate(cleanId, allowLegacy);
                item.setValid(vr.isValid());
                item.setReason(vr.getReason());
            }

            result.getItems().add(item);

            if (item.isValid()) {
                result.setValidCount(result.getValidCount() + 1);
            } else {
                result.setInvalidCount(result.getInvalidCount() + 1);
                result.getInvalidItems().add(item);
            }
        }

        return result;
    }

    /**
     * 从文本中提取身份证号的原始值（保留可能的前缀）
     * <p>
     * 用于需要检查前缀字符的场景
     * </p>
     *
     * @param text 文本
     * @return 提取的原始身份证号列表
     */
    public static List<String> extractRawIdCards(String text) {
        List<String> rawIds = new ArrayList<>();
        Pattern pattern = Pattern.compile("[?？#＊\\s]{0,3}(\\d{17}[0-9Xx])");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            rawIds.add(matcher.group(0));  // 完整匹配（包含前缀字符）
        }

        return rawIds;
    }

    /**
     * 检查是否存在带前缀的身份证号
     *
     * @param text 文本
     * @return true 表示存在带前缀的身份证号
     */
    public static boolean hasPrefixIdCard(String text) {
        List<String> rawIds = extractRawIdCards(text);
        for (String rawId : rawIds) {
            // 仅当存在非空白前缀字符时才视为“前缀错误”（空白不算前缀）
            String specialPrefix = rawId.replaceAll("[0-9Xx\\s]", "");
            if (!specialPrefix.isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /**
     * 检测错误位数的身份证号
     * <p>
     * 错误位数包括：14位、16位、17位、19位、20位、21位等非15/18位的数字序列
     * </p>
     *
     * @param text 文本
     * @return 检测结果
     */
    public static InvalidLengthDetection detectInvalidLengthIdCards(String text) {
        InvalidLengthDetection result = new InvalidLengthDetection();

        // 匹配14-21位之间的数字序列（但排除15位和18位）
        // 使用负向前瞻确保后面不是数字或字母X
        Pattern pattern = Pattern.compile("(?<![0-9Xx])(\\d{14,21})(?![0-9Xx])");
        Matcher matcher = pattern.matcher(text);

        while (matcher.find()) {
            String number = matcher.group(1);
            int length = number.length();

            // 排除15位（老身份证）和18位（标准身份证）
            if (length == 15 || length == 18) {
                continue;
            }

            // 记录错误位数的身份证号
            result.addInvalidIdCard(number, length);
            log.debug("检测到错误位数的身份证号: {} ({}位)", number, length);
        }

        return result;
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
                    .map(ExtractedItem::getReason)
                    .reduce((a, b) -> a + "; " + b)
                    .orElse("");
        }

        /**
         * 获取所有有效的身份证号列表
         */
        public List<String> getValidIdCards() {
            return items.stream()
                    .filter(ExtractedItem::isValid)
                    .map(ExtractedItem::getValue)
                    .collect(java.util.stream.Collectors.toList());
        }

        /**
         * 获取所有无效身份证号的详细信息
         */
        public List<String> getInvalidDetails() {
            return items.stream()
                    .filter(item -> !item.isValid())
                    .map(item -> String.format("%s (原因: %s)", item.getRawValue(), item.getReason()))
                    .collect(java.util.stream.Collectors.toList());
        }
    }

    /**
     * 提取项
     */
    @Data
    public static class ExtractedItem {
        private String rawValue;    // 原始值（可能带前缀）
        private String value;       // 清理后的值（纯身份证号）
        private boolean valid;
        private String reason;
    }

    /**
     * 错误位数身份证号检测结果
     */
    @Data
    public static class InvalidLengthDetection {
        private List<InvalidLengthItem> items = new ArrayList<>();

        public void addInvalidIdCard(String number, int length) {
            items.add(new InvalidLengthItem(number, length));
        }

        public boolean hasInvalid() {
            return !items.isEmpty();
        }

        public int getCount() {
            return items.size();
        }

        public String getSummary() {
            return items.stream()
                    .map(item -> String.format("%s(%d位)", item.getNumber(), item.getLength()))
                    .collect(java.util.stream.Collectors.joining("; "));
        }
    }

    /**
     * 错误位数身份证号项
     */
    @Data
    @lombok.AllArgsConstructor
    public static class InvalidLengthItem {
        private String number;    // 错误位数的身份证号
        private int length;       // 位数

        public String getReason() {
            return String.format("身份证号长度错误：%d位（必须18位或15位老格式）", length);
        }
    }
}
