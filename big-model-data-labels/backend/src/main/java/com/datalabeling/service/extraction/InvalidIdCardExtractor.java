package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 错误身份证号提取器
 *
 * 提取不符合标准格式但疑似身份证的号码，严格排除银行卡号和手机号
 *
 * 规则说明：
 * 1. 匹配16-20位数字（排除标准15位和18位身份证格式）
 * 2. 严格排除银行卡号（Luhn校验 + BIN码识别）
 * 3. 严格排除手机号（11位，1[3-9]开头）
 * 4. 优先提取具有身份证特征的号码（地区码 + 日期结构）
 */
@Slf4j
@Component
public class InvalidIdCardExtractor implements INumberExtractor {

    // ==================== 正则表达式 ====================

    /**
     * 基础匹配：16-20位数字（排除15和18位）
     */
    private static final Pattern CANDIDATE_PATTERN = Pattern.compile(
        "\\b\\d{16}\\b|\\b\\d{17}\\b|\\b\\d{19}\\b|\\b\\d{20}\\b"
    );

    /**
     * 18位标准身份证格式（用于排除）
     */
    private static final Pattern ID_CARD_18_STRICT = Pattern.compile(
        "^([1-6]\\d{5})" +           // 地区码（1-6开头）
        "(19|20)\\d{2}" +            // 年份
        "(0[1-9]|1[0-2])" +          // 月份
        "(0[1-9]|[12]\\d|3[01])" +   // 日期
        "\\d{3}" +                   // 顺序码
        "[0-9Xx]$"                   // 校验位
    );

    /**
     * 15位旧版身份证格式（用于排除）
     */
    private static final Pattern ID_CARD_15 = Pattern.compile(
        "^[1-9]\\d{4}" +             // 地区码
        "\\d{2}" +                   // 年份（2位）
        "(0[1-9]|1[0-2])" +          // 月份
        "(0[1-9]|[12]\\d|3[01])" +   // 日期
        "\\d{3}$"                    // 顺序码
    );

    /**
     * 手机号格式（用于排除）
     * 1开头，第二位3-9，共11位
     */
    private static final Pattern PHONE_PATTERN = Pattern.compile(
        "^1[3-9]\\d{9}$"
    );

    /**
     * 疑似身份证特征（优先提取）
     * 检查是否有地区码特征和日期结构
     */
    private static final Pattern ID_CARD_FEATURE = Pattern.compile(
        "^[1-6]" +                   // 地区码以1-6开头
        "\\d{5}" +                   // 后续5位地区码
        "(19|20|\\d{2})" +           // 年份部分
        "(0[1-9]|1[0-2])" +          // 月份
        "(0[1-9]|[12]\\d|3[01])"     // 日期
    );

    // ==================== 银行卡BIN码 ====================

    /**
     * 常见银行卡BIN码（前6位）
     * 用于快速识别银行卡号
     */
    private static final Map<String, String> BANK_BIN_CODES = new HashMap<>();

    static {
        // 中国工商银行
        BANK_BIN_CODES.put("622200", "中国工商银行");
        BANK_BIN_CODES.put("622202", "中国工商银行");
        BANK_BIN_CODES.put("622203", "中国工商银行");
        BANK_BIN_CODES.put("622208", "中国工商银行");
        BANK_BIN_CODES.put("622700", "中国工商银行");

        // 中国农业银行
        BANK_BIN_CODES.put("622848", "中国农业银行");
        BANK_BIN_CODES.put("622849", "中国农业银行");
        BANK_BIN_CODES.put("622846", "中国农业银行");
        BANK_BIN_CODES.put("622847", "中国农业银行");

        // 中国建设银行
        BANK_BIN_CODES.put("436742", "中国建设银行");
        BANK_BIN_CODES.put("621700", "中国建设银行");
        BANK_BIN_CODES.put("622280", "中国建设银行");
        BANK_BIN_CODES.put("622700", "中国建设银行");
        BANK_BIN_CODES.put("622966", "中国建设银行");
        BANK_BIN_CODES.put("622988", "中国建设银行");

        // 中国银行
        BANK_BIN_CODES.put("621626", "中国银行");
        BANK_BIN_CODES.put("621661", "中国银行");
        BANK_BIN_CODES.put("621662", "中国银行");
        BANK_BIN_CODES.put("621663", "中国银行");
        BANK_BIN_CODES.put("621766", "中国银行");

        // 招商银行
        BANK_BIN_CODES.put("405512", "招商银行");
        BANK_BIN_CODES.put("410062", "招商银行");
        BANK_BIN_CODES.put("521899", "招商银行");
        BANK_BIN_CODES.put("622580", "招商银行");
        BANK_BIN_CODES.put("622588", "招商银行");
        BANK_BIN_CODES.put("622598", "招商银行");
        BANK_BIN_CODES.put("622609", "招商银行");

        // 交通银行
        BANK_BIN_CODES.put("405512", "交通银行");
        BANK_BIN_CODES.put("422020", "交通银行");
        BANK_BIN_CODES.put("422021", "交通银行");
        BANK_BIN_CODES.put("622258", "交通银行");
        BANK_BIN_CODES.put("622280", "交通银行");

        // 中国邮政储蓄银行
        BANK_BIN_CODES.put("622150", "中国邮政储蓄银行");
        BANK_BIN_CODES.put("622188", "中国邮政储蓄银行");
        BANK_BIN_CODES.put("621098", "中国邮政储蓄银行");
        BANK_BIN_CODES.put("621099", "中国邮政储蓄银行");

        // 中国民生银行
        BANK_BIN_CODES.put("622622", "中国民生银行");
        BANK_BIN_CODES.put("622623", "中国民生银行");
        BANK_BIN_CODES.put("622660", "中国民生银行");

        // 中信银行
        BANK_BIN_CODES.put("622690", "中信银行");
        BANK_BIN_CODES.put("622691", "中信银行");
        BANK_BIN_CODES.put("622692", "中信银行");
        BANK_BIN_CODES.put("622696", "中信银行");

        // 兴业银行
        BANK_BIN_CODES.put("622902", "兴业银行");
        BANK_BIN_CODES.put("622903", "兴业银行");
        BANK_BIN_CODES.put("622906", "兴业银行");

        // 光大银行
        BANK_BIN_CODES.put("622655", "光大银行");
        BANK_BIN_CODES.put("622656", "光大银行");
        BANK_BIN_CODES.put("622657", "光大银行");
        BANK_BIN_CODES.put("622658", "光大银行");

        // 平安银行
        BANK_BIN_CODES.put("622155", "平安银行");
        BANK_BIN_CODES.put("622156", "平安银行");
        BANK_BIN_CODES.put("622157", "平安银行");
        BANK_BIN_CODES.put("622158", "平安银行");

        // 广发银行
        BANK_BIN_CODES.put("622568", "广发银行");
        BANK_BIN_CODES.put("622569", "广发银行");
        BANK_BIN_CODES.put("622516", "广发银行");
        BANK_BIN_CODES.put("622517", "广发银行");

        // 浦发银行
        BANK_BIN_CODES.put("622521", "浦发银行");
        BANK_BIN_CODES.put("622522", "浦发银行");
        BANK_BIN_CODES.put("622523", "浦发银行");

        // 华夏银行
        BANK_BIN_CODES.put("622630", "华夏银行");
        BANK_BIN_CODES.put("622631", "华夏银行");
        BANK_BIN_CODES.put("622632", "华夏银行");
    }

    // ==================== 权重数组（18位身份证校验位） ====================

    private static final int[] ID_CARD_WEIGHTS = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
    private static final char[] ID_CARD_CHECK_CHARS = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();

        boolean includeHighConfidence = (boolean) options.getOrDefault("includeHighConfidence", true);
        boolean includeMediumConfidence = (boolean) options.getOrDefault("includeMediumConfidence", true);
        boolean includeLowConfidence = (boolean) options.getOrDefault("includeLowConfidence", false);

        log.debug("错误身份证号提取: includeHigh={}, includeMedium={}, includeLow={}",
            includeHighConfidence, includeMediumConfidence, includeLowConfidence);

        Matcher matcher = CANDIDATE_PATTERN.matcher(text);

        while (matcher.find()) {
            String candidate = matcher.group();
            int startIndex = matcher.start();
            int endIndex = matcher.end();

            // 执行排除检查
            ExcludeResult excludeResult = checkShouldExclude(candidate);
            if (excludeResult.shouldExclude) {
                log.debug("排除号码: {}, 原因: {}", candidate, excludeResult.reason);
                continue;
            }

            // 分析置信度
            ConfidenceAnalysis analysis = analyzeConfidence(candidate);

            // 根据配置过滤
            if (analysis.confidence >= 0.8f && !includeHighConfidence) {
                continue;
            }
            if (analysis.confidence >= 0.6f && analysis.confidence < 0.8f && !includeMediumConfidence) {
                continue;
            }
            if (analysis.confidence < 0.6f && !includeLowConfidence) {
                continue;
            }

            results.add(ExtractedNumber.builder()
                .value(candidate)
                .confidence(analysis.confidence)
                .validation(analysis.validation)
                .startIndex(startIndex)
                .endIndex(endIndex)
                .build());
        }

        log.debug("错误身份证号提取完成，共提取 {} 个", results.size());
        return results;
    }

    // ==================== 排除检查逻辑 ====================

    /**
     * 检查号码是否应该被排除
     *
     * @param number 候选号码
     * @return 排除结果
     */
    private ExcludeResult checkShouldExclude(String number) {
        int len = number.length();

        // 1. 排除标准18位身份证（通过校验位）
        if (len == 18) {
            if (ID_CARD_18_STRICT.matcher(number).matches() && validateIdCardCheckBit(number)) {
                return new ExcludeResult(true, "标准18位身份证号（通过校验位验证）");
            }
        }

        // 2. 排除15位旧版身份证
        if (len == 15) {
            if (ID_CARD_15.matcher(number).matches()) {
                return new ExcludeResult(true, "标准15位旧版身份证号");
            }
        }

        // 3. 排除手机号（11位）
        // 对于16-20位号码，检查子串是否包含手机号
        if (len >= 11) {
            for (int i = 0; i <= len - 11; i++) {
                String substring = number.substring(i, i + 11);
                if (PHONE_PATTERN.matcher(substring).matches()) {
                    return new ExcludeResult(true, "包含手机号格式: " + substring);
                }
            }
        }

        // 4. 排除银行卡号
        if (isBankCardNumber(number)) {
            return new ExcludeResult(true, "银行卡号（通过Luhn校验或BIN码识别）");
        }

        // 5. 特殊情况：排除连续相同数字或明显无意义的号码
        if (isMeaninglessNumber(number)) {
            return new ExcludeResult(true, "无明显意义号码（连续相同数字或规则序列）");
        }

        return new ExcludeResult(false, null);
    }

    /**
     * 检查是否为银行卡号
     * 使用Luhn校验和BIN码识别
     */
    private boolean isBankCardNumber(String number) {
        // 检查BIN码
        if (number.length() >= 6) {
            String bin = number.substring(0, 6);
            if (BANK_BIN_CODES.containsKey(bin)) {
                // BIN码匹配，进一步用Luhn校验确认
                if (luhnCheck(number)) {
                    log.info("识别为银行卡号（BIN码匹配）: {}", number);
                    return true;
                }
            }
        }

        // 检查是否通过Luhn校验
        if (luhnCheck(number)) {
            // 通过Luhn校验，需要额外检查是否有银行卡特征
            // 银行卡通常是16-19位
            if (number.length() >= 16 && number.length() <= 19) {
                // 检查是否像身份证（1-6开头 + 日期结构）
                if (!looksLikeIdCard(number)) {
                    log.info("识别为银行卡号（Luhn校验通过）: {}", number);
                    return true;
                }
            }
        }

        return false;
    }

    /**
     * Luhn算法校验
     */
    private boolean luhnCheck(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            int digit = cardNumber.charAt(i) - '0';

            if (alternate) {
                digit *= 2;
                if (digit > 9) {
                    digit -= 9;
                }
            }

            sum += digit;
            alternate = !alternate;
        }

        return sum % 10 == 0;
    }

    /**
     * 检查号码是否像身份证（不是银行卡）
     */
    private boolean looksLikeIdCard(String number) {
        // 检查长度是否接近18位
        int len = number.length();
        if (len < 16 || len > 20) {
            return false;
        }

        // 检查是否以1-6开头（地区码特征）
        char firstChar = number.charAt(0);
        if (firstChar < '1' || firstChar > '6') {
            return false;
        }

        // 检查第7-14位是否像日期
        if (len >= 14) {
            String yearStr = number.substring(6, 10);
            String monthStr = number.substring(10, 12);
            String dayStr = number.substring(12, 14);

            try {
                int year = Integer.parseInt(yearStr);
                int month = Integer.parseInt(monthStr);
                int day = Integer.parseInt(dayStr);

                // 检查是否为合理日期
                if (year >= 1900 && year <= 2099 &&
                    month >= 1 && month <= 12 &&
                    day >= 1 && day <= 31) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // 解析失败
            }
        }

        return false;
    }

    /**
     * 检查是否为无意义号码
     */
    private boolean isMeaninglessNumber(String number) {
        // 检查是否全部为相同数字
        boolean allSame = true;
        char firstChar = number.charAt(0);
        for (int i = 1; i < number.length(); i++) {
            if (number.charAt(i) != firstChar) {
                allSame = false;
                break;
            }
        }
        if (allSame) {
            return true;
        }

        // 检查是否为连续递增序列（如123456...）
        boolean isSequential = true;
        for (int i = 1; i < number.length(); i++) {
            if (number.charAt(i) - number.charAt(i - 1) != 1) {
                isSequential = false;
                break;
            }
        }
        if (isSequential) {
            return true;
        }

        return false;
    }

    /**
     * 校验18位身份证号的校验位
     */
    private boolean validateIdCardCheckBit(String idCard) {
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * ID_CARD_WEIGHTS[i];
        }

        char expectedCheck = ID_CARD_CHECK_CHARS[sum % 11];
        char actualCheck = Character.toUpperCase(idCard.charAt(17));

        return expectedCheck == actualCheck;
    }

    // ==================== 置信度分析 ====================

    /**
     * 分析候选号码的置信度
     */
    private ConfidenceAnalysis analyzeConfidence(String candidate) {
        int len = candidate.length();
        float confidence;
        String validation;

        // 高置信度：有明显身份证特征但位数不对
        if (hasIdCardFeatures(candidate)) {
            confidence = 0.85f;
            validation = buildValidationMessage(candidate, true);
        }
        // 中等置信度：长度接近18位但无明确特征
        else if (len == 17) {
            confidence = 0.65f;
            validation = "疑似身份证号（17位，可能缺少校验位）";
        }
        else if (len == 16) {
            confidence = 0.60f;
            validation = "疑似身份证号（16位，可能缺少后2位）";
        }
        // 低置信度：其他情况
        else {
            confidence = 0.45f;
            if (len == 19) {
                validation = "疑似身份证号（19位，可能多1位）";
            } else {
                validation = "疑似身份证号（20位，位数异常）";
            }
        }

        return new ConfidenceAnalysis(confidence, validation);
    }

    /**
     * 检查是否具有身份证特征
     */
    private boolean hasIdCardFeatures(String candidate) {
        // 检查地区码特征
        if (candidate.length() < 6) {
            return false;
        }

        char firstChar = candidate.charAt(0);
        if (firstChar < '1' || firstChar > '6') {
            return false;
        }

        // 检查日期特征
        if (candidate.length() >= 14) {
            String yearStr = candidate.substring(6, 10);
            String monthStr = candidate.substring(10, 12);
            String dayStr = candidate.substring(12, 14);

            try {
                int year = Integer.parseInt(yearStr);
                int month = Integer.parseInt(monthStr);
                int day = Integer.parseInt(dayStr);

                // 检查日期合理性
                if (year >= 1900 && year <= 2099 &&
                    month >= 1 && month <= 12 &&
                    day >= 1 && day <= 31) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // 解析失败
            }
        }

        return false;
    }

    /**
     * 构建验证消息
     */
    private String buildValidationMessage(String candidate, boolean hasFeatures) {
        int len = candidate.length();
        StringBuilder sb = new StringBuilder();

        if (hasFeatures) {
            sb.append("疑似错误身份证号（");
            sb.append(len);
            sb.append("位），具有身份证特征：");

            // 解析地区码
            if (len >= 6) {
                sb.append("地区码[").append(candidate.substring(0, 6)).append("]");
            }

            // 解析生日
            if (len >= 14) {
                String birthDate = candidate.substring(6, 14);
                sb.append("，生日[").append(birthDate).append("]");
            }

            sb.append("，位数不标准");
        } else {
            sb.append("疑似错误身份证号（");
            sb.append(len);
            sb.append("位），无明确身份证特征");
        }

        return sb.toString();
    }

    // ==================== 内部类 ====================

    /**
     * 排除结果
     */
    private static class ExcludeResult {
        boolean shouldExclude;
        String reason;

        ExcludeResult(boolean shouldExclude, String reason) {
            this.shouldExclude = shouldExclude;
            this.reason = reason;
        }
    }

    /**
     * 置信度分析结果
     */
    private static class ConfidenceAnalysis {
        float confidence;
        String validation;

        ConfidenceAnalysis(float confidence, String validation) {
            this.confidence = confidence;
            this.validation = validation;
        }
    }

    @Override
    public String getExtractorType() {
        return "invalid_id_card";
    }
}
