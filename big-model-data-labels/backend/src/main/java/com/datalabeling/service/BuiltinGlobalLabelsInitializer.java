package com.datalabeling.service;

import com.datalabeling.entity.Label;
import com.datalabeling.entity.SystemPrompt;
import com.datalabeling.repository.LabelRepository;
import com.datalabeling.repository.SystemPromptRepository;
import com.datalabeling.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 内置全局标签初始化器
 *
 * <p>目标：</p>
 * <ul>
 *   <li>缺失则创建 2 个系统内置全局标签（由管理员创建，所有用户可见可选）</li>
 *   <li>为内置标签补齐预处理配置（rule_then_llm）与二次强化配置</li>
 * </ul>
 *
 * <p>说明：该初始化器应保持幂等，多次启动不应产生重复数据。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BuiltinGlobalLabelsInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final LabelRepository labelRepository;
    private final SystemPromptRepository systemPromptRepository;

    @Override
    public void run(ApplicationArguments args) {
        try {
            List<Integer> adminIds = userRepository.findAdminUserIds();
            if (adminIds == null || adminIds.isEmpty()) {
                log.warn("未找到管理员用户，跳过内置全局标签初始化");
                return;
            }

            Integer adminId = adminIds.stream().min(Comparator.naturalOrder()).orElse(adminIds.get(0));

            Integer policeEnhancementPromptId = ensureEnhancementPrompt(
                adminId,
                "police_personnel_enhancement_v1",
                "涉警当事人二次强化分析",
                buildPolicePersonnelEnhancementTemplate()
            );
            Integer studentEnhancementPromptId = ensureEnhancementPrompt(
                adminId,
                "student_info_enhancement_v1",
                "在校学生二次强化分析",
                buildStudentEnhancementTemplate()
            );

            ensureBuiltinLabel(
                adminId,
                "涉警当事人信息完整性检查",
                buildPolicePersonnelLabelDescription(),
                buildPolicePersonnelPreprocessorConfigJson(),
                buildEnhancementConfigJson(60, policeEnhancementPromptId), // L4: 降低阈值从75到60
                "person_info_integrity"
            );

            ensureBuiltinLabel(
                adminId,
                "在校学生信息完整性检查",
                buildStudentLabelDescription(),
                buildStudentPreprocessorConfigJson(),
                buildEnhancementConfigJson(70, studentEnhancementPromptId),
                "person_info_integrity"
            );

            log.info("内置全局标签初始化完成（adminId={}）", adminId);
        } catch (Exception e) {
            // 初始化不应阻断主流程，避免因存量环境差异导致服务无法启动
            log.warn("内置全局标签初始化失败（已跳过）：{}", e.getMessage(), e);
        }
    }

    private Integer ensureEnhancementPrompt(Integer adminId, String code, String name, String template) {
        return systemPromptRepository.findByCode(code)
            .map(SystemPrompt::getId)
            .orElseGet(() -> {
                SystemPrompt prompt = SystemPrompt.builder()
                    .userId(adminId)
                    .name(name)
                    .code(code)
                    .promptType(SystemPrompt.PromptType.ENHANCEMENT)
                    .template(template)
                    .variables(Arrays.asList(
                        "label_name",
                        "label_description",
                        "row_data_json",
                        "initial_result",
                        "initial_confidence",
                        "initial_reasoning",
                        "validation_result"
                    ))
                    .isSystemDefault(false)
                    .isActive(true)
                    .build();
                return systemPromptRepository.save(prompt).getId();
            });
    }

    private void ensureBuiltinLabel(Integer adminId,
                                    String name,
                                    String description,
                                    String preprocessorConfigJson,
                                    String enhancementConfigJson,
                                    String builtinCategory) {
        List<Label> versions = labelRepository.findByUserIdAndNameOrderByVersionDesc(adminId, name);
        if (versions != null && !versions.isEmpty()) {
            // 仅做必要补齐，避免覆盖已有配置
            Label latest = versions.get(0);
            boolean changed = false;

            if (latest.getBuiltinLevel() == null || latest.getBuiltinLevel().isEmpty()) {
                latest.setBuiltinLevel(Label.BuiltinLevel.SYSTEM);
                changed = true;
            }
            if (latest.getBuiltinCategory() == null || latest.getBuiltinCategory().isEmpty()) {
                latest.setBuiltinCategory(builtinCategory);
                changed = true;
            }
            if (latest.getPreprocessingMode() == null || latest.getPreprocessingMode().isEmpty()) {
                latest.setPreprocessingMode(Label.PreprocessingMode.RULE_THEN_LLM);
                changed = true;
            }
            if (latest.getPreprocessorConfig() == null || latest.getPreprocessorConfig().trim().isEmpty()) {
                latest.setPreprocessorConfig(preprocessorConfigJson);
                changed = true;
            }
            if (latest.getIncludePreprocessorInPrompt() == null) {
                latest.setIncludePreprocessorInPrompt(true);
                changed = true;
            }
            if (latest.getEnableEnhancement() == null || !latest.getEnableEnhancement()) {
                latest.setEnableEnhancement(true);
                changed = true;
            }
            if (latest.getEnhancementConfig() == null || latest.getEnhancementConfig().trim().isEmpty()) {
                latest.setEnhancementConfig(enhancementConfigJson);
                changed = true;
            }
            if (latest.getIsActive() == null) {
                latest.setIsActive(true);
                changed = true;
            }

            if (changed) {
                labelRepository.save(latest);
            }
            return;
        }

        Label label = Label.builder()
            .userId(adminId)
            .name(name)
            .version(1)
            .scope(Label.Scope.GLOBAL)
            .type(Label.Type.CLASSIFICATION)
            .description(description)
            .focusColumns(Collections.emptyList())
            .preprocessingMode(Label.PreprocessingMode.RULE_THEN_LLM)
            .preprocessorConfig(preprocessorConfigJson)
            .includePreprocessorInPrompt(true)
            .enableEnhancement(true)
            .enhancementConfig(enhancementConfigJson)
            .isActive(true)
            .builtinLevel(Label.BuiltinLevel.SYSTEM)
            .builtinCategory(builtinCategory)
            .build();

        labelRepository.save(label);
    }

    private String buildEnhancementConfigJson(int triggerConfidence, Integer promptId) {
        Map<String, Object> cfg = new HashMap<>();
        cfg.put("triggerConfidence", triggerConfidence);
        if (promptId != null) {
            cfg.put("promptId", promptId);
        }
        return toJson(cfg);
    }

    /**
     * 涉警当事人预处理配置（优化版 v3）
     * 优化内容：
     * 1. 严格18位身份证号验证（拒绝15位、17位、19位等非标准长度）
     * 2. 增加信息缺失关键词检测（包含"匿名"）
     * 3. 保留直接合格关键词检测
     * 4. 新增隐含当事人提取
     */
    private String buildPolicePersonnelPreprocessorConfigJson() {
        Map<String, Object> cfg = new HashMap<>();

        // ========== L1: 基础规则增强 - 严格18位验证 ==========
        Map<String, Object> numberIntent = new HashMap<>();
        numberIntent.put("entity", "id_card");
        numberIntent.put("task", "exists");

        Map<String, Object> policy = new HashMap<>();
        policy.put("idChecksumInvalidIsInvalid", false); // 不检测校验位
        policy.put("id15IsValid", false); // ❌ 严格模式：15位不合格
        policy.put("idNonStandardIsInvalid", true); // ✅ 新增：非18位均不合格
        policy.put("id18XIsInvalid", false); // 18位带X仍有效
        policy.put("defaultMaskedOutput", true);
        numberIntent.put("policy", policy);
        cfg.put("number_intent", numberIntent);

        // ========== L2: 预处理优化 - 增强提取器 ==========
        // 保留原有提取器 + 新增信息缺失检测
        cfg.put("extractors", Arrays.asList(
            "passport",           // 护照号提取
            "keyword_match",      // 直接合格关键词
            "missing_info",       // 信息缺失检测
            "implied_parties"     // ✅ v3新增：隐含当事人提取
        ));

        // 护照提取器配置
        Map<String, Object> passportOpt = new HashMap<>();
        passportOpt.put("include_cn_only", false);

        // 直接合格关键词（保持不变）
        Map<String, Object> kwOpt = new HashMap<>();
        kwOpt.put("keywords", Arrays.asList(
            "请求撤警", "误报警", "不需要处理", "已协商解决",
            "重复警情", "副单", "无效警情", "无效报警",
            "未发现报警情况", "现场无异常", "无警情发生"
        ));
        kwOpt.put("matchType", "any");

        // ✅ v3增强：信息缺失检测器配置（添加"匿名"等强制推翻关键词）
        Map<String, Object> missingOpt = new HashMap<>();
        missingOpt.put("negativeKeywords", Arrays.asList(
            // 强制推翻关键词（v3新增）
            "匿名", "匿名报警", "匿名报警人",
            "拒绝", "拒绝登记", "拒绝提供", "拒不透露",
            "未提供", "未登记", "无法", "无法登记", "无法提供",
            "研判无果", "查询无果", "系统查询无果",
            "不详", "记不清", "身份证号记不住", "身份信息不详",
            "未获取到", "无法核实", "无法核实身份"
        ));
        missingOpt.put("affectsJudgment", true); // 直接影响判断
        missingOpt.put("forceOverride", true);   // ✅ v3新增：强制推翻标志
        missingOpt.put("highlightInResult", true); // 在结果中高亮显示

        // ✅ v3新增：隐含当事人提取器配置
        Map<String, Object> impliedOpt = new HashMap<>();
        impliedOpt.put("extractTypes", Arrays.asList(
            "child",      // 小孩/儿童
            "agent",      // 中介
            "merchant",   // 收费方/店家/物业
            "net_friend", // 网友
            "suspect"     // 犯罪嫌疑人
        ));
        impliedOpt.put("strictMode", true); // 严格模式：找不到身份证号就标记缺失

        Map<String, Map<String, Object>> extractorOptions = new HashMap<>();
        extractorOptions.put("passport", passportOpt);
        extractorOptions.put("keyword_match", kwOpt);
        extractorOptions.put("missing_info", missingOpt); // ✅ v3增强
        extractorOptions.put("implied_parties", impliedOpt); // ✅ v3新增
        cfg.put("extractorOptions", extractorOptions);

        return toJson(cfg);
    }

    private String buildStudentPreprocessorConfigJson() {
        Map<String, Object> cfg = new HashMap<>();
        // 核心：使用 number_intent（在校学生口径：默认要求18位新身份证）
        Map<String, Object> numberIntent = new HashMap<>();
        numberIntent.put("entity", "id_card");
        numberIntent.put("task", "exists");

        Map<String, Object> policy = new HashMap<>();
        policy.put("idChecksumInvalidIsInvalid", false);
        policy.put("id15IsValid", false); // 在校学生口径：15位不计入有效
        policy.put("id18XIsInvalid", false);
        policy.put("defaultMaskedOutput", true);
        numberIntent.put("policy", policy);
        cfg.put("number_intent", numberIntent);

        // 补充提取器：学校信息、关键词
        cfg.put("extractors", Arrays.asList("school_info", "keyword_match"));

        Map<String, Object> schoolOpt = new HashMap<>();
        schoolOpt.put("exclude_training", true);

        Map<String, Object> kwOpt = new HashMap<>();
        kwOpt.put("keywords", Arrays.asList(
            "在校学生", "学生", "学号", "年级",
            "幼儿园", "小学", "中学", "初中", "高中", "中职", "技校", "学院", "大学"
        ));
        kwOpt.put("matchType", "any");

        Map<String, Map<String, Object>> extractorOptions = new HashMap<>();
        extractorOptions.put("school_info", schoolOpt);
        extractorOptions.put("keyword_match", kwOpt);
        cfg.put("extractorOptions", extractorOptions);

        return toJson(cfg);
    }

    /**
     * 涉警当事人标签描述（优化版 v3）
     * 优化内容：
     * 1. 明确18位严格验证要求
     * 2. 扩展涉警当事人识别范围（含隐含当事人）
     * 3. 强调信息缺失关键词的判定意义
     * 4. 规范直接合格条件使用
     * 5. 针对 DeepSeek 70B 限制，明确"所有涉警当事人"概念
     */
    private String buildPolicePersonnelLabelDescription() {
        return "检查警情中涉警当事人身份信息是否完整（身份证号/护照号）。\n"
            + "\n"
            + "========== 直接合格情形 ==========\n"
            + "若文本明确表示撤警/无效警情/无事实（如：请求撤警、误报警、重复警情、无效警情、\n"
            + "未发现报警情况等），且无需进行后续案件处理的，可直接判定为\"是\"。\n"
            + "⚠️ 注意：存在纠纷但协商解决的，仍需检查当事人信息完整性。\n"
            + "\n"
            + "========== 涉警当事人范围（重要）==========\n"
            + "【纳入】\n"
            + "• 纠纷双方、侵权行为人、受害人、被侵权人、违约方、合同相对方等与警情事实直接相关人员\n"
            + "• 特殊注意（隐含当事人）：\n"
            + "  - 小孩/儿童（如被咬伤、被打、被推搡等）\n"
            + "  - 中介（如中介纠纷、中介介绍工作等）\n"
            + "  - 收费方/店家/物业/商家（如停车费纠纷、购物纠纷等）\n"
            + "  - 网友（如网友见面、微信网友、抖音网友等）\n"
            + "  - 犯罪嫌疑人/侵权人（从描述推断的）\n"
            + "  - 其他从纠纷上下文可推断的涉警人员\n"
            + "\n"
            + "【排除】\n"
            + "• 处警民警/辅警、围观群众、与警情无关的路人、证人等\n"
            + "\n"
            + "========== 合格标准（严格）==========\n"
            + "✅ 所有涉警当事人均提供有效身份号码（缺一不可）\n"
            + "✅ 身份证号必须严格18位（前17位为数字，第18位为数字或X）\n"
            + "✅ 护照号可作为替代（中国护照或外国护照，需验证格式）\n"
            + "✅ 若同一姓名出现多次，任一处出现有效身份证号/护照号视为该人员信息完整\n"
            + "\n"
            + "========== 不合格标准（强制）==========\n"
            + "❌ 任一涉警当事人身份证号缺失/不完整/格式错误\n"
            + "❌ 身份证号非18位（17位、19位、15位等均不合格）\n"
            + "❌ 无法以护照号等有效信息补齐\n"
            + "❌ 出现以下关键词直接判定为[否]：\n"
            + "   - [匿名]、[匿名报警] → 无身份信息\n"
            + "   - [拒绝登记]、[拒绝提供]、[无法登记] → 信息缺失\n"
            + "   - [研判无果]、[查询无果]、[无果] → 无法获取信息\n"
            + "   - [不详]、[记不清]、[身份信息不详] → 信息不完整\n"
            + "\n"
            + "========== 判定步骤（严格遵循）==========\n"
            + "1. 识别所有涉警当事人（包括明确标注的和隐含的）\n"
            + "2. 对每个当事人提取身份证号/护照号\n"
            + "3. 严格验证身份证号格式（必须18位）\n"
            + "4. 检查是否存在信息缺失关键词（检测到直接判否）\n"
            + "5. 所有人信息完整 → \"是\"；任一人信息缺失 → \"否\"\n"
            + "\n"
            + "========== 重要提示（针对 DeepSeek 70B 限制）==========\n"
            + "• 你需要严格检查\"所有\"涉警当事人，而不仅仅是报警人\n"
            + "• 不要因为\"报警人信息完整\"就忽略其他当事人\n"
            + "• 隐含当事人（小孩、中介、网友等）同样需要完整信息\n"
            + "• 如果文本提及某人但未提供身份证号，应判为[否]";
    }

    /**
     * 在校学生标签描述（优化版 v2）
     * 优化内容：
     * 1. 明确"辍学"学生不属于在校生（解决歧义）
     * 2. 增加"学校全称"验证标准
     * 3. 明确6项信息的详细要求
     * 4. 增加年龄推断规则
     * 5. 针对 DeepSeek 70B 限制，优化结构化输出
     */
    private String buildStudentLabelDescription() {
        return "检查警情中是否涉及在校学生，以及在校学生身份信息是否完整。\n"
            + "\n"
            + "========== 在校学生定义（重要）==========\n"
            + "【在校学生】国家教育行政部门批准设立的教育机构注册的全日制在读学生。\n"
            + "包括：幼儿园、小学、初中、高中、中职、高职、高校（本科/研究生）在读学生。\n"
            + "\n"
            + "【明确不属于在校学生】\n"
            + "1. 辍学学生：已明确标注\"辍学\"、\"退学\"的学生不属于在校生\n"
            + "2. 毕业学生：已标注\"毕业\"、\"肄业\"的学生不属于在校生\n"
            + "3. 成人教育：成人教育、函授、自考、夜大等非全日制学生\n"
            + "4. 培训机构：社会培训机构、补习班、辅导班学员\n"
            + "\n"
            + "【特殊情况】\n"
            + "- 休学学生：保留学籍的休学学生仍属于在校生（但需标注\"休学\"状态）\n"
            + "- 关于\"标明学籍\"：需要记录学生曾在哪所学校就读，不等于仍在读\n"
            + "\n"
            + "========== 信息完整性标准（6项必需）==========\n"
            + "所有在校生必须提供以下6项信息（缺一不可）：\n"
            + "1. 姓名：中文姓名全称\n"
            + "2. 身份证号：必须18位（前17位数字+第18位数字或X）\n"
            + "3. 学校全称：必须包含省/市信息（如\"安徽省XX中学\"）\n"
            + "   ❌ 简称不合格：如\"北师大\"、\"安农\"、\"桐城市第八中学\"（缺省）\n"
            + "   ✅ 正确示例：安徽省桐城市第八中学、安徽农业大学\n"
            + "4. 在读年级：如\"初一\"、\"2023届\"、\"大二\"等\n"
            + "5. 院系/专业：\n"
            + "   - 小学/初中/高中：不要求\n"
            + "   - 中职/高职/专科/本科/研究生：必须提供\n"
            + "6. 联系方式：手机号码（11位）\n"
            + "\n"
            + "========== 判断规则（严格遵循）==========\n"
            + "【第一步】识别所有人员，判断是否属于在校生\n"
            + "- 明确标注\"学生\"且未\"辍学\" → 在校生\n"
            + "- 年龄6-18岁且未标注\"非学生\" → 推断为在校生\n"
            + "- 标注\"辍学\"、\"毕业\" → 不属于在校生\n"
            + "- 标注\"培训机构\"、\"函授\" → 不属于在校生\n"
            + "\n"
            + "【第二步】检查在校生的6项信息是否完整\n"
            + "- 对每个在校生逐项检查：姓名、身份证号、学校全称、年级、院系/专业、联系方式\n"
            + "- 如果有任何一项缺失，判为[否]\n"
            + "\n"
            + "【第三步】给出最终判断\n"
            + "- 不涉及在校生 → \"是\"\n"
            + "- 涉及在校生且所有在校生信息完整 → \"是\"\n"
            + "- 涉及在校生但有任意信息缺失 → \"否\"\n"
            + "\n"
            + "========== 重要提示（针对 DeepSeek 70B 限制）==========\n"
            + "• 必须逐个人员检查，不要遗漏任何一个\n"
            + "• 辍学/毕业的学生不属于在校生，不要误判\n"
            + "• 学校名称必须包含省/市，简称或缺少前缀都是不合格\n"
            + "• 对于高校/中职学生，必须检查院系/专业信息\n"
            + "• 6-18岁人员如果未明确标注\"非学生\"，应推断为在校生";
    }

    /**
     * 涉警当事人二次强化模板（优化版 v3）
     * 优化内容：
     * 1. 增加强制推翻关键词检查（匿名等）
     * 2. 强调"所有涉警当事人"完整性
     * 3. 增加18位严格验证检查
     * 4. 扩大隐含当事人识别检查
     * 5. 针对 DeepSeek 70B 限制简化逻辑
     */
    private String buildPolicePersonnelEnhancementTemplate() {
        return "你是数据质量审核专家，请对以下[涉警当事人信息完整性检查]的初步结果进行严格二次验证。\\n"
            + "\\n"
            + "========== 标签定义 ==========\\n"
            + "标签名称：{{label_name}}\\n"
            + "标签规则：{{label_description}}\\n"
            + "\\n"
            + "========== 原始数据 ==========\\n"
            + "{{row_data_json}}\\n"
            + "\\n"
            + "========== 初步分析结果 ==========\\n"
            + "判断：{{initial_result}}\\n"
            + "置信度：{{initial_confidence}}%\\n"
            + "推理：{{initial_reasoning}}\\n"
            + "\\n"
            + "{{#if validation_result}}\\n"
            + "========== 规则验证结果 ==========\\n"
            + "{{validation_result}}\\n"
            + "{{/if}}\\n"
            + "\\n"
            + "========== 二次审核重点（严格检查）==========\\n"
            + "\\n"
            + "[核心原则] 所有涉警当事人信息必须完整，缺一不可！\\n"
            + "\\n"
            + "[1] 强制推翻检查（最高优先级）：\\n"
            + "- 出现以下关键词必须直接判[否]：\\n"
            + "  - [匿名]、[匿名报警] → 无身份信息\\n"
            + "  - [拒绝登记]、[拒绝提供]、[无法登记] → 信息缺失\\n"
            + "  - [研判无果]、[查询无果]、[无果] → 无法获取信息\\n"
            + "  - [不详]、[记不清]、[身份信息不详] → 信息不完整\\n"
            + "- 即使初步判[是]，只要出现上述关键词，必须修正为[否]\\n"
            + "\\n"
            + "[2] 涉警当事人完整性检查（核心）：\\n"
            + "- 不仅识别明确标注的[报警人]、[对方当事人]\\n"
            + "- 还需识别隐含当事人（必须检查）：\\n"
            + "  - 小孩/儿童（被咬伤、被打、被推搡等）\\n"
            + "  - 中介（中介纠纷、中介介绍工作等）\\n"
            + "  - 收费方/店家/物业/商家（停车费、购物纠纷等）\\n"
            + "  - 网友（网友见面、微信网友、抖音网友等）\\n"
            + "  - 犯罪嫌疑人/侵权人（从描述推断的）\\n"
            + "  - 对方/纠纷对方（未明确身份的）\\n"
            + "- 关键词：纠纷、矛盾、中介、收费、网友、对方等\\n"
            + "- 只要有任一当事人无身份证号，就必须判[否]\\n"
            + "\\n"
            + "[3] 身份证号格式严格验证：\\n"
            + "- 必须严格18位（前17位数字 + 第18位数字或X）\\n"
            + "- 19位、17位、15位等任何非18位号码均为格式错误\\n"
            + "- 电话号码（11位）不是身份证号\\n"
            + "\\n"
            + "[4] 豁免条件正确使用：\\n"
            + "- [撤警]、[无效警情]仅当明确表示无案件、无纠纷时可直接判定为[是]\\n"
            + "- 存在纠纷但协商解决的，仍需检查所有涉警当事人信息\\n"
            + "\\n"
            + "========== 重要提示 ==========\\n"
            + "- 规则提取结果使用确定性逻辑，可信度高\\n"
            + "- 优先依赖规则提取的证据，不要凭空臆测\\n"
            + "- 规则提取与初步判断不一致时，以规则为准\\n"
            + "- 判定[是]前，必须确认：所有涉警当事人都有有效身份信息\\n"
            + "- 不要因为\"报警人信息完整\"就忽略其他当事人\\n"
            + "\\n"
            + "========== 输出要求 ==========\\n"
            + "输出JSON格式：\\n"
            + "{\\n"
            + "  \"final_result\": \"维持原判\"或\"修正为是/否\",\\n"
            + "  \"final_confidence\": 0-100,\\n"
            + "  \"validation_notes\": \"二次审核发现的问题或确认的理由（需具体说明检查了哪些方面）\",\\n"
            + "  \"should_adjust\": true/false,\\n"
            + "  \"adjustment_reason\": \"如果需要修正，详细说明原因\"\\n"
            + "}";
    }

    /**
     * 在校学生二次强化模板（优化版 v2）
     * 优化内容：
     * 1. 明确5个核心错误检查点
     * 2. 增加"辍学"学生检测（最高优先级）
     * 3. 增加学校名称完整性检查
     * 4. 增加年龄推断检查
     * 5. 针对 DeepSeek 70B 限制，简化逻辑并结构化输出
     */
    private String buildStudentEnhancementTemplate() {
        return "你是数据质量审核专家，请对以下[在校学生信息完整性检查]的初步结果进行严格二次验证。\\n"
            + "\\n"
            + "========== 标签定义 ==========\\n"
            + "标签名称：{{label_name}}\\n"
            + "标签规则：{{label_description}}\\n"
            + "\\n"
            + "========== 原始数据 ==========\\n"
            + "{{row_data_json}}\\n"
            + "\\n"
            + "========== 初步分析结果 ==========\\n"
            + "判断：{{initial_result}}\\n"
            + "置信度：{{initial_confidence}}%\\n"
            + "推理：{{initial_reasoning}}\\n"
            + "\\n"
            + "{{#if validation_result}}\\n"
            + "========== 规则验证结果 ==========\\n"
            + "{{validation_result}}\\n"
            + "{{/if}}\\n"
            + "\\n"
            + "========== 二次审核重点（严格检查5个核心错误）==========\\n"
            + "\\n"
            + "[核心错误1：辍学学生误判 - 最高优先级]\\n"
            + "检查是否存在以下关键词：\\n"
            + "- [辍学]、[退学]、[已辍学]、[已退学] → 这些人员不属于在校生\\n"
            + "- [毕业]、[已毕业]、[肄业] → 这些人员不属于在校生\\n"
            + "\\n"
            + "判定规则：\\n"
            + "- 如果警情中只涉及辍学/毕业学生 → 不涉及在校生 → 判[是]\\n"
            + "- 如果警情中同时有在校生和辍学生 → 需检查在校生信息完整性\\n"
            + "- 即使初步判[是]，只要涉及辍学生仍在校读书误判，必须修正为[否]\\n"
            + "\\n"
            + "[核心错误2：学校名称不完整]\\n"
            + "检查学校名称是否完整：\\n"
            + "- ❌ 简称不合格：[北师大]、[安农]、[安大]、[合工大]、[中科大]等\\n"
            + "- ❌ 缺少省/市不合格：[桐城市第八中学]缺少[安徽省]\\n"
            + "- ❌ 只说[某学校]但没有完整校名\\n"
            + "\\n"
            + "判定规则：\\n"
            + "- 如果所有学校名称都不完整 → 判[否]\\n"
            + "- 如果部分学校不完整 → 检查不完整学校的在校生信息是否足够判定\\n"
            + "\\n"
            + "[核心错误3：6项信息缺失]\\n"
            + "逐项检查每个在校生的6项信息：\\n"
            + "1. 姓名\\n"
            + "2. 身份证号（必须18位）\\n"
            + "3. 学校全称（必须含省/市）\\n"
            + "4. 在读年级\\n"
            + "5. 院系/专业（小学/初中/高中可不要求，中职/高校必须）\\n"
            + "6. 联系方式\\n"
            + "\\n"
            + "判定规则：\\n"
            + "- 如果有在校生缺少任意一项信息 → 判[否]\\n"
            + "\\n"
            + "[核心错误4：未识别学生身份]\\n"
            + "检查6-18岁人员是否被正确识别：\\n"
            + "- 如果6-18岁人员未标注[学生]、[在校]等关键词\\n"
            + "- 且未标注[辍学]、[工作]、[非学生]等排除关键词\\n"
            + "- 则应推断为在校生\\n"
            + "\\n"
            + "判定规则：\\n"
            + "- 有6-18岁人员但未识别为在校生 → 可能漏检 → 判[否]\\n"
            + "\\n"
            + "[核心错误5：培训机构误判]\\n"
            + "检查是否存在培训机构关键词：\\n"
            + "- [培训机构]、[培训中心]、[补习班]、[辅导班]\\n"
            + "- [函授]、[自考]、[成人教育]、[夜大]\\n"
            + "- [社会培训]、[技能培训]\\n"
            + "\\n"
            + "判定规则：\\n"
            + "- 如果涉及培训机构学员 → 不属于在校生 → 不影响判断（除非同时有在校生信息不完整）\\n"
            + "\\n"
            + "========== 输出要求 ==========\\n"
            + "输出JSON格式：\\n"
            + "{\\n"
            + "  \\\"final_result\\\": \\\"维持原判\\\"或\\\"修正为是/否\\\",\\n"
            + "  \\\"final_confidence\\\": 0-100,\\n"
            + "  \\\"validation_notes\\\": \\\"详细说明发现的问题或确认的理由（需具体说明检查了哪些方面）\\\",\\n"
            + "  \\\"should_adjust\\\": true/false,\\n"
            + "  \\\"adjustment_reason\\\": \\\"如果需要修正，详细说明原因\\\",\\n"
            + "  \\\"checked_items\\\": {\\n"
            + "    \\\"dropout_check\\\": \\\"是否检查了辍学/毕业关键词（是/否）\\\",\\n"
            + "    \\\"school_name_check\\\": \\\"是否检查了学校名称完整性（是/否）\\\",\\n"
            + "    \\\"info_completeness_check\\\": \\\"是否检查了6项信息（是/否）\\\",\\n"
            + "    \\\"age_inference_check\\\": \\\"是否检查了6-18岁人员（是/否）\\\",\\n"
            + "    \\\"training_institution_check\\\": \\\"是否检查了培训机构（是/否）\\\"\\n"
            + "  }\\n"
            + "}";
    }

    private String toJson(Object obj) {
        try {
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(obj);
        } catch (Exception e) {
            return String.valueOf(obj);
        }
    }
}
