package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

/**
 * 公司名称提取器
 *
 * 提取功能：
 * - 有限责任公司
 * - 股份有限公司
 * - 个体工商户
 * - 分公司
 * - 识别企业类型
 *
 * 业务场景：
 * - 企业信息整理
 * - 合同主体识别
 * - 客户资料管理
 */
@Slf4j
@Component
public class CompanyNameExtractor extends AbstractEnhancedExtractor {

    // 企业类型后缀
    private static final Map<String, String> COMPANY_TYPES = new LinkedHashMap<>();
    static {
        COMPANY_TYPES.put("有限责任公司", "有限责任公司");
        COMPANY_TYPES.put("有限公司", "有限责任公司");
        COMPANY_TYPES.put("股份有限公司", "股份有限公司");
        COMPANY_TYPES.put("股份公司", "股份有限公司");
        COMPANY_TYPES.put("个体工商户", "个体工商户");
        COMPANY_TYPES.put("个体户", "个体工商户");
        COMPANY_TYPES.put("分公司", "分公司");
        COMPANY_TYPES.put("子公司", "子公司");
        COMPANY_TYPES.put("集团", "集团公司");
        COMPANY_TYPES.put("厂", "工厂");
        COMPANY_TYPES.put("店", "店铺");
        COMPANY_TYPES.put("中心", "中心");
        COMPANY_TYPES.put("工作室", "工作室");
        COMPANY_TYPES.put("合伙企业", "合伙企业");
    }

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("company_name")
        .name("公司名称提取器")
        .description("提取各种类型的公司名称，识别企业类型")
        .category("builtin")
        .outputField("公司名称")
        .dataType("string")
        .multiValue(true)
        .accuracy("medium")
        .performance("medium")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("企业", "公司", "组织"))
        .useCase("企业信息、合同主体、客户资料")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("min_length")
                .name("最小长度")
                .description("公司名称最小字符数")
                .type("number")
                .defaultValue(5)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_location")
                .name("包含地名")
                .description("名称中是否包含地名前缀")
                .type("boolean")
                .defaultValue(true)
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
            // 完整公司名称（地名+公司名+类型后缀）
            ExtractorPattern.highPriority(
                "full_company",
                "[\\u4e00-\\u9fa5]{2,5}(?:省|市|区|县)?[\\u4e00-\\u9fa5]{2,20}(?:有限|股份|集团|分公司|个体|合伙|厂|店|中心|工作室)",
                0.90f
            ),
            // 简化公司名称（公司名+有限公司）
            ExtractorPattern.of(
                "simple_company",
                "[\\u4e00-\\u9fa5]{2,15}(?:有限公司|有限责任公司|股份公司)",
                0.85f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String company = matcher.group();

        // 检查最小长度（安全地处理类型转换）
        int minLength = 5;
        Object minLengthObj = options.get("min_length");
        if (minLengthObj instanceof Number) {
            minLength = ((Number) minLengthObj).intValue();
        }
        if (company.length() < minLength) {
            return null;
        }

        // 识别企业类型
        String companyType = identifyCompanyType(company);

        // 提取公司简称（去除后缀和地名）
        String shortName = extractShortName(company);

        // 构建验证信息
        String validation = "企业类型: " + (companyType != null ? companyType : "未知");
        if (shortName != null) {
            validation += "，简称: " + shortName;
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        if (companyType != null) {
            attributes.put("companyType", companyType);
        }
        if (shortName != null) {
            attributes.put("shortName", shortName);
        }
        attributes.put("length", company.length());

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(company)
            .rawValue(company)
            .confidence(companyType != null ? 0.90f : 0.70f)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning("企业或组织名称，用于商业合作和身份识别")
            .dataType("string")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("min_length", 5);
        options.put("include_location", true);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "供应商：北京某某科技有限公司",
                "[\"北京某某科技有限公司\"]",
                "提取科技公司名称"
            ),
            ExtractorExample.of(
                "合同甲方：上海XX股份有限公司",
                "[\"上海XX股份有限公司\"]",
                "提取股份公司名称"
            ),
            ExtractorExample.of(
                "个体户：张三餐饮店",
                "[\"张三餐饮店\"]",
                "提取个体户名称"
            )
        );
    }

    /**
     * 识别企业类型
     */
    private String identifyCompanyType(String company) {
        for (Map.Entry<String, String> entry : COMPANY_TYPES.entrySet()) {
            if (company.endsWith(entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    /**
     * 提取公司简称
     */
    private String extractShortName(String company) {
        String shortName = company;

        // 去除地名前缀
        int locationEnd = -1;
        String[] locations = {"省", "市", "区", "县", "自治区", "自治州"};
        for (String location : locations) {
            int index = company.indexOf(location);
            if (index > 0 && index < 6) {
                locationEnd = index + location.length();
                break;
            }
        }
        if (locationEnd > 0) {
            shortName = shortName.substring(locationEnd);
        }

        // 去除企业类型后缀
        for (String suffix : COMPANY_TYPES.keySet()) {
            if (shortName.endsWith(suffix)) {
                shortName = shortName.substring(0, shortName.length() - suffix.length());
                break;
            }
        }

        if (shortName.length() < company.length()) {
            return shortName;
        }
        return null;
    }

    @Override
    public String getExtractorType() {
        return "company_name";
    }
}
