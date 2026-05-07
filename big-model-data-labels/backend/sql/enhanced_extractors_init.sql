-- ================================================
-- 增强型预置提取器初始化脚本
-- 包含所有Java代码实现的预置提取器的数据库配置
-- ================================================

-- 清空现有数据（可选，谨慎使用）
-- TRUNCATE TABLE extractor_options;
-- TRUNCATE TABLE extractor_patterns;
-- TRUNCATE TABLE extractor_configs;

-- ================================================
-- 1. 身份证号提取器（保留原有，增强元数据）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '身份证号提取器', 'id_card',
        '提取18位标准身份证号、15位旧版身份证号，支持校验位验证和地区码识别',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 身份证号正则规则
INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '18位标准身份证', '(\\b([1-6]\\d{5})(19|20)\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}[0-9Xx]\\b)', 100, 0.95, 'checksum', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '15位旧版身份证', '(\\b[1-9]\\d{4}\\d{2}(0[1-9]|1[0-2])(0[1-9]|[12]\\d|3[01])\\d{3}\\b)', 90, 0.85, 'none', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '疑似身份证', '(\\b\\d{16,17}\\b|\\b\\d{19,20}\\b)', 50, 0.60, 'none', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- 身份证号选项
INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'include18Digit', '包含18位身份证', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'include15Digit', '包含15位身份证', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'includeLoose', '包含疑似号码', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'id_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 2. 银行卡号提取器（保留原有）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '银行卡号提取器', 'bank_card',
        '提取银行卡号，支持Luhn算法验证，识别常见银行卡类型',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '标准银行卡', '(\\b\\d{13,19}\\b)', 100, 0.90, 'luhn', NOW()
FROM extractor_configs WHERE code = 'bank_card'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 3. 手机号提取器（保留原有）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '手机号提取器', 'phone',
        '提取中国大陆手机号，识别运营商（移动、联通、电信）',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '中国移动', '(\\b(134|135|136|137|138|139|150|151|152|157|158|159|182|183|184|187|188|178)\\d{8}\\b)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'phone'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '中国联通', '(\\b(130|131|132|155|156|185|186|166)\\d{8}\\b)', 90, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'phone'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '中国电信', '(\\b(133|153|180|181|189|177|173)\\d{8}\\b)', 90, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'phone'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '通用手机号', '(\\b1[3-9]\\d{9}\\b)', 70, 0.85, 'none', NOW()
FROM extractor_configs WHERE code = 'phone'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 4. 邮箱地址提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '邮箱地址提取器', 'email',
        '提取各种格式的邮箱地址，识别常见邮箱服务商（QQ、163、Gmail等）',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '标准邮箱', '(\\b[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}\\b)', 100, 0.95, 'none', NOW()
FROM extractor_configs WHERE code = 'email'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'validate_format', '验证格式', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'email'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'identify_provider', '识别服务商', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'email'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 5. 日期时间提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '日期时间提取器', 'date',
        '提取各种格式的日期时间（ISO、中文、时间戳），自动标准化为ISO格式',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, 'ISO日期', '(\\b(\\d{4})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\\d|3[01])\\b)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'date'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, 'ISO日期时间', '(\\b(\\d{4})[-/](0[1-9]|1[0-2])[-/](0[1-9]|[12]\\d|3[01])[ T](0[1-9]|1\\d|2[0-3]):([0-5]\\d)(?::([0-5]\\d))?\\b)', 95, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'date'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '中文日期', '(\\b(\\d{4})年(0?[1-9]|1[0-2])月(0?[1-9]|[12]\\d|3[01])日\\b)', 90, 0.95, 'none', NOW()
FROM extractor_configs WHERE code = 'date'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '时间戳', '(\\b(\\d{10}|\\d{13})\\b)', 60, 0.65, 'none', NOW()
FROM extractor_configs WHERE code = 'date'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, select_options, created_at)
SELECT id, 'normalize_format', '标准化格式', 'select', 'iso', '["iso","timestamp","chinese"]', NOW()
FROM extractor_configs WHERE code = 'date'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 6. 金额提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '金额提取器', 'money',
        '提取各种格式的金额（中文、阿拉伯数字、货币符号），自动转换为标准数字格式',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '阿拉伯数字金额', '((?:[¥$￥€£]|人民币|美元|欧元|英镑)\\s*\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,4})?\\s*(?:元|美元|欧元|英镑|CNY|USD|EUR|GBP)?)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'money'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '纯数字+元', '(\\d{1,3}(?:,\\d{3})*(?:\\.\\d{1,4})?\\s*(?:元|RMB|CNY))', 95, 0.95, 'none', NOW()
FROM extractor_configs WHERE code = 'money'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '中文金额', '([一二三四五六七八九十百千万零壹贰叁肆伍陆柒捌玖拾佰仟]+元)', 90, 0.90, 'none', NOW()
FROM extractor_configs WHERE code = 'money'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 7. IP地址提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, 'IP地址提取器', 'ip_address',
        '提取IPv4/IPv6地址，识别私有地址、公网地址、特殊地址类型',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, 'IPv4地址', '(\\b(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}\\b)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'ip_address'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, 'IPv4段CIDR', '(\\b(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d{2}|[1-9]?\\d)){3}/(3[0-2]|[12]?\\d)\\b)', 90, 0.95, 'none', NOW()
FROM extractor_configs WHERE code = 'ip_address'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'include_ipv6', '包含IPv6', 'boolean', 'false', NOW()
FROM extractor_configs WHERE code = 'ip_address'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'identify_type', '识别地址类型', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'ip_address'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 8. URL提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, 'URL提取器', 'url',
        '提取HTTP/HTTPS/FTP URL，解析协议、域名、路径等组件',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, 'HTTP/HTTPS URL', '(\\b(https?|ftp)://[^\\s/$.?#][^\\s]*\\b)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'url'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '简化域名', '(\\bwww\\.[a-zA-Z0-9-]+\\.[a-zA-Z]{2,}(?:/[^\\s]*)?\\b)', 80, 0.85, 'none', NOW()
FROM extractor_configs WHERE code = 'url'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 9. 车牌号提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '车牌号提取器', 'car_plate',
        '提取中国车牌号（传统、新能源、军警），识别归属省份',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '新能源车牌', '(\\b[京津冀晋蒙辽吉黑沪苏浙皖闽赣鲁豫鄂湘粤桂琼渝川贵云藏陕甘青宁新][A-Z][0-9A-Z]{6}\\b)', 100, 0.95, 'none', NOW()
FROM extractor_configs WHERE code = 'car_plate'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '传统车牌', '(\\b[京津冀晋蒙辽吉黑沪苏浙皖闽赣鲁豫鄂湘粤桂琼渝川贵云藏陕甘青宁新][A-Z][0-9A-Z]{5}\\b)', 100, 0.98, 'none', NOW()
FROM extractor_configs WHERE code = 'car_plate'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'include_new_energy', '包含新能源车牌', 'boolean', 'true', NOW()
FROM extractor_configs WHERE code = 'car_plate'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_options (extractor_id, option_key, option_name, option_type, default_value, created_at)
SELECT id, 'include_military', '包含军警车牌', 'boolean', 'false', NOW()
FROM extractor_configs WHERE code = 'car_plate'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 10. 公司名称提取器（新增）
-- ================================================
INSERT INTO extractor_configs (user_id, name, code, description, category, is_system, is_active, created_at, updated_at)
VALUES (1, '公司名称提取器', 'company_name',
        '提取公司、企业、组织名称，识别企业类型（有限公司、股份公司等）',
        'builtin', TRUE, TRUE, NOW(), NOW())
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '完整公司名称', '([\\u4e00-\\u9fa5]{2,5}(?:省|市|区|县)?[\\u4e00-\\u9fa5]{2,20}(?:有限|股份|集团|分公司|个体|合伙|厂|店|中心|工作室))', 100, 0.90, 'none', NOW()
FROM extractor_configs WHERE code = 'company_name'
ON DUPLICATE KEY UPDATE updated_at = NOW();

INSERT INTO extractor_patterns (extractor_id, name, pattern, priority, confidence, validation_type, created_at)
SELECT id, '简化公司名称', '([\\u4e00-\\u9fa5]{2,15}(?:有限公司|有限责任公司|股份公司))', 90, 0.85, 'none', NOW()
FROM extractor_configs WHERE code = 'company_name'
ON DUPLICATE KEY UPDATE updated_at = NOW();

-- ================================================
-- 验证初始化结果
-- ================================================
SELECT '========================================' AS '';
SELECT '增强型提取器初始化完成！' AS message;
SELECT '========================================' AS '';

SELECT
    ec.id,
    ec.name,
    ec.code,
    ec.category,
    COUNT(DISTINCT ep.id) AS pattern_count,
    COUNT(DISTINCT eo.id) AS option_count,
    ec.is_system,
    ec.is_active
FROM extractor_configs ec
LEFT JOIN extractor_patterns ep ON ec.id = ep.extractor_id
LEFT JOIN extractor_options eo ON ec.id = eo.extractor_id
WHERE ec.code IN (
    'id_card', 'bank_card', 'phone', 'email', 'date', 'money',
    'ip_address', 'url', 'car_plate', 'company_name'
)
GROUP BY ec.id, ec.name, ec.code, ec.category, ec.is_system, ec.is_active
ORDER BY ec.id;

-- ================================================
-- 提取器分类统计
-- ================================================
SELECT
    '========================================' AS '';
SELECT '提取器分类统计' AS message;
SELECT '========================================' AS '';

SELECT
    category AS '分类',
    COUNT(*) AS '数量',
    GROUP_CONCAT(code ORDER BY code) AS '提取器代码'
FROM extractor_configs
WHERE is_system = TRUE
GROUP BY category
ORDER BY category;
