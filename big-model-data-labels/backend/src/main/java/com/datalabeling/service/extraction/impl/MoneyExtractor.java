package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;

/**
 * 金额提取器
 *
 * 提取功能：
 * - 中文数字金额（壹万元、五千元等）
 * - 阿拉伯数字金额（10000.00元）
 * - 带货币符号金额（¥1000、$500）
 * - 大写金额（壹万贰仟元整）
 * - 标准化金额格式（统一转换为数字）
 *
 * 业务场景：
 * - 财务数据提取
 * - 合同金额识别
 * - 交易记录整理
 */
@Slf4j
@Component
public class MoneyExtractor extends AbstractEnhancedExtractor {

    // 中文数字映射
    private static final Map<String, Integer> CHINESE_NUMBERS = new LinkedHashMap<>();
    static {
        CHINESE_NUMBERS.put("零", 0);
        CHINESE_NUMBERS.put("一", 1);
        CHINESE_NUMBERS.put("壹", 1);
        CHINESE_NUMBERS.put("二", 2);
        CHINESE_NUMBERS.put("贰", 2);
        CHINESE_NUMBERS.put("三", 3);
        CHINESE_NUMBERS.put("叁", 3);
        CHINESE_NUMBERS.put("四", 4);
        CHINESE_NUMBERS.put("肆", 4);
        CHINESE_NUMBERS.put("五", 5);
        CHINESE_NUMBERS.put("伍", 5);
        CHINESE_NUMBERS.put("六", 6);
        CHINESE_NUMBERS.put("陆", 6);
        CHINESE_NUMBERS.put("七", 7);
        CHINESE_NUMBERS.put("柒", 7);
        CHINESE_NUMBERS.put("八", 8);
        CHINESE_NUMBERS.put("捌", 8);
        CHINESE_NUMBERS.put("九", 9);
        CHINESE_NUMBERS.put("玖", 9);
        CHINESE_NUMBERS.put("十", 10);
        CHINESE_NUMBERS.put("拾", 10);
        CHINESE_NUMBERS.put("百", 100);
        CHINESE_NUMBERS.put("佰", 100);
        CHINESE_NUMBERS.put("千", 1000);
        CHINESE_NUMBERS.put("仟", 1000);
        CHINESE_NUMBERS.put("万", 10000);
        CHINESE_NUMBERS.put("亿", 100000000);
    }

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("money")
        .name("金额提取器")
        .description("提取各种格式的金额，支持中文和阿拉伯数字，自动转换为标准格式")
        .category("builtin")
        .outputField("金额")
        .dataType("decimal")
        .multiValue(true)
        .accuracy("high")
        .performance("medium")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("财务", "金额", "数字"))
        .useCase("财务数据、合同金额、交易记录")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_chinese")
                .name("包含中文金额")
                .description("提取中文数字金额")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("normalize_to_number")
                .name("标准化为数字")
                .description("将中文金额转换为阿拉伯数字")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("min_amount")
                .name("最小金额")
                .description("只提取大于此金额的数字")
                .type("number")
                .defaultValue(0)
                .build()
        ))
        .build();

    @Override
    public ExtractorMetadata getMetadata() {
        return METADATA;
    }

    @Override
    protected List<ExtractorPattern> getPatterns() {
        return Arrays.asList(
            // 阿拉伯数字金额（带货币符号和小数点）
            ExtractorPattern.of(
                "arabic_with_symbol",
                "(?:[¥$￥€£]|人民币|美元|欧元|英镑)\\s*\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,4})?\\s*(?:元|美元|欧元|英镑|CNY|USD|EUR|GBP)?",
                0.98f
            ),
            // 阿拉伯数字金额（纯数字+元）
            ExtractorPattern.of(
                "arabic_yuan",
                "\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,4})?\\s*(?:元|RMB|CNY)",
                0.95f
            ),
            // 阿拉伯数字金额（纯数字，可能需要上下文判断）
            ExtractorPattern.of(
                "arabic_plain",
                "\\b\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,4})?\\b",
                0.70f
            ),
            // 中文数字金额（简单）
            ExtractorPattern.of(
                "chinese_simple",
                "[一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟]+元",
                0.90f
            ),
            // 中文数字金额（大写）
            ExtractorPattern.of(
                "chinese_uppercase",
                "[零壹贰叁肆伍陆柒捌玖拾佰仟万亿]+[元整圆整]",
                0.95f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String matched = matcher.group();

        // 解析金额
        MoneyParseResult parseResult = parseMoney(matched);

        if (parseResult == null) {
            return null;
        }

        // 检查最小金额限制（安全地处理类型转换）
        Double minAmount = 0.0;
        Object minAmountObj = options.get("min_amount");
        if (minAmountObj instanceof Number) {
            minAmount = ((Number) minAmountObj).doubleValue();
        }

        // 只有当数值不为null时才检查最小金额
        if (parseResult.getNumericValue() != null && parseResult.getNumericValue() < minAmount) {
            return null;
        }

        // 如果没有解析出数值，设置默认置信度
        if (parseResult.getConfidence() == null) {
            parseResult.setConfidence(0.7f);
        }

        // 如果没有验证信息，设置默认验证
        if (parseResult.getValidation() == null || parseResult.getValidation().isEmpty()) {
            parseResult.setValidation("识别为金额格式");
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("currency", parseResult.getCurrency() != null ? parseResult.getCurrency() : "CNY");
        attributes.put("originalFormat", parseResult.getOriginalFormat() != null ? parseResult.getOriginalFormat() : "unknown");
        if (parseResult.getNumericValue() != null) {
            attributes.put("numericValue", parseResult.getNumericValue());
        }

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(matched)
            .rawValue(matched)
            .normalizedValue(parseResult.getNumericValue())
            .confidence(parseResult.getConfidence())
            .validation(parseResult.getValidation())
            .validationStatus("valid")
            .businessMeaning("金额数据，用于财务计算和记录")
            .dataType("decimal")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_chinese", true);
        options.put("normalize_to_number", true);
        options.put("min_amount", 0);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "合同总金额为人民币壹万元整（¥10,000.00）",
                "[\"人民币壹万元整\", \"¥10,000.00\"]",
                "提取中文和阿拉伯数字金额"
            ),
            ExtractorExample.of(
                "单价¥99.99，总价¥999.90",
                "[\"¥99.99\", \"¥999.90\"]",
                "提取带货币符号的金额"
            ),
            ExtractorExample.of(
                "合计五千元",
                "[\"五千元\"]",
                "提取中文简单金额"
            )
        );
    }

    /**
     * 解析金额
     */
    private MoneyParseResult parseMoney(String text) {
        MoneyParseResult result = new MoneyParseResult();
        result.setOriginalText(text);

        // 判断金额格式类型
        if (text.matches(".*[¥$￥€£].*")) {
            return parseArabicWithSymbol(text);
        } else if (text.matches(".*\\d.*元.*") || text.matches(".*\\d.*RMB.*")) {
            return parseArabicYuan(text);
        } else if (text.matches(".*[一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟].*[元整圆整].*")) {
            return parseChineseMoney(text);
        }

        return result;
    }

    /**
     * 解析带货币符号的阿拉伯数字金额
     */
    private MoneyParseResult parseArabicWithSymbol(String text) {
        MoneyParseResult result = new MoneyParseResult();
        result.setOriginalFormat("arabic_with_symbol");

        // 提取货币符号
        String currency = "CNY";
        if (text.contains("$") || text.contains("美元")) {
            currency = "USD";
        } else if (text.contains("€") || text.contains("欧元")) {
            currency = "EUR";
        } else if (text.contains("£") || text.contains("英镑")) {
            currency = "GBP";
        }

        // 提取数字
        String numberStr = text.replaceAll("[^\\d.,]", "").replace(",", "");
        try {
            double value = Double.parseDouble(numberStr);
            result.setNumericValue(value);
            result.setConfidence(0.98f);
            result.setValidation("阿拉伯数字金额，货币: " + currency);
            result.setCurrency(currency);
        } catch (NumberFormatException e) {
            result.setConfidence(0.5f);
            result.setValidation("数字解析失败");
        }

        return result;
    }

    /**
     * 解析阿拉伯数字+元的金额
     */
    private MoneyParseResult parseArabicYuan(String text) {
        MoneyParseResult result = new MoneyParseResult();
        result.setOriginalFormat("arabic_yuan");
        result.setCurrency("CNY");

        String numberStr = text.replaceAll("[^\\d.,]", "").replace(",", "");
        try {
            double value = Double.parseDouble(numberStr);
            result.setNumericValue(value);
            result.setConfidence(0.95f);
            result.setValidation("阿拉伯数字金额，人民币");
        } catch (NumberFormatException e) {
            result.setConfidence(0.5f);
            result.setValidation("数字解析失败");
        }

        return result;
    }

    /**
     * 解析中文金额
     */
    private MoneyParseResult parseChineseMoney(String text) {
        MoneyParseResult result = new MoneyParseResult();
        result.setOriginalFormat("chinese");
        result.setCurrency("CNY");
        result.setConfidence(0.90f);
        result.setValidation("中文金额（需要人工确认）");

        // 中文金额解析较为复杂，这里简化处理
        // 实际应用中可以实现完整的中文数字转换算法

        return result;
    }

    @Override
    public String getExtractorType() {
        return "money";
    }

    /**
     * 金额解析结果
     */
    @lombok.Data
    private static class MoneyParseResult {
        private String originalText;
        private String originalFormat;
        private Double numericValue;
        private String currency;
        private Float confidence;
        private String validation;
    }
}
