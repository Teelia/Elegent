package com.datalabeling.service.extraction;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 身份证号规则提取（NumberEvidenceExtractor）专项测试。
 *
 * <p>业务口径：不以校验位是否正确判断“无效身份证”；仅识别长度错误/格式错误。
 */
class NumberEvidenceExtractorIdCardTest {

    private final NumberEvidenceExtractor extractor = new NumberEvidenceExtractor();

    @Test
    void should_extract_valid_18_digit_id_card() {
        String body17 = "11010519900101001";
        String id18 = body17 + calcId18CheckChar(body17);

        NumberEvidence evidence = extractor.extract("身份证号：" + id18 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_VALID_18", ids.get(0).getType());
        assertEquals(18, ids.get(0).getLength());
    }

    @Test
    void should_extract_x_ending_18_digit_id_card() {
        // 常见示例：18位身份证末位 X（国家标准允许）
        String id18WithX = "11010519491231002X";
        NumberEvidence evidence = extractor.extract("身份证号：" + id18WithX + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_VALID_18", ids.get(0).getType());
        assertEquals(18, ids.get(0).getLength());
        assertTrue(ids.get(0).getValue().toUpperCase().endsWith("X"));
    }

    @Test
    void should_extract_checksum_invalid_18_digit_as_id_invalid_checksum() {
        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));

        NumberEvidence evidence = extractor.extract("身份证号：" + invalidChecksum18 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_INVALID_CHECKSUM", ids.get(0).getType());
        assertEquals(18, ids.get(0).getLength());
    }

    @Test
    void should_extract_17_and_16_digit_as_invalid_length() {
        String id17 = "11010519900101001";
        String id16 = "1101051990010100";
        NumberEvidence evidence = extractor.extract("17位：" + id17 + "；16位：" + id16 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(2, ids.size());
        assertTrue(ids.stream().allMatch(c -> "ID_INVALID_LENGTH".equals(c.getType())));
        assertTrue(ids.stream().anyMatch(c -> c.getLength() == 17));
        assertTrue(ids.stream().anyMatch(c -> c.getLength() == 16));
    }

    @Test
    void should_extract_19_digit_ending_x_as_invalid_length() {
        String id19EndingX = "110105194912310020X";
        NumberEvidence evidence = extractor.extract("证件号：" + id19EndingX + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_INVALID_LENGTH", ids.get(0).getType());
        assertEquals(19, ids.get(0).getLength());
    }

    @Test
    void should_extract_19_digit_near_miss_as_invalid_length_and_mark_validation() {
        String body17 = "11010519900208001";
        String valid18 = body17 + calcId18CheckChar(body17);
        // 构造19位：中间插入一位，删除一位可恢复为有效18位
        String id19 = valid18.substring(0, 10) + "8" + valid18.substring(10);

        NumberEvidence evidence = extractor.extract("身份证号：" + id19 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_INVALID_LENGTH", ids.get(0).getType());
        assertEquals(19, ids.get(0).getLength());
        assertTrue(ids.get(0).getValidations().stream()
            .anyMatch(v -> "id_near_miss_remove_one_digit".equals(v.getName())));
    }

    @Test
    void should_not_extract_case_number_substring_as_id_card() {
        // 22位案件编号：不应被拆成18位身份证子串
        String caseNo = "3404226100002025120115";
        NumberEvidence evidence = extractor.extract("案件编号：" + caseNo + "。");

        assertTrue(idCandidates(evidence).isEmpty(), "不应从案件编号中误提取身份证号");
    }

    @Test
    void should_extract_masked_id_and_invalid_length_masked_id() {
        String masked18 = "110105********1001";
        String masked17 = "110105********100";
        NumberEvidence evidence = extractor.extract("遮挡：" + masked18 + "；遮挡且长度错：" + masked17 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(2, ids.size());
        assertTrue(ids.stream().anyMatch(c -> "ID_MASKED".equals(c.getType())));
        assertTrue(ids.stream().anyMatch(c -> "ID_INVALID_LENGTH_MASKED".equals(c.getType())));
    }

    @Test
    void should_not_confuse_phone_as_id_card() {
        NumberEvidence evidence = extractor.extract("手机号：13800000000。");
        assertTrue(evidence.getNumbers().stream().anyMatch(c -> "PHONE".equals(c.getType())));
        assertTrue(idCandidates(evidence).isEmpty(), "手机号不应被误识别为身份证号");
    }

    @Test
    void should_ignore_invalid_area_or_birth_date_for_id_card() {
        String invalidArea = "010105199001010012"; // 地区码首位0
        String invalidDate = "110105199013010012"; // 生日段月份13

        NumberEvidence e1 = extractor.extract("证件号：" + invalidArea + "。");
        NumberEvidence e2 = extractor.extract("证件号：" + invalidDate + "。");

        assertTrue(idCandidates(e1).isEmpty());
        assertTrue(idCandidates(e2).isEmpty());
    }

    @Test
    void should_extract_15_digit_old_id_card() {
        String id15 = "110105900101001";
        NumberEvidence evidence = extractor.extract("证件号：" + id15 + "。");

        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);
        assertEquals(1, ids.size());
        assertEquals("ID_VALID_15", ids.get(0).getType());
        assertEquals(15, ids.get(0).getLength());
    }

    @Test
    void should_extract_multiple_mixed_id_cards() {
        String body17 = "11010519900101001";
        String valid18 = body17 + calcId18CheckChar(body17);
        String invalidChecksum18 = body17 + mutateCheckChar(valid18.substring(17));
        String invalidLen17 = body17;
        String masked18 = "110105********1001";

        NumberEvidence evidence = extractor.extract("混合：" + valid18 + "；" + invalidChecksum18 + "；" + invalidLen17 + "；" + masked18 + "。");
        List<NumberEvidence.NumberCandidate> ids = idCandidates(evidence);

        assertTrue(ids.stream().anyMatch(c -> "ID_VALID_18".equals(c.getType())));
        assertTrue(ids.stream().anyMatch(c -> "ID_INVALID_CHECKSUM".equals(c.getType())));
        assertTrue(ids.stream().anyMatch(c -> "ID_INVALID_LENGTH".equals(c.getType())));
        assertTrue(ids.stream().anyMatch(c -> "ID_MASKED".equals(c.getType())));
    }

    private static List<NumberEvidence.NumberCandidate> idCandidates(NumberEvidence evidence) {
        List<NumberEvidence.NumberCandidate> out = new ArrayList<>();
        if (evidence == null || evidence.getNumbers() == null) {
            return out;
        }
        for (NumberEvidence.NumberCandidate n : evidence.getNumbers()) {
            if (n != null && n.getType() != null && n.getType().startsWith("ID_")) {
                out.add(n);
            }
        }
        return out;
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

