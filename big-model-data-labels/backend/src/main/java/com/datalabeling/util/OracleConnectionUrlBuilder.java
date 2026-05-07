package com.datalabeling.util;

import org.apache.commons.lang3.StringUtils;

/**
 * Oracle 连接 URL 构建器
 * 支持多种连接模式：标准、SID、ServiceName、TNS
 *
 * @author DataLabeling
 * @since 2025-01-03
 */
public class OracleConnectionUrlBuilder {

    /**
     * 连接模式常量
     */
    public static final String MODE_STANDARD = "standard";
    public static final String MODE_SID = "sid";
    public static final String MODE_SERVICE_NAME = "service_name";
    public static final String MODE_TNS = "tns";

    /**
     * 构建 Oracle JDBC URL
     *
     * @param mode    连接模式：standard, sid, service_name, tns
     * @param host    主机地址
     * @param port    端口
     * @param sid     Oracle SID（mode=sid 时使用）
     * @param service Oracle Service Name（mode=service_name 时使用）
     * @return JDBC URL
     * @throws IllegalArgumentException 如果参数无效
     */
    public static String buildUrl(String mode, String host, Integer port,
                                   String sid, String service) {
        // 参数校验
        if (StringUtils.isBlank(host)) {
            throw new IllegalArgumentException("主机地址不能为空");
        }
        if (port == null || port <= 0 || port > 65535) {
            throw new IllegalArgumentException("端口号无效: " + port);
        }

        String normalizedMode = StringUtils.defaultString(mode, MODE_STANDARD).toLowerCase();

        switch (normalizedMode) {
            case MODE_SID:
                // 格式: jdbc:oracle:thin:@host:port:sid
                if (StringUtils.isBlank(sid)) {
                    throw new IllegalArgumentException("SID 模式下必须提供 Oracle SID");
                }
                return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);

            case MODE_SERVICE_NAME:
                // 格式: jdbc:oracle:thin:@//host:port/service_name
                if (StringUtils.isBlank(service)) {
                    throw new IllegalArgumentException("Service Name 模式下必须提供 Oracle Service Name");
                }
                return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, service);

            case MODE_TNS:
                // 格式: jdbc:oracle:thin:@TNS别名
                // TNS 模式需要配置 tnsnames.ora，这里简化处理，直接使用 service 作为 TNS 别名
                if (StringUtils.isBlank(service)) {
                    throw new IllegalArgumentException("TNS 模式下必须提供 TNS 别名");
                }
                return String.format("jdbc:oracle:thin:@%s", service);

            case MODE_STANDARD:
            default:
                // 标准模式：自动选择 SID 或 Service Name
                // 优先使用 Service Name（更现代的方式）
                if (StringUtils.isNotBlank(service)) {
                    return String.format("jdbc:oracle:thin:@//%s:%d/%s", host, port, service);
                } else if (StringUtils.isNotBlank(sid)) {
                    return String.format("jdbc:oracle:thin:@%s:%d:%s", host, port, sid);
                } else {
                    throw new IllegalArgumentException(
                        "标准模式下必须提供 SID 或 Service Name");
                }
        }
    }

    /**
     * 根据目标 Oracle 版本选择合适的驱动类名
     *
     * @param oracleVersion 目标 Oracle 版本（如 "11g", "12c", "19c", "21c"）
     * @return 驱动类名
     */
    public static String selectDriverClass(String oracleVersion) {
        if (StringUtils.isBlank(oracleVersion)) {
            return "oracle.jdbc.OracleDriver"; // 默认驱动
        }

        // 所有版本都使用相同的驱动类名
        // ojdbc8、ojdbc10、ojdbc11 的驱动类名都是 oracle.jdbc.OracleDriver
        // 区别在于支持的 Java 版本和 Oracle 版本不同
        return "oracle.jdbc.OracleDriver";
    }

    /**
     * 验证连接参数是否有效
     *
     * @param host    主机地址
     * @param port    端口
     * @param sid     Oracle SID
     * @param service Oracle Service Name
     * @param mode    连接模式
     * @return 验证结果
     */
    public static ValidationResult validateParams(String host, Integer port,
                                                   String sid, String service, String mode) {
        ValidationResult result = new ValidationResult();

        if (StringUtils.isBlank(host)) {
            result.addError("主机地址不能为空");
        }

        if (port == null || port <= 0 || port > 65535) {
            result.addError("端口号无效，范围应在 1-65535 之间");
        }

        String normalizedMode = StringUtils.defaultString(mode, MODE_STANDARD).toLowerCase();

        // 检查 Oracle 连接标识符
        if (MODE_SID.equals(normalizedMode)) {
            if (StringUtils.isBlank(sid)) {
                result.addError("SID 模式下必须提供 Oracle SID");
            }
        } else if (MODE_SERVICE_NAME.equals(normalizedMode)) {
            if (StringUtils.isBlank(service)) {
                result.addError("Service Name 模式下必须提供 Oracle Service Name");
            }
        } else if (MODE_TNS.equals(normalizedMode)) {
            if (StringUtils.isBlank(service)) {
                result.addError("TNS 模式下必须提供 TNS 别名");
            }
        } else if (MODE_STANDARD.equals(normalizedMode)) {
            if (StringUtils.isBlank(sid) && StringUtils.isBlank(service)) {
                result.addError("标准模式下必须提供 SID 或 Service Name");
            }
        } else {
            result.addError("不支持的连接模式: " + mode);
        }

        return result;
    }

    /**
     * 验证结果类
     */
    public static class ValidationResult {
        private boolean valid = true;
        private String message = "";

        public void addError(String error) {
            this.valid = false;
            if (StringUtils.isNotBlank(message)) {
                message += "; ";
            }
            message += error;
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }
    }
}
