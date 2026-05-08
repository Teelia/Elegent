/*
 Navicat Premium Data Transfer

 Source Server         : 87数据库
 Source Server Type    : MySQL
 Source Server Version : 80405
 Source Host           : 122.152.221.87:13308
 Source Schema         : data_labeling

 Target Server Type    : MySQL
 Target Server Version : 80405
 File Encoding         : 65001

 Date: 18/12/2025 17:45:41
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for analysis_task_labels
-- ----------------------------
DROP TABLE IF EXISTS `analysis_task_labels`;
CREATE TABLE `analysis_task_labels`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `analysis_task_id` int(0) NOT NULL COMMENT '分析任务ID',
  `label_id` int(0) NOT NULL COMMENT '标签ID',
  `label_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称（快照）',
  `label_version` int(0) NOT NULL COMMENT '标签版本（快照）',
  `label_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标签描述（快照）',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_label`(`analysis_task_id`, `label_id`) USING BTREE,
  INDEX `idx_analysis_task_labels_task_id`(`analysis_task_id`) USING BTREE,
  INDEX `idx_analysis_task_labels_label_id`(`label_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '分析任务标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for analysis_tasks
-- ----------------------------
DROP TABLE IF EXISTS `analysis_tasks`;
CREATE TABLE `analysis_tasks`  (
  `id` int(0) NOT NULL AUTO_INCREMENT,
  `dataset_id` int(0) NOT NULL COMMENT '数据集ID',
  `user_id` int(0) NOT NULL COMMENT '创建用户ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务名称（可选）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '任务描述',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'pending' COMMENT '状态：pending, processing, completed, failed, cancelled',
  `total_rows` int(0) NOT NULL DEFAULT 0 COMMENT '总行数',
  `processed_rows` int(0) NOT NULL DEFAULT 0 COMMENT '已处理行数',
  `failed_rows` int(0) NOT NULL DEFAULT 0 COMMENT '失败行数',
  `success_rows` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `default_confidence_threshold` decimal(3, 2) NULL DEFAULT 0.80 COMMENT '默认信心度阈值',
  `model_config_id` int(0) NULL DEFAULT NULL COMMENT '使用的大模型配置ID',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL COMMENT '错误信息',
  `last_activity_at` datetime(0) NULL DEFAULT NULL COMMENT '最后活动时间',
  `started_at` datetime(0) NULL DEFAULT NULL COMMENT '开始时间',
  `completed_at` datetime(0) NULL DEFAULT NULL COMMENT '完成时间',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0),
  `concurrency` int(0) NOT NULL DEFAULT 1,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_analysis_tasks_dataset_id`(`dataset_id`) USING BTREE,
  INDEX `idx_analysis_tasks_user_id`(`user_id`) USING BTREE,
  INDEX `idx_analysis_tasks_status`(`status`) USING BTREE,
  INDEX `idx_analysis_tasks_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '分析任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for audit_logs
-- ----------------------------
DROP TABLE IF EXISTS `audit_logs`;
CREATE TABLE `audit_logs`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
  `user_id` int(0) NULL DEFAULT NULL COMMENT '用户ID',
  `action` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作: login, create_label, upload_file, export_data等',
  `resource_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '资源类型: label, task, data_row',
  `resource_id` int(0) NULL DEFAULT NULL COMMENT '资源ID',
  `details` json NULL COMMENT '详细信息',
  `ip_address` varchar(45) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'IP地址',
  `user_agent` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT 'User Agent',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_audit_logs_user_id`(`user_id`) USING BTREE,
  INDEX `idx_audit_logs_action`(`action`) USING BTREE,
  INDEX `idx_audit_logs_created_at`(`created_at`) USING BTREE,
  CONSTRAINT `audit_logs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 9 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '操作日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for data_rows
-- ----------------------------
DROP TABLE IF EXISTS `data_rows`;
CREATE TABLE `data_rows`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '数据行ID',
  `task_id` int(0) NOT NULL COMMENT '任务ID',
  `row_index` int(0) NOT NULL COMMENT '行索引',
  `original_data` json NOT NULL COMMENT '原始数据',
  `label_results` json NULL COMMENT '标签结果 {\"标签名_v1\": \"是\", \"标签名_v2\": \"否\"}',
  `is_modified` tinyint(1) NULL DEFAULT 0 COMMENT '是否被手动修改',
  `processing_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT 'pending' COMMENT '处理状态: pending, processing, success, failed',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `ai_confidence` json NULL COMMENT 'AI信心度，格式：{\"标签名_v1\": 0.95}',
  `confidence_threshold` decimal(3, 2) NULL DEFAULT 0.80 COMMENT '信心度采纳阈值',
  `needs_review` tinyint(1) NULL DEFAULT 0 COMMENT '是否需要人工审核',
  `ai_reasoning` json NULL COMMENT 'AI分析原因，格式：{\"标签名_v1\": \"分析原因...\"}',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_row`(`task_id`, `row_index`) USING BTREE,
  INDEX `idx_data_rows_task_id`(`task_id`) USING BTREE,
  INDEX `idx_data_rows_task_row`(`task_id`, `row_index`) USING BTREE,
  INDEX `idx_data_rows_status`(`processing_status`) USING BTREE,
  INDEX `idx_data_rows_needs_review`(`task_id`, `needs_review`) USING BTREE,
  INDEX `idx_data_rows_confidence`(`task_id`, `confidence_threshold`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据行表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for datasets
-- ----------------------------
DROP TABLE IF EXISTS `datasets`;
CREATE TABLE `datasets`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '数据集ID',
  `user_id` int(0) NOT NULL COMMENT '用户ID',
  `name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据集名称',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `total_rows` int(0) NULL DEFAULT 0 COMMENT '总行数',
  `columns` json NULL COMMENT '列信息',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'uploaded' COMMENT '状态：uploaded, archived',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '描述',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '存储文件名',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_datasets_user_id`(`user_id`) USING BTREE,
  INDEX `idx_datasets_status`(`status`) USING BTREE,
  INDEX `idx_datasets_created_at`(`created_at`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据集表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for file_tasks
-- ----------------------------
DROP TABLE IF EXISTS `file_tasks`;
CREATE TABLE `file_tasks`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '任务ID',
  `user_id` int(0) NOT NULL COMMENT '用户ID',
  `filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `original_filename` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件路径',
  `file_size` bigint(0) NULL DEFAULT NULL COMMENT '文件大小',
  `file_hash` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'SHA256哈希，用于去重',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态: uploaded, processing, completed, failed, archived',
  `total_rows` int(0) NULL DEFAULT 0 COMMENT '总行数',
  `processed_rows` int(0) NULL DEFAULT 0 COMMENT '已处理行数',
  `failed_rows` int(0) NULL DEFAULT 0 COMMENT '失败行数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `started_at` timestamp(0) NULL DEFAULT NULL COMMENT '开始时间',
  `completed_at` timestamp(0) NULL DEFAULT NULL COMMENT '完成时间',
  `archived_at` timestamp(0) NULL DEFAULT NULL COMMENT '归档时间',
  `columns` json NULL COMMENT '文件列信息',
  `paused_at` datetime(0) NULL DEFAULT NULL COMMENT '暂停时间',
  `custom_tags` json NULL COMMENT '任务自定义标签',
  `run_model_config_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL,
  `run_include_reasoning` tinyint(1) NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_file_tasks_user_id`(`user_id`) USING BTREE,
  INDEX `idx_file_tasks_status`(`status`) USING BTREE,
  INDEX `idx_file_tasks_created_at`(`created_at`) USING BTREE,
  CONSTRAINT `file_tasks_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文件任务表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for label_results
-- ----------------------------
DROP TABLE IF EXISTS `label_results`;
CREATE TABLE `label_results`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT COMMENT '结果ID',
  `data_row_id` bigint(0) NOT NULL COMMENT '数据行ID',
  `analysis_task_id` int(0) NOT NULL COMMENT '分析任务ID',
  `label_id` int(0) NOT NULL COMMENT '标签ID',
  `label_key` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签键（name_v版本）',
  `result` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果：是/否',
  `ai_confidence` decimal(3, 2) NULL DEFAULT NULL COMMENT 'AI信心度（0.00-1.00）',
  `ai_reasoning` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT 'AI分析原因',
  `confidence_threshold` decimal(3, 2) NULL DEFAULT 0.80 COMMENT '信心度采纳阈值',
  `needs_review` tinyint(1) NULL DEFAULT 0 COMMENT '是否需要人工审核',
  `is_modified` tinyint(1) NULL DEFAULT 0 COMMENT '是否被人工修改',
  `processing_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'pending' COMMENT '处理状态：pending, success, failed',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_row_task_label`(`data_row_id`, `analysis_task_id`, `label_id`) USING BTREE,
  INDEX `idx_label_results_row_id`(`data_row_id`) USING BTREE,
  INDEX `idx_label_results_task_id`(`analysis_task_id`) USING BTREE,
  INDEX `idx_label_results_label_id`(`label_id`) USING BTREE,
  INDEX `idx_label_results_needs_review`(`analysis_task_id`, `needs_review`) USING BTREE,
  INDEX `idx_label_results_result`(`analysis_task_id`, `label_id`, `result`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标签结果表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for labels
-- ----------------------------
DROP TABLE IF EXISTS `labels`;
CREATE TABLE `labels`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '标签ID',
  `user_id` int(0) NOT NULL COMMENT '用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `version` int(0) NOT NULL DEFAULT 1 COMMENT '版本号',
  `scope` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'global' COMMENT '作用域：global(全局), dataset(数据集专属)',
  `dataset_id` int(0) NULL DEFAULT NULL COMMENT '关联数据集ID（仅scope=dataset时有效）',
  `description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签描述',
  `focus_columns` json NULL COMMENT '重点关注列 [\"列名1\", \"列名2\"]',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `task_id` int(0) NULL DEFAULT NULL COMMENT '关联任务ID（仅task作用域使用）',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_user_name_version`(`user_id`, `name`, `version`) USING BTREE,
  INDEX `idx_labels_user_id`(`user_id`) USING BTREE,
  INDEX `idx_labels_name`(`name`) USING BTREE,
  INDEX `idx_labels_user_name_version`(`user_id`, `name`, `version`) USING BTREE,
  INDEX `idx_labels_scope`(`scope`) USING BTREE,
  INDEX `idx_labels_dataset_id`(`dataset_id`) USING BTREE,
  INDEX `idx_labels_task_id`(`task_id`) USING BTREE,
  CONSTRAINT `labels_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '标签表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for model_configs
-- ----------------------------
DROP TABLE IF EXISTS `model_configs`;
CREATE TABLE `model_configs`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DeepSeek默认配置' COMMENT '配置名称',
  `provider` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提供方: deepseek',
  `api_key_encrypted` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '加密后的API Key',
  `base_url` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'API基础URL',
  `model` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模型名称',
  `timeout` int(0) NOT NULL DEFAULT 30000 COMMENT '超时时间（毫秒）',
  `temperature` double NOT NULL DEFAULT 0.1 COMMENT '温度参数（0-2）',
  `max_tokens` int(0) NOT NULL DEFAULT 10 COMMENT '最大返回Token数',
  `retry_times` int(0) NOT NULL DEFAULT 3 COMMENT '失败重试次数',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '配置描述',
  `is_default` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否为默认配置',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_model_configs_provider`(`provider`) USING BTREE,
  INDEX `idx_model_configs_active`(`is_active`) USING BTREE,
  INDEX `idx_model_configs_default`(`is_default`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '大模型配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for sync_configs
-- ----------------------------
DROP TABLE IF EXISTS `sync_configs`;
CREATE TABLE `sync_configs`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id` int(0) NOT NULL COMMENT '用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置名称',
  `db_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据库类型: postgresql, mysql, sqlserver',
  `host` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '主机地址',
  `port` int(0) NOT NULL COMMENT '端口',
  `database_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '数据库名',
  `username` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password_encrypted` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '加密后的密码',
  `table_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标表名',
  `field_mappings` json NOT NULL COMMENT '字段映射 {\"文件列名\": \"数据库字段名\"}',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_sync_configs_user_id`(`user_id`) USING BTREE,
  CONSTRAINT `sync_configs_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '数据库同步配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for task_execution_logs
-- ----------------------------
DROP TABLE IF EXISTS `task_execution_logs`;
CREATE TABLE `task_execution_logs`  (
  `id` bigint(0) NOT NULL AUTO_INCREMENT,
  `analysis_task_id` int(0) NOT NULL COMMENT '分析任务ID',
  `data_row_id` bigint(0) NULL DEFAULT NULL COMMENT '数据行ID（可为空，表示任务级日志）',
  `row_index` int(0) NULL DEFAULT NULL COMMENT '行索引',
  `label_key` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '标签键',
  `log_level` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'INFO' COMMENT '日志级别：INFO, WARN, ERROR',
  `message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志消息',
  `confidence` decimal(3, 2) NULL DEFAULT NULL COMMENT '信心度',
  `duration_ms` int(0) NULL DEFAULT NULL COMMENT '处理耗时（毫秒）',
  `created_at` datetime(0) NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_logs_task_id`(`analysis_task_id`) USING BTREE,
  INDEX `idx_logs_task_row`(`analysis_task_id`, `row_index`) USING BTREE,
  INDEX `idx_logs_created_at`(`created_at`) USING BTREE,
  INDEX `idx_logs_level`(`analysis_task_id`, `log_level`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '任务执行日志表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for task_labels
-- ----------------------------
DROP TABLE IF EXISTS `task_labels`;
CREATE TABLE `task_labels`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '关联ID',
  `task_id` int(0) NOT NULL COMMENT '任务ID',
  `label_id` int(0) NOT NULL COMMENT '标签ID',
  `label_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名称',
  `label_version` int(0) NOT NULL COMMENT '标签版本',
  `label_description` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '标签描述',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `uk_task_label`(`task_id`, `label_id`) USING BTREE,
  INDEX `idx_task_labels_task_id`(`task_id`) USING BTREE,
  INDEX `idx_task_labels_label_id`(`label_id`) USING BTREE,
  CONSTRAINT `task_labels_ibfk_1` FOREIGN KEY (`task_id`) REFERENCES `file_tasks` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `task_labels_ibfk_2` FOREIGN KEY (`label_id`) REFERENCES `labels` (`id`) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 1 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '任务标签关联表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `id` int(0) NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `username` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `password_hash` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码哈希',
  `role` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色: admin, normal',
  `email` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '邮箱',
  `full_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '全名',
  `is_active` tinyint(1) NULL DEFAULT 1 COMMENT '是否激活',
  `created_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` timestamp(0) NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP(0) COMMENT '更新时间',
  `last_login` timestamp(0) NULL DEFAULT NULL COMMENT '最后登录时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `username`(`username`) USING BTREE,
  INDEX `idx_users_username`(`username`) USING BTREE,
  INDEX `idx_users_role`(`role`) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 2 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- View structure for v_file_tasks
-- ----------------------------
DROP VIEW IF EXISTS `v_file_tasks`;
CREATE ALGORITHM = UNDEFINED DEFINER = `root`@`%` SQL SECURITY DEFINER VIEW `v_file_tasks` AS select `d`.`id` AS `id`,`d`.`user_id` AS `user_id`,`d`.`filename` AS `filename`,`d`.`original_filename` AS `original_filename`,`d`.`file_path` AS `file_path`,`d`.`file_size` AS `file_size`,`d`.`file_hash` AS `file_hash`,`d`.`columns` AS `columns`,`d`.`total_rows` AS `total_rows`,coalesce(`at`.`processed_rows`,0) AS `processed_rows`,coalesce(`at`.`failed_rows`,0) AS `failed_rows`,(case when (`at`.`status` = 'processing') then 'processing' when (`at`.`status` = 'completed') then 'completed' when (`at`.`status` = 'failed') then 'failed' when (`d`.`status` = 'ready') then 'completed' else 'uploaded' end) AS `status`,`at`.`error_message` AS `error_message`,`at`.`started_at` AS `started_at`,`at`.`completed_at` AS `completed_at`,NULL AS `archived_at`,`d`.`created_at` AS `created_at`,`d`.`updated_at` AS `updated_at` from (`datasets` `d` left join (select `analysis_tasks`.`dataset_id` AS `dataset_id`,`analysis_tasks`.`status` AS `status`,`analysis_tasks`.`processed_rows` AS `processed_rows`,`analysis_tasks`.`failed_rows` AS `failed_rows`,`analysis_tasks`.`error_message` AS `error_message`,`analysis_tasks`.`started_at` AS `started_at`,`analysis_tasks`.`completed_at` AS `completed_at` from `analysis_tasks` where `analysis_tasks`.`id` in (select max(`analysis_tasks`.`id`) from `analysis_tasks` group by `analysis_tasks`.`dataset_id`)) `at` on((`d`.`id` = `at`.`dataset_id`)));

-- ----------------------------
-- View structure for v_labels_latest
-- ----------------------------
DROP VIEW IF EXISTS `v_labels_latest`;
CREATE ALGORITHM = UNDEFINED DEFINER = `root`@`%` SQL SECURITY DEFINER VIEW `v_labels_latest` AS select `l1`.`id` AS `id`,`l1`.`user_id` AS `user_id`,`l1`.`name` AS `name`,`l1`.`version` AS `version`,`l1`.`description` AS `description`,`l1`.`focus_columns` AS `focus_columns`,`l1`.`is_active` AS `is_active`,`l1`.`created_at` AS `created_at`,`l1`.`updated_at` AS `updated_at` from (`labels` `l1` join (select `labels`.`user_id` AS `user_id`,`labels`.`name` AS `name`,max(`labels`.`version`) AS `max_version` from `labels` group by `labels`.`user_id`,`labels`.`name`) `l2` on(((`l1`.`user_id` = `l2`.`user_id`) and (`l1`.`name` = `l2`.`name`) and (`l1`.`version` = `l2`.`max_version`))));

SET FOREIGN_KEY_CHECKS = 1;
