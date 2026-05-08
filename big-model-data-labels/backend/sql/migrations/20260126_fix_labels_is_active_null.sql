-- 修复历史数据：labels.is_active 为空会导致“激活标签查询”过滤后不可见
-- 说明：本脚本为幂等（多次执行无副作用）

UPDATE labels
SET is_active = 1
WHERE is_active IS NULL;

