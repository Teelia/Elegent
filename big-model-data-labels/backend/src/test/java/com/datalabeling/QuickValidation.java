package com.datalabeling;

import com.datalabeling.util.*;
import com.datalabeling.service.extraction.PartyExtractor;
import java.util.*;

public class QuickValidation {
    public static void main(String[] args) {
        PostProcessValidator validator = new PostProcessValidator(
                new PartyExtractor(),
                new StudentInfoValidator());

        // 测试用例1：前缀字符 - 行号222
        String test1 = "鲍克娥（？34030219790910125X，13866603854）";
        Map<String, Object> data1 = new HashMap<>();
        data1.put("content", test1);
        PostProcessValidator.ValidationResult result1 = validator.validate("是", data1, null);
        System.out.println("测试1 - 前缀检测: valid=" + result1.isValid());
        System.out.println("         message=" + result1.getMessage());

        // 测试用例2：可疑护照号 - 行号226
        String test2 = "顾晨，C789056321,无其他信息";
        Map<String, Object> data2 = new HashMap<>();
        data2.put("content", test2);
        PostProcessValidator.ValidationResult result2 = validator.validate("是", data2, null);
        System.out.println("测试2 - 可疑护照: valid=" + result2.isValid());
        System.out.println("         message=" + result2.getMessage());

        // 测试用例3：错误位数
        IdCardLengthValidator.InvalidLengthDetection result3 =
            IdCardLengthValidator.detectInvalidLengthIdCards("身份证号12345678901234");
        System.out.println("测试3 - 错误位数(14位): hasInvalid=" + result3.hasInvalid());
        System.out.println("         summary=" + result3.getSummary());

        // 测试用例4：行号162完整场景
        String test4 = "我单位[吴烨080244]赶到现场。经查:朱雪飞(342921196402195014)系环卫工人，" +
                    "其妻子何月桂（342830196708171665）路过鲍克娥（？340811197505046323）家店铺时，" +
                    "看到鲍克娥在扔垃圾，认为鲍克娥经营的批发超市扔的垃圾太多";
        Map<String, Object> data4 = new HashMap<>();
        data4.put("content", test4);
        PostProcessValidator.ValidationResult result4 = validator.validate("是", data4, null);
        System.out.println("测试4 - 行号162场景: valid=" + result4.isValid());
        System.out.println("         message=" + result4.getMessage());

        System.out.println("\n=== 验证完成 ===");
        System.out.println("预期结果: 所有测试的valid都应该为false（判定为'否'）");
    }
}
