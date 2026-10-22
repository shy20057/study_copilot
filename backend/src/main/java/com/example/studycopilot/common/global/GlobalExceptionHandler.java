/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：全局异常处理
 * 文件名：GlobalExceptionHandler.java
 * 创建时间：2026-10-22
 * 描述：全局异常拦截与统一返回。
 */
package com.example.studycopilot.common.global;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.*;
import com.example.studycopilot.common.result.FieldViolation;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.support.trace.TraceIdFilter;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Public error messages are catalogued, never copied from exception details or rejected input. */
@RestControllerAdvice
public class GlobalExceptionHandler implements ResponseBodyAdvice<Object> {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String INVALID_FIELD = "字段值不合法";
    private static final String APPLICATION_PACKAGE = "com.example.studycopilot.";
    private static final int MAX_ORIGIN_FRAMES = 5;
    private static final Pattern SAFE_CLASS = Pattern.compile("[A-Za-z0-9_.$]{1,120}");
    private static final Pattern SAFE_METHOD = Pattern.compile("[A-Za-z0-9_$<>]{1,60}");
    private static final Pattern SAFE_TYPE = Pattern.compile("[A-Za-z0-9_$]{1,80}");
    private static final Pattern SAFE_TRACE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    @ExceptionHandler(AbstractException.class)
    public ResponseEntity<Result<Void>> domainException(AbstractException ex) {
        ScErrorCode fallback = ex instanceof NotFoundException ? ScErrorCode.NOT_FOUND
                : ex instanceof ClientException ? ScErrorCode.VALIDATION_ERROR
                : ScErrorCode.BUSINESS_ERROR;
        ScErrorCode code = ex instanceof ServiceException ? ScErrorCode.SYSTEM_ERROR
                : ScErrorCode.fromCode(ex.getErrorCode(), fallback);
        Result<Void> result = Result.failure(code);
        if (ex instanceof BusinessException business) {
            result.setCurrentVersion(business.getCurrentVersion());
            result.setCurrentSession(business.getCurrentSession());
        }
        return ResponseEntity.status(code.httpStatus()).body(result);
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<Void>> validationException(BindException ex) {
        List<FieldViolation> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), INVALID_FIELD)).distinct().toList();
        return validation(fields);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Result<Void>> missingParameter(MissingServletRequestParameterException ex) {
        return validation(List.of(new FieldViolation(ex.getParameterName(), "缺少必填参数")));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Result<Void>> invalidParameter(MethodArgumentTypeMismatchException ex) {
        return validation(List.of(new FieldViolation(ex.getName(), INVALID_FIELD)));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, ConstraintViolationException.class,
            HandlerMethodValidationException.class})
    public ResponseEntity<Result<Void>> invalidRequest(Exception ex) {
        return validation(List.of());
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Result<Void>> authenticationException(AuthenticationException ex) {
        return error(ScErrorCode.UNAUTHORIZED);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Result<Void>> accessDeniedException(AccessDeniedException ex) {
        return error(ScErrorCode.FORBIDDEN);
    }

    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<Result<Void>> notFound(Exception ex) {
        return error(ScErrorCode.NOT_FOUND);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Result<Void>> methodNotAllowed(HttpRequestMethodNotSupportedException ex) {
        return error(ScErrorCode.METHOD_NOT_ALLOWED);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<Void>> unexpectedException(Exception ex) {
        String traceId = MDC.get(TraceIdFilter.MDC_KEY);
        String type = ex.getClass().getSimpleName();
        // Explicit correlation and bounded origins remain useful without throwable messages or filenames.
        log.error("Unhandled request failure traceId={} type={} origins={}",
                traceId != null && SAFE_TRACE.matcher(traceId).matches() ? traceId : "unavailable",
                SAFE_TYPE.matcher(type).matches() ? type : "Exception", applicationOrigins(ex));
        return error(ScErrorCode.SYSTEM_ERROR);
    }

    private String applicationOrigins(Exception ex) {
        String origins = Arrays.stream(ex.getStackTrace())
                .filter(frame -> frame.getClassName().startsWith(APPLICATION_PACKAGE))
                .filter(frame -> SAFE_CLASS.matcher(frame.getClassName()).matches()
                        && SAFE_METHOD.matcher(frame.getMethodName()).matches())
                .map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .distinct().limit(MAX_ORIGIN_FRAMES).collect(Collectors.joining(","));
        return origins.isEmpty() ? "unavailable" : origins;
    }

    private ResponseEntity<Result<Void>> validation(List<FieldViolation> fields) {
        return ResponseEntity.badRequest().body(Result.<Void>failure(ScErrorCode.VALIDATION_ERROR).setFieldErrors(fields));
    }

    private ResponseEntity<Result<Void>> error(ScErrorCode code) {
        return ResponseEntity.status(code.httpStatus()).body(Result.failure(code));
    }

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        return true;
    }

    @Override
    public Object beforeBodyWrite(Object body, MethodParameter returnType, MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType, ServerHttpRequest request,
            ServerHttpResponse response) {
        if (body instanceof Result<?> result && request instanceof ServletServerHttpRequest servletRequest) {
            result.setTraceId((String) servletRequest.getServletRequest().getAttribute(TraceIdFilter.ATTRIBUTE));
        }
        return body;
    }
}
