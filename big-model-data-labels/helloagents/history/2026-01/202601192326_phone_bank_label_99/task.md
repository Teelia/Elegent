# 任务清单: 手机号/银行卡号码类标签（存在/提取/错误无效）99%+可审计改造

目录: `helloagents/plan/202601192326_phone_bank_label_99/`

---

## 1. 号码证据SSOT扩展（backend）
- [ ] 1.1 在 `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java` 增加手机号/银行卡的 invalid/masked 证据类型与派生字段（phone_* / bank_*），验证 why.md#核心场景
- [ ] 1.2 在 `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidence.java` 补充证据字段（如 mask_pattern/context_window 等如需要），验证 how.md#数据模型
- [ ] 1.3 在 `backend/src/main/java/com/datalabeling/util/PiiMaskingUtil.java` 补齐对“遮挡号码片段”的安全脱敏策略（避免日志泄露），验证 how.md#安全与性能

## 2. 标签意图配置（backend）
- [ ] 2.1 新增“号码类标签意图”配置解析模型（建议放在 `backend/src/main/java/com/datalabeling/service/model/` 或 `dto`），并定义可向后兼容的 JSON schema（存入 `labels.preprocessor_config`），验证 how.md#数据模型
- [ ] 2.2 在标签执行链路中引入 `LabelIntentEvaluator`：根据 intent + evidence 输出 exists/extract/invalid/masked 的确定性结果（优先规则），验证 why.md#变更内容
- [ ] 2.3 对需要语义归属的标签，在 `backend/src/main/java/com/datalabeling/service/DeepSeekService.java` 中强化“证据ID约束”输出与一致性检查（禁止输出证据外号码），验证 how.md#实现要点

## 3. 创建/编辑标签能力补齐（backend + frontend）
- [ ] 3.1 修复创建标签DTO缺失：在 `backend/src/main/java/com/datalabeling/dto/request/CreateLabelRequest.java` 补齐 preprocessingMode/preprocessorConfig/includePreprocessorInPrompt/enableEnhancement/enhancementConfig 等字段，并在 `LabelService.createLabel` 落库，验证 how.md#API设计
- [ ] 3.2 前端创建/编辑标签时补齐字段透传：在 `frontend/src/components/DatasetLabelManager.vue` 创建与更新请求中加入 includePreprocessorInPrompt，并允许 extraction 类型也配置 preprocessorConfig（如采用 intent 方案），验证 why.md#变更内容
- [ ] 3.3 前端新增“号码类标签向导/模板”：在“新建数据集标签”中用结构化表单生成 number_intent 配置，避免仅靠 description，验证 ADR-002

## 4. 回归集与指标评估（质量闭环）
- [ ] 4.1 新增手机号/银行卡回归xlsx（建议放到 `测试数据/analysis-task-xx-...xlsx`），覆盖 valid/invalid/masked/混杂场景，验证 how.md#测试与部署
- [ ] 4.2 新增回归测试（JUnit）：读取回归xlsx并做“期望命中/漏检/误检”检查（参考 `NegativeConditionPreprocessorTask43Test`），验证 why.md#成功指标
- [ ] 4.3 生成评估报告：对“无法99%准确”的口径给出边界说明与降级策略（needs_review/仅存在性），输出到 `helloagents/wiki/`，验证 why.md#风险评估

## 5. 安全检查
- [ ] 5.1 执行安全检查（按G9：输入验证、敏感信息处理、权限控制、避免将PII写入日志/提示词/导出），重点检查手机号/银行卡链路

## 6. 文档更新
- [ ] 6.1 更新 `helloagents/wiki/arch.md` / `helloagents/wiki/data.md`：补充 phone/bank 证据类型与标签意图配置
- [ ] 6.2 更新 `helloagents/CHANGELOG.md`：记录手机号/银行卡标签能力与标签创建配置修复

## 7. 测试
- [ ] 7.1 执行 `backend` 单测（开启 `mvn -DskipTests=false test`）并记录结果
- [ ] 7.2 抽样跑一轮真实任务（手机号/银行卡相关标签），核查：误判样本、遮挡样本处理、日志脱敏、导出结果

