# Changelog
## 2026-01-29

- fix: 全局标签后置验证链路与误判修复（未发布）
  - 后置验证透传 labelName：使“在校学生信息完整性检查”启用 StudentInfoValidator，避免误走涉警通用后置规则污染结果
  - 身份证前缀误判修复：IdCardLengthValidator 不再把空白字符当作“前缀字符错误”，仅对特殊字符前缀判错
  - 在校学生口径补齐：StudentInfoValidator 增加“不涉及在校学生 -> 判定为[是]合理”的兜底分支，并修复“部分学校不完整”分支失效；仅对基础教育学校强制省/市前置信息
  - 涉警缺失名单展示降噪：PartyExtractor.checkCompleteness 过滤明显非人名短语，减少误导性缺失名单
  - 涉警后置验证按《检测规则.docx》对齐（防误判/可纠偏）：
    - PostProcessValidator：不再把泛化词“无法”作为强制缺失关键词；并对“路见不平等无关报警人/盗窃等嫌疑人身份无法确定/当事人不在现场无法联系”的范围外对象做排除
    - PostProcessValidator：命中“直接合格情形（撤警/无效警情/无事实）”时可纠偏 否->是
    - PostProcessValidator + EnhancementService：当模型判否但规则证据显示合格时，可纠偏 否->是（suggestedResult）
    - 测试：新增 `PostProcessValidatorDocRulesTest` 覆盖上述口径
  - 运行态配置：更新系统提示词 `system_prompts.id=6 (police_personnel_enhancement_v1)` 与全局标签 `labels.id=38` 描述，并刷新提示词缓存（数据库内变更）
  - 工具：新增 Task-90/Task-91 逐行审计脚本 `tools/audit_task90_task91.py`，输出逐行归因审计xlsx
  - 工具：新增后置验证离线重放工具 `backend/src/test/java/com/datalabeling/tools/PostValidationReplayTool.java` 与对比脚本 `tools/compare_audit_replay.py`
  - 涉警当事人抽取收敛：修复纠纷抽取人名截断；过滤“物业/商家/中介/网友/房东/租客/邻居”等非姓名token；移除商家/网友/中介等泛化隐含当事人推断，减少缺失误判

## 2026-01-26

- feat: 内置标签与身份证检测优化（9d5ebf2）
  - 内置全局标签：新增字段迁移、初始化与管理接口，前端增加管理入口
  - 身份证检测：number_intent 口径对齐（不把校验位错误计入invalid，可配置15位是否有效），补充规则证据格式化并注入 validation_result
  - 测试：新增/补充号码提取与意图执行相关用例，补充 task-58 测试数据与分析脚本


本文件记录项目所有重要变更。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.0.0/)，
版本号遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 新增
- 新增号码类标签意图执行器 `NumberIntentEvaluator`（number_intent）：支持手机号/银行卡的 exists/extract/invalid/masked 规则闭环输出（默认脱敏，可审计）。
- number_intent 扩展：身份证支持 `masked` / `invalid_length_masked`（全遮挡不计存在）；新增单测 `NumberIntentEvaluatorIdMaskedTest`。
- 新增手机号/银行卡回归集与单测：`测试数据/analysis-task-45-phone-bank-results.xlsx` + `NumberIntentEvaluatorPhoneBankRegressionTest`。
- 前端简化与交互重排：内置“错误身份证号（长度错）”模板（是否存在/提取）、结果复核“证据”抽屉（默认明文+可脱敏）、提取器配置折叠收纳。

### 修复
- 修复否定条件任务（Task43）系统性漏检：用“号码证据建模”替代旧的冲突识别路径，避免银行卡规则吞噬身份证候选。
- 修复 19 位“错误身份证号”漏检：支持删除一位可恢复为有效18位身份证的近似判定（覆盖生日段被插入一位导致不可解析的样本）。
- 修复 18 位身份证末位 X 被拆分为 17 位导致“错误身份证号（长度错）”误报；并补齐 19 位末位 X 的长度错误识别：收紧候选抽取规则，避免纯数字前缀误判，同时将“18位数字+X”归类为长度错误。
- 明确并落地业务口径：15位（一代身份证）视为正确号码，不纳入 Task43 “不满足18位的错误身份证号”输出；同步更新回归测试与缺陷报告。
- 构建流程改进：默认仍跳过单测，但支持 `mvn -DskipTests=false test` 显式执行测试用于回归验证。
- number_intent 身份证口径调整：invalid 仅包含“长度错误”(ID_INVALID_LENGTH)，18位校验位不通过不算错；rule_only 分类结果也写入 extracted_data_json 供前端证据审计展示。
- 修复历史数据 `labels.is_active` 为空导致“内置全局标签”查询结果为空的问题（新增数据库修复脚本）。

### 变更
- 号码证据派生字段补齐：新增 `phone_*` / `bank_*` 的 exists/count（含 invalid/masked）；保留旧字段兼容。
- 前端“新建数据集标签”增强：创建/更新时透传 `includePreprocessorInPrompt`；支持 extraction 类型保存 preprocessorConfig；新增 number_intent 向导与预处理器JSON高级编辑。
- 标签可见性与权限：`/api/labels/active?scope=global` 返回系统内置全局标签（管理员维护，所有用户可见可选）；普通用户禁止创建/修改/删除 global 标签。

### 新增
- 新增号码证据模型与提取器（`NumberEvidence` / `NumberEvidenceExtractor`），用于规则侧SSOT与提示词证据约束。
- 新增 Task43 回归测试（读取仓库内xlsx回归集，避免回归引入系统性漏检）。

## [1.0.0] - 2026-01-19

### 新增
- 初始化 HelloAGENTS 知识库与改造方案包（号码类标签提取与分析 99% 准确改造）。
