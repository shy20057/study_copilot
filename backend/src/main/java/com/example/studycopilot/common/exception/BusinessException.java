/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共异常
 * 文件名：BusinessException.java
 * 创建时间：2026-10-22
 * 描述：业务异常。
 */
package com.example.studycopilot.common.exception;

import com.example.studycopilot.common.result.IErrorCode;
import lombok.Getter;

/**
 * 业务异常。
 */
@Getter
public class BusinessException extends AbstractException {

    private final Long currentVersion;
    private final Object currentSession;

    public BusinessException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
        this.currentVersion = null;
        this.currentSession = null;
    }

    public BusinessException(IErrorCode errorCode) {
        this(errorCode, null, null);
    }

    /** Context must be an authorized public DTO, never a persistence entity. */
    public BusinessException(IErrorCode errorCode, Long currentVersion, Object currentSession) {
        super(errorCode);
        this.currentVersion = currentVersion;
        this.currentSession = currentSession;
    }
}
