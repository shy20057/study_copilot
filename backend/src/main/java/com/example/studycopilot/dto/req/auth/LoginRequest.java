/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块请求 DTO
 * 文件名：LoginRequest.java
 * 创建时间：2026-10-22
 * 描述：登录请求。
 */
package com.example.studycopilot.dto.req.auth;

import lombok.Getter;
import lombok.Setter;

/**
 * 登录请求。
 */
@Getter @Setter
public class LoginRequest {

    private String username;

    private String password;
}
