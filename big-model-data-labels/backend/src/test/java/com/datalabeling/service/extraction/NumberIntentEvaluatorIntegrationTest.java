package com.datalabeling.service.extraction;

import com.datalabeling.dto.PreprocessorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * number_intent：身份证任务集成测试（exists / invalid / policy）。
 */
class NumberIntentEvaluatorIntegrationTest {

    @Test
    void should_treat_checksum_invalid_as_valid_for_exists_by_default() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));

        NumberIntentEvaluator.EvaluationResult r = evalWithCamelPolicy(
            evaluator,
            "身份证号：" + invalidChecksum18 + "。",
            "exists",
            true,
            null,
            false
        );

        assertTrue(r.isCanHandle());
        assertTrue(r.isHit());
        assertEquals("是", r.getSummary());

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) r.getExtractedData().get("counts");
        assertNotNull(counts);
        assertEquals(1, ((Number) counts.get("valid")).intValue(), "校验位错误应计入 valid（业务口径：不计入invalid）");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.getExtractedData().get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("ID_INVALID_CHECKSUM", items.get(0).get("type"));
    }

    @Test
    void should_not_count_15_digit_id_when_id15_is_valid_false() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();
        String id15 = "110105900101001";

        NumberIntentEvaluator.EvaluationResult r = evalWithCamelPolicy(
            evaluator,
            "证件号：" + id15 + "。",
            "exists",
            true,
            false, // id15IsValid=false（camelCase，用于验证 JsonAlias 兼容）
            false
        );

        assertTrue(r.isCanHandle());
        assertFalse(r.isHit());
        assertEquals("否", r.getSummary());

        @SuppressWarnings("unchecked")
        Map<String, Object> counts = (Map<String, Object>) r.getExtractedData().get("counts");
        assertNotNull(counts);
        assertEquals(0, ((Number) counts.get("valid")).intValue());
        assertEquals(false, counts.get("exists"));
    }

    @Test
    void should_include_invalid_length_masked_in_invalid_task() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String maskedInvalidLen = "340203*******0513"; // 17位遮挡：长度错误
        NumberIntentEvaluator.EvaluationResult r = evalWithCamelPolicy(
            evaluator,
            "报警人身份证号：" + maskedInvalidLen + "。",
            "invalid",
            true,
            null,
            false
        );

        assertTrue(r.isCanHandle());
        assertTrue(r.isHit());
        assertTrue(r.getSummary().contains(maskedInvalidLen));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.getExtractedData().get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("ID_INVALID_LENGTH_MASKED", items.get(0).get("type"));
    }

    private static NumberIntentEvaluator.EvaluationResult evalWithCamelPolicy(NumberIntentEvaluator evaluator,
                                                                              String text,
                                                                              String task,
                                                                              boolean defaultMaskedOutput,
                                                                              Boolean id15IsValid,
                                                                              boolean idChecksumInvalidIsInvalid) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"extractors\":[],");
        sb.append("\"number_intent\":{");
        sb.append("\"entity\":\"id_card\",");
        sb.append("\"task\":\"").append(task).append("\",");
        sb.append("\"include\":[\"valid\",\"invalid\",\"masked\"],");
        sb.append("\"policy\":{");
        sb.append("\"defaultMaskedOutput\":").append(defaultMaskedOutput);
        if (id15IsValid != null) {
            sb.append(",\"id15IsValid\":").append(id15IsValid);
        }
        sb.append(",\"idChecksumInvalidIsInvalid\":").append(idChecksumInvalidIsInvalid);
        sb.append(",\"id18XIsInvalid\":false");
        sb.append("}");
        sb.append("}");
        sb.append("}");

        PreprocessorConfig cfg = PreprocessorConfig.fromJson(sb.toString());
        assertNotNull(cfg.getNumberIntent(), "number_intent 解析失败");
        return evaluator.evaluate(text, cfg.getNumberIntent());
    }

    private static char calcId18CheckChar(String body17) {
        assertNotNull(body17);
        assertEquals(17, body17.length());
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] map = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            int d = body17.charAt(i) - '0';
            sum += d * weights[i];
        }
        return map[sum % 11];
    }

    private static char mutateCheckChar(String checkChar) {
        if (checkChar == null || checkChar.isEmpty()) {
            return '0';
        }
        char c = Character.toUpperCase(checkChar.charAt(0));
        return c == '0' ? '1' : '0';
    }
}

