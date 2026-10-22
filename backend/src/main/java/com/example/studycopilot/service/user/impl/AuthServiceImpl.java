/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块服务实现
 * 文件名：AuthServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：认证服务实现。
 */
package com.example.studycopilot.service.user.impl;

import com.example.studycopilot.dto.req.auth.LoginRequest;
import com.example.studycopilot.dto.req.auth.RegisterRequest;
import com.example.studycopilot.dto.resp.auth.LoginResponse;
import com.example.studycopilot.service.user.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    @Override
    public void register(RegisterRequest request) {
        // TODO: 实现用户注册逻辑
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        // TODO: 实现用户登录逻辑
        return null;
    }

    @Override
    public LoginResponse refresh(String refreshToken) {
        // TODO: 实现 Token 刷新逻辑
        return null;
    }
}
