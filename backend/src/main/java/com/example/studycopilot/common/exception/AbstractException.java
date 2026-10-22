/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共异常
 * 文件名：AbstractException.java
 * 创建时间：2026-10-22
 * 描述：抽象异常基类。
 */
package com.example.studycopilot.common.exception;

import com.example.studycopilot.common.result.IErrorCode;
import lombok.Getter;

/**
 * 抽象异常基类。
 */
@Getter
public abstract class AbstractException extends RuntimeException {

    private final String errorCode;
    private final String errorMessage;

    public AbstractException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
        this.errorMessage = errorMessage;
    }

    public AbstractException(IErrorCode errorCode) {
        super(errorCode.message());
        this.errorCode = errorCode.code();
        this.errorMessage = errorCode.message();
    }
}
