package com.datalabeling.service.extraction;

import com.datalabeling.common.ErrorCode;
import com.datalabeling.entity.Label;
import com.datalabeling.exception.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 提取器协调服务
 * 负责协调不同的提取器，统一处理提取请求
 * 支持内置提取器和动态配置的自定义提取器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExtractionOrchestrator {

    private final IdCardExtractor idCardExtractor;
    private final BankCardExtractor bankCardExtractor;
    private final PhoneExtractor phoneExtractor;
    private final PassportExtractor passportExtractor;
    private final KeywordMatcherExtractor keywordMatcherExtractor;
    private final SchoolInfoExtractor schoolInfoExtractor;
    private final InvalidIdCardExtractor invalidIdCardExtractor;
    private final DynamicExtractor dynamicExtractor;
    private final ObjectMapper objectMapper;

    /**
     * 统一提取入口
     * 根据 Label 类型选择合适的提取方式
     *
     * @param label 标签配置
     * @param rowData 数据行
     * @return 提取结果
     */
    public Map<String, Object> extract(Label label, Map<String, Object> rowData) {
        return extract(label, rowData, 0.80f); // 默认阈值80%
    }

    /**
     * 统一提取入口（带信心度阈值）
     * 根据 Label 类型选择合适的提取方式
     *
     * @param label 标签配置
     * @param rowData 数据行
     * @param confidenceThreshold 信心度阈值，低于此值的结果将被过滤
     * @return 提取结果
     */
    public Map<String, Object> extract(Label label, Map<String, Object> rowData, float confidenceThreshold) {
        String type = label.getType();

        log.debug("开始提取，标签类型: {}, 标签名称: {}, 信心度阈值: {}%", type, label.getName(), Math.round(confidenceThreshold * 100));

        // 结构化提取（新增）
        if (Label.Type.STRUCTURED_EXTRACTION.equals(type)) {
            return extractStructured(label, rowData, confidenceThreshold);
        }

        throw new IllegalArgumentException("不支持的标签类型: " + type + "，此服务仅处理结构化提取");
    }

    /**
     * 结构化提取
     * 使用规则提取器提取结构化号码
     */
    private Map<String, Object> extractStructured(Label label, Map<String, Object> rowData, float confidenceThreshold) {
        long startTime = System.currentTimeMillis();

        try {
            // 解析提取器配置
            JsonNode extractorConfig = parseExtractorConfig(label);
            String extractorType = extractorConfig.path("extractorType").asText();

            // 获取目标文本
            String text = getTargetText(label, rowData);
            if (text == null || text.trim().isEmpty()) {
                return createFailureResult("目标文本为空", startTime);
            }

            log.debug("目标文本长度: {} 字符", text.length());

            // 根据提取器类型分发
            List<ExtractedNumber> results;
            switch (extractorType) {
                case "id_card":
                    results = extractIdCard(text, extractorConfig);
                    break;
                case "bank_card":
                    results = extractBankCard(text, extractorConfig);
                    break;
                case "phone":
                    results = extractPhone(text, extractorConfig);
                    break;
                case "invalid_id_card":
                    results = extractInvalidIdCard(text, extractorConfig);
                    break;
                case "composite":
                    results = extractComposite(text, extractorConfig);
                    break;
                default:
                    // 尝试使用动态提取器（自定义提取器）
                    results = extractDynamic(text, extractorType, extractorConfig);
                    break;
            }

            // 构建返回结果（传入信心度阈值，低于阈值的结果将被过滤）
            return buildResult(label, results, startTime, confidenceThreshold);

        } catch (Exception e) {
            log.error("结构化提取失败", e);
            return createFailureResult("提取失败: " + e.getMessage(), startTime);
        }
    }

    /**
     * 提取身份证号
     */
    private List<ExtractedNumber> extractIdCard(String text, JsonNode extractorConfig) {
        Map<String, Object> options = parseOptions(extractorConfig.path("options"));
        List<ExtractedNumber> results = idCardExtractor.extract(text, options);

        // 设置字段名
        for (ExtractedNumber result : results) {
            result.setField("身份证号");
        }

        return results;
    }

    /**
     * 提取银行卡号
     */
    private List<ExtractedNumber> extractBankCard(String text, JsonNode extractorConfig) {
        Map<String, Object> options = parseOptions(extractorConfig.path("options"));
        List<ExtractedNumber> results = bankCardExtractor.extract(text, options);

        // 设置字段名
        for (ExtractedNumber result : results) {
            result.setField("银行卡号");
        }

        return results;
    }

    /**
     * 提取手机号
     */
    private List<ExtractedNumber> extractPhone(String text, JsonNode extractorConfig) {
        Map<String, Object> options = parseOptions(extractorConfig.path("options"));
        List<ExtractedNumber> results = phoneExtractor.extract(text, options);

        // 设置字段名
        for (ExtractedNumber result : results) {
            result.setField("手机号");
        }

        return results;
    }

    /**
     * 提取错误身份证号
     */
    private List<ExtractedNumber> extractInvalidIdCard(String text, JsonNode extractorConfig) {
        Map<String, Object> options = parseOptions(extractorConfig.path("options"));
        List<ExtractedNumber> results = invalidIdCardExtractor.extract(text, options);

        // 设置字段名
        for (ExtractedNumber result : results) {
            result.setField("错误身份证号");
        }

        return results;
    }

    /**
     * 复合提取（多种号码）
     */
    private List<ExtractedNumber> extractComposite(String text, JsonNode extractorConfig) {
        List<ExtractedNumber> allResults = new ArrayList<>();

        JsonNode extractors = extractorConfig.path("extractors");
        if (!extractors.isArray() || extractors.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "复合提取必须指定 extractors 配置");
        }

        for (JsonNode extractorDef : extractors) {
            String extractorType = extractorDef.path("extractorType").asText();
            String field = extractorDef.path("field").asText();

            List<ExtractedNumber> partialResults;
            switch (extractorType) {
                case "id_card":
                    partialResults = idCardExtractor.extract(text, new HashMap<>());
                    break;
                case "bank_card":
                    partialResults = bankCardExtractor.extract(text, new HashMap<>());
                    break;
                case "phone":
                    partialResults = phoneExtractor.extract(text, new HashMap<>());
                    break;
                case "invalid_id_card":
                    partialResults = invalidIdCardExtractor.extract(text, new HashMap<>());
                    break;
                default:
                    // 尝试使用动态提取器
                    Map<String, Object> options = new HashMap<>();
                    options.put("extractorCode", extractorType);
                    partialResults = dynamicExtractor.extract(text, options);
                    if (partialResults.isEmpty()) {
                        log.warn("未知的提取器类型或无匹配结果: {}", extractorType);
                    }
                    break;
            }

            // 设置字段标识
            for (ExtractedNumber result : partialResults) {
                result.setField(field);
            }

            allResults.addAll(partialResults);
        }

        return allResults;
    }

    /**
     * 动态提取（使用数据库配置的自定义提取器）
     */
    private List<ExtractedNumber> extractDynamic(String text, String extractorCode, JsonNode extractorConfig) {
        Map<String, Object> options = parseOptions(extractorConfig.path("options"));
        options.put("extractorCode", extractorCode);
        
        List<ExtractedNumber> results = dynamicExtractor.extractWithCode(text, extractorCode, options);

        // 获取字段名（从配置或使用提取器代码）
        String fieldName = extractorConfig.path("fieldName").asText();
        if (fieldName == null || fieldName.isEmpty()) {
            fieldName = extractorCode;
        }

        // 设置字段名
        for (ExtractedNumber result : results) {
            result.setField(fieldName);
        }

        return results;
    }

    /**
     * 解析提取器配置
     */
    private JsonNode parseExtractorConfig(Label label) {
        String configStr = label.getExtractorConfig();
        if (configStr == null || configStr.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "结构化提取必须配置 extractorConfig");
        }

        try {
            return objectMapper.readTree(configStr);
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "解析 extractorConfig 失败: " + e.getMessage());
        }
    }

    /**
     * 解析选项
     */
    private Map<String, Object> parseOptions(JsonNode optionsNode) {
        Map<String, Object> options = new HashMap<>();

        if (optionsNode != null && optionsNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = optionsNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                String key = entry.getKey();
                JsonNode valueNode = entry.getValue();

                if (valueNode.isBoolean()) {
                    options.put(key, valueNode.asBoolean());
                } else if (valueNode.isInt()) {
                    options.put(key, valueNode.asInt());
                } else if (valueNode.isTextual()) {
                    options.put(key, valueNode.asText());
                }
            }
        }

        return options;
    }

    /**
     * 获取目标文本
     * 优先使用关注列，如果没有则使用所有数据
     */
    private String getTargetText(Label label, Map<String, Object> rowData) {
        List<String> focusColumns = label.getFocusColumns();

        if (focusColumns != null && !focusColumns.isEmpty()) {
            // 只关注指定列
            StringBuilder sb = new StringBuilder();
            for (String column : focusColumns) {
                Object value = rowData.get(column);
                if (value != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(value.toString());
                }
            }
            return sb.toString();
        } else {
            // 使用所有数据
            StringBuilder sb = new StringBuilder();
            for (Object value : rowData.values()) {
                if (value != null) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(value.toString());
                }
            }
            return sb.toString();
        }
    }

    /**
     * 构建返回结果
     * @param label 标签
     * @param results 提取结果
     * @param startTime 开始时间
     * @param confidenceThreshold 信心度阈值，低于此值的结果将被直接过滤
     */
    private Map<String, Object> buildResult(Label label, List<ExtractedNumber> results, long startTime, float confidenceThreshold) {
        if (results.isEmpty()) {
            return createEmptyResult(startTime);
        }

        // 去重：按值去重，保留信心度最高的
        Map<String, ExtractedNumber> uniqueResults = new LinkedHashMap<>();
        for (ExtractedNumber result : results) {
            String key = result.getField() + ":" + result.getValue();
            ExtractedNumber existing = uniqueResults.get(key);
            if (existing == null || result.getConfidence() > existing.getConfidence()) {
                uniqueResults.put(key, result);
            }
        }

        // 直接过滤掉低于阈值的结果，不再展示
        List<ExtractedNumber> filteredResults = uniqueResults.values().stream()
            .filter(r -> r.getConfidence() >= confidenceThreshold)
            .collect(Collectors.toList());

        // 记录被过滤的数量（用于日志）
        int filteredOutCount = uniqueResults.size() - filteredResults.size();
        if (filteredOutCount > 0) {
            log.info("过滤掉 {} 个低信心度结果（阈值: {}%）", filteredOutCount, Math.round(confidenceThreshold * 100));
        }

        // 如果过滤后没有结果，返回空结果
        if (filteredResults.isEmpty()) {
            return createEmptyResult(startTime);
        }

        // 按字段分组
        Map<String, List<String>> groupedResults = filteredResults.stream()
            .collect(Collectors.groupingBy(
                ExtractedNumber::getField,
                LinkedHashMap::new,
                Collectors.mapping(ExtractedNumber::getValue, Collectors.toList())
            ));

        // 计算平均置信度（仅计算通过阈值的结果）
        float avgConfidence = (float) filteredResults.stream()
            .mapToDouble(ExtractedNumber::getConfidence)
            .average()
            .orElse(0);

        // 生成结构化的验证信息
        StringBuilder reasoning = new StringBuilder();
        reasoning.append("✓ 提取结果（").append(filteredResults.size()).append("个）：\n");
        for (ExtractedNumber r : filteredResults) {
            reasoning.append("  • ").append(r.getField()).append(": ")
                .append(r.getValue())
                .append(" (信心度: ").append(Math.round(r.getConfidence() * 100)).append("%")
                .append(", ").append(r.getValidation()).append(")\n");
        }

        // 构建返回结果
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("extractedData", groupedResults);
        result.put("confidence", Math.round(avgConfidence * 100));
        result.put("reasoning", reasoning.toString().trim());
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("totalCount", filteredResults.size());
        result.put("filteredOutCount", filteredOutCount);
        result.put("extractResults", filteredResults);

        log.info("提取完成: 提取到 {} 个结果，过滤掉 {} 个低信心度结果，平均置信度: {}%",
            filteredResults.size(), filteredOutCount, result.get("confidence"));

        return result;
    }

    /**
     * 创建空结果
     */
    private Map<String, Object> createEmptyResult(long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", true);
        result.put("extractedData", new HashMap<>());
        result.put("confidence", 0);
        result.put("reasoning", "未提取到符合条件的信息");
        result.put("durationMs", System.currentTimeMillis() - startTime);
        result.put("highConfidenceCount", 0);
        result.put("lowConfidenceCount", 0);
        result.put("totalCount", 0);
        result.put("extractResults", new ArrayList<>());

        return result;
    }

    /**
     * 创建失败结果
     */
    private Map<String, Object> createFailureResult(String errorMessage, long startTime) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("extractedData", null);
        result.put("confidence", 0);
        result.put("error", errorMessage);
        result.put("durationMs", System.currentTimeMillis() - startTime);

        return result;
    }

    // ============ 提取器访问方法 ============

    /**
     * 获取身份证提取器
     */
    public IdCardExtractor getIdCardExtractor() {
        return idCardExtractor;
    }

    /**
     * 获取银行卡提取器
     */
    public BankCardExtractor getBankCardExtractor() {
        return bankCardExtractor;
    }

    /**
     * 获取手机号提取器
     */
    public PhoneExtractor getPhoneExtractor() {
        return phoneExtractor;
    }

    /**
     * 获取护照号提取器
     */
    public PassportExtractor getPassportExtractor() {
        return passportExtractor;
    }

    /**
     * 获取关键词匹配提取器
     */
    public KeywordMatcherExtractor getKeywordMatcherExtractor() {
        return keywordMatcherExtractor;
    }

    /**
     * 获取学校信息提取器
     */
    public SchoolInfoExtractor getSchoolInfoExtractor() {
        return schoolInfoExtractor;
    }

    /**
     * 获取动态提取器
     */
    public DynamicExtractor getDynamicExtractor() {
        return dynamicExtractor;
    }

    /**
     * 获取错误身份证号提取器
     */
    public InvalidIdCardExtractor getInvalidIdCardExtractor() {
        return invalidIdCardExtractor;
    }
}
