package com.datalabeling.service.extraction;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 关键词匹配提取器
 *
 * <p>用途：将一组关键词作为“规则证据”，为 LLM 判断提供可审计的提示。</p>
 *
 * <p>options（可选）：</p>
 * <ul>
 *   <li>keywords: List&lt;String&gt; 或 逗号分隔字符串</li>
 *   <li>matchType: any/all（默认 any）</li>
 *   <li>caseSensitive: true/false（默认 false）</li>
 * </ul>
 */
@Slf4j
@Component
public class KeywordMatcherExtractor implements INumberExtractor {

    @Override
    public List<ExtractedNumber> extract(String text, Map<String, Object> options) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> keywords = parseKeywords(options != null ? options.get("keywords") : null);
        if (keywords.isEmpty()) {
            return Collections.emptyList();
        }

        String matchType = String.valueOf(options != null ? options.getOrDefault("matchType", "any") : "any");
        boolean requireAll = "all".equalsIgnoreCase(matchType);
        boolean caseSensitive = Boolean.TRUE.equals(options != null ? options.get("caseSensitive") : null);

        String haystack = caseSensitive ? text : text.toLowerCase();
        Set<String> matched = new HashSet<>();

        for (String kw : keywords) {
            if (kw == null) {
                continue;
            }
            String needle = caseSensitive ? kw : kw.toLowerCase();
            if (needle.isEmpty()) {
                continue;
            }
            if (haystack.contains(needle)) {
                matched.add(kw);
            }
        }

        if (requireAll && matched.size() < keywords.size()) {
            // all 模式：必须全部命中才输出证据
            return Collections.emptyList();
        }

        if (matched.isEmpty()) {
            return Collections.emptyList();
        }

        List<ExtractedNumber> results = new ArrayList<>();
        for (String kw : matched) {
            int idx = caseSensitive ? text.indexOf(kw) : text.toLowerCase().indexOf(kw.toLowerCase());
            Integer start = idx >= 0 ? idx : null;
            Integer end = (idx >= 0) ? (idx + kw.length()) : null;
            results.add(ExtractedNumber.builder()
                .field("关键词匹配")
                .value(kw)
                .confidence(0.85f)
                .validation("命中关键词")
                .startIndex(start)
                .endIndex(end)
                .build());
        }

        log.debug("关键词匹配完成：mode={}, matched={}", matchType, results.size());
        return results;
    }

    private List<String> parseKeywords(Object raw) {
        if (raw == null) {
            return Collections.emptyList();
        }

        List<String> out = new ArrayList<>();
        if (raw instanceof String) {
            String s = ((String) raw).trim();
            if (s.isEmpty()) {
                return Collections.emptyList();
            }
            String[] parts = s.split("[,，]");
            for (String p : parts) {
                String kw = p != null ? p.trim() : "";
                if (!kw.isEmpty()) {
                    out.add(kw);
                }
            }
            return out;
        }

        if (raw instanceof Collection) {
            for (Object item : (Collection<?>) raw) {
                if (item == null) {
                    continue;
                }
                String kw = String.valueOf(item).trim();
                if (!kw.isEmpty()) {
                    out.add(kw);
                }
            }
            return out;
        }

        // 兜底：转字符串再按逗号切分
        String s = String.valueOf(raw).trim();
        if (s.isEmpty()) {
            return Collections.emptyList();
        }
        String[] parts = s.split("[,，]");
        for (String p : parts) {
            String kw = p != null ? p.trim() : "";
            if (!kw.isEmpty()) {
                out.add(kw);
            }
        }
        return out;
    }

    @Override
    public String getExtractorType() {
        return "keyword_match";
    }
}

