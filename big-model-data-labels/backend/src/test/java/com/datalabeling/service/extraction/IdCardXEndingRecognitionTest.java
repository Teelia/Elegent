package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * X结尾身份证号识别测试
 *
 * <p>修复问题：X结尾的18位身份证号被错误识别为"少一位"
 *
 * <p>根本原因：NumberEvidenceExtractor.isIdLikeByAreaAndBirth() 使用 isAllDigits()
 * 检查，导致X结尾的身份证号返回false
 *
 * <p>修复方案：新增 isAllDigitsOrXEnding() 方法，支持18位身份证以X/x结尾
 */
class IdCardXEndingRecognitionTest {

    private final NumberEvidenceExtractor extractor = new NumberEvidenceExtractor();

    /**
     * 测试X结尾的有效18位身份证能被正确识别为 ID_VALID_18
     */
    @Test
    void should_recognize_x_ending_id_card_as_valid_18() {
        // 11010519900101001 + 计算出的校验位
        String body17 = "11010519900101001";
        char checkChar = calcId18CheckChar(body17);

        if (checkChar == 'X') {
            String validId18WithX = body17 + checkChar;
            String text = "身份证号：" + validId18WithX;

            NumberEvidence result = extractor.extract(text);

            assertFalse(result.getNumbers().isEmpty(),
                "X结尾的18位身份证应被识别");
            assertEquals(1, result.getNumbers().size(),
                "应识别到一个候选");

            NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
            assertEquals("ID_VALID_18", candidate.getType(),
                "X结尾且校验位正确的身份证应识别为 ID_VALID_18");
            assertEquals(18, candidate.getLength(),
                "长度应为18位");
            assertTrue(candidate.getValue().toUpperCase().endsWith("X"),
                "值应以X结尾");
        } else {
            // 如果计算出的校验位不是X，使用已知的X结尾身份证
            testKnownXEndingIdCard();
        }
    }

    /**
     * 测试已知的X结尾18位身份证（11010119900101123X）
     * 这是一个真实有效的X结尾身份证号示例
     */
    @Test
    void testKnownXEndingIdCard() {
        // 常见示例：18位身份证末位 X（校验位为X）
        String validId18WithX = "11010519491231002X";
        String text = "身份证号：" + validId18WithX;

        NumberEvidence result = extractor.extract(text);

        assertFalse(result.getNumbers().isEmpty(),
            "X结尾的18位身份证应被识别");
        assertEquals(1, result.getNumbers().size(),
            "应识别到一个候选");

        NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
        assertEquals("ID_VALID_18", candidate.getType(),
            "X结尾且校验位正确的身份证应识别为 ID_VALID_18");
        assertEquals(18, candidate.getLength(),
            "长度应为18位");

        // 验证脱敏值
        String maskedValue = candidate.getMaskedValue();
        assertNotNull(maskedValue);
        assertTrue(maskedValue.contains("*"),
            "脱敏值应包含*");
    }

    /**
     * 测试x结尾（小写）的18位身份证也能被识别
     */
    @Test
    void should_recognize_lowercase_x_ending_id_card() {
        String idCardWithLowerX = "11010519491231002x"; // 小写x
        String text = "证件号：" + idCardWithLowerX;

        NumberEvidence result = extractor.extract(text);

        assertFalse(result.getNumbers().isEmpty(),
            "x结尾（小写）的18位身份证应被识别");

        NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
        assertEquals("ID_VALID_18", candidate.getType(),
            "x结尾且校验位正确的身份证应识别为 ID_VALID_18");
        assertEquals(18, candidate.getLength(),
            "长度应为18位");
    }

    /**
     * 测试X结尾但校验位不正确的18位身份证应识别为 ID_INVALID_CHECKSUM
     */
    @Test
    void should_recognize_x_ending_with_wrong_checksum_as_invalid_checksum() {
        // 11010519900101001 + 错误的校验位X（假设正确校验位不是X）
        String body17 = "11010519900101001";
        char correctCheck = calcId18CheckChar(body17);

        if (correctCheck != 'X') {
            String invalidId18WithX = body17 + "X"; // 强制使用X作为校验位
            String text = "身份证号：" + invalidId18WithX;

            NumberEvidence result = extractor.extract(text);

            assertFalse(result.getNumbers().isEmpty(),
                "X结尾但校验位错误的18位身份证应被识别");

            NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
            assertEquals("ID_INVALID_CHECKSUM", candidate.getType(),
                "X结尾但校验位错误的身份证应识别为 ID_INVALID_CHECKSUM");
            assertEquals(18, candidate.getLength(),
                "长度应为18位");
        } else {
            // 如果正确的校验位恰好是X，跳过此测试
            // 实际上这种情况很少见
        }
    }

    /**
     * 测试纯数字的18位身份证仍能正常识别（确保修复没有破坏原有功能）
     */
    @Test
    void should_still_recognize_pure_digit_id_card() {
        String body17 = "11010519900101001";
        char checkChar = calcId18CheckChar(body17);

        if (checkChar != 'X') {
            String validId18 = body17 + checkChar;
            String text = "身份证号：" + validId18;

            NumberEvidence result = extractor.extract(text);

            assertFalse(result.getNumbers().isEmpty(),
                "纯数字的18位身份证应被识别");
            assertEquals(1, result.getNumbers().size(),
                "应识别到一个候选");

            NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
            assertEquals("ID_VALID_18", candidate.getType(),
                "纯数字且校验位正确的身份证应识别为 ID_VALID_18");
            assertEquals(18, candidate.getLength(),
                "长度应为18位");
        }
    }

    /**
     * 测试17位数字（少一位）应识别为 ID_INVALID_LENGTH
     * （验证X结尾身份证不会被误认为17位）
     */
    @Test
    void should_recognize_17_digit_as_invalid_length() {
        String id17 = "11010519900101001"; // 17位，缺少校验位
        String text = "身份证号：" + id17;

        NumberEvidence result = extractor.extract(text);

        assertFalse(result.getNumbers().isEmpty(),
            "17位数字应被识别");

        NumberEvidence.NumberCandidate candidate = result.getNumbers().get(0);
        assertEquals("ID_INVALID_LENGTH", candidate.getType(),
            "17位数字应识别为 ID_INVALID_LENGTH（长度错误）");
        assertEquals(17, candidate.getLength(),
            "长度应为17位");
    }

    /**
     * 计算18位身份证号的校验位
     *
     * @param id17 前17位身份证号
     * @return 校验位（0-9或X）
     */
    private char calcId18CheckChar(String id17) {
        if (id17 == null || id17.length() != 17) {
            throw new IllegalArgumentException("前17位身份证号长度必须为17");
        }

        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        int sum = 0;
        for (int i = 0; i < 17; i++) {
            char ch = id17.charAt(i);
            if (ch < '0' || ch > '9') {
                throw new IllegalArgumentException("前17位必须全为数字");
            }
            sum += (ch - '0') * weights[i];
        }

        return checkChars[sum % 11];
    }

    /**
     * 变异校验位（用于生成错误的校验位）
     */
    @SuppressWarnings("unused")
    private char mutateCheckChar(char checkChar) {
        // 简单地将校验位变为另一个字符
        if (checkChar == 'X') {
            return '0';
        } else if (checkChar == '9') {
            return 'X';
        } else {
            return (char) (checkChar + 1);
        }
    }
}
