package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;

/**
 * LLM超时异常
 *
 * 用于处理请求超时错误
 *
 * @author Claude Code
 * @since 2025-01-19
 */
public class LLMTimeoutException extends LLMException {

    private static final long serialVersionUID = 1L;

    /**
     * 超时时长（毫秒）
     */
    private final Long timeoutMs;

    public LLMTimeoutException(ErrorCode errorCode, String message, Long timeoutMs) {
        super(errorCode, message, true, 5000L);  // 可重试，建议延迟5秒
        this.timeoutMs = timeoutMs;
    }

    public LLMTimeoutException(ErrorCode errorCode, String message, Long timeoutMs, Throwable cause) {
        super(errorCode, message, cause, true, 5000L);  // 可重试，建议延迟5秒
        this.timeoutMs = timeoutMs;
    }

    public Long getTimeoutMs() {
        return timeoutMs;
    }
}
