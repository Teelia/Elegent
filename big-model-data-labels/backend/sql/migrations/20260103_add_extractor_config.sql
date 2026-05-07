-- 提取器配置表
-- 用于存储自定义的正则表达式提取器配置

DROP TABLE IF EXISTS `extractor_configs`;
CREATE TABLE `extractor_configs` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '配置ID',
  `user_id` int NOT NULL COMMENT '创建用户ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提取器名称',
  `code` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提取器代码（唯一标识）',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '提取器描述',
  `category` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NU
      LL DEFAULT 'custom' COMMENT '分类：builtin(内置), custom(自定义)',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否激活',
  `is_system` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否系统内置（系统内置不可删除）',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_code` (`code`),
  INDEX `idx_user_id` (`user_id`),
  INDEX `idx_category` (`category`),
  INDEX `idx_is_active` (`is_active`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提取器配置表';

-- 提取器正则规则表
-- 存储每个提取器的正则表达式规则
DROP TABLE IF EXISTS `extractor_patterns`;
CREATE TABLE `extractor_patterns` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '规则ID',
  `extractor_id` int NOT NULL COMMENT '提取器ID',
  `name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `pattern` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '正则表达式',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '规则描述',
  `priority` int NOT NULL DEFAULT 0 COMMENT '优先级（数字越大优先级越高）',
  `confidence` decimal(3,2) NOT NULL DEFAULT 0.90 COMMENT '匹配时的默认信心度',
  `validation_type` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '验证类型：checksum(校验位), luhn(Luhn算法), none(无)',
  `validation_config` json NULL COMMENT '验证配置（JSON格式）',
  `is_active` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否激活',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  INDEX `idx_extractor_id` (`extractor_id`),
  INDEX `idx_priority` (`extractor_id`, `priority` DESC),
  CONSTRAINT `fk_pattern_extractor` FOREIGN KEY (`extractor_id`) REFERENCES `extractor_configs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提取器正则规则表';

-- 提取器选项配置表
-- 存储提取器的可配置选项
DROP TABLE IF EXISTS `extractor_options`;
CREATE TABLE `extractor_options` (
  `id` int NOT NULL AUTO_INCREMENT COMMENT '选项ID',
  `extractor_id` int NOT NULL COMMENT '提取器ID',
  `option_key` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项键',
  `option_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '选项名称',
  `option_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'boolean' COMMENT '选项类型：boolean, string, number, select',
  `default_value` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '默认值',
  `description` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '选项描述',
  `select_options` json NULL COMMENT '下拉选项（仅type=select时有效）',
  `sort_order` int NOT NULL DEFAULT 0 COMMENT '排序顺序',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE INDEX `uk_extractor_option` (`extractor_id`, `option_key`),
  INDEX `idx_extractor_id` (`extractor_id`),
  CONSTRAINT `fk_option_extractor` FOREIGN KEY (`extractor_id`) REFERENCES `extractor_configs` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提取器选项配置表';

-- 插入内置提取器配置
-- 1. 身份证号提取器
INSERT INTO `extractor_configs` (`user_id`, `name`, `code`, `description`, `category`, `is_system`) VALUES
(1, '身份证号提取', 'id_card', '提取18位标准身份证号、15位旧版身份证号，支持校验位验证', 'builtin', 1);

SET @id_card_extractor_id = LAST_INSERT_ID();

-- 身份证号正则规则
INSERT INTO `extractor_patterns` (`extractor_id`, `name`, `pattern`, `description`, `priority`, `confidence`, `validation_type`, `sort_order`) VALUES
(@id_card_extractor_id, '18位标准身份证号', '\\b([1-6]\\d{5})(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b', '匹配18位标准身份证号，包含地区码、出生日期和校验位', 100, 0.95, 'checksum', 1),
(@id_card_extractor_id, '15位旧版身份证号', '\\b[1-9]\\d{4}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}\\b', '匹配15位旧版身份证号', 90, 0.85, 'none', 2),
(@id_card_extractor_id, '疑似身份证号', '\\b\\d{16,17}\\b|\\b\\d{19,20}\\b', '匹配16-17位或19-20位数字，可能是格式不完整的身份证号', 50, 0.60, 'none', 3);

-- 身份证号选项
INSERT INTO `extractor_options` (`extractor_id`, `option_key`, `option_name`, `option_type`, `default_value`, `description`, `sort_order`) VALUES
(@id_card_extractor_id, 'include18Digit', '包含18位标准', 'boolean', 'true', '提取18位标准身份证号，带校验位验证', 1),
(@id_card_extractor_id, 'include15Digit', '包含15位旧版', 'boolean', 'true', '提取15位旧版身份证号', 2),
(@id_card_extractor_id, 'includeLoose', '包含疑似号码', 'boolean', 'true', '提取格式不完全符合的疑似身份证号', 3);

-- 2. 银行卡号提取器
INSERT INTO `extractor_configs` (`user_id`, `name`, `code`, `description`, `category`, `is_system`) VALUES
(1, '银行卡号提取', 'bank_card', '提取16-19位银行卡号，支持Luhn算法校验和发卡行识别', 'builtin', 1);

SET @bank_card_extractor_id = LAST_INSERT_ID();

-- 银行卡号正则规则
INSERT INTO `extractor_patterns` (`extractor_id`, `name`, `pattern`, `description`, `priority`, `confidence`, `validation_type`, `sort_order`) VALUES
(@bank_card_extractor_id, '标准银行卡号', '\\b\\d{16,19}\\b', '匹配16-19位银行卡号', 100, 0.90, 'luhn', 1);

-- 银行卡号选项
INSERT INTO `extractor_options` (`extractor_id`, `option_key`, `option_name`, `option_type`, `default_value`, `description`, `sort_order`) VALUES
(@bank_card_extractor_id, 'validateLuhn', '启用Luhn校验', 'boolean', 'true', '使用Luhn算法验证银行卡号有效性', 1),
(@bank_card_extractor_id, 'identifyBank', '识别发卡行', 'boolean', 'true', '根据BIN码识别发卡银行', 2);

-- 3. 手机号提取器
INSERT INTO `extractor_configs` (`user_id`, `name`, `code`, `description`, `category`, `is_system`) VALUES
(1, '手机号提取', 'phone', '提取11位中国大陆手机号，支持运营商识别', 'builtin', 1);

SET @phone_extractor_id = LAST_INSERT_ID();

-- 手机号正则规则
INSERT INTO `extractor_patterns` (`extractor_id`, `name`, `pattern`, `description`, `priority`, `confidence`, `validation_type`, `sort_order`) VALUES
(@phone_extractor_id, '中国手机号', '\\b1[3-9]\\d{9}\\b', '匹配11位中国大陆手机号（1开头，第二位3-9）', 100, 0.92, 'none', 1);

-- 手机号选项
INSERT INTO `extractor_options` (`extractor_id`, `option_key`, `option_name`, `option_type`, `default_value`, `description`, `sort_order`) VALUES
(@phone_extractor_id, 'identifyOperator', '识别运营商', 'boolean', 'true', '根据号段识别运营商（移动、联通、电信）', 1);

-- 4. 组合提取器
INSERT INTO `extractor_configs` (`user_id`, `name`, `code`, `description`, `category`, `is_system`) VALUES
(1, '组合提取', 'composite', '同时提取多种类型的号码', 'builtin', 1);