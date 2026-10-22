/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：公共错误码
 * 文件名：ScErrorCode.java
 * 创建时间：2026-10-22
 * 描述：StudyCopilot 统一错误码枚举。
 */
package com.example.studycopilot.common.errorcode;

import com.example.studycopilot.common.result.IErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * StudyCopilot 统一错误码枚举。
 */
@Getter
@AllArgsConstructor
public enum ScErrorCode implements IErrorCode {

    // 通用错误
    SYSTEM_ERROR("SC_001000", "系统异常，请稍后重试"),
    VALIDATION_ERROR("SC_001001", "请求参数错误"),
    NOT_FOUND("SC_001002", "资源不存在"),
    BUSINESS_ERROR("SC_001003", "业务处理失败"),
    METHOD_NOT_ALLOWED("SC_001004", "不支持的请求方法"),
    VERSION_CONFLICT("SC_001005", "数据版本已更新，请重新加载"),
    IDEMPOTENCY_CONFLICT("SC_001006", "相同请求标识的参数不一致"),
    RATE_LIMIT("SC_001007", "请求过于频繁，请稍后重试"),

    // 认证与用户
    USER_NOT_FOUND("SC_002001", "用户不存在"),
    USER_DISABLED("SC_002002", "账号已被禁用"),
    USER_PASSWORD_ERROR("SC_002003", "账号或密码不正确"),
    TOKEN_EXPIRED("SC_002004", "Token 已过期"),
    TOKEN_INVALID("SC_002005", "Token 无效"),
    UNAUTHORIZED("SC_002006", "未登录或登录已过期"),
    // Intentional compatibility alias; SC_002006 normalizes to UNAUTHORIZED and its public message.
    AUTH_REQUIRED("SC_002006", "请先登录"),
    FORBIDDEN("SC_002007", "无权限访问"),
    TOKEN_REVOKED("SC_002008", "登录凭证已撤销，请重新登录"),

    // 设置
    SETTING_NOT_FOUND("SC_003001", "用户设置不存在"),
    RULE_CHANGE_BLOCKED("SC_003002", "存在运行会话，暂时无法修改学习日规则"),

    // 科目
    SUBJECT_NOT_FOUND("SC_004001", "科目不存在"),
    SUBJECT_DUPLICATE("SC_004002", "科目名称已存在"),
    SUBJECT_IN_USE("SC_004003", "科目正在使用中"),
    LAST_ACTIVE_SUBJECT("SC_004004", "不能归档最后一门有效科目"),

    // 阶段
    MILESTONE_NOT_FOUND("SC_005001", "阶段不存在"),
    MILESTONE_STATUS_INVALID("SC_005002", "阶段状态流转不合法"),

    // 任务
    TASK_NOT_FOUND("SC_006001", "任务不存在"),
    TASK_STATUS_INVALID("SC_006002", "任务状态不合法"),

    // 番茄钟
    POMODORO_NOT_FOUND("SC_007001", "番茄钟记录不存在"),
    POMODORO_STATUS_INVALID("SC_007002", "番茄钟状态不合法"),
    POMODORO_DURATION_INVALID("SC_007003", "专注时长不足，无法结算"),
    SESSION_EXISTS("SC_007004", "已存在运行会话"),
    SESSION_NOT_DUE("SC_007005", "会话尚未到计划结束时间"),
    TIME_OVERLAP("SC_007006", "学习时间区间重叠"),

    // 日志
    JOURNAL_NOT_FOUND("SC_008001", "日志不存在"),
    JOURNAL_DUPLICATE("SC_008002", "当日日志已存在"),

    // 萌宠
    PET_NOT_FOUND("SC_009001", "萌宠状态不存在"),
    PET_TYPE_INVALID("SC_009002", "宠物类型不合法"),

    // AI
    AI_RATE_LIMIT("SC_010001", "AI 调用过于频繁"),
    AI_SERVICE_UNAVAILABLE("SC_010002", "AI 服务暂不可用"),

    // 错题
    MISTAKE_NOT_FOUND("SC_011001", "错题不存在"),

    // 知识点
    KNOWLEDGE_POINT_NOT_FOUND("SC_012001", "知识点不存在"),

    // 提醒
    REMINDER_NOT_FOUND("SC_013001", "提醒设置不存在"),

    // 通知
    NOTIFICATION_NOT_FOUND("SC_014001", "站内消息不存在"),
    ;

    private final String code;
    private final String message;

    public HttpStatus httpStatus() {
        return switch (this) {
            case SYSTEM_ERROR, AI_SERVICE_UNAVAILABLE -> HttpStatus.INTERNAL_SERVER_ERROR;
            case VALIDATION_ERROR, MILESTONE_STATUS_INVALID, TASK_STATUS_INVALID,
                    POMODORO_STATUS_INVALID, POMODORO_DURATION_INVALID, PET_TYPE_INVALID -> HttpStatus.BAD_REQUEST;
            case USER_PASSWORD_ERROR, TOKEN_EXPIRED, TOKEN_INVALID, UNAUTHORIZED, AUTH_REQUIRED,
                    TOKEN_REVOKED -> HttpStatus.UNAUTHORIZED;
            case USER_DISABLED, FORBIDDEN -> HttpStatus.FORBIDDEN;
            case NOT_FOUND, USER_NOT_FOUND, SETTING_NOT_FOUND, SUBJECT_NOT_FOUND, MILESTONE_NOT_FOUND,
                    TASK_NOT_FOUND, POMODORO_NOT_FOUND, JOURNAL_NOT_FOUND, PET_NOT_FOUND, MISTAKE_NOT_FOUND,
                    KNOWLEDGE_POINT_NOT_FOUND, REMINDER_NOT_FOUND, NOTIFICATION_NOT_FOUND -> HttpStatus.NOT_FOUND;
            case METHOD_NOT_ALLOWED -> HttpStatus.METHOD_NOT_ALLOWED;
            case RATE_LIMIT, AI_RATE_LIMIT -> HttpStatus.TOO_MANY_REQUESTS;
            default -> HttpStatus.CONFLICT;
        };
    }

    public boolean retryable() {
        return httpStatus() == HttpStatus.INTERNAL_SERVER_ERROR || httpStatus() == HttpStatus.TOO_MANY_REQUESTS;
    }

    public static ScErrorCode fromCode(String code, ScErrorCode fallback) {
        if (UNAUTHORIZED.code.equals(code)) {
            return UNAUTHORIZED;
        }
        for (ScErrorCode candidate : values()) {
            if (candidate.code.equals(code)) {
                return candidate;
            }
        }
        return fallback;
    }

    @Override
    public String code() {
        return code;
    }

    @Override
    public String message() {
        return message;
    }
}
