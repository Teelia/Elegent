-- =====================================================
-- 修改 label_type 字段长度
-- 版本: V20251230
-- 说明: 将 label_results 表的 label_type 字段长度从 20 扩展到 30，以支持 structured_extraction 类型
-- =====================================================

ALTER TABLE label_results
MODIFY COLUMN label_type VARCHAR(30) COMMENT '标签类型（冗余字段，便于查询）';
