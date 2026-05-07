-- 将“身份证号相关的内置标签”提升为系统内置全局标签（对所有数据集可见）
--
-- 背景：
-- - “新建分析任务”选择标签会合并 global + dataset 标签
-- - 如果身份证号相关标签当前是 scope='dataset' 且绑定在某个 dataset_id（如 21/23），
--   那么其他数据集（如 31）看不到这些“内置标签”
--
-- 使用方式（建议）：
-- 1) 先执行下面的“预览查询”确认将被提升的记录
-- 2) 再执行 UPDATE 提升作用域

-- 预览：管理员创建的、包含“身份证”语义的 dataset 标签
SELECT id, user_id, name, scope, dataset_id, is_active, created_at, updated_at
FROM labels
WHERE scope = 'dataset'
  AND dataset_id IS NOT NULL
  AND user_id IN (SELECT id FROM users WHERE LOWER(role) = 'admin')
  AND (name LIKE '%身份证%' OR description LIKE '%身份证%')
ORDER BY updated_at DESC;

-- 执行：提升为全局（对所有数据集可见）
UPDATE labels
SET scope = 'global',
    dataset_id = NULL,
    task_id = NULL,
    is_active = 1
WHERE scope = 'dataset'
  AND dataset_id IS NOT NULL
  AND user_id IN (SELECT id FROM users WHERE LOWER(role) = 'admin')
  AND (name LIKE '%身份证%' OR description LIKE '%身份证%');

