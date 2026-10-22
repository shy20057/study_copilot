/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块服务接口
 * 文件名：AuthService.java
 * 创建时间：2026-10-22
 * 描述：认证服务接口。
 */
package com.example.studycopilot.service.user;

import com.example.studycopilot.dto.req.auth.LoginRequest;
import com.example.studycopilot.dto.req.auth.RegisterRequest;
import com.example.studycopilot.dto.resp.auth.LoginResponse;

/**
 * 认证服务接口。
 */
public interface AuthService {

    /**
     * 用户注册。
     *
     * @param request 注册请求
     */
    void register(RegisterRequest request);

    /**
     * 用户登录。
     *
     * @param request 登录请求
     * @return 登录响应
     */
    LoginResponse login(LoginRequest request);

    /**
     * 刷新 Token。
     *
     * @param refreshToken Refresh Token
     * @return 登录响应
     */
    LoginResponse refresh(String refreshToken);
}
