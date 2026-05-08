-- ====================================================
-- 大模型配置并发限制字段迁移脚本
-- 日期: 2025-12-18
-- 描述: 为 model_configs 增加 max_concurrency（全局并发限制）
-- 兼容: MySQL 8.0+
-- ====================================================

ALTER TABLE model_configs
    ADD COLUMN IF NOT EXISTS max_concurrency INT NOT NULL DEFAULT 10 COMMENT '最大并发数（全局并发限制）' AFTER retry_times;

