/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：安全组件
 * 文件名：JwtProperties.java
 * 创建时间：2026-10-22
 * 描述：JWT 配置属性。
 */
package com.example.studycopilot.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性。
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtProperties {

    /** 签发者 */
    private String issuer;
    /** 密钥 */
    private String secret;
    /** Access Token 有效期（秒） */
    private Long accessTtlSeconds;
    /** Refresh Token 有效期（秒） */
    private Long refreshTtlSeconds;
}
