package com.example.studycopilot.security;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.support.trace.TraceIdFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {
    private final ObjectMapper json;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response, AuthenticationException ex) throws IOException {
        write(request, response, ScErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response, AccessDeniedException ex) throws IOException {
        write(request, response, ScErrorCode.FORBIDDEN);
    }

    private void write(HttpServletRequest request, HttpServletResponse response, ScErrorCode code) throws IOException {
        Result<Void> body = Result.<Void>failure(code).setTraceId((String) request.getAttribute(TraceIdFilter.ATTRIBUTE));
        response.setStatus(code.httpStatus().value());
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        json.writeValue(response.getOutputStream(), body);
    }
}
