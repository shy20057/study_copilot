/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共异常
 * 文件名：NotFoundException.java
 * 创建时间：2026-10-22
 * 描述：资源不存在异常。
 */
package com.example.studycopilot.common.exception;

import com.example.studycopilot.common.result.IErrorCode;
import lombok.Getter;

/**
 * 资源不存在异常。
 */
@Getter
public class NotFoundException extends AbstractException {

    public NotFoundException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public NotFoundException(IErrorCode errorCode) {
        super(errorCode);
    }
}
