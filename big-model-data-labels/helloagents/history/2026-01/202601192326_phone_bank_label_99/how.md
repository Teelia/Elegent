# 技术设计: 手机号/银行卡号码类标签（存在/提取/错误无效）99%+可审计改造

## 技术方案

### 核心技术
- Java 8 / Spring Boot 2.7.x
- 规则侧证据建模：`NumberEvidenceExtractor`（扩展）
- DeepSeek 70B（可选：仅做语义归属与解释，严格受“证据ID约束”）

### 实现要点（推荐方案）
1. **证据为SSOT**：号码候选的抽取/校验/分类/冲突仲裁完全在规则侧完成，并输出结构化证据 JSON。
2. **标签意图显式化**：在标签配置中声明“目标实体/任务/输出策略”，避免通过 description 解析导致不可控分支。
3. **LLM受限**：当需要语义归属（报警人/嫌疑人/当事人）时允许调用 LLM，但输出必须引用证据ID；否则一律规则输出。
4. **一致性校验**：对模型输出执行“证据引用存在性 + 类型一致性”校验，不一致则 needs_review 或重问（可选）。

---

## 架构设计

```mermaid
flowchart TD
  R[原始文本/数据行] --> N[NumberEvidenceExtractor: 候选/校验/分类/遮挡识别]
  N --> E[Evidence JSON (SSOT)]
  E --> I[LabelIntentEvaluator: exists/extract/invalid/masked]
  I --> O[规则输出: 结构化结果 + 证据摘要]
  E --> L[DeepSeek70B(可选): 语义归属/解释(证据ID约束)]
  L --> V[ConsistencyValidator(可选)]
  V --> O
```

---

## 架构决策 ADR

### ADR-002: 号码类标签以“意图配置 + 证据SSOT”驱动（不依赖描述文本解析）
**上下文:** 当前否定条件任务通过规则证据已验证可显著提升稳定性；但手机号/银行卡的“存在/提取/无效/遮挡”组合复杂，依赖 description 解析会产生歧义与不可控分支，且前端配置无法完整落库。  
**决策:** 引入 `number_intent` 配置（存储于 `labels.preprocessor_config` 的 JSON 扩展字段），由后端解析并驱动规则执行；LLM 仅在需要语义归属时参与且受证据约束。  
**替代方案:** 仅扩展多个“结构化提取器”并用自然语言 description 区分 → 拒绝原因：组合表达能力弱、可维护性差、回归难、与UI配置不匹配。  
**影响:**  
- 需要升级前端标签创建表单以支持意图配置；  
- 需要后端在创建/更新标签时完整保存相关字段；  
- 需要新增回归集与指标脚本作为验收基线。  

---

## API设计（变更点）

### [POST] /labels
- **问题**：当前后端 `CreateLabelRequest` 缺失 `preprocessingMode/preprocessorConfig/includePreprocessorInPrompt/enableEnhancement/enhancementConfig` 等字段，导致前端创建时的配置无法保存。
- **改造**：补齐创建DTO与落库逻辑，确保创建与更新行为一致。

---

## 数据模型（建议：JSON扩展，避免强制迁移）

### 1) labels.preprocessor_config（扩展 number_intent）
示例（概念）：
```json
{
  "version": "2026-01-19",
  "number_intent": {
    "entity": "phone",
    "task": "extract",
    "include": ["valid", "invalid", "masked"],
    "invalid_rules": ["wrong_prefix", "wrong_length"],
    "masked_rules": ["digits_star_digits"]
  }
}
```

### 2) Evidence JSON（规则侧SSOT，已落地并将扩展）
- `numbers[]`: 候选列表（type、masked_value、span、校验信息、冲突信息、置信度）
- `derived`: 派生字段（如 phone_exists/phone_valid_count/phone_invalid_count/phone_masked_count，bank_* 同理）

---

## 安全与性能
- **安全（PII）**：
  - 默认：日志/提示词/评估报告使用脱敏号码（前4后4）。
  - 仅在必要输出（业务要求）时返回明文，并在权限与审计下控制（建议后续加开关与审计字段）。
  - 遮挡号码不尝试“恢复明文”，只输出 masked 证据与存在性结论。
- **性能**：
  - 规则抽取 O(n) 扫描，优先规则完成；LLM 仅对需要语义归属或 needs_review 样本调用。
  - 你可接受慢：可引入“二次LLM复核”作为可选开关，仅对歧义样本触发。

---

## 测试与部署
- **回归集**：新增手机号/银行卡回归xlsx（参考 Task44 的方式），覆盖：
  - valid/invalid/masked/混杂长数字串/跨类型冲突（身份证与银行卡混杂）
- **指标**：输出 precision/recall/F1 + needs_review 比例（避免“命中数”误导）。
- **部署**：优先在测试环境跑全量任务，确认日志脱敏与导出策略后再上线。

