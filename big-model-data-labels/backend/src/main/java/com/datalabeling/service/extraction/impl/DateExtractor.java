package com.datalabeling.service.extraction.impl;

import com.datalabeling.service.extraction.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;

/**
 * 日期提取器
 *
 * 提取功能：
 * - 标准日期格式（2024-01-15、2024/01/15）
 * - 中文日期（2024年1月15日）
 * - 短日期格式（01-15、1月15日）
 * - 时间戳（1705334400）
 * - 日期时间（2024-01-15 14:30:00）
 * - 标准化为ISO格式
 *
 * 业务场景：
 * - 合同日期提取
 * - 日程安排识别
 * - 时间记录整理
 */
@Slf4j
@Component
public class DateExtractor extends AbstractEnhancedExtractor {

    private static final ExtractorMetadata METADATA = ExtractorMetadata.builder()
        .code("date")
        .name("日期提取器")
        .description("提取各种格式的日期时间，支持中文和标准格式，自动标准化为ISO格式")
        .category("builtin")
        .outputField("日期")
        .dataType("date")
        .multiValue(true)
        .accuracy("high")
        .performance("fast")
        .version("1.0.0")
        .author("System")
        .tags(Arrays.asList("日期", "时间", "格式化"))
        .useCase("合同日期、日程安排、时间记录")
        .options(Arrays.asList(
            ExtractorMetadata.ExtractorOption.builder()
                .key("include_time")
                .name("包含时间")
                .description("提取日期时间（不仅仅是日期）")
                .type("boolean")
                .defaultValue(true)
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("normalize_format")
                .name("标准化格式")
                .description("标准化输出格式（iso/timestamp/chinese）")
                .type("select")
                .defaultValue("iso")
                .selectOptions(Arrays.asList("iso", "timestamp", "chinese"))
                .build(),
            ExtractorMetadata.ExtractorOption.builder()
                .key("allow_ambiguous")
                .name("允许模糊日期")
                .description("允许01-15这种可能表示1月15日或其他含义的格式")
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
            // ISO 8601格式（YYYY-MM-DD或YYYY/MM/DD）
            ExtractorPattern.highPriority(
                "iso_date",
                "\\b(\\d{4})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\\d|3[01])\\b",
                0.98f
            ),
            // ISO 8601日期时间
            ExtractorPattern.of(
                "iso_datetime",
                "\\b(\\d{4})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\\d|3[01])[ T](0[1-9]|1\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?\\b",
                0.98f
            ),
            // 中文日期（2024年1月15日）
            ExtractorPattern.of(
                "chinese_date",
                "\\b(\\d{4})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日\\b",
                0.95f
            ),
            // 中文日期时间（2024年1月15日 14时30分）
            ExtractorPattern.of(
                "chinese_datetime",
                "\\b(\\d{4})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日[\\s]*(0?[1-9]|1\\d|2[0-3])时([0-5]\\d)分(?([0-5]\\d)秒)?\\b",
                0.95f
            ),
            // 短日期（01-15，需要上下文判断）
            ExtractorPattern.of(
                "short_date",
                "\\b(0[1-9]|1[0-2])[-/](0[1-9]|[12]\\d|3[01])\\b",
                0.70f
            ),
            // 时间戳（10位或13位数字）
            ExtractorPattern.of(
                "timestamp",
                "\\b(\\d{10}|\\d{13})\\b",
                0.65f
            )
        );
    }

    @Override
    protected EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options) {
        String matched = matcher.group();

        // 解析日期
        DateParseResult parseResult = parseDate(matched);

        if (parseResult == null || !parseResult.isValid()) {
            return null;
        }

        // 检查是否允许模糊日期
        if (!Boolean.TRUE.equals(options.get("allow_ambiguous")) && parseResult.isAmbiguous()) {
            return null;
        }

        // 标准化格式
        String normalizedValue = normalizeDate(parseResult, (String) options.getOrDefault("normalize_format", "iso"));

        // 构建结果
        Map<String, Object> attributes = new HashMap<>();
        attributes.put("year", parseResult.getYear());
        attributes.put("month", parseResult.getMonth());
        attributes.put("day", parseResult.getDay());
        if (parseResult.getHour() != null) {
            attributes.put("hour", parseResult.getHour());
        }
        if (parseResult.getMinute() != null) {
            attributes.put("minute", parseResult.getMinute());
        }
        if (parseResult.getSecond() != null) {
            attributes.put("second", parseResult.getSecond());
        }
        attributes.put("format", parseResult.getFormat());

        return EnhancedExtractedResult.builder()
            .field(getMetadata().getOutputField())
            .value(matched)
            .rawValue(matched)
            .normalizedValue(normalizedValue)
            .confidence(parseResult.getConfidence())
            .validation(parseResult.getValidation())
            .validationStatus("valid")
            .businessMeaning("日期时间信息，用于时间相关业务处理")
            .dataType("date")
            .attributes(attributes)
            .startIndex(matcher.start())
            .endIndex(matcher.end())
            .build();
    }

    @Override
    public Map<String, Object> getDefaultOptions() {
        Map<String, Object> options = new HashMap<>();
        options.put("include_time", true);
        options.put("normalize_format", "iso");
        options.put("allow_ambiguous", false);
        return options;
    }

    @Override
    public List<ExtractorExample> getExamples() {
        return Arrays.asList(
            ExtractorExample.of(
                "合同签署日期：2024-01-15",
                "[\"2024-01-15\"]",
                "提取ISO格式日期"
            ),
            ExtractorExample.of(
                "会议时间：2024年3月20日14时30分",
                "[\"2024年3月20日14时30分\"]",
                "提取中文日期时间"
            ),
            ExtractorExample.of(
                "创建于2024/06/01 10:30:00",
                "[\"2024/06/01 10:30:00\"]",
                "提取日期时间"
            )
        );
    }

    /**
     * 解析日期
     */
    private DateParseResult parseDate(String text) {
        DateParseResult result = new DateParseResult();

        // ISO日期（YYYY-MM-DD）
        if (text.matches("^\\d{4}-\\d{2}-\\d{2}$")) {
            String[] parts = text.split("-");
            result.setYear(Integer.parseInt(parts[0]));
            result.setMonth(Integer.parseInt(parts[1]));
            result.setDay(Integer.parseInt(parts[2]));
            result.setFormat("iso_date");
            result.setConfidence(0.98f);
            result.setValidation("标准ISO日期格式");
            result.setValid(true);
        }
        // ISO日期时间（YYYY-MM-DD HH:MM:SS）
        else if (text.matches("^\\d{4}-\\d{2}-\\d{2}[ T]\\d{2}:\\d{2}:\\d{2}$")) {
            String[] parts = text.split("[ T]");
            String[] dateParts = parts[0].split("-");
            String[] timeParts = parts[1].split(":");

            result.setYear(Integer.parseInt(dateParts[0]));
            result.setMonth(Integer.parseInt(dateParts[1]));
            result.setDay(Integer.parseInt(dateParts[2]));
            result.setHour(Integer.parseInt(timeParts[0]));
            result.setMinute(Integer.parseInt(timeParts[1]));
            result.setSecond(Integer.parseInt(timeParts[2]));
            result.setFormat("iso_datetime");
            result.setConfidence(0.98f);
            result.setValidation("标准ISO日期时间格式");
            result.setValid(true);
        }
        // 中文日期（YYYY年MM月DD日）
        else if (text.matches("^\\d{4}年\\d{1,2}月\\d{1,2}日.*")) {
            String[] parts = text.split("[年月日]");
            result.setYear(Integer.parseInt(parts[0]));
            result.setMonth(Integer.parseInt(parts[1]));
            result.setDay(Integer.parseInt(parts[2]));
            result.setFormat("chinese_date");
            result.setConfidence(0.95f);
            result.setValidation("中文日期格式");
            result.setValid(true);

            // 尝试解析时间部分
            if (parts.length > 3 && parts[3] != null && !parts[3].isEmpty()) {
                String timePart = parts[3].replaceAll("[时分秒]", " ");
                String[] timeParts = timePart.trim().split(" ");
                if (timeParts.length >= 2) {
                    try {
                        result.setHour(Integer.parseInt(timeParts[0]));
                        result.setMinute(Integer.parseInt(timeParts[1]));
                        if (timeParts.length >= 3) {
                            result.setSecond(Integer.parseInt(timeParts[2]));
                        }
                        result.setFormat("chinese_datetime");
                    } catch (NumberFormatException e) {
                        // 忽略时间解析错误
                    }
                }
            }
        }
        // 短日期（MM-DD）
        else if (text.matches("^\\d{2}-\\d{2}$")) {
            String[] parts = text.split("-");
            result.setMonth(Integer.parseInt(parts[0]));
            result.setDay(Integer.parseInt(parts[1]));
            result.setFormat("short_date");
            result.setConfidence(0.70f);
            result.setValidation("短日期格式（可能模糊）");
            result.setValid(true);
            result.setAmbiguous(true);
        }
        // 时间戳
        else if (text.matches("^\\d{10}$") || text.matches("^\\d{13}$")) {
            long timestamp = Long.parseLong(text);
            java.util.Date date = new java.util.Date(text.length() == 10 ? timestamp * 1000 : timestamp);
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String formatted = sdf.format(date);

            result.setFormat("timestamp");
            result.setConfidence(text.length() == 10 ? 0.65f : 0.75f);
            result.setValidation("Unix时间戳，转换为: " + formatted);
            result.setValid(true);
            result.setAmbiguous(true);
        }

        return result;
    }

    /**
     * 标准化日期格式
     */
    private String normalizeDate(DateParseResult parseResult, String format) {
        switch (format) {
            case "iso":
                if (parseResult.getYear() != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(String.format("%04d", parseResult.getYear())).append("-");
                    sb.append(String.format("%02d", parseResult.getMonth())).append("-");
                    sb.append(String.format("%02d", parseResult.getDay()));
                    if (parseResult.getHour() != null) {
                        sb.append("T");
                        sb.append(String.format("%02d", parseResult.getHour())).append(":");
                        sb.append(String.format("%02d", parseResult.getMinute()));
                        if (parseResult.getSecond() != null) {
                            sb.append(":").append(String.format("%02d", parseResult.getSecond()));
                        }
                    }
                    return sb.toString();
                }
                break;
            case "chinese":
                if (parseResult.getYear() != null) {
                    StringBuilder sb = new StringBuilder();
                    sb.append(parseResult.getYear()).append("年");
                    sb.append(parseResult.getMonth()).append("月");
                    sb.append(parseResult.getDay()).append("日");
                    if (parseResult.getHour() != null) {
                        sb.append(" ").append(parseResult.getHour()).append("时");
                        sb.append(parseResult.getMinute()).append("分");
                        if (parseResult.getSecond() != null) {
                            sb.append(parseResult.getSecond()).append("秒");
                        }
                    }
                    return sb.toString();
                }
                break;
            case "timestamp":
                // 计算时间戳（简化处理，实际应用中应使用Calendar等工具）
                return "(时间戳转换)";
        }
        return parseResult.getFormat();
    }

    @Override
    public String getExtractorType() {
        return "date";
    }

    /**
     * 日期解析结果
     */
    @lombok.Data
    private static class DateParseResult {
        private Integer year;
        private Integer month;
        private Integer day;
        private Integer hour;
        private Integer minute;
        private Integer second;
        private String format;
        private Float confidence;
        private String validation;
        private boolean valid = false;
        private boolean ambiguous = false;
    }
}
