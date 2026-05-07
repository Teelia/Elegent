package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class SchoolInfoExtractorTest {

    @Test
    void should_detect_training_institution_by_default() {
        SchoolInfoExtractor extractor = new SchoolInfoExtractor();
        List<ExtractedNumber> results = extractor.extract("在合肥某某培训学校学习烘焙发生纠纷", new HashMap<>());
        assertEquals(1, results.size());
        assertEquals("学校信息", results.get(0).getField());
        assertTrue(results.get(0).getValue().contains("培训机构"));
    }

    @Test
    void should_extract_university_name() {
        SchoolInfoExtractor extractor = new SchoolInfoExtractor();
        Map<String, Object> options = new HashMap<>();
        options.put("exclude_training", true);

        List<ExtractedNumber> results = extractor.extract("清华大学计算机系学生张三", options);
        assertEquals(1, results.size());
        String json = results.get(0).getValue();
        assertTrue(json.contains("university") || json.contains("大学") || json.contains("学院"));
        assertTrue(json.contains("清华大学"));
    }
}

