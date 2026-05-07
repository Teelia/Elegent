package com.datalabeling.service.extraction;

import com.datalabeling.entity.ExtractorConfig;
import com.datalabeling.entity.ExtractorPattern;
import com.datalabeling.service.ExtractorConfigService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 动态提取器
 * 根据数据库配置的正则表达式进行提取
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DynamicExtractor implements INumberExtractor {

    private final ExtractorConfigService extractorConfigService;

    // 缓存编译后的正则表达式
    private final Map<String, List<CompiledPattern>> patternCache = new HashMap<>();

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        String extractorCode = (String) options.get("extractorCode");
        if (extractorCode == null || extractorCode.isEmpty()) {
            log.warn("未指定提取器代码");
            return Collections.emptyList();
        }

        return extractWithCode(text, extractorCode, options);
    }

    /**
     * 使用指定的提取器代码进行提取
     */
    public List<ExtractedNumber> extractWithCode(String text, String extractorCode, Map<String, Object> options) {
        List<ExtractedNumber> results = new ArrayList<>();

        try {
            // 获取提取器配置
            ExtractorConfig config = extractorConfigService.getConfigByCode(extractorCode);
            if (config == null || config.getPatterns() == null || config.getPatterns().isEmpty()) {
                log.warn("提取器配置不存在或没有正则规则: {}", extractorCode);
                return results;
            }

            // 获取编译后的正则表达式
            List<CompiledPattern> compiledPatterns = getCompiledPatterns(config);

            // 遍历每个正则规则进行匹配
            for (CompiledPattern cp : compiledPatterns) {
                // 检查选项是否启用该规则
                if (!isPatternEnabled(cp, options)) {
                    continue;
                }

                Matcher matcher = cp.pattern.matcher(text);
                while (matcher.find()) {
                    String value = matcher.group();
                    int startIndex = matcher.start();
                    int endIndex = matcher.end();

                    // 检查是否与已有结果重叠
                    if (isOverlapping(results, startIndex, endIndex)) {
                        continue;
                    }

                    // 执行验证
                    boolean validated = validate(value, cp);
                    float confidence = validated ? cp.confidence : cp.confidence * 0.7f;

                    String validation = cp.description;
                    if (cp.validationType != null && !cp.validationType.isEmpty()) {
                        validation += validated ? "，通过验证" : "，未通过验证";
                    }

                    results.add(ExtractedNumber.builder()
                            .value(value)
                            .confidence(confidence)
                            .validation(validation)
                            .startIndex(startIndex)
                            .endIndex(endIndex)
                            .build());
                }
            }

            log.debug("动态提取完成: extractor={}, 提取到 {} 个结果", extractorCode, results.size());

        } catch (Exception e) {
            log.error("动态提取失败: extractor={}", extractorCode, e);
        }

        return results;
    }

    /**
     * 获取编译后的正则表达式（带缓存）
     */
    private List<CompiledPattern> getCompiledPatterns(ExtractorConfig config) {
        String cacheKey = config.getCode() + "_" + config.getUpdatedAt();
        
        if (patternCache.containsKey(cacheKey)) {
            return patternCache.get(cacheKey);
        }

        List<CompiledPattern> compiledPatterns = new ArrayList<>();
        for (ExtractorPattern ep : config.getPatterns()) {
            if (!ep.getIsActive()) {
                continue;
            }

            try {
                Pattern pattern = Pattern.compile(ep.getPattern());
                CompiledPattern cp = new CompiledPattern();
                cp.pattern = pattern;
                cp.name = ep.getName();
                cp.description = ep.getDescription();
                cp.priority = ep.getPriority();
                cp.confidence = ep.getConfidence() != null ? ep.getConfidence().floatValue() : 0.9f;
                cp.validationType = ep.getValidationType();
                cp.validationConfig = ep.getValidationConfig();
                compiledPatterns.add(cp);
            } catch (Exception e) {
                log.error("编译正则表达式失败: pattern={}, error={}", ep.getPattern(), e.getMessage());
            }
        }

        // 按优先级排序
        compiledPatterns.sort((a, b) -> Integer.compare(b.priority, a.priority));

        // 缓存
        patternCache.put(cacheKey, compiledPatterns);

        return compiledPatterns;
    }

    /**
     * 检查规则是否启用
     */
    private boolean isPatternEnabled(CompiledPattern cp, Map<String, Object> options) {
        // 根据规则名称检查选项
        // 例如：include18Digit 对应 "18位标准身份证号" 规则
        if (cp.name.contains("18位") && options.containsKey("include18Digit")) {
            return (Boolean) options.getOrDefault("include18Digit", true);
        }
        if (cp.name.contains("15位") && options.containsKey("include15Digit")) {
            return (Boolean) options.getOrDefault("include15Digit", true);
        }
        if (cp.name.contains("疑似") && options.containsKey("includeLoose")) {
            return (Boolean) options.getOrDefault("includeLoose", true);
        }
        return true;
    }

    /**
     * 检查是否与已有结果重叠
     */
    private boolean isOverlapping(List<ExtractedNumber> results, int startIndex, int endIndex) {
        for (ExtractedNumber r : results) {
            if ((startIndex >= r.getStartIndex() && startIndex < r.getEndIndex()) ||
                (endIndex > r.getStartIndex() && endIndex <= r.getEndIndex()) ||
                (startIndex <= r.getStartIndex() && endIndex >= r.getEndIndex())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 执行验证
     */
    private boolean validate(String value, CompiledPattern cp) {
        if (cp.validationType == null || cp.validationType.isEmpty() || "none".equals(cp.validationType)) {
            return true;
        }

        switch (cp.validationType) {
            case "checksum":
                return validateChecksum(value);
            case "luhn":
                return validateLuhn(value);
            default:
                return true;
        }
    }

    /**
     * 身份证校验位验证
     */
    private boolean validateChecksum(String idCard) {
        if (idCard.length() != 18) {
            return false;
        }

        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char c = idCard.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            sum += (c - '0') * weights[i];
        }

        char expectedCheck = checkChars[sum % 11];
        char actualCheck = Character.toUpperCase(idCard.charAt(17));

        return expectedCheck == actualCheck;
    }

    /**
     * Luhn算法验证
     */
    private boolean validateLuhn(String cardNumber) {
        int sum = 0;
        boolean alternate = false;

        for (int i = cardNumber.length() - 1; i >= 0; i--) {
            char c = cardNumber.charAt(i);
            if (!Character.isDigit(c)) {
                return false;
            }
            int digit = c - '0';

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
     * 清除缓存
     */
    public void clearCache() {
        patternCache.clear();
    }

    /**
     * 清除指定提取器的缓存
     */
    public void clearCache(String extractorCode) {
        patternCache.entrySet().removeIf(entry -> entry.getKey().startsWith(extractorCode + "_"));
    }

    @Override
    public String getExtractorType() {
        return "dynamic";
    }

    /**
     * 编译后的正则表达式
     */
    private static class CompiledPattern {
        Pattern pattern;
        String name;
        String description;
        int priority;
        float confidence;
        String validationType;
        String validationConfig;
    }
}