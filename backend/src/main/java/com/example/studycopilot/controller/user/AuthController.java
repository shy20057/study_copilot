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
import com.example.studycopilot.dto.req.auth.RefreshTokenRequest;
import com.example.studycopilot.dto.req.auth.LogoutRequest;
import com.example.studycopilot.dto.resp.auth.LoginResponse;
import com.example.studycopilot.service.user.AuthService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.beans.factory.annotation.Value;
import java.util.Map;

/**
 * 认证接口控制器。
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Value("${study-copilot.recovery-support-channel}")
    private String recoverySupportChannel;

    @PostMapping("/register")
    public Result<LoginResponse> register(@Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String requestId) {
        return Result.success(authService.register(request, requestId));
    }

    @PostMapping("/login")
    public Result<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return Result.success(authService.login(request));
    }

    @PostMapping("/refresh")
    public Result<LoginResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return Result.success(authService.refresh(request));
    }

    @PostMapping("/logout")
    public Result<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request);
        return Result.success();
    }

    @GetMapping("/recovery-info")
    public Result<Map<String, String>> recoveryInfo() {
        return Result.success(Map.of("supportChannel", recoverySupportChannel,
                "message", "受邀内测期间，请通过人工支持渠道申请账户恢复，工作人员会核实账户归属。"));
    }
}
