package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;

/**
 * IP地址提取器
 *
 * 提取功能：
 * - IPv4地址（192.168.1.1）
 * - IPv4地址段（192.168.1.0/24）
 * - IPv6地址（简化支持）
 * - 识别私有地址、公网地址
 * - 识别特殊地址（ loopback、组播等）
 *
 * 业务场景：
 * - 网络日志分析
 * - 安全审计
 * - 访问记录整理
 */
@Slf4j
@Component
public class IpAddressExtractor extends AbstractEnhancedExtractor {

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("ip_address")
        .name("IP地址提取器")
        .description("提取IPv4/IPv6地址，识别私有地址、公网地址等类型")
        .category("builtin")
        .outputField("IP地址")
        .dataType("string")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("网络", "IP", "地址"))
        .useCase("网络日志、安全审计、访问记录")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_ipv6")
                .name("包含IPv6")
                .description("提取IPv6地址")
                .type("boolean")
                .defaultValue(false)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("identify_type")
                .name("识别地址类型")
                .description("识别地址类型（私有/公网/特殊）")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("exclude_reserved")
                .name("排除保留地址")
                .description("排除0.0.0.0等保留地址")
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
            // IPv4地址（标准格式）
            ExtractorPattern.highPriority(
                "ipv4",
                "\\b(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
                + "(\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}\\b",
                0.98f
            ),
            // IPv4地址段（CIDR格式）
            ExtractorPattern.of(
                "ipv4_cidr",
                "\\b(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)"
                + "(\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}"
                + "/(3[0-2]|[12]?\\d)\\b",
                0.95f
            ),
            // IPv6地址（简化版，完整版过于复杂）
            ExtractorPattern.of(
                "ipv6",
                "\\b([0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}\\b",
                0.90f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String ip = matcher.group();

        // 检查是否为IPv6
        boolean isIPv6 = ip.contains(":");
        if (isIPv6 && !Boolean.TRUE.equals(options.get("include_ipv6"))) {
            return null;
        }

        // 识别地址类型
        String addressType = null;
        String validation = null;

        if (!isIPv6) {
            addressType = identifyIPv4Type(ip);
            validation = "IPv4地址，类型: " + addressType;
        } else {
            addressType = "IPv6";
            validation = "IPv6地址";
        }

        // 检查是否排除保留地址
        if (Boolean.TRUE.equals(options.get("exclude_reserved"))) {
            if ("保留地址".equals(addressType) || "环回地址".equals(addressType)) {
                return null;
            }
        }

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("version", isIPv6 ? 6 : 4);
        attributes.put("addressType", addressType);

        if (Boolean.TRUE.equals(options.get("identify_type"))) {
            attributes.put("isPrivate", "私有地址".equals(addressType) || "本地链路地址".equals(addressType));
            attributes.put("isPublic", "公网地址".equals(addressType));
        }

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(ip)
            .rawValue(ip)
            .confidence(isIPv6 ? 0.90f : 0.98f)
            .validation(validation)
            .validationStatus("valid")
            .businessMeaning("网络地址，用于设备定位和通信")
            .dataType("string")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_ipv6", false);
        options.put("identify_type", true);
        options.put("exclude_reserved", false);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "服务器IP：192.168.1.100",
                "[\"192.168.1.100\"]",
                "提取私有IP地址"
            ),
            ExtractorExample.of(
                "访问来源：8.8.8.8，目标：192.168.1.1",
                "[\"8.8.8.8\", \"192.168.1.1\"]",
                "提取公网和私有IP"
            ),
            ExtractorExample.of(
                "网段：10.0.0.0/24",
                "[\"10.0.0.0/24\"]",
                "提取IP地址段"
            )
        );
    }

    /**
     * 识别IPv4地址类型
     */
    private String identifyIPv4Type(String ip) {
        String[] parts = ip.split("\\.");
        if (parts.length != 4) {
            return "格式错误";
        }

        try {
            int first = Integer.parseInt(parts[0]);
            int second = Integer.parseInt(parts[1]);

            // 环回地址
            if (ip.equals("127.0.0.1") || ip.startsWith("127.")) {
                return "环回地址";
            }

            // 保留地址
            if (ip.equals("0.0.0.0")) {
                return "保留地址";
            }

            // 私有地址
            if (first == 10) {
                return "私有地址";
            }
            if (first == 172 && second >= 16 && second <= 31) {
                return "私有地址";
            }
            if (first == 192 && second == 168) {
                return "私有地址";
            }
            if (first == 169 && second == 254) {
                return "本地链路地址";
            }

            // 组播地址
            if (first >= 224 && first <= 239) {
                return "组播地址";
            }

            // 公网地址
            return "公网地址";

        } catch (NumberFormatException e) {
            return "格式错误";
        }
    }

    @Override
    public String getExtractorType() {
        return "ip_address";
    }
}
