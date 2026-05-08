package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;
import lombok.Getter;

/**
 * LLM调用相关异常基类
 *
 * 用于统一处理所有与大模型调用相关的异常，
 * 便于区分不同类型的错误并采取相应的恢复策略
 *
 * @author Claude Code
 * @since 2025-01-19
 */
@Getter
public abstract class LLMException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private final Integer code;

    /**
     * 错误信息
     */
    private final String message;

    /**
     * 重试建议：是否应该重试此异常
     */
    private final boolean retryable;

    /**
     * 重试延迟建议（毫秒），-1表示使用默认退避策略
     */
    private final long retryDelayMs;

    public LLMException(ErrorCode errorCode, String message, boolean retryable) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
        this.retryable = retryable;
        this.retryDelayMs = -1;
    }

    public LLMException(ErrorCode errorCode, String message, boolean retryable, long retryDelayMs) {
        super(message);
        this.code = errorCode.getCode();
        this.message = message;
        this.retryable = retryable;
        this.retryDelayMs = retryDelayMs;
    }

    public LLMException(ErrorCode errorCode, String message, Throwable cause, boolean retryable) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.message = message;
        this.retryable = retryable;
        this.retryDelayMs = -1;
    }

    public LLMException(ErrorCode errorCode, String message, Throwable cause, boolean retryable, long retryDelayMs) {
        super(message, cause);
        this.code = errorCode.getCode();
        this.message = message;
        this.retryable = retryable;
        this.retryDelayMs = retryDelayMs;
    }

    /**
     * 判断异常是否可重试
     */
    public boolean isRetryable() {
        return retryable;
    }

    /**
     * 获取建议的重试延迟时间（毫秒）
     */
    public long getRetryDelayMs() {
        return retryDelayMs;
    }
}
