package com.datalabeling.util;

import com.datalabeling.service.extraction.PartyExtractor;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：按《检测规则.docx》口径的排除与纠偏
 */
public class PostProcessValidatorDocRulesTest {

    @Test
    void shouldSuggestYesWhenModelSaysNoButRuleEvidenceShowsComplete() {
        PartyExtractor partyExtractor = new PartyExtractor();
        StudentInfoValidator studentInfoValidator = new StudentInfoValidator();
        PostProcessValidator v = new PostProcessValidator(partyExtractor, studentInfoValidator);

        String text = "报警人：张三（身份证号：340811199901011234），现场无其他涉警当事人。";
        Map<String, Object> row = new HashMap<>();
        row.put("反馈内容", text);

        PostProcessValidator.ValidationResult r = v.validate("否", row, null, "涉警当事人信息完整性检查");
        assertTrue(r.isValid());
        assertEquals(PostProcessValidator.ValidationLevel.WARNING, r.getLevel());
        assertEquals("是", r.getSuggestedResult());
    }

    @Test
    void shouldNotTreatUnknownSuspectInTheftAsMissingParty() {
        PartyExtractor partyExtractor = new PartyExtractor();
        StudentInfoValidator studentInfoValidator = new StudentInfoValidator();
        PostProcessValidator v = new PostProcessValidator(partyExtractor, studentInfoValidator);

        String text = "发生盗窃，嫌疑人身份无法确定。报警人：张三（身份证号：340811199901011234）。";
        Map<String, Object> row = new HashMap<>();
        row.put("反馈内容", text);

        PostProcessValidator.ValidationResult r = v.validate("是", row, null, "涉警当事人信息完整性检查");
        assertTrue(r.isValid(), r.getMessage());
    }

    @Test
    void shouldNotRequireGenericOpponentWhenNotOnSceneOrUnreachable() {
        PartyExtractor partyExtractor = new PartyExtractor();
        StudentInfoValidator studentInfoValidator = new StudentInfoValidator();
        PostProcessValidator v = new PostProcessValidator(partyExtractor, studentInfoValidator);

        String text = "双方发生纠纷，对方不在现场无法联系。报警人：张三（身份证号：340811199901011234）。";
        Map<String, Object> row = new HashMap<>();
        row.put("反馈内容", text);

        PostProcessValidator.ValidationResult r = v.validate("是", row, null, "涉警当事人信息完整性检查");
        assertTrue(r.isValid(), r.getMessage());
    }

    @Test
    void shouldSuggestYesForWithdrawalCaseEvenIfModelSaysNo() {
        PartyExtractor partyExtractor = new PartyExtractor();
        StudentInfoValidator studentInfoValidator = new StudentInfoValidator();
        PostProcessValidator v = new PostProcessValidator(partyExtractor, studentInfoValidator);

        String text = "报警人请求撤警，误报警，不需要处理。";
        Map<String, Object> row = new HashMap<>();
        row.put("反馈内容", text);

        PostProcessValidator.ValidationResult r = v.validate("否", row, null, "涉警当事人信息完整性检查");
        assertTrue(r.isValid());
        assertEquals("是", r.getSuggestedResult());
    }
}
