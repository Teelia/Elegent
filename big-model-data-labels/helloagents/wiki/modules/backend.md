# backend

## 目的
提供标签管理、任务执行、规则提取/预处理、大模型调用、结果存储与导出能力。

## 模块概述
- **职责:** Spring Boot API、异步任务编排、DeepSeek 调用、规则提取器体系、结果持久化。
- **状态:** 🚧开发中
- **最后更新:** 2026-01-26

## 规范

### 需求: 号码类标签提取与分析 99% 准确改造
**模块:** backend
对警情/接警文本中的身份证号/银行卡号等进行“规则建模→LLM分析→一致性校验”的稳定链路改造，确保号码类标签达到可量化的高准确率（以规则可验证定义为准）。

#### 场景: 否定条件任务（如“不满足18位的错误身份证号”）稳定提取
- 预期结果1：规则预处理可稳定输出候选与证据（不再被错误的冲突规则吞掉）。
- 预期结果2：LLM 输出必须引用证据ID，不得凭空生成号码。

**当前实现落点（已完成）：**
- 否定条件预处理器：`backend/src/main/java/com/datalabeling/service/extraction/NegativeConditionPreprocessor.java`
- 号码证据建模：`backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java`
  - 证据模型：`backend/src/main/java/com/datalabeling/service/extraction/NumberEvidence.java`
  - 修复：18位身份证末位 X 不应被拆成 17 位纯数字导致 `ID_INVALID_LENGTH` 误报；银行卡遮挡候选必须包含 `*` 避免误判
- 回归测试：`backend/src/test/java/com/datalabeling/service/extraction/NegativeConditionPreprocessorTask43Test.java`
- 号码类意图（number_intent）：`backend/src/main/java/com/datalabeling/service/extraction/NumberIntentEvaluator.java`
  - 支持手机号/银行卡：exists/extract/invalid/masked
  - 支持身份证遮挡：masked/invalid_length_masked（全遮挡不计存在）
  - 身份证“错误身份证号（长度错）”口径：invalid 仅包含 `ID_INVALID_LENGTH`（15位视为正确；18位校验位不通过不算错；遮挡不进入 invalid 输出集合）
- 任务编排落点：`backend/src/main/java/com/datalabeling/service/AnalysisTaskAsyncService.java`
  - rule_only 分类标签在 number_intent 命中时也会写入 `extracted_data_json`，供前端证据抽屉审计展示
- 回归测试：`backend/src/test/java/com/datalabeling/service/extraction/NumberIntentEvaluatorIdInvalidLengthOnlyTest.java`

## API接口
详见 `helloagents/wiki/api.md`。

## 数据模型
详见 `helloagents/wiki/data.md`。

## 依赖
- MySQL（核心存储）
- DeepSeek 70B（OpenAI 兼容）
-（可选）Redis（并发/缓存/限流）

## 变更历史
- [202601192135_num_extraction_99](../../history/2026-01/202601192135_num_extraction_99/) - 号码类标签提取与分析 99% 准确改造

## 2026-01-26 更新

- 内置全局标签：新增 BuiltinGlobalLabelsInitializer / BuiltinLabelController / BuiltinLabelService
- number_intent：身份证口径完善（id15IsValid、idChecksumInvalidIsInvalid、id18XIsInvalid）
- 分析任务：规则验证结果格式化并注入提示词/二次强化（validation_result）
- 数据库：新增内置标签相关字段迁移脚本

## 2026-01-29 更新

- 修复后置验证链路：二次强化后的后置验证现在透传 `labelName`，使“在校学生信息完整性检查”启用 `StudentInfoValidator`（避免误走涉警通用后置规则导致结果污染）。
- 修复身份证前缀误判：`IdCardLengthValidator` 不再把空白字符当作“前缀字符错误”，仅对 `?/?/#/*` 等特殊字符前缀判错。
- 在校学生验证口径补齐：`StudentInfoValidator` 增加“不涉及在校学生 -> 判定为[是]合理”的兜底分支，并修复“部分学校不完整”逻辑分支失效的问题；基础教育学校（幼儿园/小学/中学/学校）才强制要求含省/市等前置信息，避免误杀大学类写法。
- 优化涉警当事人缺失名单展示：`PartyExtractor.checkCompleteness` 对明显非人名短语做降噪过滤，避免在“缺失名单”中出现“表示知晓/向报警人…”等误导性片段（不改变缺失计数口径）。
- 按《检测规则.docx》对齐涉警后置验证（防误判/可纠偏）：
  - `PostProcessValidator` 不再把泛化词“无法”作为强制缺失关键词，避免误伤“嫌疑人身份无法确定/当事人无法联系”等范围排除场景。
  - `PostProcessValidator` 增加“当事人范围排除”逻辑：路见不平等无关报警人、盗窃/损坏财物等案件中“嫌疑人身份无法确定”的泛指嫌疑人、以及明确“不在现场/无法联系”导致无法确定身份的泛指对象，不纳入完整性缺失判定。
  - `PostProcessValidator` 新增“否 -> 是”纠偏建议：当模型判否但规则证据显示合格（证件格式有效、无缺失关键词、当事人完整）时，建议修正为“是”，由 `EnhancementService` 落地修正。
  - `PostProcessValidator` 对“直接合格情形（撤警/无效警情/无事实）”增加纠偏：命中时可将“否”修正为“是”。
- 新增逐行审计脚本：`tools/audit_task90_task91.py` 生成 Task-90/Task-91 的逐行归因审计表，便于回归与抽样复核。
- 新增离线重放工具（不连库/不调用LLM）：`backend/src/test/java/com/datalabeling/tools/PostValidationReplayTool.java` 可对 results-with-reasoning 做“后置验证重放”并输出对比xlsx；并提供 `tools/compare_audit_replay.py` 生成“旧审计 vs 重放”对比表。
- 运行态配置更新（数据库内变更）：
  - 更新系统提示词 `system_prompts.id=6`（`police_personnel_enhancement_v1`）：按《检测规则.docx》补充直接合格优先级、当事人范围排除与输出要求；并刷新提示词缓存。
  - 更新全局标签 `labels.id=38` 描述：显式写入《检测规则.docx》的优先级与当事人范围口径（管理员维护）。
- PartyExtractor 抽取收敛（降低伪当事人 -> 缺失误判）：
  - 修复纠纷语境当事人抽取的人名截断：改用捕获组提取姓名，避免 substring 导致伪当事人。
  - 增加非姓名 token 过滤：对“物业/商家/中介/网友/房东/租客/邻居”等泛化实体/角色词不作为姓名纳入当事人。
  - 移除“中介/商家/物业/网友”等泛化隐含当事人自动追加逻辑，避免误判为身份缺失。

