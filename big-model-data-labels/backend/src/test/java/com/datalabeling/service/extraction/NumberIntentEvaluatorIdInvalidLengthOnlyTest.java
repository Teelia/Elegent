package com.datalabeling.service.extraction;

import com.datalabeling.dto.PreprocessorConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 身份证 number_intent：错误身份证号（仅长度错误）的口径回归。
 *
 * <p>口径：
 * - 15位视为正确（不算错）
 * - 18位校验位不通过不算错
 * - 遮挡号不纳入判断与提取逻辑（不进入 invalid 输出集合）
 */
public class NumberIntentEvaluatorIdInvalidLengthOnlyTest {

    @Test
    void should_only_extract_invalid_length_for_id_card_invalid_task() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));
        String invalidLen17 = body17; // 17位：长度错误
        String masked = "110105********1001"; // 遮挡号：不应进入 invalid 输出集合

        String text = "有效18位:" + valid18 + "；校验位不通过:" + invalidChecksum18 + "；长度错:" + invalidLen17 + "；遮挡:" + masked + "。";

        NumberIntentEvaluator.EvaluationResult r = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r.isCanHandle());
        assertTrue(r.isHit());
        assertNotNull(r.getExtractedData());

        // summary 默认明文输出（不含*）且仅包含长度错误的号码
        assertTrue(r.getSummary().contains(invalidLen17));
        assertFalse(r.getSummary().contains(valid18));
        assertFalse(r.getSummary().contains(invalidChecksum18));
        assertFalse(r.getSummary().contains("*"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) r.getExtractedData().get("items");
        assertNotNull(items);
        assertEquals(1, items.size());
        assertEquals("ID_INVALID_LENGTH", items.get(0).get("type"));
        assertEquals(invalidLen17, items.get(0).get("value"));
    }

    @Test
    void should_not_hit_when_only_checksum_invalid_or_masked_present() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));
        String masked = "110105********1001";

        String text = "校验位不通过:" + invalidChecksum18 + "；遮挡:" + masked + "。";

        NumberIntentEvaluator.EvaluationResult r = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r.isCanHandle());
        assertFalse(r.isHit());
        assertEquals("无", r.getSummary());
    }

    @Test
    void should_not_split_x_ending_id_into_17_digit_invalid_length() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        // 常见示例：18位身份证末位 X（校验位为X）
        String valid18WithX = "11010519491231002X";
        String text = "报警人身份证号：" + valid18WithX + "。";

        NumberIntentEvaluator.EvaluationResult r = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r.isCanHandle());
        assertFalse(r.isHit(), "末位X的18位身份证不应触发“长度错误”invalid");
        assertEquals("无", r.getSummary());
    }

    @Test
    void should_hit_when_19_length_ending_x_present() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        // 19位且末位X：18位数字 + X，明显属于“长度错误”
        String invalid19EndingX = "110105194912310020X";
        String text = "证件号：" + invalid19EndingX + "。";

        NumberIntentEvaluator.EvaluationResult r = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r.isCanHandle());
        assertTrue(r.isHit(), "19位末位X应触发“长度错误”invalid");
        assertTrue(r.getSummary().contains(invalid19EndingX));
    }

    @Test
    void should_hit_when_checksum_invalid_and_policy_enabled() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));
        String text = "身份证号：" + invalidChecksum18;

        // 默认口径：校验位不通过不算错
        NumberIntentEvaluator.EvaluationResult r0 = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r0.isCanHandle());
        assertFalse(r0.isHit());

        // 开启增强口径：校验位不通过计入 invalid
        NumberIntentEvaluator.EvaluationResult r1 = evalWithPolicy(
            evaluator, text, "id_card", "invalid", false,
            true,  // id_checksum_invalid_is_invalid
            false  // id18_x_is_invalid
        );
        assertTrue(r1.isCanHandle());
        assertTrue(r1.isHit());
        assertTrue(r1.getSummary().contains(invalidChecksum18));
    }

    @Test
    void should_hit_when_x_ending_id_present_and_policy_digits_only_enabled() {
        NumberIntentEvaluator evaluator = new NumberIntentEvaluator();

        // 18位末位X（国家标准允许）；但在“仅允许数字”的业务口径下可视为无效
        String valid18WithX = "11010519491231002X";
        String text = "身份证号：" + valid18WithX;

        // 默认口径：不算错
        NumberIntentEvaluator.EvaluationResult r0 = eval(evaluator, text, "id_card", "invalid", false);
        assertTrue(r0.isCanHandle());
        assertFalse(r0.isHit());

        // digits-only 口径：末位X计入 invalid
        NumberIntentEvaluator.EvaluationResult r1 = evalWithPolicy(
            evaluator, text, "id_card", "invalid", false,
            false, // id_checksum_invalid_is_invalid
            true   // id18_x_is_invalid
        );
        assertTrue(r1.isCanHandle());
        assertTrue(r1.isHit());
        assertTrue(r1.getSummary().contains(valid18WithX));
    }

    private static NumberIntentEvaluator.EvaluationResult eval(NumberIntentEvaluator evaluator, String text, String entity, String task, boolean defaultMaskedOutput) {
        return evalWithPolicy(evaluator, text, entity, task, defaultMaskedOutput, null, null);
    }

    private static NumberIntentEvaluator.EvaluationResult evalWithPolicy(NumberIntentEvaluator evaluator,
                                                                         String text, String entity, String task,
                                                                         boolean defaultMaskedOutput,
                                                                         Boolean idChecksumInvalidIsInvalid,
                                                                         Boolean id18XIsInvalid) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"extractors\":[],");
        sb.append("\"number_intent\":{");
        sb.append("\"entity\":\"").append(entity).append("\",");
        sb.append("\"task\":\"").append(task).append("\",");
        sb.append("\"include\":[\"valid\",\"invalid\",\"masked\"],");
        sb.append("\"policy\":{");
        sb.append("\"default_masked_output\":").append(defaultMaskedOutput);
        if (idChecksumInvalidIsInvalid != null) {
            sb.append(",\"id_checksum_invalid_is_invalid\":").append(idChecksumInvalidIsInvalid);
        }
        if (id18XIsInvalid != null) {
            sb.append(",\"id18_x_is_invalid\":").append(id18XIsInvalid);
        }
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
        // 只要不同即可，保证构造“校验位不通过”
        return c == '0' ? '1' : '0';
    }
}
