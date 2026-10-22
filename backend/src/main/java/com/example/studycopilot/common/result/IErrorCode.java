/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共结果封装
 * 文件名：IErrorCode.java
 * 创建时间：2026-10-22
 * 描述：错误码接口。
 */
package com.example.studycopilot.common.result;

/**
 * 错误码接口。
 */
public interface IErrorCode {

    /**
     * 错误码。
     *
     * @return 错误码字符串
     */
    String code();

    /**
     * 错误信息。
     *
     * @return 错误信息
     */
    String message();
}
