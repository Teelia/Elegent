package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class PassportExtractorTest {

    @Test
    void should_extract_cn_passport() {
        PassportExtractor extractor = new PassportExtractor();
        List<ExtractedNumber> results = extractor.extract("张三，护照号G12345678。", new HashMap<>());
        assertFalse(results.isEmpty());
        assertTrue(results.stream().anyMatch(r -> "G12345678".equals(r.getValue())));
        assertTrue(results.stream().allMatch(r -> "护照号".equals(r.getField())));
    }

    @Test
    void should_respect_include_cn_only() {
        PassportExtractor extractor = new PassportExtractor();
        Map<String, Object> options = new HashMap<>();
        options.put("include_cn_only", true);

        List<ExtractedNumber> results = extractor.extract("John passport AB123456。", options);
        assertTrue(results.isEmpty(), "仅中国护照模式不应返回通用护照号");
    }
}

