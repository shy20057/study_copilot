/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块响应 DTO
 * 文件名：LoginResponse.java
 * 创建时间：2026-10-22
 * 描述：登录响应。
 */
package com.example.studycopilot.dto.resp.auth;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录响应。
 */
@Getter @Setter
public class LoginResponse {

    /** Access Token */
    private String accessToken;

    /** Refresh Token */
    private String refreshToken;

    /** 用户 ID */
    private Long userId;

    /** 用户名 */
    private String username;

    private String nextRoute;
}
