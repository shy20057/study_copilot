/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：安全组件
 * 文件名：JwtAuthenticationFilter.java
 * 创建时间：2026-10-22
 * 描述：JWT 认证过滤器。
 */
package com.example.studycopilot.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import com.example.studycopilot.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * JWT 认证过滤器。
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final JdbcTemplate jdbc;
    private final SecurityErrorHandler errors;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            String token = bearerToken.substring(7);
            try {
                var claims = jwtTokenProvider.parseAccess(token);
                Long userId = Long.valueOf(claims.getSubject());
                Long version = ((Number) claims.get("sessionVersion")).longValue();
                boolean active = Boolean.TRUE.equals(jdbc.queryForObject("""
                        select exists(select 1 from study_copilot.t_user u
                          join study_copilot.t_refresh_session s on s.user_id=u.id and s.id=?
                        where u.id=? and u.status=1 and u.deleted=0 and u.session_version=?
                          and s.revoked_at is null and s.expires_at>now() and s.session_version=u.session_version)
                        """, Boolean.class, Long.parseLong(claims.getId()), userId, version));
                if (!active) {
                    SecurityContextHolder.clearContext();
                    filterChain.doFilter(request, response);
                    return;
                }
                jdbc.update("update study_copilot.t_user set last_seen_at=clock_timestamp() where id=? and status=1 and deleted=0 and (last_seen_at is null or last_seen_at<clock_timestamp()-interval '1 minute')",userId);
                UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(userId, null, Collections.emptyList());
                authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (BusinessException ex) {
                SecurityContextHolder.clearContext();
            } catch (org.springframework.dao.DataAccessException ex) {
                SecurityContextHolder.clearContext();
                errors.unavailable(request,response);
                return;
            }
        }
        filterChain.doFilter(request, response);
    }
}
