package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

/**
 * URL提取器
 *
 * 提取功能：
 * - HTTP/HTTPS URL
 * - FTP URL
 * - 识别域名、路径、参数
 * - 识别常见网站
 *
 * 业务场景：
 * - 网页链接提取
 * - 资源URL整理
 * - 网络日志分析
 */
@Slf4j
@Component
public class UrlExtractor extends AbstractEnhancedExtractor {

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("url")
        .name("URL提取器")
        .description("提取各种格式的URL，识别域名和路径")
        .category("builtin")
        .outputField("URL")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("网络", "URL", "链接"))
        .useCase("网页链接、资源URL、网络日志")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_protocol")
                .name("包含协议")
                .description("是否包含协议头（http://等）")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("parse_components")
                .name("解析组件")
                .description("解析URL的各个组件（协议、域名、路径等）")
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
            // HTTP/HTTPS URL（完整格式）
            ExtractorPattern.highPriority(
                "http_url",
                "\\b(https?|ftp)://[^\\s/$.?#][^\\s]*\\b",
                0.98f
            ),
            // 简化域名（www开头，无协议）
            ExtractorPattern.of(
                "www_domain",
                "\\bwww\\.[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?\\b",
                0.85f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String url = matcher.group();

        // 解析URL组件
        Map<String, Object> components = new HashMap<>();
        if (Boolean.TRUE.equals(options.get("parse_components"))) {
            components = parseUrlComponents(url);
        }

        // 构建验证信息
        String validation = "标准URL格式";
        if (components.containsKey("domain")) {
            validation += "，域名: " + components.get("domain");
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("protocol", components.getOrDefault("protocol", "unknown"));
        if (components.containsKey("domain")) {
            attributes.put("domain", components.get("domain"));
        }
        if (components.containsKey("path")) {
            attributes.put("path", components.get("path"));
        }
        if (components.containsKey("query")) {
            attributes.put("hasQuery", true);
        }

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(url)
            .rawValue(url)
            .confidence(url.startsWith("http") ? 0.98f : 0.85f)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning("网络资源地址，用于访问网页或资源")
            .dataType("url")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_protocol", true);
        options.put("parse_components", true);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "官网：https://www.example.com",
                "[\"https://www.example.com\"]",
                "提取HTTPS URL"
            ),
            ExtractorExample.of(
                "访问 http://localhost:8080/api/users",
                "[\"http://localhost:8080/api/users\"]",
                "提取带路径的URL"
            ),
            ExtractorExample.of(
                "更多信息请访问 www.baidu.com",
                "[\"www.baidu.com\"]",
                "提取简化域名"
            )
        );
    }

    /**
     * 解析URL组件
     */
    private Map<String, Object> parseUrlComponents(String url) {
        Map<String, Object> components = new HashMap<>();

        // 提取协议
        int protocolEnd = url.indexOf("://");
        if (protocolEnd > 0) {
            components.put("protocol", url.substring(0, protocolEnd));
        } else {
            components.put("protocol", "http");
        }

        // 提取域名
        int domainStart = protocolEnd > 0 ? protocolEnd + 3 : 0;
        int domainEnd = url.indexOf('/', domainStart);
        if (domainEnd < 0) {
            domainEnd = url.indexOf('?', domainStart);
        }
        if (domainEnd < 0) {
            domainEnd = url.indexOf('#', domainStart);
        }
        if (domainEnd < 0) {
            domainEnd = url.length();
        }

        String domain = url.substring(domainStart, domainEnd);
        components.put("domain", domain);

        // 提取路径
        int pathEnd = url.indexOf('?', domainEnd);
        if (pathEnd < 0) {
            pathEnd = url.indexOf('#', domainEnd);
        }
        if (pathEnd < 0) {
            pathEnd = url.length();
        }

        if (domainEnd < pathEnd) {
            String path = url.substring(domainEnd, pathEnd);
            if (!path.isEmpty()) {
                components.put("path", path);
            }
        }

        // 检查是否有查询参数
        if (url.contains("?") && url.indexOf('?') < url.indexOf('#')) {
            components.put("query", true);
        }

        return components;
    }

    @Override
    public String getExtractorType() {
        return "url";
    }
}
