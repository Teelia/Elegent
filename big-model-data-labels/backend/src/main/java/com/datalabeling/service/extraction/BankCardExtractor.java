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
 * 银行卡号提取器
 * 支持提取16-19位银行卡号，并进行BIN码识别和Luhn算法校验
 */
@Slf4j
@Component
public class BankCardExtractor implements INumberExtractor {

    // 银行卡号正则（16-19位）
    private static final Pattern BANK_CARD = Pattern.compile("\\b\\d{16,19}\\b");

    // 18位身份证号正则（用于排除）
    // 前6位地区码（1-6开头）+ 8位生日（19xx或20xx年）+ 3位顺序码 + 1位校验位
    private static final Pattern ID_CARD_18 = Pattern.compile(
        "^[1-6]\\d{5}" +              // 地区码（1-6开头的6位数字）
        "(19|20)\\d{2}" +             // 年份（19xx或20xx）
        "(0[1-9]|1[0-2])" +           // 月份
        "(0[1-9]|[12]\\d|3[01])" +    // 日期
        "\\d{3}" +                     // 顺序码
        "[0-9Xx]$"                     // 校验位
    );

    // 常见银行卡BIN码（前6位）
    private static final Map<String, String> BANK_BIN_CODES = new HashMap<>();

    static {
        // 中国工商银行
        BANK_BIN_CODES.put("622700", "中国工商银行");
        BANK_BIN_CODES.put("622202", "中国工商银行");
        BANK_BIN_CODES.put("622200", "中国工商银行");
        BANK_BIN_CODES.put("622208", "中国工商银行");

        // 中国农业银行
        BANK_BIN_CODES.put("622848", "中国农业银行");
        BANK_BIN_CODES.put("622849", "中国农业银行");
        BANK_BIN_CODES.put("622846", "中国农业银行");

        // 中国建设银行
        BANK_BIN_CODES.put("621700", "中国建设银行");
        BANK_BIN_CODES.put("622700", "中国建设银行");
        BANK_BIN_CODES.put("622280", "中国建设银行");

        // 中国银行
        BANK_BIN_CODES.put("621626", "中国银行");
        BANK_BIN_CODES.put("621661", "中国银行");
        BANK_BIN_CODES.put("621662", "中国银行");

        // 招商银行
        BANK_BIN_CODES.put("521899", "招商银行");
        BANK_BIN_CODES.put("622580", "招商银行");
        BANK_BIN_CODES.put("622588", "招商银行");

        // 交通银行
        BANK_BIN_CODES.put("622280", "交通银行");
        BANK_BIN_CODES.put("405512", "交通银行");

        // 中国邮政储蓄银行
        BANK_BIN_CODES.put("622188", "中国邮政储蓄银行");
        BANK_BIN_CODES.put("622150", "中国邮政储蓄银行");

        // 中国民生银行
        BANK_BIN_CODES.put("622622", "中国民生银行");
        BANK_BIN_CODES.put("622623", "中国民生银行");

        // 中信银行
        BANK_BIN_CODES.put("622690", "中信银行");
        BANK_BIN_CODES.put("622691", "中信银行");

        // 兴业银行
        BANK_BIN_CODES.put("622902", "兴业银行");
        BANK_BIN_CODES.put("622903", "兴业银行");

        // 光大银行
        BANK_BIN_CODES.put("622655", "光大银行");
        BANK_BIN_CODES.put("622656", "光大银行");
    }

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = BANK_CARD.matcher(text);

        log.info("银行卡号提取开始");

        while (matcher.find()) {
            String cardNumber = matcher.group();

            // 排除身份证号及其变体（16-19位）
            if (isIdCardLikeNumber(cardNumber)) {
                log.info("排除疑似身份证号或其变体: {} (长度:{})", cardNumber, cardNumber.length());
                continue;
            }

            // Luhn算法校验 - 未通过则直接跳过
            if (!luhnCheck(cardNumber)) {
                log.info("排除未通过Luhn校验的号码: {}", cardNumber);
                continue;
            }

            // 识别发卡行
            String bank = "未知银行";
            if (cardNumber.length() >= 6) {
                String bin = cardNumber.substring(0, 6);
                bank = BANK_BIN_CODES.getOrDefault(bin, "未知银行");
            }

            results.add(ExtractedNumber.builder()
                .value(cardNumber)
                .confidence(0.9f)
                .validation(bank + "，通过Luhn校验")
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .build());
        }

        log.debug("银行卡号提取完成，共提取 {} 个", results.size());
        return results;
    }

    /**
     * Luhn算法校验
     * 算法：从右到左，偶数位乘以2（如果结果>9则减去9），奇数位不变，求和后对10取模
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
     * 判断是否为身份证号或其变体
     * 处理以下情况：
     * - 16位：身份证前16位（缺少顺序码后2位）
     * - 17位：身份证前17位（缺少校验位）
     * - 18位：完整身份证号
     * - 19位：身份证号+额外1位数字
     */
    private boolean isIdCardLikeNumber(String number) {
        int len = number.length();

        // 18位：完整身份证号
        if (len == 18) {
            return isIdCardFormat(number);
        }

        // 19位：检查前18位是否为身份证号
        if (len == 19) {
            return isIdCardFormat(number.substring(0, 18));
        }

        // 16位：检查是否为身份证前16位（地区码+出生日期+顺序码前2位）
        if (len == 16) {
            return isPartialIdCardFormat(number, 16);
        }

        // 17位：检查是否为身份证前17位（缺少校验位）
        if (len == 17) {
            return isPartialIdCardFormat(number, 17);
        }

        return false;
    }

    /**
     * 判断是否为身份证号的前N位（N=16或17）
     * 检查地区码和出生日期是否符合身份证特征
     */
    private boolean isPartialIdCardFormat(String number, int expectedLength) {
        if (number.length() != expectedLength) {
            return false;
        }

        // 检查首位是否为1-6（有效地区码开头）
        char firstChar = number.charAt(0);
        if (firstChar < '1' || firstChar > '6') {
            return false;
        }

        // 检查第7-14位是否为有效日期（身份证出生日期位置）
        if (number.length() >= 14) {
            String yearStr = number.substring(6, 10);
            String monthStr = number.substring(10, 12);
            String dayStr = number.substring(12, 14);

            try {
                int year = Integer.parseInt(yearStr);
                int month = Integer.parseInt(monthStr);
                int day = Integer.parseInt(dayStr);

                // 年份在1920-2020之间（更严格的范围），月份1-12，日期1-31
                if (year >= 1920 && year <= 2020 &&
                    month >= 1 && month <= 12 &&
                    day >= 1 && day <= 31) {
                    return true;
                }
            } catch (NumberFormatException e) {
                // 解析失败，不是身份证格式
            }
        }

        return false;
    }

    /**
     * 判断是否为身份证号格式
     * 检查18位数字是否符合身份证号的结构特征
     */
    private boolean isIdCardFormat(String number) {
        // 使用正则匹配身份证格式
        if (ID_CARD_18.matcher(number).matches()) {
            return true;
        }

        // 额外检查：即使最后一位不是X，也检查前17位是否符合身份证特征
        // 地区码1-6开头 + 有效日期格式
        if (number.length() == 18) {
            char firstChar = number.charAt(0);
            if (firstChar >= '1' && firstChar <= '6') {
                // 检查第7-14位是否为有效日期（19xx或20xx年）
                String yearStr = number.substring(6, 10);
                String monthStr = number.substring(10, 12);
                String dayStr = number.substring(12, 14);

                try {
                    int year = Integer.parseInt(yearStr);
                    int month = Integer.parseInt(monthStr);
                    int day = Integer.parseInt(dayStr);

                    // 年份在1900-2099之间，月份1-12，日期1-31
                    if (year >= 1900 && year <= 2099 &&
                        month >= 1 && month <= 12 &&
                        day >= 1 && day <= 31) {
                        return true;
                    }
                } catch (NumberFormatException e) {
                    // 解析失败，不是身份证格式
                }
            }
        }

        return false;
    }

    @Override
    public String getExtractorType() {
        return "bank_card";
    }
}
