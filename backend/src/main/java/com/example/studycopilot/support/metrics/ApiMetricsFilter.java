package com.example.studycopilot.support.metrics;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/** 固定操作标签的请求指标，不采集用户、正文或带资源 ID 的路径。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
@RequiredArgsConstructor
public class ApiMetricsFilter extends OncePerRequestFilter {
    public static final String ERROR_ATTRIBUTE = ApiMetricsFilter.class.getName() + ".errorCode";
    private final MeterRegistry metrics;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith(request.getContextPath() + "/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Timer.Sample sample = Timer.start(metrics);
        boolean failed = true;
        try {
            chain.doFilter(request, response);
            failed = false;
        } finally {
            String operation = operation(request);
            String outcome = failed ? "5xx" : response.getStatus() / 100 + "xx";
            sample.stop(metrics.timer("study_copilot.api.duration", "operation", operation, "outcome", outcome));
            metrics.counter("study_copilot.api.requests", "operation", operation, "outcome", outcome).increment();
            Object error = request.getAttribute(ERROR_ATTRIBUTE);
            if (error instanceof String code) {
                String normalized = ScErrorCode.fromCode(code, ScErrorCode.SYSTEM_ERROR).code();
                metrics.counter("study_copilot.api.errors", "code", normalized).increment();
            }
        }
    }

    private String operation(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        if (path.startsWith("/api/auth/")) {
            return switch (path) {
                case "/api/auth/register" -> "auth_register";
                case "/api/auth/login" -> "auth_login";
                case "/api/auth/refresh" -> "auth_refresh";
                case "/api/auth/logout" -> "auth_logout";
                default -> "auth_other";
            };
        }
        if (path.endsWith("/restore") && (path.startsWith("/api/tasks/") || path.startsWith("/api/focus/records/"))) {
            return "record_restore";
        }
        if (path.equals("/api/focus/start") || path.equals("/api/focus/break/start")
                || path.equals("/api/focus/break/end-and-start")) {
            return "session_start";
        }
        if (path.equals("/api/focus/current")) {
            return "session_recovery";
        }
        if (path.startsWith("/api/focus/") && (path.endsWith("/complete") || path.endsWith("/end-early"))) {
            return "session_finish";
        }
        if ((path.equals("/api/tasks") || path.startsWith("/api/tasks/")) && !request.getMethod().equals("GET")) {
            return "task_write";
        }
        if (path.equals("/api/account/deletion")) {
            return "account_deletion";
        }
        if (path.startsWith("/api/upload/")) {
            return "avatar";
        }
        return "other";
    }
}
