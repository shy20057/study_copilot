package com.example.studycopilot.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.function.Supplier;

/** 指标采集使用独立运维凭据，不授予普通用户管理员权限。 */
@Component
public class MetricsBearerAuthorization implements AuthorizationManager<RequestAuthorizationContext> {
    @Value("${study-copilot.metrics-token:}")
    private String token;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication, RequestAuthorizationContext context) {
        String header = context.getRequest().getHeader("Authorization");
        boolean allowed = token != null && !token.isBlank() && header != null
                && MessageDigest.isEqual(("Bearer " + token).getBytes(StandardCharsets.UTF_8),
                        header.getBytes(StandardCharsets.UTF_8));
        return new AuthorizationDecision(allowed);
    }
}
