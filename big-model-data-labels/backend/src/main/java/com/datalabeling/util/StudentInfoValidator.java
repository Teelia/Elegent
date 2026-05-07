package com.datalabeling.util;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 在校学生信息后置验证器
 * <p>
 * 针对"在校学生信息完整性检查"的专用验证器，解决 DeepSeek 70B 长文本理解限制
 * </p>
 *
 * @author Data Labeling Team
 * @since 2026-01-28
 */
@Slf4j
@Component
public class StudentInfoValidator {

    // ========== 辍学/毕业关键词 ==========
    private static final List<String> DROPOUT_KEYWORDS = Arrays.asList(
            "辍学", "已辍学", "退学", "已退学",
            "毕业", "已毕业", "肄业"
    );

    // ========== 培训机构关键词（非在校学生）==========
    private static final List<String> TRAINING_INSTITUTION_KEYWORDS = Arrays.asList(
            "培训机构", "培训中心", "补习班", "辅导班",
            "函授", "自考", "成人教育", "夜大",
            "社会培训", "技能培训", "职业培训"
    );

    // ========== 学校简称映射表 ==========
    private static final List<SchoolAbbreviation> SCHOOL_ABBREVIATIONS = Arrays.asList(
            new SchoolAbbreviation("北师大", "北京师范大学"),
            new SchoolAbbreviation("安农", "安徽农业大学"),
            new SchoolAbbreviation("安大", "安徽大学"),
            new SchoolAbbreviation("合工大", "合肥工业大学"),
            new SchoolAbbreviation("中科大", "中国科学技术大学"),
            new SchoolAbbreviation("复旦", "复旦大学"),
            new SchoolAbbreviation("上交", "上海交通大学"),
            new SchoolAbbreviation("清华", "清华大学"),
            new SchoolAbbreviation("北大", "北京大学"),
            new SchoolAbbreviation("浙大", "浙江大学"),
            new SchoolAbbreviation("南大", "南京大学"),
            new SchoolAbbreviation("安工大", "安徽工业大学"),
            new SchoolAbbreviation("安理工", "安徽理工大学"),
            new SchoolAbbreviation("安医大", "安徽医科大学"),
            new SchoolAbbreviation("安师大", "安徽师范大学"),
            new SchoolAbbreviation("安财大", "安徽财经大学")
    );

    // ========== 学生身份关键词 ==========
    private static final List<String> STUDENT_KEYWORDS = Arrays.asList(
            "学生", "在校", "在读", "学籍",
            "年级", "班级", "班主任", "同学"
    );

    // ========== 教育阶段关键词 ==========
    private static final List<String> EDUCATION_LEVEL_KEYWORDS = Arrays.asList(
            "幼儿园", "小学", "初中", "高中", "中职", "技校",
            "高职", "专科", "本科", "研究生", "硕士", "博士"
    );

    // K12/基础教育学校：通常需要“省/市”等前置信息（大学/学院等不强制要求，避免误杀“清华大学/北京大学”等常见写法）
    private static final Pattern K12_SCHOOL_SUFFIX = Pattern.compile(".*(幼儿园|小学|中学|学校)$");

    /**
     * 验证在校学生信息完整性检查的判断结果
     *
     * @param result  LLM判断结果（"是"或"否"）
     * @param text    原始文本
     * @return 验证结果
     */
    public ValidationResult validate(String result, String text) {
        if (text == null || text.isEmpty()) {
            return ValidationResult.valid("文本为空，无法验证");
        }

        ValidationContext context = analyzeText(text);

        // 如果判断为"是"，进行严格验证
        if ("是".equals(result)) {
            return validatePositiveResult(context);
        } else {
            return validateNegativeResult(context);
        }
    }

    /**
     * 分析文本，提取关键信息
     */
    private ValidationContext analyzeText(String text) {
        ValidationContext context = new ValidationContext();
        context.setText(text);

        // 1. 检测辍学/毕业
        for (String keyword : DROPOUT_KEYWORDS) {
            if (text.contains(keyword)) {
                context.addDropoutKeyword(keyword);
                // 提取上下文，判断是谁辍学了
                extractDropoutContext(text, keyword, context);
            }
        }

        // 2. 检测培训机构
        for (String keyword : TRAINING_INSTITUTION_KEYWORDS) {
            if (text.contains(keyword)) {
                context.addTrainingInstitution(keyword);
            }
        }

        // 3. 检测学校名称
        extractSchoolNames(text, context);

        // 4. 检测学生身份
        for (String keyword : STUDENT_KEYWORDS) {
            if (text.contains(keyword)) {
                context.addStudentKeyword(keyword);
            }
        }

        // 5. 检测年龄（用于推断学生身份）
        extractAges(text, context);

        // 6. 检测6项关键信息
        checkSixRequirements(text, context);

        return context;
    }

    /**
     * 提取辍学上下文
     */
    private void extractDropoutContext(String text, String keyword, ValidationContext context) {
        // 查找关键词附近的文本，提取辍学人员姓名
        Pattern pattern = Pattern.compile("([^。，，]{2,4})[\\u4e00-\\u9fa5]{0,10}" + keyword);
        Matcher matcher = pattern.matcher(text);
        while (matcher.find()) {
            context.addDropoutPerson(matcher.group(1));
        }
    }

    /**
     * 提取学校名称
     */
    private void extractSchoolNames(String text, ValidationContext context) {
        // 匹配学校名称模式
        Pattern schoolPattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,15}(?:学校|学院|中学|小学|大学))");
        Matcher matcher = schoolPattern.matcher(text);
        while (matcher.find()) {
            String school = matcher.group(1);
            context.addSchoolName(school);

            // 检查是否为简称
            for (SchoolAbbreviation abbr : SCHOOL_ABBREVIATIONS) {
                if (school.contains(abbr.getAbbreviation()) || text.contains(abbr.getAbbreviation())) {
                    context.addAbbreviation(abbr.getAbbreviation(), abbr.getFullName());
                }
            }
        }

        // 检查是否缺少省/市/自治区等前置信息（仅针对基础教育学校）
        for (String school : context.getSchoolNames()) {
            if (!K12_SCHOOL_SUFFIX.matcher(school).matches()) {
                continue;
            }
            boolean hasRegion =
                school.contains("省") ||
                school.contains("市") ||
                school.contains("自治区") ||
                school.contains("特别行政区");
            if (!hasRegion) {
                context.addSchoolWithoutPrefix(school);
            }
        }
    }

    /**
     * 提取年龄信息
     */
    private void extractAges(String text, ValidationContext context) {
        Pattern agePattern = Pattern.compile("(\\d+)岁");
        Matcher matcher = agePattern.matcher(text);
        while (matcher.find()) {
            int age = Integer.parseInt(matcher.group(1));
            context.addAge(age);
            if (age >= 6 && age <= 18) {
                context.addStudentAge(age);
            }
        }
    }

    /**
     * 检查6项关键信息
     */
    private void checkSixRequirements(String text, ValidationContext context) {
        // 1. 姓名：通过中文姓名模式检测
        Pattern namePattern = Pattern.compile("([\\u4e00-\\u9fa5]{2,4})(?=[:,，]|身份证|护照)");
        if (namePattern.matcher(text).find()) {
            context.setHasName(true);
        }

        // 2. 身份证号：18位身份证号
        Pattern idPattern = Pattern.compile("(?:身份证|身份证号|居民身份证)[号码号]??\\s*[:：]?\\s*[1-9]\\d{5}\\d{8}(?:\\d{3}[\\dXx])");
        if (idPattern.matcher(text).find()) {
            context.setHasIdCard(true);
        }

        // 3. 学校全称：前面已提取
        context.setHasSchool(!context.getSchoolNames().isEmpty());

        // 4. 在读年级
        Pattern gradePattern = Pattern.compile("(?:年级|班级|届)[：:]?\\s*[\\u4e00-\\u9fa50-9]+");
        if (gradePattern.matcher(text).find()) {
            context.setHasGrade(true);
        }

        // 5. 院系/专业
        Pattern majorPattern = Pattern.compile("(?:专业|院系|学院)[：:]?\\s*[\\u4e00-\\u9fa5]{2,20}");
        if (majorPattern.matcher(text).find()) {
            context.setHasDepartmentMajor(true);
        }

        // 6. 联系方式
        Pattern contactPattern = Pattern.compile("(?:手机|电话|联系方式)[号码号]??\\s*[:：]?\\s*1[3-9]\\d{9}");
        if (contactPattern.matcher(text).find()) {
            context.setHasContact(true);
        }
    }

    /**
     * 验证"是"的判断（严格验证）
     */
    private ValidationResult validatePositiveResult(ValidationContext context) {
        // 0) 先判断是否“涉及在校学生”
        // 标签口径：不涉及在校学生 -> 判定为[是]（通过）
        boolean hasStudentSignals =
            !context.getStudentKeywords().isEmpty() ||
            !context.getStudentAges().isEmpty() ||
            !context.getSchoolNames().isEmpty() ||
            containsAny(context.getText(), EDUCATION_LEVEL_KEYWORDS);

        if (!hasStudentSignals) {
            return ValidationResult.valid("未检测到在校学生要素，判定为[是]合理");
        }

        List<String> errors = new ArrayList<>();

        // ========== 核心错误1：辍学学生被误判为在校生 ==========
        if (!context.getDropoutPersons().isEmpty()) {
            return ValidationResult.invalid(
                    String.format("检测到辍学学生[%s]，已辍学人员不属于在校学生，应判为[否]",
                            String.join("、", context.getDropoutPersons())));
        }

        if (!context.getDropoutKeywords().isEmpty()) {
            return ValidationResult.invalid(
                    String.format("检测到辍学/毕业关键词[%s]，表明相关人员已不在校，应判为[否]",
                            String.join("、", context.getDropoutKeywords())));
        }

        // ========== 核心错误2：培训机构学员被误判为在校生 ==========
        if (!context.getTrainingInstitutions().isEmpty()) {
            return ValidationResult.invalid(
                    String.format("检测到培训机构关键词[%s]，培训机构学员不属于在校学生，应判为[否]",
                            String.join("、", context.getTrainingInstitutions())));
        }

        // ========== 核心错误3：学校名称不完整 ==========
        if (!context.getSchoolsWithoutPrefix().isEmpty()) {
            // 检查是否所有学校都缺少省/市前缀
            if (context.getSchoolsWithoutPrefix().size() == context.getSchoolNames().size()) {
                errors.add(String.format("所有学校名称缺少省/市前缀[%s]，不满足'学校全称'要求",
                        String.join("、", context.getSchoolsWithoutPrefix())));
            } else {
                // 部分学校不完整
                errors.add(String.format("部分学校名称缺少省/市前缀[%s]",
                        String.join("、", context.getSchoolsWithoutPrefix())));
            }
        }

        // ========== 核心错误4：检测到学校简称 ==========
        if (!context.getAbbreviations().isEmpty()) {
            List<String> abbrList = new ArrayList<>(context.getAbbreviations().keySet());
            errors.add(String.format("检测到学校简称[%s]，不满足'学校全称'要求",
                    String.join("、", abbrList)));
        }

        // ========== 核心错误5：6项信息不完整 ==========
        List<String> missingItems = new ArrayList<>();
        if (!context.isHasName()) missingItems.add("姓名");
        if (!context.isHasIdCard()) missingItems.add("身份证号");
        if (!context.isHasSchool()) missingItems.add("学校全称");
        if (!context.isHasGrade()) missingItems.add("在读年级");
        if (!context.isHasDepartmentMajor()) {
            // 检查是否为小学/初中/高中
            boolean isBasicEducation = false;
            for (String kw : EDUCATION_LEVEL_KEYWORDS) {
                if (kw.equals("小学") || kw.equals("初中") || kw.equals("高中")) {
                    if (context.getText().contains(kw)) {
                        isBasicEducation = true;
                        break;
                    }
                }
            }
            if (!isBasicEducation) {
                missingItems.add("院系/专业");
            }
        }
        if (!context.isHasContact()) missingItems.add("联系方式");

        if (!missingItems.isEmpty()) {
            errors.add(String.format("缺少必需信息项：%s", String.join("、", missingItems)));
        }

        // ========== 核心错误6：年龄推断 ==========
        if (!context.getStudentAges().isEmpty() && context.getSchoolNames().isEmpty()) {
            // 有6-18岁人员，但未提取到学校信息
            return ValidationResult.invalid(
                    String.format("检测到%d个6-18岁人员[%s]，但未提取到学校信息，应判为[否]",
                            context.getStudentAges().size(),
                            context.getStudentAges().toString()));
        }

        // 生成验证结果
        if (!errors.isEmpty()) {
            return ValidationResult.invalid(
                    String.format("判定为[是]但发现%d个问题：%s",
                            errors.size(),
                            String.join("; ", errors)));
        }

        return ValidationResult.valid("规则验证通过，判定结果正确");
    }

    private boolean containsAny(String text, List<String> keywords) {
        if (text == null || text.isEmpty() || keywords == null || keywords.isEmpty()) {
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
     * 验证"否"的判断（检查误判风险）
     */
    private ValidationResult validateNegativeResult(ValidationContext context) {
        // 检查是否可能误判
        boolean hasStudent = !context.getStudentKeywords().isEmpty() || !context.getStudentAges().isEmpty();
        boolean hasSchool = !context.getSchoolNames().isEmpty();
        boolean noDropout = context.getDropoutPersons().isEmpty() && context.getDropoutKeywords().isEmpty();
        boolean noTraining = context.getTrainingInstitutions().isEmpty();
        boolean infoComplete = context.isHasName() && context.isHasIdCard()
                && context.isHasSchool() && context.isHasGrade() && context.isHasContact();

        if (hasStudent && hasSchool && noDropout && noTraining && infoComplete) {
            // 所有信息完整，但判断为"否"，可能误判
            return ValidationResult.warning(
                    "检测到在校学生信息且信息完整，存在误判风险，建议复核");
        }

        return ValidationResult.valid("规则验证通过，判定结果正确");
    }

    /**
     * 学校简称映射
     */
    @Data
    private static class SchoolAbbreviation {
        private final String abbreviation;
        private final String fullName;

        public SchoolAbbreviation(String abbreviation, String fullName) {
            this.abbreviation = abbreviation;
            this.fullName = fullName;
        }
    }

    /**
     * 验证上下文
     */
    @Data
    private static class ValidationContext {
        private String text;
        private List<String> dropoutKeywords = new ArrayList<>();
        private List<String> dropoutPersons = new ArrayList<>();
        private List<String> trainingInstitutions = new ArrayList<>();
        private List<String> schoolNames = new ArrayList<>();
        private List<String> schoolsWithoutPrefix = new ArrayList<>();
        private java.util.Map<String, String> abbreviations = new java.util.HashMap<>();
        private List<String> studentKeywords = new ArrayList<>();
        private List<Integer> ages = new ArrayList<>();
        private List<Integer> studentAges = new ArrayList<>();

        // 6项信息
        private boolean hasName = false;
        private boolean hasIdCard = false;
        private boolean hasSchool = false;
        private boolean hasGrade = false;
        private boolean hasDepartmentMajor = false;
        private boolean hasContact = false;

        void addDropoutKeyword(String keyword) {
            if (!dropoutKeywords.contains(keyword)) {
                dropoutKeywords.add(keyword);
            }
        }

        void addDropoutPerson(String person) {
            if (!dropoutPersons.contains(person)) {
                dropoutPersons.add(person);
            }
        }

        void addTrainingInstitution(String institution) {
            if (!trainingInstitutions.contains(institution)) {
                trainingInstitutions.add(institution);
            }
        }

        void addSchoolName(String school) {
            if (!schoolNames.contains(school)) {
                schoolNames.add(school);
            }
        }

        void addSchoolWithoutPrefix(String school) {
            if (!schoolsWithoutPrefix.contains(school)) {
                schoolsWithoutPrefix.add(school);
            }
        }

        void addAbbreviation(String abbr, String fullName) {
            abbreviations.put(abbr, fullName);
        }

        void addStudentKeyword(String keyword) {
            if (!studentKeywords.contains(keyword)) {
                studentKeywords.add(keyword);
            }
        }

        void addAge(int age) {
            ages.add(age);
        }

        void addStudentAge(int age) {
            studentAges.add(age);
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

        public static ValidationResult valid(String message) {
            return new ValidationResult(true, message, ValidationLevel.SUCCESS);
        }

        public static ValidationResult invalid(String message) {
            return new ValidationResult(false, message, ValidationLevel.ERROR);
        }

        public static ValidationResult warning(String message) {
            return new ValidationResult(true, message, ValidationLevel.WARNING);
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
