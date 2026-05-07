-- =====================================================
-- 添加结构化提取支持
-- 版本: V20251230
-- 说明: 为 labels 表添加 extractor_config 字段，支持结构化号码提取
-- =====================================================

-- 添加 extractor_config 字段
ALTER TABLE labels
ADD COLUMN extractor_config JSON COMMENT '提取器配置（仅structured_extraction类型使用）';

-- 添加类型索引
CREATE INDEX idx_labels_type ON labels(type);

-- 添加注释说明新的标签类型
ALTER TABLE labels MODIFY COLUMN type VARCHAR(30) NOT NULL COMMENT '标签类型：classification(分类判断), extraction(LLM通用提取), structured_extraction(结构化号码提取)';
