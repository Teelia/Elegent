package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;

/**
 * LLM速率限制异常
 *
 * 用于处理API调用频率超限错误（HTTP 429）
 *
 * @author Claude Code
 * @since 2025-01-19
 */
public class LLMRateLimitException extends LLMException {

    private static final long serialVersionUID = 1L;

    /**
     * 建议的重试等待时间（毫秒），从响应头解析
     */
    private final Long retryAfterMs;

    public LLMRateLimitException(ErrorCode errorCode, String message) {
        super(errorCode, message, true, 10000L);  // 可重试，默认等待10秒
        this.retryAfterMs = 10000L;
    }

    public LLMRateLimitException(ErrorCode errorCode, String message, Long retryAfterMs) {
        super(errorCode, message, true, retryAfterMs);  // 可重试，使用服务器返回的等待时间
        this.retryAfterMs = retryAfterMs;
    }

    public Long getRetryAfterMs() {
        return retryAfterMs;
    }
}
