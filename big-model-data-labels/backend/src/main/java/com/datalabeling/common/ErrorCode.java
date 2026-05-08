package com.datalabeling.common;

import lombok.Getter;

/**
 * 错误码枚举
 */
@Getter
public enum ErrorCode {

    // ========== 通用错误码 ==========
    SUCCESS(0, "操作成功"),
    SYSTEM_ERROR(500, "系统内部错误"),
    PARAM_ERROR(400, "参数错误"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    REQUEST_TIMEOUT(408, "请求超时"),
    TOO_MANY_REQUESTS(429, "请求过于频繁"),

    // ========== 认证授权相关 1xxx ==========
    UNAUTHORIZED(1001, "未登录或登录已过期"),
    FORBIDDEN(1002, "无权访问"),
    TOKEN_INVALID(1003, "Token无效"),
    TOKEN_EXPIRED(1004, "Token已过期"),
    USERNAME_PASSWORD_ERROR(1005, "用户名或密码错误"),
    ACCOUNT_DISABLED(1006, "账号已被禁用"),
    ACCOUNT_NOT_FOUND(1007, "账号不存在"),

    // ========== 用户相关 2xxx ==========
    USER_NOT_FOUND(2001, "用户不存在"),
    USERNAME_EXIST(2002, "用户名已存在"),
    EMAIL_EXIST(2003, "邮箱已存在"),
    USER_CREATE_FAILED(2004, "用户创建失败"),

    // ========== 标签相关 3xxx ==========
    LABEL_NOT_FOUND(3001, "标签不存在"),
    LABEL_NAME_EXIST(3002, "标签名称已存在"),
    LABEL_IN_USE(3003, "标签正在使用中，无法删除"),
    LABEL_CREATE_FAILED(3004, "标签创建失败"),
    LABEL_UPDATE_FAILED(3005, "标签更新失败"),

    // ========== 文件任务相关 4xxx ==========
    TASK_NOT_FOUND(4001, "任务不存在"),
    FILE_UPLOAD_FAILED(4002, "文件上传失败"),
    FILE_SIZE_EXCEED(4003, "文件大小超过限制"),
    FILE_TYPE_NOT_SUPPORT(4004, "不支持的文件类型"),
    FILE_PARSE_FAILED(4005, "文件解析失败"),
    FILE_ALREADY_EXIST(4006, "文件已存在"),
    TASK_STATUS_INVALID(4007, "任务状态无效"),
    TASK_PROCESSING(4008, "任务正在处理中"),
    TASK_ANALYSIS_FAILED(4009, "任务分析失败"),

    // ========== 数据行相关 5xxx ==========
    DATA_ROW_NOT_FOUND(5001, "数据行不存在"),
    DATA_ROW_UPDATE_FAILED(5002, "数据行更新失败"),

    // ========== 数据库同步相关 6xxx ==========
    SYNC_CONFIG_NOT_FOUND(6001, "同步配置不存在"),
    DB_CONNECTION_FAILED(6002, "数据库连接失败"),
    SYNC_FAILED(6003, "数据同步失败"),

    // ========== DeepSeek API相关 7xxx ==========
    LLM_API_ERROR(7001, "大模型API调用失败"),
    LLM_TIMEOUT(7002, "大模型响应超时"),
    LLM_RATE_LIMIT(7003, "大模型API调用频率超限"),

    // ========== 业务逻辑相关 8xxx ==========
    OPERATION_FAILED(8001, "操作失败"),
    DATA_INTEGRITY_ERROR(8002, "数据完整性错误"),
    CONCURRENT_MODIFICATION(8003, "数据已被其他用户修改"),
    EXPORT_FAILED(8004, "导出失败");

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String message;

    ErrorCode(Integer code, String message) {
        this.code = code;
        this.message = message;
    }
}
