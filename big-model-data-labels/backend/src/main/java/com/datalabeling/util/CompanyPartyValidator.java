package com.datalabeling.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 公司主体识别与验证器
 * <p>
 * 识别文本中的公司主体，并验证其是否提供了有效的身份信息
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
@Component
public class CompanyPartyValidator {

    /**
     * 公司主体关键词（按优先级排序）
     */
    private static final List<String> COMPANY_KEYWORDS = new ArrayList<>();

    static {
        // 高优先级：明确的法人主体
        COMPANY_KEYWORDS.add("有限公司");
        COMPANY_KEYWORDS.add("有限责任公司");
        COMPANY_KEYWORDS.add("股份有限公司");
        COMPANY_KEYWORDS.add("集团公司");
        COMPANY_KEYWORDS.add("集团总公司");

        // 中优先级：其他企业形式
        COMPANY_KEYWORDS.add("分公司");
        COMPANY_KEYWORDS.add("子公司");
        COMPANY_KEYWORDS.add("合作社");
        COMPANY_KEYWORDS.add("企业");

        // 低优先级：通用商业实体
        COMPANY_KEYWORDS.add("公司");
        COMPANY_KEYWORDS.add("厂");
        COMPANY_KEYWORDS.add("店");
        COMPANY_KEYWORDS.add("铺");
        COMPANY_KEYWORDS.add("中心");
        COMPANY_KEYWORDS.add("行");
        COMPANY_KEYWORDS.add("部");
    }

    /**
     * 统一社会信用代码正则（18位，大写字母+数字）
     */
    private static final Pattern CREDIT_CODE_PATTERN =
            Pattern.compile("\\b[0-9A-Z]{18}\\b");

    /**
     * 营业执照注册号正则（15位数字，旧版）
     */
    private static final Pattern REG_NUMBER_PATTERN =
            Pattern.compile("\\b\\d{15}\\b");

    /**
     * 组织机构代码正则（9位，字母+数字）
     */
    private static final Pattern ORG_CODE_PATTERN =
            Pattern.compile("\\b[A-Z0-9]{8}-[A-Z0-9]\\b");

    /**
     * 社会信用代码关键字
     */
    private static final Pattern CREDIT_CODE_KEYWORD =
            Pattern.compile("统一社会信用代码|社会信用代码|信用代码");

    /**
     * 营业执照关键字
     */
    private static final Pattern REG_LICENSE_KEYWORD =
            Pattern.compile("营业执照|注册号|工商注册");

    /**
     * 检测文本中是否涉及公司主体
     *
     * @param text 文本
     * @return 检测结果
     */
    public DetectionResult detectCompanyParties(String text) {
        DetectionResult result = new DetectionResult();

        // 1. 识别公司名称
        Set<String> detectedCompanies = new HashSet<>();
        for (String keyword : COMPANY_KEYWORDS) {
            // 匹配模式：公司名称 + 关键词
            // 例如："XX科技有限公司"、"XX公司"
            Pattern pattern = Pattern.compile(
                    "([\\u4e00-\\u9fa5]{2,15}" + keyword + ")");
            Matcher matcher = pattern.matcher(text);

            while (matcher.find()) {
                String company = matcher.group(1);
                if (!detectedCompanies.contains(company)) {
                    detectedCompanies.add(company);
                    result.addCompany(new DetectedCompany(company, keyword, detectCompanyPosition(text, company)));
                }
            }
        }

        // 2. 检查是否有营业执照信息
        boolean hasCreditCode = CREDIT_CODE_PATTERN.matcher(text).find();
        boolean hasRegNumber = REG_NUMBER_PATTERN.matcher(text).find();
        boolean hasOrgCode = ORG_CODE_PATTERN.matcher(text).find();
        boolean hasLicenseKeyword = REG_LICENSE_KEYWORD.matcher(text).find() ||
                CREDIT_CODE_KEYWORD.matcher(text).find();

        result.setHasBusinessLicense(hasCreditCode || hasRegNumber || hasOrgCode);
        result.setHasLicenseKeyword(hasLicenseKeyword);

        log.debug("检测到{}个公司主体，是否有营业执照信息: {}", detectedCompanies.size(), result.isHasBusinessLicense());

        return result;
    }

    /**
     * 检测公司在文本中的大致位置（用于后续关联分析）
     */
    private int detectCompanyPosition(String text, String company) {
        return text.indexOf(company);
    }

    /**
     * 验证公司主体的身份信息是否完整
     *
     * @param text 文本
     * @return 验证结果
     */
    public ValidationResult validateCompanyParties(String text) {
        DetectionResult detection = detectCompanyParties(text);

        // 如果没有检测到公司主体，直接返回通过
        if (detection.getCompanies().isEmpty()) {
            return ValidationResult.valid("未涉及公司主体");
        }

        List<String> errors = new ArrayList<>();

        // 检查是否有营业执照信息
        if (!detection.isHasBusinessLicense() && !detection.isHasLicenseKeyword()) {
            // 没有检测到任何营业执照相关信息
            String companyNames = detection.getCompanies().stream()
                    .limit(3)  // 只显示前3个
                    .map(DetectedCompany::getName)
                    .collect(Collectors.joining("、"));

            int totalCompanies = detection.getCompanies().size();
            if (totalCompanies > 3) {
                companyNames += "等";
            }

            errors.add(String.format("涉及%d个公司主体（%s），但未检测到营业执照/统一社会信用代码等有效身份信息",
                    totalCompanies, companyNames));
        }

        if (!errors.isEmpty()) {
            return ValidationResult.invalid(String.join("; ", errors));
        }

        return ValidationResult.valid(String.format("涉及%d个公司主体，身份信息完整",
                detection.getCompanies().size()));
    }

    /**
     * 提取有效的营业执照信息
     *
     * @param text 文本
     * @return 提取的营业执照信息
     */
    public List<LicenseInfo> extractLicenseInfo(String text) {
        List<LicenseInfo> licenses = new ArrayList<>();

        // 提取统一社会信用代码
        Matcher creditMatcher = CREDIT_CODE_PATTERN.matcher(text);
        while (creditMatcher.find()) {
            licenses.add(new LicenseInfo("统一社会信用代码", creditMatcher.group()));
        }

        // 提取营业执照注册号
        Matcher regMatcher = REG_NUMBER_PATTERN.matcher(text);
        while (regMatcher.find()) {
            licenses.add(new LicenseInfo("营业执照注册号", regMatcher.group()));
        }

        // 提取组织机构代码
        Matcher orgMatcher = ORG_CODE_PATTERN.matcher(text);
        while (orgMatcher.find()) {
            licenses.add(new LicenseInfo("组织机构代码", orgMatcher.group()));
        }

        return licenses;
    }

    /**
     * 检测结果
     */
    @Data
    public static class DetectionResult {
        private List<DetectedCompany> companies = new ArrayList<>();
        private boolean hasBusinessLicense = false;
        private boolean hasLicenseKeyword = false;

        public void addCompany(DetectedCompany company) {
            companies.add(company);
        }

        public boolean hasCompanies() {
            return !companies.isEmpty();
        }
    }

    /**
     * 检测到的公司
     */
    @Data
    @lombok.AllArgsConstructor
    public static class DetectedCompany {
        private String name;           // 公司名称
        private String keyword;        // 匹配到的关键词
        private int position;          // 在文本中的位置
    }

    /**
     * 营业执照信息
     */
    @Data
    @lombok.AllArgsConstructor
    public static class LicenseInfo {
        private String type;    // 类型
        private String code;    // 代码
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String reason;

        public static ValidationResult valid() {
            return new ValidationResult(true, "格式正确");
        }

        public static ValidationResult valid(String reason) {
            return new ValidationResult(true, reason);
        }

        public static ValidationResult invalid(String reason) {
            return new ValidationResult(false, reason);
        }
    }
}
