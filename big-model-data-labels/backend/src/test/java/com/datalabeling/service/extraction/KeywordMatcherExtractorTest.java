package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class KeywordMatcherExtractorTest {

    @Test
    void any_should_match_one() {
        KeywordMatcherExtractor extractor = new KeywordMatcherExtractor();
        Map<String, Object> options = new HashMap<>();
        options.put("keywords", Arrays.asList("请求撤警", "误报警"));
        options.put("matchType", "any");

        List<ExtractedNumber> results = extractor.extract("报警人误报警请求撤警", options);
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> "误报警".equals(r.getValue())));
    }

    @Test
    void all_should_require_all_keywords() {
        KeywordMatcherExtractor extractor = new KeywordMatcherExtractor();
        Map<String, Object> options = new HashMap<>();
        options.put("keywords", Arrays.asList("A", "B"));
        options.put("matchType", "all");

        List<ExtractedNumber> results = extractor.extract("只有A，没有其他", options);
        assertTrue(results.isEmpty(), "all 模式未全部命中时应返回空");
    }

    @Test
    void should_be_case_insensitive_by_default() {
        KeywordMatcherExtractor extractor = new KeywordMatcherExtractor();
        Map<String, Object> options = new HashMap<>();
        options.put("keywords", Arrays.asList("abc"));
        // 默认 caseSensitive=false

        List<ExtractedNumber> results = extractor.extract("ABC", options);
        assertFalse(results.isEmpty());
        assertEquals("abc", results.get(0).getValue());
    }
}

