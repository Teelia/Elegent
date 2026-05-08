import com.datalabeling.service.extraction.NumberEvidenceExtractor;

/**
 * 测试X结尾身份证号的识别问题
 */
public class TestIdCardXEnding {
    public static void main(String[] args) {
        NumberEvidenceExtractor extractor = new NumberEvidenceExtractor();

        // 测试用例1：X结尾的有效18位身份证
        String testCase1 = "身份证号：11010119900101123X";
        System.out.println("=== 测试用例1: X结尾的有效18位身份证 ===");
        System.out.println("输入: " + testCase1);
        NumberEvidence result1 = extractor.extract(testCase1);
        System.out.println("结果: " + result1.getNumbers().size() + " 个候选");
        for (NumberEvidence.NumberCandidate candidate : result1.getNumbers()) {
            System.out.println("  - 类型: " + candidate.getType() + ", 值: " + candidate.getValue() +
                             ", 置信度: " + candidate.getConfidenceRule());
        }
        System.out.println();

        // 测试用例2：x结尾（小写）的18位身份证
        String testCase2 = "证件号11010119900101123x已提交";
        System.out.println("=== 测试用例2: x结尾（小写）的18位身份证 ===");
        System.out.println("输入: " + testCase2);
        NumberEvidence result2 = extractor.extract(testCase2);
        System.out.println("结果: " + result2.getNumbers().size() + " 个候选");
        for (NumberEvidence.NumberCandidate candidate : result2.getNumbers()) {
            System.out.println("  - 类型: " + candidate.getType() + ", 值: " + candidate.getValue() +
                             ", 置信度: " + candidate.getConfidenceRule());
        }
        System.out.println();

        // 测试用例3：纯数字的18位身份证
        String testCase3 = "身份证号：110101199001011234";
        System.out.println("=== 测试用例3: 纯数字的18位身份证 ===");
        System.out.println("输入: " + testCase3);
        NumberEvidence result3 = extractor.extract(testCase3);
        System.out.println("结果: " + result3.getNumbers().size() + " 个候选");
        for (NumberEvidence.NumberCandidate candidate : result3.getNumbers()) {
            System.out.println("  - 类型: " + candidate.getType() + ", 值: " + candidate.getValue() +
                             ", 置信度: " + candidate.getConfidenceRule());
        }
        System.out.println();

        // 测试用例4：X结尾但校验位不正确的18位（应识别为校验位错误）
        String testCase4 = "身份证号：11010119900101121X"; // 最后一位应该是3，不是1
        System.out.println("=== 测试用例4: X结尾但校验位不正确的18位 ===");
        System.out.println("输入: " + testCase4);
        NumberEvidence result4 = extractor.extract(testCase4);
        System.out.println("结果: " + result4.getNumbers().size() + " 个候选");
        for (NumberEvidence.NumberCandidate candidate : result4.getNumbers()) {
            System.out.println("  - 类型: " + candidate.getType() + ", 值: " + candidate.getValue() +
                             ", 置信度: " + candidate.getConfidenceRule());
        }
        System.out.println();

        System.out.println("=== 测试完成 ===");
    }
}
