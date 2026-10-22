/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共异常
 * 文件名：ClientException.java
 * 创建时间：2026-10-22
 * 描述：客户端异常。
 */
package com.example.studycopilot.common.exception;

import com.example.studycopilot.common.result.IErrorCode;
import lombok.Getter;

/**
 * 客户端异常。
 */
@Getter
public class ClientException extends AbstractException {

    public ClientException(String errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public ClientException(IErrorCode errorCode) {
        super(errorCode);
    }
}
