-- 添加预处理模式和二次强化分析字段到 labels 表
-- 日期: 2024-12-30

-- 添加预处理模式字段
ALTER TABLE labels ADD COLUMN preprocessing_mode VARCHAR(20) DEFAULT 'none' COMMENT '预处理模式: none/rule/llm/hybrid' AFTER extractor_config;

-- 添加预处理器配置字段
ALTER TABLE labels ADD COLUMN preprocessor_config JSON COMMENT '预处理器配置（JSON格式）' AFTER preprocessing_mode;

-- 添加是否启用二次强化分析字段
ALTER TABLE labels ADD COLUMN enable_enhancement BOOLEAN DEFAULT FALSE COMMENT '是否启用二次强化分析' AFTER preprocessor_config;

-- 添加强化分析配置字段
ALTER TABLE labels ADD COLUMN enhancement_config JSON COMMENT '强化分析配置（JSON格式）' AFTER enable_enhancement;
