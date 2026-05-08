package com.datalabeling.service.extraction;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 错误身份证号增强型提取器
 *
 * 适配器模式的实现，将 InvalidIdCardExtractor 包装为 IEnhancedExtractor
 * 使其能够自动注册到 ExtractorRegistry 并显示在提取器列表中
 *
 * @author DataLabeling System
 * @version 1.0.0
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InvalidIdCardExtractorEnhanced implements IEnhancedExtractor {

    private final InvalidIdCardExtractor delegate;

    /**
     * 获取提取器元数据
     */
    @Override
    public ExtractorMetadata getMetadata() {
        return ExtractorMetadata.builder()
            .code("invalid_id_card")
            .name("错误身份证号提取器")
            .description("提取不符合标准格式但疑似身份证的号码，严格排除银行卡号和手机号")
            .category("builtin")
            .outputField("错误身份证号")
            .dataType("string")
            .multiValue(true)
            .tags(Arrays.asList("身份证", "号码提取", "数据校验", "错误检测"))
            .useCase("数据清洗、质量检查、错误身份证号识别")
            .accuracy("medium")
            .performance("fast")
            .version("1.0.0")
            .author("DataLabeling System")
            .options(buildExtractorOptions())
            .build();
    }

    /**
     * 构建配置选项
     */
    private List<ExtractorMetadata.ExtractorOption> buildExtractorOptions() {
        List<ExtractorMetadata.ExtractorOption> options = new ArrayList<>();

        options.add(ExtractorMetadata.ExtractorOption.builder()
            .key("includeHighConfidence")
            .name("包含高置信度结果")
            .description("是否提取具有身份证特征但位数不对的号码")
            .type("boolean")
            .defaultValue(true)
            .required(false)
            .build());

        options.add(ExtractorMetadata.ExtractorOption.builder()
            .key("includeMediumConfidence")
            .name("包含中置信度结果")
            .description("是否提取16-17位号码")
            .type("boolean")
            .defaultValue(true)
            .required(false)
            .build());

        options.add(ExtractorMetadata.ExtractorOption.builder()
            .key("includeLowConfidence")
            .name("包含低置信度结果")
            .description("是否提取19-20位号码")
            .type("boolean")
            .defaultValue(false)
            .required(false)
            .build());

        return options;
    }

    /**
     * 基础提取方法（委托给 InvalidIdCardExtractor）
     */
    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        return delegate.extract(text, options);
    }

    /**
     * 增强型提取方法
     * 返回带有丰富上下文信息的提取结果
     */
    @Override
    public List<EnhancedExtractedResult> extractWithContext(String text, Map<String, Object> options) {
        validateOptions(options);

        List<ExtractedNumber> basicResults = delegate.extract(text, options);

        // 转换为增强型结果
        return basicResults.stream()
            .map(this::toEnhancedResult)
            .collect(Collectors.toList());
    }

    /**
     * 将基础提取结果转换为增强型结果
     */
    private EnhancedExtractedResult toEnhancedResult(ExtractedNumber basic) {
        EnhancedExtractedResult enhanced = EnhancedExtractedResult.builder()
            .field(basic.getField() != null ? basic.getField() : "错误身份证号")
            .value(basic.getValue())
            .rawValue(basic.getValue())
            .confidence(basic.getConfidence())
            .validation(basic.getValidation())
            .validationStatus(determineValidationStatus(basic.getConfidence()))
            .startIndex(basic.getStartIndex())
            .endIndex(basic.getEndIndex())
            .dataType("string")
            .extractorVersion("1.0.0")
            .businessMeaning("疑似错误的身份证号码，需要人工核实")
            .build();

        // 添加附加属性
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("length", basic.getValue().length());
        attributes.put("category", getCategoryByLength(basic.getValue().length()));
        enhanced.setAttributes(attributes);

        return enhanced;
    }

    /**
     * 根据置信度确定验证状态
     */
    private String determineValidationStatus(float confidence) {
        if (confidence >= 0.8f) {
            return "likely"; // 很可能是错误身份证号
        } else if (confidence >= 0.6f) {
            return "possible"; // 可能是错误身份证号
        } else {
            return "unknown"; // 不确定
        }
    }

    /**
     * 根据长度获取分类
     */
    private String getCategoryByLength(int length) {
        switch (length) {
            case 16:
                return "缺少后2位";
            case 17:
                return "缺少校验位";
            case 19:
                return "多1位";
            case 20:
                return "多2位";
            default:
                return "位数异常";
        }
    }

    /**
     * 构建大模型提示词上下文
     */
    @Override
    public String buildLLMPromptContext(List<EnhancedExtractedResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("=== 错误身份证号提取结果 ===\n");
        sb.append("提取器: invalid_id_card\n");
        sb.append("说明: 以下号码不符合标准身份证格式，但疑似身份证号\n");
        sb.append("排除: 已排除银行卡号、手机号、标准身份证号\n");
        sb.append("结果数量: ").append(results.size()).append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            EnhancedExtractedResult result = results.get(i);
            sb.append("结果 #").append(i + 1).append(":\n");
            sb.append("  值: ").append(result.getValue()).append("\n");
            sb.append("  长度: ").append(result.getValue().length()).append("位\n");
            sb.append("  置信度: ").append(Math.round(result.getConfidence() * 100)).append("%\n");
            sb.append("  验证状态: ").append(result.getValidationStatus()).append("\n");

            if (result.getValidation() != null) {
                sb.append("  说明: ").append(result.getValidation()).append("\n");
            }

            if (result.getAttributes() != null) {
                sb.append("  分类: ").append(result.getAttributes().get("category")).append("\n");
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 验证提取选项
     */
    @Override
    public void validateOptions(Map<String, Object> options) {
        if (options == null) {
            return;
        }

        // 验证布尔型选项
        String[] booleanOptions = {
            "includeHighConfidence",
            "includeMediumConfidence",
            "includeLowConfidence"
        };

        for (String key : booleanOptions) {
            if (options.containsKey(key) && !(options.get(key) instanceof Boolean)) {
                throw new IllegalArgumentException(
                    "选项 '" + key + "' 必须是布尔类型 (true/false)"
                );
            }
        }
    }

    /**
     * 获取默认配置选项
     */
    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> defaults = new HashMap<>();
        defaults.put("includeHighConfidence", true);
        defaults.put("includeMediumConfidence", true);
        defaults.put("includeLowConfidence", false);
        return defaults;
    }

    /**
     * 获取示例数据
     */
    @Override
    public List<ExtractorExample> getExamples() {
        List<ExtractorExample> examples = new ArrayList<>();

        // 示例1: 17位身份证（缺少校验位）
        examples.add(ExtractorExample.of(
            "身份证号码：11010119900101123",
            "[{\"value\": \"11010119900101123\", \"confidence\": 85}]",
            "17位号码，具有身份证特征但缺少校验位"
        ));

        // 示例2: 16位身份证（缺少后2位）
        examples.add(ExtractorExample.of(
            "身份证号3101011990010112",
            "[{\"value\": \"3101011990010112\", \"confidence\": 85}]",
            "16位号码，具有身份证特征但缺少后2位"
        ));

        // 示例3: 19位号码（多1位）
        examples.add(ExtractorExample.of(
            "身份证1234567890123456789",
            "[{\"value\": \"1234567890123456789\", \"confidence\": 45}]",
            "19位号码，位数异常"
        ));

        // 示例4: 混合场景（银行卡号应被排除）
        examples.add(ExtractorExample.of(
            "卡号6227001234567890 身份证11010119900101123 手机13812345678",
            "[{\"value\": \"11010119900101123\", \"confidence\": 85}]",
            "混合场景：银行卡号和手机号应被排除，只提取错误身份证号"
        ));

        // 示例5: 多个错误身份证号
        examples.add(ExtractorExample.of(
            "身份证1：11010119900101123，身份证2：3101011990010112",
            "[{\"value\": \"11010119900101123\", \"confidence\": 85}, {\"value\": \"3101011990010112\", \"confidence\": 85}]",
            "提取多个错误身份证号"
        ));

        return examples;
    }

    /**
     * 获取提取器类型
     */
    @Override
    public String getExtractorType() {
        return "invalid_id_card";
    }
}
