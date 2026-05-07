package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;

/**
 * LLM通信异常
 *
 * 用于处理网络通信错误、HTTP错误等
 *
 * @author Claude Code
 * @since 2025-01-19
 */
public class LLMCommunicationException extends LLMException {

    private static final long serialVersionUID = 1L;

    /**
     * HTTP状态码
     */
    private final Integer httpStatusCode;

    public LLMCommunicationException(ErrorCode errorCode, String message, Integer httpStatusCode) {
        super(errorCode, message, true, 1000L);  // 可重试，建议延迟1秒
        this.httpStatusCode = httpStatusCode;
    }

    public LLMCommunicationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause, true, 2000L);  // 可重试，建议延迟2秒
        this.httpStatusCode = null;
    }

    public LLMCommunicationException(ErrorCode errorCode, String message, Throwable cause, boolean retryable) {
        super(errorCode, message, cause, retryable, 2000L);
        this.httpStatusCode = null;
    }

    public Integer getHttpStatusCode() {
        return httpStatusCode;
    }
}
