-- 创建系统提示词表
-- 日期: 2024-12-30

CREATE TABLE IF NOT EXISTS system_prompts (
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
    INDEX idx_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统提示词表';

-- 插入默认提示词模板
INSERT INTO system_prompts (user_id, name, code, prompt_type, template, variables, is_system_default) VALUES
(1, '默认分类判断模板', 'default_classification', 'classification',
'你是数据标注助手。请根据以下规则判断这行数据是否符合标签定义。

标签名称: {{label_name}}
标签规则: {{label_description}}
关注列: {{focus_columns}}

{{#if preprocessor_result}}
=== 规则预处理结果 ===
{{preprocessor_result}}
{{/if}}

=== 原始数据 ===
{{row_data_json}}

请仅回答"是"或"否"，不要有任何额外解释。',
'["label_name","label_description","focus_columns","preprocessor_result","row_data_json"]',
TRUE),

(1, '默认LLM结构化提取模板', 'default_extraction', 'extraction',
'你是专业的数据提取助手，请从给定的数据中提取指定字段的信息。

需要提取的字段：{{extract_fields}}

返回JSON格式：
{
  "{{field1}}": "提取的值或null",
  "{{field2}}": "提取的值或null",
  "confidence": 0-100的整数,
  "reasoning": "简要说明提取依据和过程"
}

【重要】提取规则：
- 严格按照字段名称进行精确匹配
- 如果某字段在数据中找不到【完全对应】的信息，必须返回 null
- 提取的值必须是原始数据中的【原文内容】
- reasoning 中必须说明：从哪个字段提取、原文是什么、如果为null说明原因

=== 原始数据 ===
{{row_data_json}}',
'["extract_fields","row_data_json"]',
TRUE),

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
TRUE);
