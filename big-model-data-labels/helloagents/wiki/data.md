# 数据模型

## 概述
数据存储以 MySQL 为主，核心包含：用户、标签、任务、数据行、标签结果，以及结构化提取器/提示词等配置。
本文件为高层概览，字段以代码与数据库迁移脚本为准。

---

## 核心表（概览）

### labels
**描述:** 标签定义（版本化），支持 `classification` / `extraction` / `structured_extraction`。

**关键字段（概念级）:**
- `name`, `version`, `type`, `description`
- `focus_columns`（关注列）
- `extract_fields`（结构化提取字段）
- `extractor_config`（结构化提取器配置 JSON）
- `preprocessing_mode` / `preprocessor_config`（规则预处理配置）
- `enable_enhancement` / `enhancement_config`（二次强化配置）

### data_rows
**描述:** 数据行存储（原始数据 JSON + 处理结果/状态）。

### analysis_tasks / analysis_task_labels / label_results
**描述:** 新版分析任务编排与结果存储（支持并发与断点续传）。

### extractor_configs / extractor_patterns / extractor_options
**描述:** 动态/内置提取器配置（正则、优先级、选项）。

### system_prompts
**描述:** 系统提示词模板（分类/提取/强化等），可在数据库中配置默认模板。

---

## 号码证据模型（已落地：规则侧 SSOT）

> 目标：把“号码类判断”转成可审计的结构化证据，为 LLM 分析与二次校验提供 SSOT。

**后端实现（代码侧SSOT）：**
- `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidence.java`
- `backend/src/main/java/com/datalabeling/service/extraction/NumberEvidenceExtractor.java`

**当前用法：**
- 否定条件规则预处理（例如 Task43“不满足18位的错误身份证号”）直接使用证据提取器裁决，避免冲突吞噬造成漏检。
- 自由提取提示词构建阶段，会把证据列表（含证据ID与脱敏号码）写入提示词上下文，并加入“禁止输出证据外号码”的约束。

**业务口径补充（号码类）：**
- **15位（一代身份证）视为正确号码**：在 Task43“提取不满足18位的错误身份证号”中，`ID_VALID_15` 不纳入输出结果。
- Task43 的返回集合 = `numbers[].type == ID_INVALID_LENGTH` 的 value 列表（脱敏仅用于日志/提示词，对外结果按业务需要决定是否脱敏）。

**建议作为 `label_results.extracted_data` 或独立字段存储（脱敏版/可选原文片段）：**

- `evidence_version`: 版本号
- `text_fingerprint`: 原文指纹（hash），避免落库明文
- `numbers[]`: 结构化候选列表（每条含 type、masked_value、span、校验信息、置信度、冲突标记）
- `derived`: 派生结论（例如 `id_exists`、`phone_exists`、`bank_exists` 以及各类 count）

### numbers[].type（当前已落地范围）
- 身份证：`ID_VALID_18` / `ID_VALID_15` / `ID_INVALID_LENGTH` / `ID_INVALID_CHECKSUM`
- 手机号：`PHONE` / `PHONE_INVALID` / `PHONE_MASKED`
- 银行卡：`BANK_CARD` / `BANK_CARD_INVALID` / `BANK_CARD_MASKED`

### derived（建议字段）
- 身份证：
  - `id_exists`
  - `id_valid_18_count` / `id_valid_15_count`
  - `id_invalid_length_count` / `id_invalid_checksum_count`
- 手机号：
  - `phone_exists`
  - `phone_valid_count` / `phone_invalid_count` / `phone_masked_count`
- 银行卡：
  - `bank_exists`
  - `bank_valid_count` / `bank_invalid_count` / `bank_masked_count`

> 兼容说明：历史字段 `phone_count` / `bank_card_count` 保留，含义为“valid数量”，新功能优先使用 `phone_*` 与 `bank_*`。

---

## labels.preprocessor_config（JSON扩展：number_intent）

为支撑“号码类标签”（存在/提取/无效/遮挡）不再依赖 description 猜测，在 `labels.preprocessor_config` 中扩展 `number_intent`：

- `number_intent.entity`: `phone` / `bank_card` / `id_card`
- `number_intent.task`: `exists` / `extract` / `invalid` / `masked`
- `number_intent.include`: `valid` / `invalid` / `masked`（task=extract时生效）
- `number_intent.policy.require_keyword_for_invalid_bank`: 无效银行卡弱信号是否要求关键词窗命中（降低误判）
