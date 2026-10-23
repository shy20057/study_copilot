package com.example.studycopilot.common.exception;

import com.example.studycopilot.common.errorcode.ScErrorCode;

/** 公开剩余等待秒数，不包含账号或来源标识。 */
public class RateLimitException extends BusinessException {
    private final long retryAfterSeconds;

    public RateLimitException(long retryAfterSeconds) {
        super(ScErrorCode.RATE_LIMIT);
        this.retryAfterSeconds=Math.max(1,retryAfterSeconds);
    }

    public long getRetryAfterSeconds() { return retryAfterSeconds; }
}
