/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共结果封装
 * 文件名：Result.java
 * 创建时间：2026-10-22
 * 描述：统一接口返回结果。
 */
package com.example.studycopilot.common.result;

import lombok.Data;
import lombok.experimental.Accessors;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import org.slf4j.MDC;

import java.io.Serializable;
import java.util.List;

/**
 * 统一接口返回结果。
 *
 * @param <T> 数据类型
 */
@Data
@Accessors(chain = true)
@JsonInclude(JsonInclude.Include.ALWAYS)
public class Result<T> implements Serializable {

    public static final String SUCCESS_CODE = "0";
    public static final String SUCCESS_MESSAGE = "操作成功";

    /** 错误码 */
    private String code;
    /** 错误信息 */
    private String message;
    /** 业务数据 */
    private T data;
    /** 请求唯一标识 */
    private String traceId = MDC.get("traceId");
    private boolean retryable;
    private List<FieldViolation> fieldErrors = List.of();
    private Long currentVersion;
    private Object currentSession;

    /** Source-compatible alias; the wire contract exposes traceId only. */
    @Deprecated
    @JsonIgnore
    public String getRequestId() {
        return traceId;
    }

    @Deprecated
    public Result<T> setRequestId(String requestId) {
        this.traceId = requestId;
        return this;
    }

    @JsonIgnore
    public boolean isSuccess() {
        return SUCCESS_CODE.equals(code);
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage(SUCCESS_MESSAGE);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> success(T data, String message) {
        Result<T> result = new Result<>();
        result.setCode(SUCCESS_CODE);
        result.setMessage(message);
        result.setData(data);
        return result;
    }

    public static <T> Result<T> failure(String errorCode, String errorMessage) {
        Result<T> result = new Result<>();
        result.setCode(errorCode);
        result.setMessage(errorMessage);
        return result;
    }

    public static <T> Result<T> failure(IErrorCode errorCode) {
        Result<T> result = new Result<>();
        result.setCode(errorCode.code());
        result.setMessage(errorCode.message());
        if (errorCode instanceof ScErrorCode scErrorCode) {
            result.setRetryable(scErrorCode.retryable());
        }
        return result;
    }
}
