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
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.Instant;
import java.util.Date;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;

/**
 * JWT Token 生成与解析。
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtProperties jwtProperties;

    public String generateAccessToken(long userId, long version, long sessionId, Instant issuedAt, Instant expiresAt) {
        return builder(userId, issuedAt, expiresAt).setId(Long.toString(sessionId))
                .claim("type", "access").claim("sessionVersion", version)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    public String generateRefreshToken(long userId, long sessionId, Instant issuedAt, Instant expiresAt) {
        return builder(userId, issuedAt, expiresAt).claim("type", "refresh").claim("refreshSessionId", sessionId)
                .signWith(getSigningKey(), SignatureAlgorithm.HS256).compact();
    }

    private io.jsonwebtoken.JwtBuilder builder(long userId, Instant issuedAt, Instant expiresAt) {
        return Jwts.builder().setIssuer(jwtProperties.getIssuer()).setSubject(Long.toString(userId))
                .setIssuedAt(Date.from(issuedAt)).setExpiration(Date.from(expiresAt));
    }

    public Claims parseAccess(String token) { return parse(token, "access", "sessionVersion"); }
    public Claims parseRefresh(String token) { return parse(token, "refresh", "refreshSessionId"); }

    private Claims parse(String token, String type, String numericClaim) {
        try {
            if (token == null || token.length() > 4096) throw new IllegalArgumentException();
            Claims claims = Jwts.parserBuilder().requireIssuer(jwtProperties.getIssuer()).require("type", type)
                    .setSigningKey(getSigningKey()).build().parseClaimsJws(token).getBody();
            long userId = Long.parseLong(claims.getSubject());
            if(type.equals("access") && Long.parseLong(claims.getId())<1) throw new IllegalArgumentException();
            Object number = claims.get(numericClaim);
            if (userId <= 0 || !(number instanceof Integer || number instanceof Long)
                    || ((Number) number).longValue() < (type.equals("refresh") ? 1 : 0)
                    || claims.getIssuedAt() == null || claims.getExpiration() == null
                    || !claims.getExpiration().after(claims.getIssuedAt())
                    || claims.getIssuedAt().toInstant().isAfter(Instant.now())) throw new IllegalArgumentException();
            return claims;
        } catch (JwtException | IllegalArgumentException ex) {
            // Parser messages can contain token fragments and are deliberately discarded.
            throw new BusinessException(ScErrorCode.UNAUTHORIZED);
        }
    }

    private Key getSigningKey() {
        return Keys.hmacShaKeyFor(jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
