-- 标签信息提取功能支持
-- 日期: 2025-12-18

-- 1. 为 labels 表添加标签类型字段
ALTER TABLE labels ADD COLUMN IF NOT EXISTS type VARCHAR(20) DEFAULT 'classification' COMMENT '标签类型: classification(分类判断), extraction(信息提取)';

-- 2. 为 labels 表添加提取字段定义
ALTER TABLE labels ADD COLUMN IF NOT EXISTS extract_fields JSON COMMENT '提取字段定义，如 ["姓名","手机号"]';

-- 3. 扩展 label_results 表的 result 字段长度（用于存储提取结果摘要）
ALTER TABLE label_results MODIFY COLUMN result VARCHAR(500) COMMENT '结果：分类标签为是/否，提取标签为摘要';

-- 4. 为 label_results 表添加提取数据字段
ALTER TABLE label_results ADD COLUMN IF NOT EXISTS extracted_data JSON COMMENT '提取的数据，格式：{"姓名":"张三","手机号":"138xxx"}';

-- 5. 为 label_results 表添加标签类型冗余字段
ALTER TABLE label_results ADD COLUMN IF NOT EXISTS label_type VARCHAR(20) COMMENT '标签类型（冗余字段）';

-- 6. 添加索引优化按类型查询
CREATE INDEX IF NOT EXISTS idx_labels_type ON labels(type);
CREATE INDEX IF NOT EXISTS idx_label_results_type ON label_results(label_type);
