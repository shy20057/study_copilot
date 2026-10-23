package com.example.studycopilot.security;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/** Dedicated stable fingerprint key; never derive it from JWT signing material. */
@Component
@ConfigurationProperties(prefix = "study-copilot.idempotency")
@Getter @Setter
public class IdempotencyProperties {
    private String hmacSecret;

    @PostConstruct
    void validate() {
        // Fixed diagnostic: Bean Validation binding diagnostics can echo rejected secret values.
        if (hmacSecret == null || hmacSecret.isBlank() || hmacSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("A dedicated idempotency HMAC secret of at least 32 UTF-8 bytes is required");
        }
    }
}
