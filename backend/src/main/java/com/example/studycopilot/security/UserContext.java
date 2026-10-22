/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：安全组件
 * 文件名：UserContext.java
 * 创建时间：2026-10-22
 * 描述：当前登录用户上下文。
 */
package com.example.studycopilot.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.InsufficientAuthenticationException;

/**
 * 当前登录用户上下文工具。
 */
public class UserContext {

    /**
     * 获取当前登录用户ID。
     *
     * @return 用户ID
     */
    public static Long getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof Long id && id > 0) {
            return (Long) authentication.getPrincipal();
        }
        return null;
    }

    public static Long requireUserId() {
        Long id = getCurrentUserId();
        if (id == null) throw new InsufficientAuthenticationException("Authentication required");
        return id;
    }
}
