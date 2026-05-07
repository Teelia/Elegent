-- 创建提取器配置表
CREATE TABLE IF NOT EXISTS extractor_configs (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    user_id INT NOT NULL COMMENT '创建用户ID',
    name VARCHAR(100) NOT NULL COMMENT '提取器名称',
    code VARCHAR(50) NOT NULL UNIQUE COMMENT '提取器代码（唯一标识）',
    description VARCHAR(500) COMMENT '提取器描述',
    category VARCHAR(50) NOT NULL DEFAULT 'custom' COMMENT '分类：builtin(内置), custom(自定义)',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
    is_system BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否系统内置（系统内置不可删除）',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    INDEX idx_user_id (user_id),
    INDEX idx_code (code),
    INDEX idx_category (category),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提取器配置表';

-- 创建正则规则表
CREATE TABLE IF NOT EXISTS extractor_patterns (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    extractor_id INT NOT NULL COMMENT '关联的提取器ID',
    name VARCHAR(100) NOT NULL COMMENT '规则名称',
    pattern TEXT NOT NULL COMMENT '正则表达式',
    description VARCHAR(500) COMMENT '规则描述',
    priority INT NOT NULL DEFAULT 0 COMMENT '优先级（数值越大优先级越高）',
    confidence DECIMAL(5,2) COMMENT '置信度阈值（0.00-1.00）',
    validation_type VARCHAR(50) COMMENT '验证类型：none, checksum, length, format, custom',
    validation_config JSON COMMENT '验证配置（JSON格式）',
    is_active BOOLEAN NOT NULL DEFAULT TRUE COMMENT '是否激活',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (extractor_id) REFERENCES extractor_configs(id) ON DELETE CASCADE,
    INDEX idx_extractor_id (extractor_id),
    INDEX idx_is_active (is_active),
    INDEX idx_priority (priority),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='正则规则表';

-- 创建选项配置表
CREATE TABLE IF NOT EXISTS extractor_options (
    id INT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
    extractor_id INT NOT NULL COMMENT '关联的提取器ID',
    option_key VARCHAR(50) NOT NULL COMMENT '选项键',
    option_name VARCHAR(100) NOT NULL COMMENT '选项名称',
    option_type VARCHAR(20) NOT NULL DEFAULT 'boolean' COMMENT '选项类型：boolean, string, number, select',
    default_value VARCHAR(255) COMMENT '默认值',
    description VARCHAR(500) COMMENT '选项描述',
    select_options JSON COMMENT '下拉选项（仅type=select时有效）',
    sort_order INT NOT NULL DEFAULT 0 COMMENT '排序顺序',
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    FOREIGN KEY (extractor_id) REFERENCES extractor_configs(id) ON DELETE CASCADE,
    INDEX idx_extractor_id (extractor_id),
    INDEX idx_option_key (option_key),
    INDEX idx_sort_order (sort_order)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='选项配置表';

-- 插入系统内置提取器（身份证号提取器）
INSERT INTO extractor_configs (user_id, name, code, description, category, is_active, is_system) VALUES
(1, '身份证号提取器', 'id_card', '用于从文本中提取中国居民身份证号码', 'builtin', TRUE, TRUE);

-- 获取刚插入的提取器ID（假设为1）
SET @extractor_id = LAST_INSERT_ID();

-- 插入身份证号正则规则
INSERT INTO extractor_patterns (extractor_id, name, pattern, description, priority, confidence, validation_type, validation_config, is_active, sort_order) VALUES
(@extractor_id, '18位身份证号', '\\b[1-9]\\d{5}(18|19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b', '18位身份证号码（标准格式）', 100, 0.95, 'checksum', '{"algorithm": "id_card"}', TRUE, 1),
(@extractor_id, '15位身份证号', '\\b[1-9]\\d{5}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}\\b', '15位身份证号码（旧版格式）', 90, 0.80, 'length', '{"min": 15, "max": 15}', TRUE, 2),
(@extractor_id, '带空格身份证号', '\\b[1-9]\\d{5}\\s*\\d{2}\\s*(0[1-9]|1[0-2])\\s*(0[1-9]|[12]\\d|3[01])\\s*\\d{3}\\s*[0-9Xx]\\b', '可能包含空格的身份证号码（自动去除）', 80, 0.70, 'none', '{}', TRUE, 3);

-- 插入身份证号提取器选项
INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, description, sort_order) VALUES
(@extractor_id, 'auto_remove_spaces', '自动去除空格', 'boolean', 'true', '是否自动去除身份证号码中的空格', 1),
(@extractor_id, 'enable_checksum', '启用校验和验证', 'boolean', 'true', '是否验证身份证校验和', 2),
(@extractor_id, 'min_confidence', '最小置信度', 'number', '0.70', '只接受置信度大于等于此值的结果', 3),
(@extractor_id, 'output_format', '输出格式', 'select', 'original', '输出格式：original（原始）, formatted（格式化）, masked（脱敏）', 4);

-- 更新选项的select_options（JSON格式需要特殊处理）
UPDATE extractor_options SET select_options = '["original", "formatted", "masked"]' WHERE option_key = 'output_format';
