package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;

/**
 * LLM序列化异常
 *
 * 用于处理JSON序列化/反序列化错误
 *
 * @author Claude Code
 * @since 2025-01-19
 */
public class LLMSerializationException extends LLMException {

    private static final long serialVersionUID = 1L;

    /**
     * 序列化类型：REQUEST(请求序列化) 或 RESPONSE(响应反序列化)
     */
    private final String serializationType;

    public LLMSerializationException(ErrorCode errorCode, String message, String serializationType) {
        super(errorCode, message, false);  // 序列化错误不可重试
        this.serializationType = serializationType;
    }

    public LLMSerializationException(ErrorCode errorCode, String message, Throwable cause, String serializationType) {
        super(errorCode, message, cause, false);  // 序列化错误不可重试
        this.serializationType = serializationType;
    }

    public String getSerializationType() {
        return serializationType;
    }
}
