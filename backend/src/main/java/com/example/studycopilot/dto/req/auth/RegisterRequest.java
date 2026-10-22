/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块请求 DTO
 * 文件名：RegisterRequest.java
 * 创建时间：2026-10-22
 * 描述：注册请求。
 */
package com.example.studycopilot.dto.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 */
@Data
public class RegisterRequest {

    @NotBlank
    private String username;

    @NotBlank
    private String password;

    private String email;
}
