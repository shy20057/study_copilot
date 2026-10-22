/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：安全组件
 * 文件名：JwtTokenProvider.java
 * 创建时间：2026-10-22
 * 描述：JWT Token 生成与解析。
 */
package com.example.studycopilot.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;

/**
 * JWT Token 生成与解析。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    /**
     * 生成 Access Token。
     *
     * @param userId 用户ID
     * @return Token 字符串
     */
    public String generateAccessToken(Long userId) {
        return generateToken(userId, jwtProperties.getAccessTtlSeconds());
    }

    /**
     * 生成 Refresh Token。
     *
     * @param userId 用户ID
     * @return Token 字符串
     */
    public String generateRefreshToken(Long userId) {
        return generateToken(userId, jwtProperties.getRefreshTtlSeconds());
    }

    private String generateToken(Long userId, Long ttlSeconds) {
        Instant now = Instant.now();
        return Jwts.builder()
                .setIssuer(jwtProperties.getIssuer())
                .setSubject(String.valueOf(userId))
                .setIssuedAt(Date.from(now))
                .setExpiration(Date.from(now.plusSeconds(ttlSeconds)))
                .signWith(getSigningKey(), SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 Token 获取用户ID。
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public Long parseUserId(String token) {
        Claims claims = Jwts.parserBuilder()
                .setSigningKey(getSigningKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
        return Long.valueOf(claims.getSubject());
    }

    /**
     * 校验 Token 是否有效。
     *
     * @param token JWT Token
     * @return true 有效，false 无效
     */
    public boolean validateToken(String token) {
        try {
            Jwts.parserBuilder().setSigningKey(getSigningKey()).build().parseClaimsJws(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
