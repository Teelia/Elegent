package com.datalabeling.util;

import com.datalabeling.service.extraction.ExtractedNumber;
import com.datalabeling.service.extraction.PartyExtractor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 后置规则验证器（增强版 v2）
 * <p>
 * 对LLM判定结果进行规则层面的最终验证，防止误判
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PostProcessValidator {

    private final PartyExtractor partyExtractor;
    private final StudentInfoValidator studentInfoValidator; // v2新增：在校学生信息验证器

    /**
     * 信息缺失关键词
     */
    private static final List<String> MISSING_INFO_KEYWORDS = Arrays.asList(
            // 与“身份信息录入缺失”更强相关的表达（用于误判风险判断 & 审计提示）
            // 注意：不把单独的“无法”作为缺失关键词（会误伤：无法确定嫌疑人身份/无法联系等口径排除场景）
            "拒绝登记", "拒绝提供", "拒不透露",
            "未提供", "未登记",
            "无法登记", "无法提供",
            "研判无果", "查询无果", "系统查询无果",
            "不详", "记不清",
            "身份证号记不住", "身份信息不详",
            "未获取到", "未获取到身份信息",
            "无法核实", "无法核实身份"
    );

    /**
     * 强制推翻关键词（v3新增）
     * <p>这些关键词一旦检测到，直接判定为[否]，不给予警告机会</p>
     */
    private static final List<String> FORCE_OVERRIDE_KEYWORDS = Arrays.asList(
            "匿名", "匿名报警", "匿名报警人",
            // 仅保留“明确指向身份信息缺失”的强信号，避免“无法”泛化词误杀
            "拒绝登记", "拒绝提供", "拒不透露",
            "未提供", "未登记", "无法登记", "无法提供",
            "研判无果", "查询无果", "系统查询无果",
            "不详", "记不清", "身份信息不详", "未获取到", "未获取到身份信息",
            "无法核实", "无法核实身份"
    );

    /**
     * 撤警/无效警情关键词
     */
    private static final List<String> WITHDRAWAL_KEYWORDS = Arrays.asList(
            "请求撤警", "要求撤警", "自愿撤警", "主动撤警",
            "误报警", "不需要处理", "已协商解决",
            "重复警情", "副单", "无效警情", "无效报警", "无事实警情",
            "未发现报警情况", "现场无异常", "无警情发生",
            "无需处理", "现场调解成功"
    );

    /**
     * 验证判断结果（增强版）
     *
     * @param result          LLM判断结果
     * @param rowData         原始数据
     * @param extractedNumbers 提取的号码
     * @return 验证结果
     */
    public ValidationResult validate(String result,
                                     java.util.Map<String, Object> rowData,
                                     List<ExtractedNumber> extractedNumbers) {

        // 构建验证上下文
        ValidationContext context = buildContext(rowData);

        // 1. 首先检查是否属于撤警情形
        if (isWithdrawalCase(context.getText())) {
            // 《检测规则》：直接合格情形优先级最高，必要时应纠偏为“是”
            log.debug("检测到直接合格情形（撤警/无效警情等），建议判定为[是]");
            if (!"是".equals(result)) {
                return ValidationResult.suggest("是", "命中直接合格情形（撤警/无效警情/无事实），修正为[是]");
            }
            return ValidationResult.valid("命中直接合格情形（撤警/无效警情/无事实），判定为[是]");
        }

        // 2. 根据判断结果进行不同验证
        if ("是".equals(result)) {
            return validatePositiveResultEnhanced(context);
        } else {
            return validateNegativeResult(context);
        }
    }

    /**
     * 验证判断结果（增强版 v2 - 支持标签类型判断）
     *
     * @param result          LLM判断结果
     * @param rowData         原始数据
     * @param extractedNumbers 提取的号码
     * @param labelName       标签名称（用于判断是否使用专用验证器）
     * @return 验证结果
     */
    public ValidationResult validate(String result,
                                     java.util.Map<String, Object> rowData,
                                     List<ExtractedNumber> extractedNumbers,
                                     String labelName) {

        // ========== v2新增：在校学生专用验证 ==========
        if (labelName != null && labelName.contains("在校学生")) {
            log.debug("检测到在校学生标签，使用专用验证器");
            String text = extractTextFromRowData(rowData);
            StudentInfoValidator.ValidationResult studentResult =
                    studentInfoValidator.validate(result, text);

            // 转换验证结果
            if (!studentResult.isValid()) {
                return ValidationResult.invalid(studentResult.getMessage());
            } else if (studentResult.getLevel() == StudentInfoValidator.ValidationLevel.WARNING) {
                return ValidationResult.warning(studentResult.getMessage());
            } else {
                return ValidationResult.valid(studentResult.getMessage());
            }
        }

        // 其他标签使用原有验证逻辑
        return validate(result, rowData, extractedNumbers);
    }

    /**
     * 检查是否属于撤警/无效警情情形
     */
    private boolean isWithdrawalCase(String text) {
        for (String keyword : WITHDRAWAL_KEYWORDS) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 验证"是"的判断（增强版 v3 - 强制推翻机制）
     */
    private ValidationResult validatePositiveResultEnhanced(ValidationContext context) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        String text = context.getText();

        // ========== 0. 强制推翻检查（最高优先级，v3新增）==========

        // 0.1 检查匿名报警
        if (text.contains("匿名") || text.contains("匿名报警") || text.contains("匿名报警人")) {
            return ValidationResult.invalid(
                    "检测到[匿名]关键词，匿名报警无完整身份信息，强制判定为[否]");
        }

        // 0.2 检查强制信息缺失关键词
        for (String keyword : FORCE_OVERRIDE_KEYWORDS) {
            if (text.contains(keyword)) {
                // 检查是否有足够的身份证号来抵消这个关键词
                // 策略：如果提及"拒绝"但未提供该人的替代信息，则强制判否
                return ValidationResult.invalid(
                        String.format("检测到信息缺失关键词[%s]，表明存在当事人身份信息缺失，强制判定为[否]", keyword));
            }
        }

        // ========== 1. 检查身份证号格式（带前缀检测）==========
        IdCardLengthValidator.ExtractResult idResult = context.getIdCardValidationResult();
        if (idResult.hasInvalid()) {
            List<String> invalidDetails = idResult.getInvalidDetails();
            for (String detail : invalidDetails) {
                errors.add("身份证号格式错误: " + detail);
            }
        }

        // 1.1 检查错误位数的身份证号（14/16/17/19/20/21位等）
        IdCardLengthValidator.InvalidLengthDetection invalidLengthDetection =
                context.getInvalidLengthDetection();
        if (invalidLengthDetection != null && invalidLengthDetection.hasInvalid()) {
            errors.add(String.format("检测到%d个错误位数的身份证号: %s",
                    invalidLengthDetection.getCount(),
                    invalidLengthDetection.getSummary()));
        }

        // ========== 2. 检查护照号格式 ==========
        PassportValidator.ExtractResult passportResult = context.getPassportValidationResult();
        if (passportResult.hasSuspiciousPassports()) {
            errors.add("护照号格式不符合标准: " + passportResult.getInvalidReasons());
        }

        // 2.1 检查可疑护照号（如C789056321等9位非标准格式）
        if (context.hasSuspiciousPassports()) {
            PassportValidator.DetectionResult suspicious = context.getSuspiciousPassportDetection();
            errors.add("检测到" + suspicious.getPassports().size() + "个可疑护照号: " +
                    String.join(", ", suspicious.getPassports()));
        }

        // ========== 3. 检查公司主体 ==========
        CompanyPartyValidator.ValidationResult companyResult =
                context.getCompanyValidationResult();
        if (!companyResult.isValid()) {
            errors.add(companyResult.getReason());
        }

        // ========== 4. 检查当事人信息完整性（核心检查）==========
        PartyExtractor.CompletenessResult completenessResult =
                partyExtractor.checkCompleteness(context.getParties());

        if (!completenessResult.isComplete()) {
            // 核心问题：当事人信息不完整
            errors.add(completenessResult.getMessage());
        }

        // ========== 生成验证结果 ==========
        if (!errors.isEmpty()) {
            return ValidationResult.invalid(
                    String.format("判定为[是]但发现%d个问题：%s",
                            errors.size(),
                            String.join("; ", errors)));
        }

        if (!warnings.isEmpty()) {
            return ValidationResult.warning(
                    String.format("判定为[是]，但有%d个警告：%s",
                            warnings.size(),
                            String.join("; ", warnings)));
        }

        return ValidationResult.valid("规则验证通过，判定结果正确");
    }

    /**
     * 验证"否"的判断（检查误判风险）
     */
    private ValidationResult validateNegativeResult(ValidationContext context) {
        // 检查是否可能误判
        boolean allIdCardsValid = !context.getIdCardValidationResult().hasInvalid();
        boolean noMissingInfo = !context.hasMissingInfoKeywords();
        boolean allPersonsComplete = partyExtractor.checkCompleteness(context.getParties()).isComplete();

        if (allIdCardsValid && noMissingInfo && allPersonsComplete) {
            // doc口径：若证据表明合格，应允许纠偏为“是”
            return ValidationResult.suggest(
                    "是",
                    "规则证据表明涉警当事人身份信息完整（无缺失关键词、证件格式有效、当事人完整），建议修正为[是]");
        }

        return ValidationResult.valid("规则验证通过，判定结果正确");
    }

    /**
     * 构建验证上下文（增强版）
     */
    private ValidationContext buildContext(java.util.Map<String, Object> rowData) {
        ValidationContext context = new ValidationContext();

        // 提取文本内容
        String text = extractTextFromRowData(rowData);
        context.setText(text);

        // 1. 身份证号验证（带前缀检测）
        context.setIdCardValidationResult(
                IdCardLengthValidator.extractAndValidate(text, false));

        // 1.1 检测错误位数的身份证号（14/16/17/19/20/21位等）
        context.setInvalidLengthDetection(
                IdCardLengthValidator.detectInvalidLengthIdCards(text));

        // 2. 护照号验证
        context.setPassportValidationResult(
                new PassportValidator().extractAndValidate(text));

        // 2.1 可疑护照号检测（检测C789056321等非标准格式）
        context.setSuspiciousPassportDetection(
                new PassportValidator().detectSuspiciousPassports(text));

        // 3. 公司主体验证
        context.setCompanyValidationResult(
                new CompanyPartyValidator().validateCompanyParties(text));

        // 4. 检查信息缺失关键词
        for (String keyword : MISSING_INFO_KEYWORDS) {
            if (text.contains(keyword)) {
                context.addMissingKeyword(keyword);
            }
        }

        // 5. 提取当事人
        List<PartyExtractor.Party> parties = partyExtractor.extractParties(text, rowData);
        context.setParties(applyDocExclusionRules(text, parties));

        return context;
    }

    /**
     * 按《检测规则.docx》口径做“涉警当事人范围”排除，避免误把范围外对象当作缺失：
     * - 与警情无直接关系的报警人（如路见不平报警）
     * - 盗窃/损坏财物等涉嫌违法犯罪警情中，无法确定嫌疑人具体身份的
     * - 记录明确说明当事人不在现场/无法联系等，现场无法确定具体身份的（通常为泛指对方/嫌疑人）
     *
     * 说明：该排除仅影响后置规则的“当事人完整性”判断，不改动原始文本。
     */
    private List<PartyExtractor.Party> applyDocExclusionRules(String text, List<PartyExtractor.Party> parties) {
        if (parties == null || parties.isEmpty()) {
            return parties;
        }
        boolean excludeUnrelatedReporter = isUnrelatedReporterCase(text);
        boolean excludeUnknownSuspectInCrime = isUnknownSuspectInCrimeCase(text);
        boolean excludeNotOnSceneOrUnreachable = isNotOnSceneOrUnreachableCase(text);

        List<PartyExtractor.Party> out = new ArrayList<>();
        for (PartyExtractor.Party p : parties) {
            if (p == null) {
                continue;
            }
            // 1) 排除：路见不平等“无直接关系报警人”
            if (excludeUnrelatedReporter && p.getType() == PartyExtractor.PartyType.REPORTER) {
                continue;
            }
            // 2) 排除：盗窃/损坏财物等案件中“嫌疑人身份无法确定”的泛指嫌疑人
            if (excludeUnknownSuspectInCrime
                    && p.getType() == PartyExtractor.PartyType.SUSPECT
                    && p.isGeneric()) {
                continue;
            }
            // 3) 排除：当事人不在现场/无法联系导致无法确定身份的泛指对象（对方/嫌疑人）
            if (excludeNotOnSceneOrUnreachable && p.isGeneric()) {
                String implied = p.getImpliedType();
                if ("对方".equals(implied) || "犯罪嫌疑人".equals(implied)) {
                    continue;
                }
                // 防御：历史版本可能生成“网友/收费方”等泛指对象，这类不应作为必然缺失主体
                if ("网友".equals(implied) || "收费方".equals(implied) || "中介".equals(implied)) {
                    continue;
                }
                String name = p.getName();
                if (name != null && (name.contains("对方") || name.contains("嫌疑人"))) {
                    continue;
                }
            }
            out.add(p);
        }
        return out;
    }

    private boolean isUnrelatedReporterCase(String text) {
        if (text == null) {
            return false;
        }
        // 《检测规则》：“仅因路见不平而报警”的报警人不纳入
        if (text.contains("路见不平")) {
            return true;
        }
        // 常见表达：路人报警/群众报警/围观群众报警
        boolean hasReporter = text.contains("报警");
        boolean roadPerson = text.contains("路人") || text.contains("群众") || text.contains("围观");
        return hasReporter && roadPerson;
    }

    private boolean isUnknownSuspectInCrimeCase(String text) {
        if (text == null) {
            return false;
        }
        // 《检测规则》：“盗窃、损坏财物等涉嫌违法犯罪警情中，无法确定嫌疑人具体身份的”不纳入
        boolean crime = containsAny(text, Arrays.asList("盗窃", "被盗", "偷盗", "损坏财物", "毁坏", "砸坏", "破坏", "盗取"));
        if (!crime) {
            return false;
        }
        boolean hasSuspect = text.contains("嫌疑人") || text.contains("违法犯罪嫌疑人") || text.contains("犯罪嫌疑人");
        if (!hasSuspect) {
            return false;
        }
        return containsAny(text, Arrays.asList("无法确定", "无法查明", "无法获知", "不详", "未查明"));
    }

    private boolean isNotOnSceneOrUnreachableCase(String text) {
        if (text == null) {
            return false;
        }
        // 《检测规则》：当事人不在现场/无法联系导致现场无法确定身份的，不纳入
        return containsAny(text, Arrays.asList(
                "不在现场", "未在现场", "不在场",
                "已离开", "已经离开", "离开现场",
                "外出", "外出不在", "不在家", "未在家",
                "无法联系", "联系不上", "无法取得联系", "无法取得当事人联系",
                "电话无人接听", "电话未接", "电话关机"
        ));
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String k : keywords) {
            if (k != null && !k.isEmpty() && text.contains(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从行数据中提取文本
     */
    private String extractTextFromRowData(java.util.Map<String, Object> rowData) {
        StringBuilder sb = new StringBuilder();
        for (Object value : rowData.values()) {
            if (value != null) {
                sb.append(value.toString()).append(" ");
            }
        }
        return sb.toString();
    }

    /**
     * 快速验证（简化版，用于初步筛查）
     */
    public static ValidationResult quickValidate(String result, String text) {
        // 1. 检查撤警情形
        List<String> withdrawalKeywords = Arrays.asList(
                "请求撤警", "要求撤警", "误报警", "不需要处理", "已协商解决",
                "重复警情", "副单", "无效警情", "无效报警",
                "无事实警情", "未发现报警情况", "现场无异常", "无警情发生");

        for (String keyword : withdrawalKeywords) {
            if (text.contains(keyword)) {
                return ValidationResult.valid("撤警情形，判定为[是]");
            }
        }

        // 2. 检查身份证号前缀
        if (IdCardLengthValidator.hasPrefixIdCard(text)) {
            return ValidationResult.invalid("检测到带前缀的身份证号，格式错误");
        }

        // 3. 检查当事人数量
        // 简单统计"报警人"和"对方"的出现次数
        int reporterCount = countOccurrences(text, "报警人", "报案人", "报称人");
        int otherPartyCount = countOccurrences(text, "对方", "对方当事人", "对方人员");

        if (reporterCount + otherPartyCount > 1) {
            // 多人案件，需要更详细的检查
            return ValidationResult.warning("涉及多个当事人，建议详细检查");
        }

        // 4. 检查信息缺失关键词
        List<String> missingKeywords = Arrays.asList(
                "未提供", "未登记", "拒绝提供", "拒绝登记", "不详", "身份信息不详");
        for (String keyword : missingKeywords) {
            if (text.contains(keyword)) {
                return ValidationResult.invalid("检测到信息缺失关键词: " + keyword);
            }
        }

        return ValidationResult.valid("快速验证通过");
    }

    /**
     * 统计关键词出现次数
     */
    private static int countOccurrences(String text, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            int index = 0;
            while ((index = text.indexOf(keyword, index)) != -1) {
                count++;
                index += keyword.length();
            }
        }
        return count;
    }

    /**
     * 验证上下文
     */
    @Data
    private static class ValidationContext {
        private String text;
        private IdCardLengthValidator.ExtractResult idCardValidationResult;
        private IdCardLengthValidator.InvalidLengthDetection invalidLengthDetection;
        private PassportValidator.ExtractResult passportValidationResult;
        private PassportValidator.DetectionResult suspiciousPassportDetection;
        private CompanyPartyValidator.ValidationResult companyValidationResult;
        private List<String> missingKeywords = new ArrayList<>();
        private List<PartyExtractor.Party> parties = new ArrayList<>();

        boolean hasMissingInfoKeywords() {
            return !missingKeywords.isEmpty();
        }

        String getMissingKeywords() {
            return String.join("、", missingKeywords);
        }

        void addMissingKeyword(String keyword) {
            if (!missingKeywords.contains(keyword)) {
                missingKeywords.add(keyword);
            }
        }

        boolean hasSuspiciousPassports() {
            return suspiciousPassportDetection != null && suspiciousPassportDetection.hasSuspicious();
        }
    }

    /**
     * 验证结果
     */
    @Data
    public static class ValidationResult {
        private final boolean valid;
        private final String message;
        private final ValidationLevel level;
        /**
         * 建议修正后的结果（可选）：
         * - null：不建议调整
         * - "是"/"否"：建议将最终结论修正为该值
         */
        private final String suggestedResult;

        public static ValidationResult valid(String message) {
            return new ValidationResult(true, message, ValidationLevel.SUCCESS, null);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, ValidationLevel.ERROR, null);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING, null);
        }

        public static ValidationResult suggest(String suggestedResult, String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING, suggestedResult);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public ValidationLevel getLevel() {
            return level;
        }
    }

    /**
     * 验证级别
     */
    public enum ValidationLevel {
        SUCCESS,   // 验证通过
        WARNING,   // 警告（可能有问题）
        ERROR      // 错误（确定有问题）
    }
}
