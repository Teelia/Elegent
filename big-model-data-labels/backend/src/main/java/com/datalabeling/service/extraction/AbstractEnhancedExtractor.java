package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 增强型提取器基类
 * 提供通用的提取逻辑，简化具体提取器的实现
 *
 * 子类只需实现：
 * 1. getMetadata() - 定义元数据
 * 2. getPatterns() - 定义正则表达式
 * 3. processMatch() - 处理匹配结果
 * 4. getExamples() - 提供示例
 */
@Slf4j
public abstract class AbstractEnhancedExtractor implements IEnhancedExtractor {

    /**
     * 获取正则表达式列表
     * 返回按优先级排序的正则表达式
     *
     * @return 正则表达式列表（优先级从高到低）
     */
    protected abstract List<ExtractorPattern> getPatterns();

    /**
     * 处理正则匹配结果
     * 将Matcher转换为EnhancedExtractedResult
     *
     * @param matcher 正则匹配器
     * @param text 原始文本
     * @param options 提取选项
     * @return 提取结果
     */
    protected abstract EnhancedExtractedResult processMatch(Matcher matcher, String text, Map<String, Object> options);

    /**
     * 获取上下文窗口大小
     * 默认前后各50个字符
     */
    protected int getContextWindowSize() {
        return 50;
    }

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        return extractWithContext(text, options).stream()
            .map(result -> (ExtractedNumber) result)
            .collect(Collectors.toList());
    }

    @Override
    public List<EnhancedExtractedResult> extractWithContext(String text, Map<String, Object> options) {
        validateOptions(options);

        List<EnhancedExtractedResult> results = new ArrayList<>();
        Set<String> seenValues = new HashSet<>(); // 用于去重

        for (ExtractorPattern pattern : getPatterns()) {
            if (!pattern.isEnabled(options)) {
                continue;
            }

            Pattern regex = pattern.getCompiledPattern();
            Matcher matcher = regex.matcher(text);

            while (matcher.find()) {
                String value = matcher.group();

                // 去重检查
                String dedupeKey = pattern.getName() + ":" + value;
                if (seenValues.contains(dedupeKey)) {
                    continue;
                }
                seenValues.add(dedupeKey);

                // 处理匹配
                EnhancedExtractedResult result = processMatch(matcher, text, options);
                if (result != null) {
                    // 设置上下文
                    result.setContext(extractContext(text, matcher.start(), matcher.end()));

                    // 如果未设置原始值，使用匹配的值
                    if (result.getRawValue() == null) {
                        result.setRawValue(value);
                    }

                    // 设置提取器版本
                    result.setExtractorVersion(getMetadata().getVersion());

                    results.add(result);
                }
            }

            // 如果是独占模式且已找到结果，不再继续匹配
            if (pattern.isExclusive() && !results.isEmpty()) {
                break;
            }
        }

        log.debug("{} 提取完成，共提取 {} 个结果", getMetadata().getCode(), results.size());
        return results;
    }

    @Override
    public String buildLLMPromptContext(List<EnhancedExtractedResult> results) {
        if (results == null || results.isEmpty()) {
            return "";
        }

        ExtractorMetadata metadata = getMetadata();

        StringBuilder sb = new StringBuilder();
        sb.append("=== ").append(metadata.getName()).append("提取结果 ===\n");
        sb.append("提取器: ").append(metadata.getCode()).append("\n");
        sb.append("提取说明: ").append(metadata.getDescription()).append("\n");
        sb.append("数据类型: ").append(metadata.getDataType()).append("\n");
        sb.append("结果数量: ").append(results.size()).append("\n\n");

        for (int i = 0; i < results.size(); i++) {
            EnhancedExtractedResult result = results.get(i);
            sb.append("结果 #").append(i + 1).append(":\n");
            sb.append("  值: ").append(result.getValue()).append("\n");
            sb.append("  置信度: ").append(Math.round(result.getConfidence() * 100)).append("%\n");

            if (StringUtils.hasText(result.getValidation())) {
                sb.append("  验证: ").append(result.getValidation()).append("\n");
            }

            if (StringUtils.hasText(result.getBusinessMeaning())) {
                sb.append("  业务含义: ").append(result.getBusinessMeaning()).append("\n");
            }

            if (result.getAttributes() != null && !result.getAttributes().isEmpty()) {
                sb.append("  附加信息:\n");
                result.getAttributes().forEach((key, value) ->
                    sb.append("    ").append(key).append(": ").append(value).append("\n")
                );
            }

            sb.append("\n");
        }

        return sb.toString();
    }

    /**
     * 提取上下文
     */
    protected String extractContext(String text, int start, int end) {
        int windowSize = getContextWindowSize();
        int contextStart = Math.max(0, start - windowSize);
        int contextEnd = Math.min(text.length(), end + windowSize);

        String context = text.substring(contextStart, contextEnd);

        // 添加标记标识提取部分
        if (contextStart > 0) {
            context = "..." + context;
        }
        if (contextEnd < text.length()) {
            context = context + "...";
        }

        return context;
    }

    @Override
    public String getExtractorType() {
        return getMetadata().getCode();
    }

    /**
     * 提取器正则模式定义
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ExtractorPattern {
        /**
         * 模式名称
         */
        private String name;

        /**
         * 正则表达式
         */
        private String regex;

        /**
         * 编译后的正则（缓存，不参与序列化）
         */
        @lombok.Builder.Default
        private transient Pattern compiledPattern = null;

        /**
         * 默认置信度
         */
        @lombok.Builder.Default
        private float defaultConfidence = 0.8f;

        /**
         * 是否启用（可通过配置覆盖）
         */
        @lombok.Builder.Default
        private boolean enabledByDefault = true;

        /**
         * 是否独占模式（找到结果后停止后续匹配）
         */
        @lombok.Builder.Default
        private boolean exclusive = false;

        /**
         * 优先级（数字越大越高）
         */
        @lombok.Builder.Default
        private int priority = 0;

        /**
         * 模式说明
         */
        @lombok.Builder.Default
        private String description = "";

        /**
         * 检查是否启用
         */
        public boolean isEnabled(Map<String, Object> options) {
            String key = "enable_" + name;
            if (options.containsKey(key)) {
                return Boolean.TRUE.equals(options.get(key));
            }
            return enabledByDefault;
        }

        /**
         * 获取编译后的正则（懒加载）
         */
        public Pattern getCompiledPattern() {
            if (compiledPattern == null) {
                if (regex == null || regex.isEmpty()) {
                    throw new IllegalStateException("正则表达式不能为空: " + name);
                }
                try {
                    compiledPattern = Pattern.compile(regex);
                } catch (Exception e) {
                    throw new IllegalStateException("正则表达式编译失败: " + regex, e);
                }
            }
            return compiledPattern;
        }

        /**
         * 创建简单模式
         */
        public static ExtractorPattern of(String name, String regex, float confidence) {
            return ExtractorPattern.builder()
                .name(name)
                .regex(regex)
                .defaultConfidence(confidence)
                .build();
        }

        /**
         * 创建高优先级独占模式
         */
        public static ExtractorPattern highPriority(String name, String regex, float confidence) {
            return ExtractorPattern.builder()
                .name(name)
                .regex(regex)
                .defaultConfidence(confidence)
                .priority(100)
                .exclusive(true)
                .build();
        }

        /**
         * 创建带说明的模式
         */
        public static ExtractorPattern ofWithDescription(String name, String regex, float confidence, String description) {
            return ExtractorPattern.builder()
                .name(name)
                .regex(regex)
                .defaultConfidence(confidence)
                .description(description)
                .build();
        }
    }
}
