-- =====================================================
-- 标签预处理模式迁移脚本
-- 添加 include_preprocessor_in_prompt 字段，更新预处理模式常量
-- =====================================================

-- 1. 添加新字段（MySQL 不支持 IF NOT EXISTS，使用存储过程检查）
DELIMITER $$

CREATE PROCEDURE add_column_if_not_exists()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
        AND table_name = 'labels'
        AND column_name = 'include_preprocessor_in_prompt'
    ) THEN
        ALTER TABLE labels
        ADD COLUMN include_preprocessor_in_prompt BOOLEAN DEFAULT FALSE
        COMMENT '是否将预处理结果传入LLM（仅rule_then_llm模式有效）';
    END IF;
END$$

DELIMITER ;

-- 执行存储过程
CALL add_column_if_not_exists();

-- 删除存储过程
DROP PROCEDURE IF EXISTS add_column_if_not_exists;

-- 2. 更新现有数据的预处理模式（映射旧值到新值）
UPDATE labels
SET preprocessing_mode = CASE
    WHEN preprocessing_mode = 'none' THEN 'llm_only'
    WHEN preprocessing_mode = 'llm' THEN 'llm_only'
    WHEN preprocessing_mode = 'rule' THEN 'rule_only'
    WHEN preprocessing_mode = 'hybrid' THEN 'rule_then_llm'
    ELSE preprocessing_mode
END
WHERE preprocessing_mode IN ('none', 'llm', 'rule', 'hybrid');

-- 3. 对于现有 rule_then_llm（原 hybrid）模式，默认开启传入 LLM
UPDATE labels
SET include_preprocessor_in_prompt = TRUE
WHERE preprocessing_mode = 'rule_then_llm' AND include_preprocessor_in_prompt IS NULL;

-- 4. 验证迁移结果
SELECT
    '========================================' AS '';
SELECT '预处理模式迁移完成！' AS message;
SELECT '========================================' AS '';

SELECT
    preprocessing_mode AS '预处理模式',
    COUNT(*) AS '数量',
    SUM(CASE WHEN include_preprocessor_in_prompt = TRUE THEN 1 ELSE 0 END) AS '启用传入LLM'
FROM labels
GROUP BY preprocessing_mode
ORDER BY preprocessing_mode;

-- 5. 查看详情（可选）
SELECT
    id,
    name,
    type AS '类型',
    preprocessing_mode AS '预处理模式',
    include_preprocessor_in_prompt AS '传入LLM',
    enable_enhancement AS '二次强化',
    is_active AS '激活'
FROM labels
ORDER BY id, version;
