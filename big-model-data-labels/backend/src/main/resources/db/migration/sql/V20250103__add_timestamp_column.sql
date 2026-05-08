-- 添加时间戳列名字段，用于增量更新
ALTER TABLE sync_configs ADD COLUMN timestamp_column VARCHAR(100) NULL COMMENT '时间戳列名（用于增量更新）';

-- 添加索引以加速增量更新查询
CREATE INDEX idx_timestamp_column ON sync_configs(timestamp_column);
