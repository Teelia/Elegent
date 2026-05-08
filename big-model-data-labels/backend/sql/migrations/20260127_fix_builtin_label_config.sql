-- ============================================================
-- 修正内置标签配置 SQL
-- 问题：Task 58 使用的标签配置了错误的 number_intent
-- 解决：移除 number_intent，使用正确的 extractors 配置
-- ============================================================

-- ============================================================
-- 第一步：检查当前错误的标签配置
-- ============================================================

-- 查看所有"涉警当事人信息完整性"相关的标签
SELECT
    id,
    name,
    version,
    preprocessing_mode,
    preprocessor_config,
    builtin_level,
    builtin_category,
    is_active,
    created_at
FROM labels
WHERE name LIKE '%涉警当事人信息完整性%'
ORDER BY version DESC;

-- 查看是否有 number_intent 配置的标签（这些配置是错误的）
SELECT
    id,
    name,
    version,
    preprocessing_mode,
    preprocessor_config,
    builtin_level,
    is_active
FROM labels
WHERE preprocessor_config LIKE '%number_intent%'
  AND preprocessing_mode = 'rule_only';

-- ============================================================
-- 第二步：备份当前配置（安全起见）
-- ============================================================

-- 创建备份表
CREATE TABLE IF NOT EXISTS labels_backup_20260127 AS
SELECT * FROM labels WHERE 1=0;

-- 备份所有内置全局标签
INSERT INTO labels_backup_20260127
SELECT * FROM labels
WHERE builtin_level = 'system' AND scope = 'global';

SELECT COUNT(*) AS '已备份数量' FROM labels_backup_20260127;

-- ============================================================
-- 第三步：修正标签配置
-- ============================================================

-- ------------------------------------------------------------
-- 方案A: 更新现有的错误标签（如果存在）
-- ------------------------------------------------------------

-- 查找需要更新的标签ID（根据名称匹配）
SET @label_id = (
    SELECT id FROM labels
    WHERE name LIKE '涉警当事人信息完整性%'
      AND preprocessing_mode = 'rule_only'
      AND preprocessor_config LIKE '%number_intent%'
    ORDER BY id DESC
    LIMIT 1
);

-- 如果找到了，执行更新
UPDATE labels
SET
    -- 修正预处理模式
    preprocessing_mode = 'rule_then_llm',

    -- 修正预处理器配置（移除 number_intent，使用 extractors）
    preprocessor_config = '{
        "extractors": ["id_card", "passport", "keyword_match"],
        "extractorOptions": {
            "id_card": {
                "include18Digit": true,
                "include15Digit": false,
                "includeLoose": false
            },
            "passport": {
                "include_cn_only": false
            },
            "keyword_match": {
                "keywords": [
                    "请求撤警",
                    "误报警",
                    "不需要处理",
                    "已协商解决",
                    "重复警情",
                    "副单",
                    "无效警情",
                    "无效报警",
                    "未发现报警情况",
                    "现场无异常",
                    "无警情发生"
                ],
                "matchType": "any"
            }
        }
    }',

    -- 确保包含预处理结果到 Prompt
    include_preprocessor_in_prompt = true,

    -- 启用二次强化
    enable_enhancement = true,

    -- 设置二次强化配置
    enhancement_config = '{
        "triggerConfidence": 75
    }',

    -- 标记为系统内置标签
    builtin_level = 'system',
    builtin_category = 'person_info_integrity',

    -- 确保标签是激活状态
    is_active = true,

    -- 更新修改时间
    updated_at = NOW()
WHERE id = @label_id;

SELECT CONCAT('✓ 已更新标签 ID: ', @label_id) AS '更新结果';


-- ------------------------------------------------------------
-- 方案B: 删除错误的标签，让系统重新初始化
-- ------------------------------------------------------------

-- 如果上述更新失败，可以考虑删除错误的标签
-- 注意：删除前请确认有备份！

-- DELETE FROM labels
-- WHERE id = @label_id
--   AND name LIKE '涉警当事人信息完整性%'
--   AND preprocessing_mode = 'rule_only';


-- ============================================================
-- 第四步：验证修正结果
-- ============================================================

-- 验证修正后的配置
SELECT
    id,
    name,
    version,
    preprocessing_mode,
    JSON_EXTRACT(preprocessor_config, '$.extractors') AS extractors,
    JSON_EXTRACT(preprocessor_config, '$.extractorOptions.keyword_match.keywords') AS keywords,
    include_preprocessor_in_prompt,
    enable_enhancement,
    builtin_level,
    builtin_category,
    is_active
FROM labels
WHERE name LIKE '%涉警当事人信息完整性%'
  AND builtin_level = 'system'
ORDER BY version DESC
LIMIT 1;


-- ============================================================
-- 第五步：修正在校学生信息完整性检查标签（如果有类似问题）
-- ============================================================

-- 检查在校学生标签是否有类似问题
SELECT
    id,
    name,
    preprocessing_mode,
    preprocessor_config,
    builtin_level
FROM labels
WHERE name LIKE '%在校学生信息完整性%';

-- 如果在校学生标签也有 number_intent 配置，执行更新
UPDATE labels
SET
    preprocessing_mode = 'rule_then_llm',
    preprocessor_config = '{
        "extractors": ["school_info", "keyword_match", "id_card"],
        "extractorOptions": {
            "school_info": {
                "exclude_training": true
            },
            "keyword_match": {
                "keywords": [
                    "在校学生",
                    "学生",
                    "学号",
                    "年级",
                    "幼儿园",
                    "小学",
                    "中学",
                    "初中",
                    "高中",
                    "中职",
                    "技校",
                    "学院",
                    "大学"
                ],
                "matchType": "any"
            },
            "id_card": {
                "include18Digit": true,
                "include15Digit": true,
                "includeLoose": true
            }
        }
    }',
    include_preprocessor_in_prompt = true,
    enable_enhancement = true,
    enhancement_config = '{
        "triggerConfidence": 70
    }',
    builtin_level = 'system',
    builtin_category = 'person_info_integrity',
    is_active = true,
    updated_at = NOW()
WHERE name LIKE '%在校学生信息完整性%'
  AND preprocessing_mode = 'rule_only'
  AND preprocessor_config LIKE '%number_intent%';


-- ============================================================
-- 附加：清理所有错误的 number_intent 配置
-- ============================================================

-- 查找所有包含 number_intent 但不是用于特定号码提取任务的标签
-- 这些标签应该使用 RULE_THEN_LLM 而不是 RULE_ONLY + number_intent
SELECT
    id,
    name,
    version,
    preprocessing_mode,
    JSON_EXTRACT(preprocessor_config, '$.number_intent') AS number_intent_config,
    builtin_level
FROM labels
WHERE preprocessor_config LIKE '%number_intent%'
  AND builtin_level = 'system'
  AND type = 'classification';


-- ============================================================
-- 回滚脚本（如果需要恢复）
-- ============================================================

-- 从备份表恢复
-- REPLACE INTO labels
-- SELECT * FROM labels_backup_20260127;

-- 删除备份表（确认修复成功后执行）
-- DROP TABLE IF EXISTS labels_backup_20260127;
