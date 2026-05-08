-- 新增内置全局标签字段：builtin_level / builtin_category
-- 说明：本脚本用于手工运维执行（与 resources/db/migration 下脚本含义一致）

ALTER TABLE labels
  ADD COLUMN builtin_level VARCHAR(20) NOT NULL DEFAULT 'custom' COMMENT '内置级别: system(系统内置) / custom(用户自定义)',
  ADD COLUMN builtin_category VARCHAR(50) NULL COMMENT '内置分类: person_info_integrity / case_feature / data_quality 等',
  ADD INDEX idx_labels_builtin_level (builtin_level),
  ADD INDEX idx_labels_builtin_category (builtin_category);

-- 将存量 global 标签标记为 system（与“global 仅管理员可创建”语义保持一致）
UPDATE labels
SET builtin_level = 'system'
WHERE scope = 'global' AND (builtin_level IS NULL OR builtin_level = 'custom');

