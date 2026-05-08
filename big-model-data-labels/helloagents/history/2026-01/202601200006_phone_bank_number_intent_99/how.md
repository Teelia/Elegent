# 技术设计: 手机号/银行卡号码类标签（存在/提取/无效/遮挡）99%+可审计改造

## 技术方案

### 核心技术
- Java 8 / Spring Boot 2.7.x
- 规则证据建模（SSOT）：`NumberEvidenceExtractor` / `NumberEvidence`
- 任务编排：`AnalysisTaskAsyncService`
-（可选）语义解释与复核：DeepSeek 70B（OpenAI 兼容接口）

### 设计原则（与“99%+可审计”对齐）
1. **可验证优先**：号码是否出现、是否满足格式/校验规则 → 必须由代码确定性计算输出
2. **证据闭环**：LLM 若参与，只允许引用规则证据（证据ID），禁止凭空生成号码
3. **允许不确定**：遇到遮挡/歧义/证据不足 → 输出 `needs_review`，比胡猜更接近高准确
4. **默认脱敏**：日志/提示词/导出默认使用 maskedValue；遮挡号码不做“还原明文”

---

## 架构设计

```mermaid
flowchart TD
  R[原始文本/数据行] --> E[NumberEvidenceExtractor: 候选/校验/分类/遮挡识别]
  E --> S[Evidence JSON (SSOT)]
  S --> I[NumberIntentEvaluator: exists/extract/invalid/masked]
  I --> O[规则输出: 结论 + 证据摘要]
  S --> L[DeepSeek70B(可选): 语义归属/解释(证据ID约束)]
  L --> V[ConsistencyValidator(可选): 证据引用与类型一致性]
  V --> O
```

说明：
- **规则侧输出**为“事实层”（是否出现、校验是否通过、遮挡模式、冲突裁决原因）。
- **LLM**只负责“解释/归属/摘要”，且必须引用证据ID；不参与号码抽取本身。

---

## 架构决策 ADR

### ADR-003: 号码类标签引入 `number_intent` 配置（显式意图驱动，规则优先）
**上下文:**  
手机号/银行卡的需求组合为“存在/提取/无效/遮挡”等多维意图。仅依赖标签 description 做自然语言解析会导致：
- 分支不可控、可维护性差
- UI 无法准确表达真实意图
- 回归难、结果不可审计

**决策:**  
在 `labels.preprocessor_config` 中扩展保存 `number_intent`（JSON），由后端解析驱动规则执行；LLM 仅在需要语义归属时启用，并受“证据ID约束 + 一致性校验”控制。

**替代方案:**  
1) 继续通过 description 解析任务类型 → 拒绝原因：歧义与不可控分支，难以回归  
2) LLM 双轮抽取/复核（你的原始流程） → 拒绝原因：仍存在幻觉与不可证明正确  
3) 新增数据库字段存储 intent → 暂缓：优先复用现有 JSON 字段，减少迁移风险

**影响:**  
- 前端需新增“号码类标签向导/模板”，生成并透传 `number_intent`  
- 后端需支持“旧 PreprocessorConfig + 新 number_intent”共存（解析兼容）  
- 需要新增手机号/银行卡回归集与指标脚本，形成验收基线

---

## API 设计（变更点）

### [POST] /labels、[PUT] /labels/{id}
- **目标:** 创建与更新必须能完整保存并回传以下配置字段：
  - `preprocessingMode`
  - `preprocessorConfig`（包含 `number_intent`）
  - `includePreprocessorInPrompt`（混合模式下控制是否将规则结果传入LLM）
  - `enableEnhancement` / `enhancementConfig`

---

## 数据模型（建议：JSON 扩展，避免强制迁移）

### 1) labels.preprocessor_config（扩展 number_intent）
建议 schema（示例）：
```json
{
  "version": "2026-01-20",
  "extractors": ["id_card", "phone", "bank_card"],
  "idCardOptions": { "include18Digit": true, "include15Digit": true, "includeLoose": false },
  "phoneOptions": { "includeLoose": false },
  "bankCardOptions": { "useLuhnValidation": true },
  "number_intent": {
    "entity": "phone",
    "task": "extract",
    "include": ["valid", "invalid", "masked"],
    "output": { "format": "list", "maxItems": 50 },
    "policy": {
      "id15_is_valid": true,
      "default_masked_output": true
    }
  }
}
```

兼容性要求：
- 旧字段（extractors/options）继续可用（不破坏现有 classification 规则预处理）
- 新字段 `number_intent` 需要后端专门解析；旧解析器必须能忽略未知字段或以“兼容解析器”替代

### 2) Evidence JSON（规则侧 SSOT）
已有 `NumberEvidence` 结构，扩展/验收重点：
- `numbers[]`：包含 phone/bank 的 valid/invalid/masked 类型、maskPattern、keywordHint、校验信息
- `derived`：新增 `phone_*`、`bank_*` 的 exists/count（含 invalid/masked），用于 exists 类标签快速判断

---

## 规则与口径（关键落地细节）

### 1) “存在性”统一定义（可验证口径）
- `exists = true`：只要文本中出现“足够像该实体的号码证据”，**无论 valid/invalid/masked** 都算存在
- `exists = false`：规则未发现任何对应实体证据

### 2) “无效/错误”统一定义（可验证口径）
- 手机号无效：11位且以1开头，但号段规则不合法（例如第二位非3-9）
- 银行卡无效：16-19位、前缀弱特征命中（62/4/5/3），但 Luhn 校验失败；为降低误判，可要求命中关键词窗时才输出为 invalid
- 身份证无效：18位结构满足但校验位不通过（checksum），或长度非15/18但具强身份证结构特征（地区码+生日段可解析）

### 3) 15位旧身份证口径（已确认）
- 15位（一代身份证）视为正确号码
- 不纳入“非18位错误身份证”类输出

---

## 安全与性能

### 安全（PII / EHRB）
- 日志/提示词/导出默认使用 `maskedValue`（前4后4，中间*）
- 对遮挡号码仅输出遮挡证据（maskPattern、digitCount/maskCount），不尝试推断明文
- 若未来需要输出明文：必须引入权限校验与审计字段（本次不强制落地，但需在任务清单中评估风险）

### 性能
- 规则证据建模为 O(n) 扫描，优先规则完成
- 你可接受慢：二次 LLM 复核仅对 `needs_review` 或高风险样本触发，避免全量双调用

---

## 测试与部署

### 回归集与指标
- 建议新增手机号/银行卡回归集（xlsx）覆盖：valid/invalid/masked/混杂数字串/跨类型冲突
- 指标输出：precision/recall/F1 + needs_review 占比（禁止只看“命中数”）

### 现有身份证回归结论（用于基线确认）
- Task43/Task44（360行）已用于验证“错误身份证（强结构但非18位）”的系统性漏检修复
- 口径已固化：15位旧身份证不计入 Task43 命中

