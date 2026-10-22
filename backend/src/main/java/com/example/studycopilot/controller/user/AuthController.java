/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块控制器
 * 文件名：AuthController.java
 * 创建时间：2026-10-22
 * 描述：认证接口控制器。
 */
package com.example.studycopilot.controller.user;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.auth.LoginRequest;
import com.example.studycopilot.dto.req.auth.RegisterRequest;
import com.example.studycopilot.dto.resp.auth.LoginResponse;
import com.example.studycopilot.service.user.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@RequestBody String refreshToken) {
        return Result.success(authService.refresh(refreshToken));
    }
}
