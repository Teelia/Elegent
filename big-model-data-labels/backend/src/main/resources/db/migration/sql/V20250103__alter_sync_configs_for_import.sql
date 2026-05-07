-- ====================================================
-- 外部数据源导入支持 - sync_configs 表扩展
-- ====================================================
-- 功能: 扩展 sync_configs 表以支持数据导入和 Oracle 连接
-- 日期: 2025-01-03
-- ====================================================

-- 1. 新增数据方向字段（区分导入/导出）
ALTER TABLE sync_configs
ADD COLUMN direction VARCHAR(10) DEFAULT 'export' COMMENT '数据方向: import=导入, export=导出' AFTER db_type;

-- 2. 新增 Oracle 专用字段
ALTER TABLE sync_configs
ADD COLUMN oracle_sid VARCHAR(100) COMMENT 'Oracle SID(当connection_mode=sid时使用)' AFTER database_name,
ADD COLUMN oracle_service_name VARCHAR(100) COMMENT 'Oracle Service Name(当connection_mode=service_name时使用)' AFTER oracle_sid,
ADD COLUMN connection_mode VARCHAR(20) DEFAULT 'standard' COMMENT '连接模式: standard=标准, sid=SID模式, service_name=ServiceName模式, tns=TNS模式' AFTER oracle_service_name;

-- 3. 新增导入专用字段
ALTER TABLE sync_configs
ADD COLUMN import_query TEXT COMMENT '自定义SQL查询条件(仅导入时使用)' AFTER table_name,
ADD COLUMN last_import_time TIMESTAMP NULL COMMENT '上次导入时间(用于增量更新)' AFTER import_query,
ADD COLUMN import_status VARCHAR(20) COMMENT '导入状态: pending/importing/completed/failed' AFTER last_import_time,
ADD COLUMN connection_test_status VARCHAR(20) COMMENT '连接测试状态: success/failed/unknown' AFTER import_status,
ADD COLUMN connection_test_time TIMESTAMP NULL COMMENT '最后测试连接时间' AFTER connection_test_status;

-- 4. 调整现有字段为可选（导入场景不需要，导出场景才需要）
ALTER TABLE sync_configs
MODIFY COLUMN table_name VARCHAR(100) NULL COMMENT '表名(导出时为目标表,导入时为源表)',
MODIFY COLUMN field_mappings JSON NULL COMMENT '字段映射(导出时需要,导入时可为空)';

-- 5. 新增索引以优化查询性能
ALTER TABLE sync_configs
ADD INDEX idx_direction (direction),
ADD INDEX idx_connection_test (connection_test_status),
ADD INDEX idx_import_status (import_status);

-- 6. 更新现有记录的 direction 字段为默认值
UPDATE sync_configs SET direction = 'export' WHERE direction IS NULL;
