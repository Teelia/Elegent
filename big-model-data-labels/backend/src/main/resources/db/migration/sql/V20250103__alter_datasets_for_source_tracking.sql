-- ====================================================
-- 数据集数据源追踪支持 - datasets 表扩展
-- ====================================================
-- 功能: 扩展 datasets 表以支持数据来源追踪和增量更新
-- 日期: 2025-01-03
-- ====================================================

-- 1. 新增数据来源类型字段
ALTER TABLE datasets
ADD COLUMN source_type VARCHAR(20) DEFAULT 'file' COMMENT '数据来源: file=文件上传, database=数据库导入' AFTER description;

-- 2. 新增外部数据源关联字段
ALTER TABLE datasets
ADD COLUMN external_source_id INT COMMENT '外部数据源ID(当source_type=database时)' AFTER source_type;

-- 3. 新增导入查询条件记录字段
ALTER TABLE datasets
ADD COLUMN import_query TEXT COMMENT '导入时使用的SQL查询条件' AFTER external_source_id;

-- 4. 新增最后导入时间字段（用于增量更新）
ALTER TABLE datasets
ADD COLUMN last_import_time TIMESTAMP NULL COMMENT '最后导入时间(用于增量更新)' AFTER import_query;

-- 5. 新增索引以优化查询性能
ALTER TABLE datasets
ADD INDEX idx_source_type (source_type),
ADD INDEX idx_external_source_id (external_source_id);

-- 6. 更新现有记录的 source_type 字段为默认值
UPDATE datasets SET source_type = 'file' WHERE source_type IS NULL;
