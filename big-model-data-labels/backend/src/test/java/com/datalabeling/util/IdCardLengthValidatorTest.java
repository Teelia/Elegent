package com.datalabeling.util;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * IdCardLengthValidator 单元测试
 */
class IdCardLengthValidatorTest {

    @Test
    void testValid18BitIdCard() {
        // 测试有效的18位身份证号
        String validId = "34040419971118021X";
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(validId, false);

        assertTrue(result.isValid(), "18位身份证号应该通过验证");
        assertEquals("格式正确", result.getReason());
    }

    @Test
    void testInvalid19BitIdCard() {
        // 测试19位身份证号（错误）
        String invalidId = "340404199711180211X";
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(invalidId, false);

        assertFalse(result.isValid(), "19位身份证号应该验证失败");
        assertTrue(result.getReason().contains("长度错误"));
    }

    @Test
    void testInvalid17BitIdCard() {
        // 测试17位身份证号（错误）
        String invalidId = "34282519610314710";
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(invalidId, false);

        assertFalse(result.isValid(), "17位身份证号应该验证失败");
        assertTrue(result.getReason().contains("长度错误"));
    }

    @Test
    void testInvalid15BitIdCard_StrictMode() {
        // 测试15位身份证号，严格模式
        String invalidId = "340404701234567";
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(invalidId, false);

        assertFalse(result.isValid(), "严格模式下15位身份证号应该验证失败");
    }

    @Test
    void testValid15BitIdCard_LegacyMode() {
        // 测试15位身份证号，兼容模式
        String legacyId = "340404701234567";
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(legacyId, true);

        assertTrue(result.isValid(), "兼容模式下15位身份证号应该通过验证");
    }

    @Test
    void testInvalidFormat() {
        // 测试格式错误的身份证号
        String invalidId = "34040419971118021"; // 17位且最后一位是数字
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(invalidId, false);

        assertFalse(result.isValid(), "格式错误的身份证号应该验证失败");
    }

    @Test
    void testEmptyIdCard() {
        // 测试空值
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate("", false);

        assertFalse(result.isValid(), "空身份证号应该验证失败");
        assertTrue(result.getReason().contains("空"));
    }

    @Test
    void testNullIdCard() {
        // 测试null值
        IdCardLengthValidator.ValidationResult result =
            IdCardLengthValidator.validate(null, false);

        assertFalse(result.isValid(), "null身份证号应该验证失败");
    }

    @Test
    void testExtractAndValidate() {
        // 测试从文本中提取并验证
        String text = "报警人陈文武（身份证号：340121197707234933）称其与对方" +
                     "（34040419971118021X，现场未提供,研判无果）发生纠纷";

        IdCardLengthValidator.ExtractResult result =
            IdCardLengthValidator.extractAndValidate(text, false);

        assertEquals(2, result.getItems().size(), "应该提取到2个身份证号");
        assertEquals(2, result.getValidCount(), "2个身份证号都应该是有效的");
        assertEquals(0, result.getInvalidCount(), "不应该有无效的身份证号");
    }

    @Test
    void testExtractAndValidate_WhitespacePrefixShouldNotBeTreatedAsPrefixError() {
        // 空白（空格/Tab/换行）仅作为分隔符，不应触发“前缀字符导致格式错误”的判定
        String text = "身份证号： 34040419971118021X；另一个身份证号：\t340121197707234933";

        IdCardLengthValidator.ExtractResult result =
            IdCardLengthValidator.extractAndValidate(text, false);

        assertEquals(2, result.getItems().size(), "应该提取到2个身份证号");
        assertEquals(2, result.getValidCount(), "空白前缀不应影响有效性统计");
        assertEquals(0, result.getInvalidCount(), "空白前缀不应导致无效");
    }

    @Test
    void testHasPrefixIdCard_WhitespaceOnlyShouldBeFalse() {
        // 仅空白分隔不算“前缀错误”
        String text = "身份证号： 34040419971118021X";
        assertFalse(IdCardLengthValidator.hasPrefixIdCard(text));
    }

    @Test
    void testHasPrefixIdCard_SpecialCharPrefixShouldBeTrue() {
        // 特殊字符前缀应判定为“前缀错误”
        String text = "身份证号：?34040419971118021X";
        assertTrue(IdCardLengthValidator.hasPrefixIdCard(text));
    }

    @Test
    void testExtractAndValidate_WithInvalid() {
        // 测试包含无效身份证号的文本
        String text = "对方（340404199711180211X）有19位身份证号";

        IdCardLengthValidator.ExtractResult result =
            IdCardLengthValidator.extractAndValidate(text, false);

        assertTrue(result.hasInvalid(), "应该检测到无效的身份证号");
        assertEquals(1, result.getInvalidCount(), "应该有1个无效的身份证号");
    }

    @Test
    void testQuickValidate() {
        // 测试快速验证方法
        assertTrue(IdCardLengthValidator.quickValidate("34040419971118021X"));
        assertFalse(IdCardLengthValidator.quickValidate("340404199711180211X"));
        assertFalse(IdCardLengthValidator.quickValidate("340404701234567"));
    }
}
