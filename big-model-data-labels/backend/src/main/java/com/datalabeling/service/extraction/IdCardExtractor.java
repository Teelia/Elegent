package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 身份证号提取器
 * 支持提取18位标准身份证号、15位旧版身份证号、疑似身份证号
 */
@Slf4j
@Component
public class IdCardExtractor implements INumberExtractor {

    // 18位身份证号正则（严格模式）
    // 前6位地区码（1-6开头，覆盖全国所有省份）+ 8位生日 + 3位顺序码 + 1位校验位
    // 地区码规则：1-华北、2-东北、3-华东、4-中南、5-西南、6-西北
    private static final Pattern ID_CARD_18_STRICT = Pattern.compile(
        "\\b([1-6]\\d{5})" +         // 地区码（1-6开头的6位数字）
        "(19|20)\\d{2}" +            // 年份（19xx或20xx）
        "(0[1-9]|1[0-2])" +          // 月份
        "(0[1-9]|[12]\\d|3[01])" +   // 日期
        "\\d{3}" +                    // 顺序码
        "[0-9Xx]" +                  // 校验位
        "\\b"
    );

    // 15位身份证号正则（旧版）
    private static final Pattern ID_CARD_15 = Pattern.compile(
        "\\b" +
        "[1-9]\\d{4}" +              // 地区码
        "\\d{2}" +                    // 年份（2位）
        "(0[1-9]|1[0-2])" +          // 月份
        "(0[1-9]|[12]\\d|3[01])" +   // 日期
        "\\d{3}" +                    // 顺序码
        "\\b"
    );

    // 宽松模式（疑似身份证号）
    // 匹配 16-17 位或 19-20 位的数字（排除标准的 15 位和 18 位）
    private static final Pattern ID_CARD_LOOSE = Pattern.compile(
        "\\b\\d{16,17}\\b|\\b\\d{19,20}\\b"
    );

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();

        boolean include18Digit = (boolean) options.getOrDefault("include18Digit", true);
        boolean include15Digit = (boolean) options.getOrDefault("include15Digit", true);
        boolean includeLoose = (boolean) options.getOrDefault("includeLoose", true);

        log.debug("身份证号提取: include18Digit={}, include15Digit={}, includeLoose={}",
            include18Digit, include15Digit, includeLoose);

        // 1. 提取18位标准身份证号
        if (include18Digit) {
            results.addAll(extract18DigitIdCards(text));
        }

        // 2. 提取15位旧版身份证号
        if (include15Digit) {
            results.addAll(extract15DigitIdCards(text));
        }

        // 3. 提取疑似身份证号
        if (includeLoose) {
            results.addAll(extractLooseIdCards(text, results, include18Digit, include15Digit));
        }

        log.debug("身份证号提取完成，共提取 {} 个", results.size());
        return results;
    }

    /**
     * 提取18位标准身份证号
     */
    private List<ExtractedNumber> extract18DigitIdCards(String text) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = ID_CARD_18_STRICT.matcher(text);

        while (matcher.find()) {
            String idCard = matcher.group();
            if (validateCheckBit(idCard)) {
                // 解析地区和生日
                String areaCode = idCard.substring(0, 6);
                String birthDate = idCard.substring(6, 14);

                results.add(ExtractedNumber.builder()
                    .value(idCard)
                    .confidence(0.95f)
                    .validation("18位标准格式，地区码:" + areaCode + ", 生日:" + birthDate + ", 通过校验位验证")
                    .startIndex(matcher.start())
                    .endIndex(matcher.end())
                    .build());
            }
        }
        return results;
    }

    /**
     * 提取15位旧版身份证号
     */
    private List<ExtractedNumber> extract15DigitIdCards(String text) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = ID_CARD_15.matcher(text);

        while (matcher.find()) {
            String idCard = matcher.group();

            // 解析地区和生日
            String areaCode = idCard.substring(0, 6);
            String birthYear = "19" + idCard.substring(6, 8);
            String birthMonth = idCard.substring(8, 10);
            String birthDay = idCard.substring(10, 12);
            String birthDate = birthYear + birthMonth + birthDay;

            results.add(ExtractedNumber.builder()
                .value(idCard)
                .confidence(0.85f)
                .validation("15位旧版格式，地区码:" + areaCode + ", 生日:" + birthDate)
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .build());
        }
        return results;
    }

    /**
     * 提取疑似身份证号
     */
    private List<ExtractedNumber> extractLooseIdCards(String text, List<ExtractedNumber> existingResults, boolean include18Digit, boolean include15Digit) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = ID_CARD_LOOSE.matcher(text);

        while (matcher.find()) {
            String candidate = matcher.group();
            int startIndex = matcher.start();
            int endIndex = matcher.end();

            // 排除已经匹配的（检查位置重叠）
            boolean isOverlap = existingResults.stream().anyMatch(r ->
                (startIndex >= r.getStartIndex() && startIndex < r.getEndIndex()) ||
                (endIndex > r.getStartIndex() && endIndex <= r.getEndIndex()) ||
                (startIndex <= r.getStartIndex() && endIndex >= r.getEndIndex())
            );

            if (isOverlap) {
                continue;
            }

            int length = candidate.length();

            // 如果用户关闭了18位标准提取，则宽松模式不应提取符合18位标准格式的号码
            if (!include18Digit && length == 18) {
                if (ID_CARD_18_STRICT.matcher(candidate).matches()) {
                    continue; // 跳过符合18位标准格式的号码
                }
            }

            // 如果用户关闭了15位旧版提取，则宽松模式不应提取符合15位格式的号码
            if (!include15Digit && length == 15) {
                if (ID_CARD_15.matcher(candidate).matches()) {
                    continue; // 跳过符合15位格式的号码
                }
            }

            // 根据位数给出更详细的验证信息
            String validation;
            if (length < 15) {
                validation = "位数不足（" + length + "位），疑似身份证号片段";
            } else if (length == 16 || length == 17) {
                validation = "位数错误（" + length + "位），应为15位或18位";
            } else if (length == 19 || length == 20) {
                validation = "位数过多（" + length + "位），可能包含多余字符";
            } else {
                validation = "格式不完全符合标准，需要人工确认";
            }

            results.add(ExtractedNumber.builder()
                .value(candidate)
                .confidence(0.6f)
                .validation(validation)
                .startIndex(startIndex)
                .endIndex(endIndex)
                .build());
        }
        return results;
    }

    /**
     * 校验18位身份证号的校验位
     * 算法：前17位分别乘以权重，求和后对11取模，根据余数查表得到校验位
     */
    private boolean validateCheckBit(String idCard) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * weights[i];
        }

        char expectedCheck = checkChars[sum % 11];
        char actualCheck = Character.toUpperCase(idCard.charAt(17));

        return expectedCheck == actualCheck;
    }

    @Override
    public String getExtractorType() {
        return "id_card";
    }
}
