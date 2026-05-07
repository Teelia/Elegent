import java.util.*;
import java.util.regex.*;

/**
 * 简化版身份证号识别测试
 * 验证扩展范围后能否正确识别 19 位等错误身份证号
 */
public class TestIdCardRecognition {
    
    // 原始范围 15-18 位
    private static final Pattern ORIGINAL_RANGE = Pattern.compile("\d{15,18}");
    
    // 扩展范围 14-22 位
    private static final Pattern EXTENDED_RANGE = Pattern.compile("\d{14,22}");
    
    public static void main(String[] args) {
        String[] testCases = {
            "34022319980605623",      // 17位 - 应该被识别
            "3402231998060562312",     // 19位 - 扩展后应该被识别
            "21021919731213405",       // 17位 - 应该被识别
            "34052119720206545",       // 17位 - 应该被识别
            "3402231989030661411",     // 19位 - 扩展后应该被识别
            "411327199511061118",      // 18位 - 标准格式
            "340203197107120513",      // 18位 - 标准格式
        };
        
        System.out.println("========================================");
        System.out.println("身份证号识别范围测试对比");
        System.out.println("========================================\n");
        
        System.out.println("【原始范围 15-18位】");
        int originalCount = 0;
        for (String id : testCases) {
            boolean matched = ORIGINAL_RANGE.matcher(id).matches();
            if (matched) originalCount++;
            System.out.printf("  %s (%d位): %s\n", 
                id, id.length(), matched ? "✓ 识别" : "✗ 未识别");
        }
        System.out.printf("  总计: %d/%d\n\n", originalCount, testCases.length);
        
        System.out.println("【扩展范围 14-22位】");
        int extendedCount = 0;
        for (String id : testCases) {
            boolean matched = EXTENDED_RANGE.matcher(id).matches();
            if (matched) extendedCount++;
            System.out.printf("  %s (%d位): %s\n", 
                id, id.length(), matched ? "✓ 识别" : "✗ 未识别");
        }
        System.out.printf("  总计: %d/%d\n\n", extendedCount, testCases.length);
        
        System.out.println("========================================");
        System.out.println("结论:");
        System.out.printf("  原始范围识别率: %.1f%%\n", originalCount * 100.0 / testCases.length);
        System.out.printf("  扩展范围识别率: %.1f%%\n", extendedCount * 100.0 / testCases.length);
        System.out.printf("  提升幅度: +%.1f个百分点\n\n", (extendedCount - originalCount) * 100.0 / testCases.length);
        
        // 验证关键改进
        System.out.println("【关键改进验证】");
        System.out.println("  19位号码 '3402231998060562312':");
        System.out.println("    原始范围: " + (ORIGINAL_RANGE.matcher("3402231998060562312").matches() ? "✓ 识别" : "✗ 未识别"));
        System.out.println("    扩展范围: " + (EXTENDED_RANGE.matcher("3402231998060562312").matches() ? "✓ 识别" : "✗ 未识别"));
        System.out.println("========================================");
    }
}
