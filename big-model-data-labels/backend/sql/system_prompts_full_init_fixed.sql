-- ====================================================
-- system_prompts 表完整建表及初始化脚本（修复版）
-- 日期: 2025-12-30
-- 描述: 创建系统提示词表并初始化默认提示词模板
-- 兼容: MySQL 8.0+
--
-- 修复说明：
-- - 修复了 classification 类型有两个 is_system_default=TRUE 的问题
-- - classification_with_confidence 改为 is_system_default=FALSE
--   （该提示词通过硬编码 code 查询，不需要默认标记）
-- ====================================================

-- ------------------------------------------------
-- 1. 创建 system_prompts 表
-- ------------------------------------------------
DROP TABLE IF EXISTS system_prompts;

CREATE TABLE system_prompts (
    id INT PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    user_id INT NOT NULL COMMENT '所属用户ID（admin的为全局共享）',
    name VARCHAR(100) NOT NULL COMMENT '提示词名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '提示词代码（系统引用标识）',
    prompt_type VARCHAR(20) NOT NULL COMMENT '类型: classification/extraction/validation/enhancement',
    template TEXT NOT NULL COMMENT '提示词模板（支持变量插值）',
    variables JSON COMMENT '变量定义（JSON格式）',
    is_system_default BOOLEAN DEFAULT FALSE COMMENT '是否系统默认模板',
    is_active BOOLEAN DEFAULT TRUE COMMENT '是否启用',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',

    INDEX idx_user_type (user_id, prompt_type),
    INDEX idx_type (prompt_type),
    INDEX idx_code (code),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统提示词表';


-- ------------------------------------------------
-- 2. 初始化默认提示词模板
-- ------------------------------------------------

-- 2.1 默认分类判断模板（classification 类型的生效提示词）
INSERT INTO system_prompts (user_id, name, code, prompt_type, template, variables, is_system_default, is_active) VALUES
(1, '默认分类判断模板', 'default_classification', 'classification',
'你是数据标注助手。请根据以下规则判断这行数据是否符合标签定义。

标签名称: {{label_name}}
标签规则: {{label_description}}
关注列: {{focus_columns}}

{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}

{{#if extracted_numbers}}
=== 提取到的号码 ===
{{extracted_numbers}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}

请仅回答"是"或"否"，不要有任何额外解释。',
'["label_name","label_description","focus_columns","preprocessor_result","extracted_numbers","row_data_json"]',
TRUE, TRUE),

-- 2.2 默认LLM结构化提取模板（extraction 类型的生效提示词）
(1, '默认LLM结构化提取模板', 'default_extraction', 'extraction',
'你是专业的数据提取助手，请从给定的数据中提取指定字段的信息。

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
{{row_data_json}}',
'["extract_fields","label_description","focus_columns","row_data_json"]',
TRUE, TRUE),

-- 2.3 默认二次强化分析模板（enhancement 类型的生效提示词）
(1, '默认二次强化分析模板', 'default_enhancement', 'enhancement',
'你是数据质量审核专家。请对以下初步分析结果进行二次验证和强化。

=== 任务信息 ===
标签名称: {{label_name}}
标签规则: {{label_description}}

=== 原始数据 ===
{{row_data_json}}

=== 初步分析结果 ===
判断: {{initial_result}}
置信度: {{initial_confidence}}%
推理: {{initial_reasoning}}

{{#if validation_result}}
=== 规则验证结果 ===
{{validation_result}}
{{/if}}

=== 二次分析要求 ===
请重新审视初步分析结果，重点关注：
1. 初步结论是否基于充分的证据？
2. 推理逻辑是否存在漏洞？
3. 是否被号码格式等因素误导？
4. 置信度是否合理？

请输出JSON格式：
{
  "final_result": "维持原判"或"修正为是/否",
  "final_confidence": 0-100,
  "validation_notes": "二次审核发现的问题或确认的理由",
  "should_adjust": true/false,
  "adjustment_reason": "如果需要修正，说明原因"}',
'["label_name","label_description","row_data_json","initial_result","initial_confidence","initial_reasoning","validation_result"]',
TRUE, TRUE),

-- 2.4 默认自由提取模板（extraction 类型，但不作为默认）
(1, '默认自由提取模板', 'default_free_form_extraction', 'extraction',
'你是专业的数据提取助手。请根据用户给出的提取要求，从数据中提取信息。

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

=== 提取要求 ===
{{label_description}}

{{#if focus_columns}}
=== 关注列 ===
{{focus_columns}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}',
'["label_description","focus_columns","row_data_json"]',
FALSE, TRUE),

-- 2.5 默认分类判断模板（带信心度）
-- 注意：此提示词通过硬编码 code 查询，不需要 is_system_default 标记
-- 修改：is_system_default 改为 FALSE
(1, '默认分类判断模板（带信心度）', 'classification_with_confidence', 'classification',
'你是专业的数据标注助手，请根据规则严格判断数据。

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
{{/if}}',
'["row_data_json","preprocessor_result","extracted_numbers"]',
FALSE, TRUE);


-- ------------------------------------------------
-- 3. 验证初始化结果
-- ------------------------------------------------
SELECT
    '========================================' AS '',
    'system_prompts 表初始化完成！' AS message,
    '========================================' AS '';

SELECT
    id,
    name,
    code,
    prompt_type,
    is_system_default,
    is_active,
    created_at
FROM system_prompts
ORDER BY id;

-- ------------------------------------------------
-- 4. 验证每个类型只有一个默认提示词
-- ------------------------------------------------
SELECT
    '========================================' AS '',
    '验证每个类型的默认提示词数量' AS message,
    '========================================' AS '';

SELECT
    prompt_type AS '提示词类型',
    COUNT(*) AS '默认提示词数量',
    GROUP_CONCAT(code) AS '默认提示词code'
FROM system_prompts
WHERE is_system_default = TRUE
GROUP BY prompt_type
ORDER BY prompt_type;

-- 期望结果：每个类型的默认提示词数量都应该是 1
