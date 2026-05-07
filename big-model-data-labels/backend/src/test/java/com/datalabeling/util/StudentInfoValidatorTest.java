package com.datalabeling.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StudentInfoValidatorTest {

    @Test
    void testNoStudentSignals_ResultYesShouldBeValid() {
        StudentInfoValidator v = new StudentInfoValidator();

        String text = "报警人张三称小区噪音扰民，民警到场未发现异常。";
        StudentInfoValidator.ValidationResult r = v.validate("是", text);

        assertTrue(r.isValid(), "不涉及在校学生时，判定为[是]应被接受");
    }

    @Test
    void testK12SchoolWithoutRegionPrefix_ShouldBeInvalidWhenResultYes() {
        StudentInfoValidator v = new StudentInfoValidator();

        String text = "学生张三，身份证号：34040419971118021X，学校：桐城市第八中学，年级：初二，联系方式：13800138000";
        StudentInfoValidator.ValidationResult r = v.validate("是", text);

        assertFalse(r.isValid(), "基础教育学校缺少省/市前缀时，判定为[是]应被拦截");
        assertTrue(r.getMessage().contains("缺少省/市前缀") || r.getMessage().contains("学校名称"), "应给出学校全称问题提示");
    }
}

