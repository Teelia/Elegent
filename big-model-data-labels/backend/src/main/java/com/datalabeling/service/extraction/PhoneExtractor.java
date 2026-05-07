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
 * 手机号提取器
 * 支持提取11位中国大陆手机号，并识别运营商
 */
@Slf4j
@Component
public class PhoneExtractor implements INumberExtractor {

    // 中国手机号正则（严格）
    // 1开头，第二位3-9
    private static final Pattern PHONE_STRICT = Pattern.compile(
        "\\b(1[3-9])\\d{9}\\b"
    );

    // 三大运营商号段
    private static final Map<String, String> OPERATORS = new HashMap<>();

    static {
        // 中国联通
        OPERATORS.put("130", "中国联通");
        OPERATORS.put("131", "中国联通");
        OPERATORS.put("132", "中国联通");
        OPERATORS.put("145", "中国联通");
        OPERATORS.put("155", "中国联通");
        OPERATORS.put("156", "中国联通");
        OPERATORS.put("166", "中国联通");
        OPERATORS.put("167", "中国联通");
        OPERATORS.put("171", "中国联通");
        OPERATORS.put("175", "中国联通");
        OPERATORS.put("176", "中国联通");
        OPERATORS.put("185", "中国联通");
        OPERATORS.put("186", "中国联通");

        // 中国移动
        OPERATORS.put("134", "中国移动");
        OPERATORS.put("135", "中国移动");
        OPERATORS.put("136", "中国移动");
        OPERATORS.put("137", "中国移动");
        OPERATORS.put("138", "中国移动");
        OPERATORS.put("139", "中国移动");
        OPERATORS.put("147", "中国移动");
        OPERATORS.put("150", "中国移动");
        OPERATORS.put("151", "中国移动");
        OPERATORS.put("152", "中国移动");
        OPERATORS.put("157", "中国移动");
        OPERATORS.put("158", "中国移动");
        OPERATORS.put("159", "中国移动");
        OPERATORS.put("172", "中国移动");
        OPERATORS.put("178", "中国移动");
        OPERATORS.put("182", "中国移动");
        OPERATORS.put("183", "中国移动");
        OPERATORS.put("184", "中国移动");
        OPERATORS.put("187", "中国移动");
        OPERATORS.put("188", "中国移动");
        OPERATORS.put("198", "中国移动");

        // 中国电信
        OPERATORS.put("133", "中国电信");
        OPERATORS.put("149", "中国电信");
        OPERATORS.put("153", "中国电信");
        OPERATORS.put("173", "中国电信");
        OPERATORS.put("177", "中国电信");
        OPERATORS.put("180", "中国电信");
        OPERATORS.put("181", "中国电信");
        OPERATORS.put("189", "中国电信");
        OPERATORS.put("191", "中国电信");
        OPERATORS.put("199", "中国电信");
    }

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();
        Matcher matcher = PHONE_STRICT.matcher(text);

        log.debug("手机号提取开始");

        while (matcher.find()) {
            String phone = matcher.group();

            // 识别运营商
            String operator = "未知运营商";
            if (phone.length() >= 3) {
                String prefix = phone.substring(0, 3);
                operator = OPERATORS.getOrDefault(prefix, "未知运营商");
            }

            results.add(ExtractedNumber.builder()
                .value(phone)
                .confidence(0.92f)
                .validation(operator)
                .startIndex(matcher.start())
                .endIndex(matcher.end())
                .build());
        }

        log.debug("手机号提取完成，共提取 {} 个", results.size());
        return results;
    }

    @Override
    public String getExtractorType() {
        return "phone";
    }
}
