package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

/**
 * 邮箱地址提取器
 *
 * 提取功能：
 * - 标准邮箱格式（username@domain.com）
 * - 支持子域名和域名后缀
 * - 识别常见邮箱服务商（QQ、163、Gmail等）
 * - 验证邮箱格式合法性
 *
 * 业务场景：
 * - 用户注册信息
 * - 联系方式采集
 * - 客户资料整理
 */
@Slf4j
@Component
public class EmailExtractor extends AbstractEnhancedExtractor {

    // 常见邮箱服务商域名
    private static final Map<String, String> EMAIL_PROVIDERS = new LinkedHashMap<>();
    static {
        EMAIL_PROVIDERS.put("qq.com", "QQ邮箱");
        EMAIL_PROVIDERS.put("163.com", "网易163邮箱");
        EMAIL_PROVIDERS.put("126.com", "网易126邮箱");
        EMAIL_PROVIDERS.put("gmail.com", "Gmail");
        EMAIL_PROVIDERS.put("outlook.com", "Outlook");
        EMAIL_PROVIDERS.put("hotmail.com", "Hotmail");
        EMAIL_PROVIDERS.put("yahoo.com", "Yahoo邮箱");
        EMAIL_PROVIDERS.put("sina.com", "新浪邮箱");
        EMAIL_PROVIDERS.put("sohu.com", "搜狐邮箱");
        EMAIL_PROVIDERS.put("foxmail.com", "Foxmail");
        EMAIL_PROVIDERS.put("aliyun.com", "阿里云邮箱");
        EMAIL_PROVIDERS.put("icloud.com", "iCloud邮箱");
        EMAIL_PROVIDERS.put("139.com", "139移动邮箱");
        EMAIL_PROVIDERS.put("189.cn", "189电信邮箱");
    }

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("email")
        .name("邮箱地址提取器")
        .description("提取各种格式的邮箱地址，识别常见邮箱服务商")
        .category("builtin")
        .outputField("邮箱")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("联系方式", "用户信息", "邮箱"))
        .useCase("用户注册、客户资料、联系方式采集")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("validate_format")
                .name("验证格式")
                .description("验证邮箱格式是否符合标准")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("identify_provider")
                .name("识别服务商")
                .description("识别邮箱所属服务商")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("exclude_test_emails")
                .name("排除测试邮箱")
                .description("排除test@example.com等测试邮箱")
                .type("boolean")
                .defaultValue(false)
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
            // 标准邮箱格式（高置信度）
            ExtractorPattern.highPriority(
                "standard",
                "\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b",
                0.95f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String email = matcher.group();

        // 检查是否排除测试邮箱
        if (Boolean.TRUE.equals(options.get("exclude_test_emails"))) {
            if (isTestEmail(email)) {
                return null;
            }
        }

        // 提取邮箱用户名和域名
        String[] parts = email.split("@");
        String username = parts[0];
        String domain = parts.length > 1 ? parts[1] : "";

        // 识别邮箱服务商
        String provider = null;
        if (Boolean.TRUE.equals(options.get("identify_provider"))) {
            provider = identifyProvider(domain);
        }

        // 计算置信度
        float confidence = 0.95f;
        String validation = "标准邮箱格式";

        if (provider != null) {
            validation += "，服务商: " + provider;
            confidence = 0.98f; // 识别到服务商提高置信度
        } else {
            validation += "，域名: " + domain;
        }

        // 验证邮箱格式
        if (Boolean.TRUE.equals(options.get("validate_format"))) {
            if (!isValidEmail(email)) {
                confidence = 0.6f;
                validation = "邮箱格式可能不正确";
            }
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("username", username);
        attributes.put("domain", domain);
        if (provider != null) {
            attributes.put("provider", provider);
        }

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(email)
            .rawValue(email)
            .confidence(confidence)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning("用户邮箱地址，用于联系和身份验证")
            .dataType("email")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("validate_format", true);
        options.put("identify_provider", true);
        options.put("exclude_test_emails", false);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "我的邮箱是 zhangsan@qq.com，请联系我",
                "[\"zhangsan@qq.com\"]",
                "提取QQ邮箱"
            ),
            ExtractorExample.of(
                "工作邮箱：john.doe@example.com，私人邮箱：john@gmail.com",
                "[\"john.doe@example.com\", \"john@gmail.com\"]",
                "提取多个邮箱"
            ),
            ExtractorExample.of(
                "联系邮箱：user_123@163.com",
                "[\"user_123@163.com\"]",
                "提取带下划线的邮箱"
            )
        );
    }

    /**
     * 识别邮箱服务商
     */
    private String identifyProvider(String domain) {
        return EMAIL_PROVIDERS.get(domain.toLowerCase());
    }

    /**
     * 验证邮箱格式
     */
    private boolean isValidEmail(String email) {
        // 简单验证：包含@，域名部分有点号
        int atIndex = email.lastIndexOf('@');
        if (atIndex < 0 || atIndex == email.length() - 1) {
            return false;
        }

        String domain = email.substring(atIndex + 1);
        int dotIndex = domain.lastIndexOf('.');
        return dotIndex > 0 && dotIndex < domain.length() - 1;
    }

    /**
     * 判断是否为测试邮箱
     */
    private boolean isTestEmail(String email) {
        String lowerEmail = email.toLowerCase();
        return lowerEmail.startsWith("test@") ||
               lowerEmail.startsWith("example@") ||
               lowerEmail.contains("@example.com") ||
               lowerEmail.contains("@test.com");
    }

    @Override
    public String getExtractorType() {
        return "email";
    }
}
