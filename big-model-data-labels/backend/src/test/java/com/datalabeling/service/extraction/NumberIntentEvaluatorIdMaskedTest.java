package com.datalabeling.service.extraction;

import com.datalabeling.dto.PreprocessorConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 身份证 number_intent：遮挡/错误长度遮挡 的单元测试。
 *
 * <p>业务口径：全遮挡（全部*）不计存在（当作无用字符）。
 */
public class NumberIntentEvaluatorIdMaskedTest {

    @Test
    void should_extract_masked_id_and_invalid_length_masked_id() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String text = "报警人身份证号：340203********0513；其同行人身份证号：340203*******0513。";

        // masked
        NumberIntentEvaluator.EvaluationResult masked = eval(evaluator, text, "id_card", "masked");
        assertTrue(masked.isCanHandle());
        assertEquals("340203********0513，340203*******0513", masked.getSummary());

        // invalid_length_masked（仅返回错误长度的遮挡片段）
        NumberIntentEvaluator.EvaluationResult invalidLenMasked = eval(evaluator, text, "id_card", "invalid_length_masked");
        assertTrue(invalidLenMasked.isCanHandle());
        assertEquals("340203*******0513", invalidLenMasked.getSummary());
    }

    @Test
    void should_treat_fully_masked_id_as_not_exists() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String text = "报警人身份证号：******************。";

        NumberIntentEvaluator.EvaluationResult exists = eval(evaluator, text, "id_card", "exists");
        assertTrue(exists.isCanHandle());
        assertEquals("否", exists.getSummary());
        assertNotNull(exists.getExtractedData());
        assertEquals(false, exists.getExtractedData().get("needs_review"));
    }

    private static NumberIntentEvaluator.EvaluationResult eval(NumberIntentEvaluator evaluator, String text, String entity, String task) {
        String json = "{"
            + "\"extractors\":[],"
            + "\"number_intent\":{"
            + "\"entity\":\"" + entity + "\","
            + "\"task\":\"" + task + "\","
            + "\"include\":[\"valid\",\"invalid\",\"masked\"],"
            + "\"policy\":{\"default_masked_output\":true}"
            + "}"
            + "}";
        PreprocessorConfig cfg = PreprocessorConfig.fromJson(json);
        assertNotNull(cfg.getNumberIntent(), "number_intent 解析失败");
        return evaluator.evaluate(text, cfg.getNumberIntent());
    }
}

