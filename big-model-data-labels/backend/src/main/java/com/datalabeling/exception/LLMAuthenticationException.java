package com.datalabeling.exception;

import com.datalabeling.common.ErrorCode;

/**
 * LLM认证异常
 *
 * 用于处理API Key认证失败、权限不足等错误
 *
 * @author Claude Code
 * @since 2025-01-19
 */
public class LLMAuthenticationException extends LLMException {

    private static final long serialVersionUID = 1L;

    public LLMAuthenticationException(ErrorCode errorCode, String message) {
        super(errorCode, message, false);  // 认证错误不可重试
    }

    public LLMAuthenticationException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause, false);  // 认证错误不可重试
    }
}
