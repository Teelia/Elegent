# 任务清单: 手机号/银行卡号码类标签（存在/提取/无效/遮挡）99%+可审计改造

目录: `helloagents/plan/202601200006_phone_bank_number_intent_99/`

---

## 1. 规则证据SSOT补齐（backend）
- [√] 1.1 在 `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java` 补齐派生字段（phone_* / bank_* 的 exists/count，含 invalid/masked），验证 why.md#需求-手机号存在性（错误遮挡也算存在）、why.md#需求-银行卡号存在性提取无效判断（遮挡也算存在）
- [√] 1.2 在 `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidence.java` 如需扩展证据字段（maskPattern/keywordHint 等），确保可审计且不引入原文片段泄露，验证 how.md#数据模型（建议json-扩展避免强制迁移）
  > 备注: 本次补齐了 NumberCandidate.keywordHint 的透传与派生字段；未引入原文片段落库。

## 2. number_intent 配置与解析（backend）
- [√] 2.1 新增 `backend/src/main/java/com/datalabeling/dto/NumberIntentConfig.java`（或同等位置）定义 `number_intent` schema（entity/task/include/output/policy），验证 how.md#数据模型（建议json-扩展避免强制迁移）
- [√] 2.2 在 `backend/src/main/java/com/datalabeling/dto/PreprocessorConfig.java` 增加“忽略未知字段/兼容解析”能力，确保 `number_intent` 与旧配置共存不报错，验证 how.md#数据模型（建议json-扩展避免强制迁移）

## 3. 号码类意图规则执行（backend）
- [√] 3.1 新增 `backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java`：输入（text + evidence + number_intent）→ 输出（exists/valid/invalid/masked 的结论 + 证据摘要 + 可选 needs_review），验证 why.md#核心场景
- [√] 3.2 在 `backend/src/main/java/com/datalabeling/service/AnalysisTaskAsyncService.java` 的规则路径中接入 NumberIntentEvaluator（覆盖 classification.rule_only 与 extraction.rule_only），验证 why.md#需求-手机号存在性（错误遮挡也算存在）、why.md#需求-银行卡号存在性提取无效判断（遮挡也算存在）
- [-] 3.3（可选）在 `backend/src/main/java/com/datalabeling/service/DeepSeekService.java` 强化证据约束输出：仅允许引用证据ID/derived 字段；发现“证据外号码”则标记 needs_review 或触发重问，验证 how.md#设计原则（与99+可审计对齐）
  > 备注: 当前 `DeepSeekService` 已包含“证据列表 + 禁止输出证据外号码”的提示词约束，本次优先完成规则闭环与前端配置；如后续引入严格JSON schema/校验器，再补齐此项。

## 4. 前端“新建数据集标签”能力补齐（frontend）
- [√] 4.1 在 `frontend/src/components/DatasetLabelManager.vue` 创建/更新请求中补齐 `includePreprocessorInPrompt` 透传，确保创建与编辑一致，验证 why.md#需求-标签定义与创建能力升级（号码类向导）
- [√] 4.2 在 `frontend/src/components/DatasetLabelManager.vue` 放开 extraction 类型的 `preprocessorConfig` 构建与保存（当前仅classification构建），验证 why.md#变更内容
- [√] 4.3 在 `frontend/src/components/DatasetLabelManager.vue` 新增“号码类标签向导/模板”：生成 `preprocessor_config.number_intent`（entity/task/include），并提供高级模式（直接编辑JSON），验证 how.md#架构决策-adr-003-号码类标签引入-number_intent-配置（显式意图驱动规则优先）

## 5. 回归集与指标（质量闭环）
- [√] 5.1 新增手机号/银行卡回归xlsx（建议：`测试数据/analysis-task-45-phone-bank-results.xlsx`），覆盖 valid/invalid/masked/混杂数字串/跨类型冲突，验证 how.md#测试与部署
- [√] 5.2 新增 JUnit 回归测试：读取回归xlsx并校验命中/漏检/误检（参考 Task43 的测试方式），验证 why.md#价值主张与成功指标
- [√] 5.3 生成评估报告：对无法自动确定的口径输出边界与降级策略（needs_review/仅存在性），写入 `helloagents/wiki/`，验证 why.md#风险评估

## 6. 安全检查（EHRB）
- [√] 6.1 执行安全检查：日志/提示词/导出默认脱敏；遮挡号码不还原；必要时补权限与审计点（按G9），验证 how.md#安全（pii-ehrb）

## 7. 文档同步（知识库）
- [√] 7.1 更新 `helloagents/wiki/data.md`：补齐 phone/bank 的证据类型与 derived 字段说明
- [√] 7.2 更新 `helloagents/wiki/arch.md`：补齐“证据SSOT→意图执行→可选LLM→一致性校验”的链路与ADR索引
- [√] 7.3 更新 `helloagents/CHANGELOG.md`：记录本次号码类标签能力升级与前端配置修复

## 8. 验证
- [√] 8.1 执行后端单测：`mvn -DskipTests=false test`
- [√] 8.2 执行前端构建：`npm run build`（或等价CI命令），并抽样验证“新建数据集标签”配置可保存/可回显/可执行
