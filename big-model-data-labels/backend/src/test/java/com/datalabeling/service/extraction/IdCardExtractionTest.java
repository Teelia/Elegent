package com.datalabeling.service.extraction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试身份证号提取：验证 34040419971118021X 的识别情况
 */
public class IdCardExtractionTest {

    public static void main(String[] args) {
        IdCardExtractor extractor = new IdCardExtractor();

        // 第一行原始数据（根据Excel文件）
        String text = "涉警纠纷双方当事人了解，当事人之一：甲方（身份证号：340121197707234933，手机号：13671973547）；" +
                "当事人之二：34040419971118021X（手机号未提供,联系方式无关系）；" +
                "双方因合同纠纷产生争执，甲方报警称乙方未履行合同，乙方否认违约，双方各执一词。" +
                "警方到达现场调解，但双方意见分歧较大，无法达成一致。警方将双方带离现场进一步处理。" +
                "双方情绪激动，现场一度混乱。警方警告双方保持冷静，双方离开现场。警方继续处理双方问题。";

        System.out.println("========================================");
        System.out.println("身份证号提取测试");
        System.out.println("========================================");
        System.out.println();
        System.out.println("测试文本:");
        System.out.println(text);
        System.out.println();

        // 测试1: 提取所有身份证号（18位 + 15位）
        Map<String, Object> options = new HashMap<>();
        options.put("include18Digit", true);
        options.put("include15Digit", true);
        options.put("includeLoose", false);

        List<ExtractedNumber> results = extractor.extract(text, options);

        System.out.println("========================================");
        System.out.println("提取结果（includeLoose=false）:");
        System.out.println("========================================");
        System.out.println("共提取到 " + results.size() + " 个身份证号");
        System.out.println();

        for (int i = 0; i < results.size(); i++) {
            ExtractedNumber result = results.get(i);
            System.out.println("--- 身份证号 " + (i + 1) + " ---");
            System.out.println("值: " + result.getValue());
            System.out.println("置信度: " + result.getConfidence());
            System.out.println("验证信息: " + result.getValidation());
            System.out.println("位置: [" + result.getStartIndex() + ", " + result.getEndIndex() + "]");
            System.out.println();
        }

        // 测试2: 包含宽松模式
        options.put("includeLoose", true);
        List<ExtractedNumber> resultsWithLoose = extractor.extract(text, options);

        System.out.println("========================================");
        System.out.println("提取结果（includeLoose=true）:");
        System.out.println("========================================");
        System.out.println("共提取到 " + resultsWithLoose.size() + " 个身份证号");
        System.out.println();

        for (int i = 0; i < resultsWithLoose.size(); i++) {
            ExtractedNumber result = resultsWithLoose.get(i);
            System.out.println("--- 身份证号 " + (i + 1) + " ---");
            System.out.println("值: " + result.getValue());
            System.out.println("置信度: " + result.getConfidence());
            System.out.println("验证信息: " + result.getValidation());
            System.out.println();
        }

        // 测试3: 重点验证 34040419971118021X
        System.out.println("========================================");
        System.out.println("重点验证 34040419971118021X:");
        System.out.println("========================================");

        String targetId = "34040419971118021X";
        boolean found = false;
        for (ExtractedNumber result : resultsWithLoose) {
            if (targetId.equals(result.getValue())) {
                found = true;
                System.out.println("✓ 找到目标身份证号");
                System.out.println("  值: " + result.getValue());
                System.out.println("  置信度: " + result.getConfidence());
                System.out.println("  验证信息: " + result.getValidation());
                break;
            }
        }

        if (!found) {
            System.out.println("✗ 未找到目标身份证号: " + targetId);
            System.out.println();
            System.out.println("可能的原因:");
            System.out.println("1. 正则表达式不匹配");
            System.out.println("2. 校验位验证失败");
            System.out.println("3. 被其他规则排除");
        }

        // 测试4: 手动验证校验位
        System.out.println();
        System.out.println("========================================");
        System.out.println("手动验证校验位:");
        System.out.println("========================================");
        validateCheckBit(targetId);
    }

    /**
     * 手动验证18位身份证号的校验位
     */
    private static void validateCheckBit(String idCard) {
        int[] weights = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};
        char[] checkChars = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

        System.out.println("身份证号: " + idCard);
        System.out.println();

        int sum = 0;
        System.out.println("校验计算:");
        for (int i = 0; i < 17; i++) {
            int digit = idCard.charAt(i) - '0';
            int weight = weights[i];
            int product = digit * weight;
            sum += product;
            System.out.printf("  第%d位 × %d = %d (累计: %d)\n", i + 1, weight, product, sum);
        }

        int remainder = sum % 11;
        char expectedCheck = checkChars[remainder];
        char actualCheck = Character.toUpperCase(idCard.charAt(17));

        System.out.println();
        System.out.println("计算结果:");
        System.out.println("  加权总和: " + sum);
        System.out.println("  取模结果: " + sum + " % 11 = " + remainder);
        System.out.println("  预期校验位: " + expectedCheck);
        System.out.println("  实际校验位: " + actualCheck);
        System.out.println("  验证结果: " + (expectedCheck == actualCheck ? "✓ 通过" : "✗ 失败"));
    }
}
