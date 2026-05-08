# 提示词系统梳理与迁移规划

## 一、当前硬编码提示词梳理

### 1.1 分类判断提示词 (classification)

**位置**: `DeepSeekService.java`
- 第60行: 简化版（仅返回"是"/"否"）
- 第810-824行: 带信心度和推理的完整版
- 第944-963行: 用户提示词（构建标签名称、规则、数据）
- 第970-984行: 带信心度的系统提示词

**用途**: 判断数据是否符合标签定义

**返回格式**:
- 简单版: "是"或"否"
- 完整版: JSON `{result, confidence, reasoning}`

**当前状态**: 数据库中存在简化版本，但缺少详细的信心度标准

---

### 1.2 自由提取提示词 (free_form_extraction)

**位置**: `DeepSeekService.java` 第368-388行

**完整内容**:
```
你是专业的数据提取助手。请根据用户给出的提取要求，从数据中提取信息。

请按以下JSON格式返回结果：
{
  "result": "提取到的内容",
  "confidence": 0-100的整数，表示提取结果的信心程度,
  "reasoning": "简要说明提取依据和过程（不超过200字）"
}

【重要】提取规则：
- 严格按照用户的提取要求进行匹配，不要进行语义扩展或模糊匹配
- 如果用户要求"包含"某个字符串，必须确保数据中【完全包含】该字符串（区分大小写），不能是相似词或近义词
- 如果用户要求"查找"或"提取"某个关键词，必须是【精确匹配】，不是语义相关
- 如果数据中没有【完全匹配】用户要求的内容，result 必须返回"未提取到相关信息"
- 提取的内容必须是原始数据中的原文，不要编造、改写或推测
- reasoning 中必须说明：1)在哪个字段找到的 2)匹配的原文是什么 3)如果未找到，说明检查了哪些字段
- 请只返回JSON，不要有其他内容

【信心度标准】：
- 90-100: 找到完全匹配的内容
- 70-89: 找到部分匹配的内容
- 50-69: 找到可能相关但不确定的内容
- 0-49: 未找到匹配内容，result应为"未提取到相关信息"
```

**用途**: 自由形式的数据提取（用户描述提取需求）

**返回格式**: JSON `{result, confidence, reasoning}`

**当前状态**: 完全缺失，需要添加到数据库

---

### 1.3 结构化提取提示词 (structured_extraction)

**位置**: `DeepSeekService.java` 第652-674行

**完整内容**:
```
你是专业的数据提取助手，请从给定的数据中提取指定字段的信息。

需要提取的字段：{{extract_fields}}

请按以下JSON格式返回结果：
{
  "{{field1}}": "提取的值或null",
  "{{field2}}": "提取的值或null",
  ...
  "confidence": 0-100的整数，表示对提取结果的信心程度,
  "reasoning": "简要说明提取依据和过程（不超过100字）"
}

【重要】提取规则：
- 严格按照字段名称进行精确匹配，不要进行语义扩展
- 如果某字段在数据中找不到【完全对应】的信息，必须返回 null
- 提取的值必须是原始数据中的【原文内容】，不要编造、改写或推测
- 不要将相似但不完全匹配的内容作为提取结果
- reasoning 中必须说明：从哪个字段提取、原文是什么、如果为null说明原因
- 请只返回JSON，不要有其他内容

【信心度标准】：
- 90-100: 找到完全匹配的字段和值
- 70-89: 找到高度相关的内容
- 50-69: 找到可能相关但不确定的内容
- 0-49: 未找到匹配内容，对应字段应为null
```

**用途**: 提取指定字段的信息

**返回格式**: JSON `{field1: value1, field2: value2, ..., confidence, reasoning}`

**当前状态**: 数据库中存在简化版本，但缺少详细的提取规则和信心度标准

---

### 1.4 二次强化分析提示词 (enhancement)

**位置**:
- `PromptTemplateEngine.java` 第223-256行（完整版）
- `DeepSeekService.java` 第1206行（简化版）
- 数据库迁移脚本

**用途**: 对初步分析结果进行二次验证和强化

**返回格式**: JSON `{final_result, final_confidence, validation_notes, should_adjust, adjustment_reason}`

**当前状态**: 已存在于数据库中

---

## 二、问题分析

### 2.1 现有数据库提示词的问题

1. **default_extraction 不够详细**:
   - 缺少精确匹配规则说明
   - 缺少详细的信心度标准
   - 缺少提取规则说明

2. **缺少 free_form_extraction 类型**:
   - 自由提取功能完全依赖硬编码
   - 无法让用户自定义自由提取的提示词

3. **分类判断提示词缺少信心度标准**:
   - 简单版本没有信心度说明
   - 详细的信心度标准只在代码中

### 2.2 DeepSeekService.java 依赖硬编码

当前 `DeepSeekService.java` 中的方法仍然使用硬编码的提示词：
- `judge()` - 使用硬编码的简单分类提示词
- `extractFreeForm()` - 使用硬编码的自由提取提示词
- `extract()` - 使用硬编码的结构化提取提示词
- `judgeWithConfidence()` - 使用硬编码的带信心度提示词

这些方法需要修改为从数据库获取提示词。

---

## 三、迁移方案

### 3.1 更新数据库迁移脚本

**创建新文件**: `V20251230__update_prompts_with_detailed_rules.sql`

**内容**:
1. 更新 `default_extraction` 提示词，添加详细的提取规则和信心度标准
2. 添加 `default_free_form_extraction` 提示词
3. 添加 `classification_with_confidence` 提示词（带信心度标准的分类判断）

### 3.2 修改 DeepSeekService.java

**修改的方法**:
1. `judge()` - 改为使用 `SystemPromptService` 获取分类提示词
2. `extractFreeForm()` - 改为使用 `SystemPromptService` 获取自由提取提示词
3. `extract()` - 改为使用 `SystemPromptService` 获取结构化提取提示词
4. `judgeWithConfidence()` - 改为使用 `SystemPromptService` 获取带信心度的分类提示词

**新增方法**:
- `getSystemPrompt(String promptType, Label label)` - 从数据库获取提示词并通过模板引擎渲染

### 3.3 提示词变量支持

确保所有提示词都支持以下变量：
- 分类类型: `{{label_name}}`, `{{label_description}}`, `{{focus_columns}}`, `{{row_data_json}}`, `{{preprocessor_result}}`, `{{extracted_numbers}}`
- 提取类型: `{{label_name}}`, `{{label_description}}`, `{{extract_fields}}`, `{{row_data_json}}`, `{{preprocessor_result}}`
- 强化类型: `{{label_name}}`, `{{label_description}}`, `{{row_data_json}}`, `{{initial_result}}`, `{{initial_confidence}}`, `{{initial_reasoning}}`, `{{validation_result}}`

---

## 四、实施步骤

### 阶段1: 数据库脚本更新
1. 创建 `V20251230__update_prompts_with_detailed_rules.sql`
2. 添加详细的提取规则提示词
3. 添加自由提取提示词
4. 添加带信心度标准的分类提示词

### 阶段2: 后端代码改造
1. 修改 `DeepSeekService.java`，移除硬编码提示词
2. 注入 `SystemPromptService` 和 `PromptTemplateEngine`
3. 创建 `getSystemPrompt()` 方法

### 阶段3: 测试验证
1. 测试分类判断功能
2. 测试自由提取功能
3. 测试结构化提取功能
4. 测试二次强化分析功能
5. 验证用户自定义提示词是否生效

---

## 五、提示词完整版本汇总

### 5.1 default_classification_with_confidence

**代码**: `classification_with_confidence`
**用途**: 带信心度和推理的分类判断
**返回格式**: JSON `{result, confidence, reasoning}`

**模板内容**:
```
你是专业的数据标注助手，请根据规则严格判断数据。

请按以下JSON格式返回结果：
{
  "result": "是" 或 "否",
  "confidence": 0-100的整数，表示你对判断结果的信心程度,
  "reasoning": "简短说明判断依据（不超过100字）"
}

信心度说明：
- 90-100: 非常确定，数据明确符合/不符合规则
- 70-89: 比较确定，有较强的判断依据
- 50-69: 一般确定，存在一些模糊之处
- 0-49: 不太确定，数据信息不足或规则难以判断

请只返回JSON，不要有其他内容。

=== 原始数据 ===
{{row_data_json}}

{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}

{{#if extracted_numbers}}
=== 提取到的号码 ===
{{extracted_numbers}}
{{/if}}
```

### 5.2 default_free_form_extraction

**代码**: `default_free_form_extraction`
**用途**: 自由形式的数据提取
**返回格式**: JSON `{result, confidence, reasoning}`

**模板内容**:
```
你是专业的数据提取助手。请根据用户给出的提取要求，从数据中提取信息。

请按以下JSON格式返回结果：
{
  "result": "提取到的内容",
  "confidence": 0-100的整数，表示提取结果的信心程度,
  "reasoning": "简要说明提取依据和过程（不超过200字）"
}

【重要】提取规则：
- 严格按照用户的提取要求进行匹配，不要进行语义扩展或模糊匹配
- 如果用户要求"包含"某个字符串，必须确保数据中【完全包含】该字符串（区分大小写）
- 如果用户要求"查找"或"提取"某个关键词，必须是【精确匹配】
- 如果数据中没有【完全匹配】用户要求的内容，result 必须返回"未提取到相关信息"
- 提取的内容必须是原始数据中的原文，不要编造、改写或推测
- reasoning 中必须说明：1)在哪个字段找到的 2)匹配的原文是什么 3)如果未找到，说明检查了哪些字段
- 请只返回JSON，不要有其他内容

【信心度标准】：
- 90-100: 找到完全匹配的内容
- 70-89: 找到部分匹配的内容
- 50-69: 找到可能相关但不确定的内容
- 0-49: 未找到匹配内容，result应为"未提取到相关信息"

=== 提取要求 ===
{{label_description}}

{{#if focus_columns}}
=== 关注列 ===
{{focus_columns}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}
```

### 5.3 default_extraction (更新版)

**代码**: `default_extraction`
**用途**: 结构化字段提取
**返回格式**: JSON `{field1: value1, field2: value2, ..., confidence, reasoning}`

**模板内容**:
```
你是专业的数据提取助手，请从给定的数据中提取指定字段的信息。

需要提取的字段：{{extract_fields}}

请按以下JSON格式返回结果：
{
{{#each extract_fields}}
  "{{this}}": "提取的值或null",
{{/each}}
  "confidence": 0-100的整数，表示对提取结果的信心程度,
  "reasoning": "简要说明提取依据和过程（不超过100字）"
}

【重要】提取规则：
- 严格按照字段名称进行精确匹配，不要进行语义扩展
- 如果某字段在数据中找不到【完全对应】的信息，必须返回 null
- 提取的值必须是原始数据中的【原文内容】，不要编造、改写或推测
- 不要将相似但不完全匹配的内容作为提取结果
- reasoning 中必须说明：从哪个字段提取、原文是什么、如果为null说明原因
- 请只返回JSON，不要有其他内容

【信心度标准】：
- 90-100: 找到完全匹配的字段和值
- 70-89: 找到高度相关的内容
- 50-69: 找到可能相关但不确定的内容
- 0-49: 未找到匹配内容，对应字段应为null

=== 提取说明 ===
{{label_description}}

{{#if focus_columns}}
=== 关注列 ===
{{focus_columns}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}
```
