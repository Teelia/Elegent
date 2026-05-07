package com.datalabeling.service.extraction;

import com.datalabeling.entity.Label;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归：19位“错误身份证号”中间多一位导致生日段无法解析，但删除一位可恢复为有效18位身份证。
 *
 * <p>该类样本在接警文本里常见（例如：姓名（身份证号，手机号...）括号内直接列号码，不带关键词）。
 */
public class NegativeConditionPreprocessorNearMiss19IdTest {

    private static final String LABEL_DESC = "帮我提取不满足18位的错误的身份证号";

    @Test
    void should_extract_19_digit_invalid_id_when_remove_one_digit_can_repair_to_valid_18() {
        // 生成一个结构+校验位都通过的18位身份证（用于构造“中间多一位”的19位错误号码）
        String id18 = buildValidId18("11010519900208001"); // 17位主体（地区码+生日+顺序码）
        // 在生日段位置插入一位，破坏生日段可解析性，使旧的 isIdLikeByAreaAndBirth 漏检
        String id19 = id18.substring(0, 10) + "8" + id18.substring(10);

        String text = "报警人张三（" + id19 + "，13800000000）称：测试。";

        Map<String, Object> row = new HashMap<>();
        row.put("测试数据", text);

        NegativeConditionPreprocessor pre = new NegativeConditionPreprocessor();
        Label label = Label.builder().description(LABEL_DESC).build();

        NegativeConditionPreprocessor.PreprocessResult r = pre.preprocess(label, row);
        assertNotNull(r);
        assertTrue(r.canHandle());
        assertFalse(r.isEmpty(), "预期提取到19位错误身份证号");
        assertEquals(id19, r.getResult());
    }

    private static String buildValidId18(String id17) {
        assertNotNull(id17);
        assertEquals(17, id17.length());
        for (int i = 0; i < 17; i++) {
            char ch = id17.charAt(i);
            assertTrue(ch >= '0' && ch <= '9', "id17 前17位必须为数字");
        }

        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (id17.charAt(i) - '0') * weights[i];
        }
        char check = checkChars[sum % 11];
        return id17 + check;
    }
}

