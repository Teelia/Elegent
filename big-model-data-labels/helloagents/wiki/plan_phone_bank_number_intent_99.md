# 手机号/银行卡号码类标签改造方案（目标：99%+ 可审计准确）

**编写日期**：2026-01-20  
**适用数据**：警情/接警自由文本（混杂时间戳/警号/案号/金额/流水等长数字串）  
**适用模型**：DeepSeek 70B（内网，OpenAI 兼容接口；可选参与）  
**关联方案包**：`helloagents/history/2026-01/202601200006_phone_bank_number_intent_99/`  

---

## 1. 结论先行：99%能不能做到？“100%准确”是不是不可实现？

### 1.1 可以做到 99%+ 的前提（可验证口径）
当“准确”的定义限定为以下“可验证事实”时，99%+ 是可落地目标：
- **存在性**：文本中是否出现手机号/银行卡号证据（包含错误/无效/遮挡也算存在）
- **格式/校验**：手机号号段规则、银行卡 Luhn、遮挡模式等
- **一致性**：模型输出必须引用规则证据（证据ID/派生字段），禁止凭空生成号码

### 1.2 无法承诺 100% 的部分（信息论缺失）
以下诉求仅靠文本 + LLM 无法保证：
- “号码是否真实存在/是否可用/是否属于某人/是否已注销”等真实性校验  
  需要权威库或业务系统接口（公安/银行/内部库）做二次验证。

### 1.3 推荐验收方式
用回归集衡量 precision/recall/F1，并允许对歧义/遮挡样本输出 `needs_review`，比“强行自动给结论”更接近高准确与可持续迭代。

---

## 2. 当前项目现状（与手机号/银行卡相关）

### 2.1 已验证的关键经验：跨类型冲突会造成系统性漏检
身份证任务（Task43）曾出现“95%未找到”的系统性漏检，根因不是正则过严，而是“跨类型冲突处理策略（银行卡优先占用范围）”吞掉身份证候选。  
已通过规则证据建模（SSOT）修复，并固化回归测试与缺陷报告：
- 相关文档：`docs/身份证提取功能缺陷分析报告-20250119.md`
- 关键实现：`backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java`

### 2.2 口径已确认：15位旧身份证视为正确
15位（一代身份证）属于“正确号码”，不纳入“非18位错误身份证”类输出。该口径会影响后续“身份证是否错误/是否存在”标签定义，应显式写入配置策略。

### 2.3 Task43/Task44 回归集基线（仅统计，不输出明文）
对 360 行样本的结构分布（用于理解难点而非泄露数据）：
- 含 14-22 位长数字串候选的行：约 120/360
- 含“强身份证结构但非18位”的行（Task43 命中面）：约 38/360
- Task44（修复后）命中行数与上述命中面一致，结论输出的长度分布以 17/19/22 位为主

---

## 3. 目标标签定义（手机号/银行卡）

> 统一原则：**存在性与错误/无效判断**必须基于规则证据（SSOT），LLM 不直接抽取号码。

### 3.1 手机号（phone）

**证据类型（建议）**
- `PHONE`：有效手机号（11位，1[3-9]开头）
- `PHONE_INVALID`：11位且以1开头，但号段规则不合法
- `PHONE_MASKED`：11位片段包含 `*`（不还原明文）

**派生字段（derived）**
- `phone_exists`：是否存在（valid/invalid/masked 任一即为 true）
- `phone_valid_count` / `phone_invalid_count` / `phone_masked_count`

**标签示例（可按需创建多标签）**
- `手机号是否存在（含错误/遮挡）`：输出 是/否
- `提取手机号（valid/invalid/masked）`：输出列表（默认脱敏）
- `提取无效手机号`：仅输出 invalid 列表

### 3.2 银行卡（bank_card）

**证据类型（建议）**
- `BANK_CARD`：16-19位，Luhn 通过
- `BANK_CARD_INVALID`：16-19位，前缀弱特征命中但 Luhn 不通过（为降低误判，可要求命中关键词窗时才输出）
- `BANK_CARD_MASKED`：16-19位包含 `*`（不还原明文）

**派生字段（derived）**
- `bank_exists`：是否存在（valid/invalid/masked 任一即为 true）
- `bank_valid_count` / `bank_invalid_count` / `bank_masked_count`

**标签示例**
- `银行卡是否存在（含错误/遮挡）`
- `提取银行卡号（valid/invalid/masked）`
- `提取无效银行卡号`

---

## 4. 推荐架构（与你期望的流程对齐）

你期望的流程：
原始数据 → 正则+代码预处理（结构化）→ JSON建模 → 构建提示词 → DeepSeek分析 → 推理+结论 →（可选）二次判断 → 输出

推荐落地为“三段式”：
1) **规则证据SSOT（事实层）**：统一抽取候选、校验、分类、遮挡识别、冲突裁决，输出 Evidence JSON  
2) **意图驱动规则执行（结论层）**：根据标签意图（exists/extract/invalid/masked）生成最终结论与证据摘要  
3) **可选 LLM（解释/归属/复核层）**：只允许引用证据ID，经过一致性校验；必要时二次强化复核

---

## 5. 标签配置升级：number_intent（解决“新建数据集标签”能力不足）

### 5.1 问题
当前前端“新建数据集标签”无法稳定表达：
- extraction 类型也要保存 preprocessorConfig
- includePreprocessorInPrompt 在创建/更新时的透传一致性
- “号码类意图”（存在/提取/无效/遮挡）无法结构化配置，导致后端只能靠 description 猜测

### 5.2 方案：在 `labels.preprocessor_config` 中扩展 `number_intent`
用一个明确的 JSON schema 表达“实体+任务+输出策略”，示例（概念）：
```json
{
  "version": "2026-01-20",
  "number_intent": {
    "entity": "phone",
    "task": "extract",
    "include": ["valid", "invalid", "masked"]
  }
}
```

前端提供“号码类标签向导/模板”生成上述配置；后端解析并使用规则 SSOT 执行（无需 LLM）。

---

## 6. 实施计划（入口）

已生成详细方案包（含 why/how/task）：
- `helloagents/plan/202601200006_phone_bank_number_intent_99/why.md`
- `helloagents/plan/202601200006_phone_bank_number_intent_99/how.md`
- `helloagents/plan/202601200006_phone_bank_number_intent_99/task.md`

执行阶段将按 task.md 的任务清单推进，并补齐：
- 手机号/银行卡回归集（xlsx）
- JUnit 回归测试与指标
- 知识库同步（arch/data/changelog）
