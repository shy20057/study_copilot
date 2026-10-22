package com.example.studycopilot.dto.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class RefreshTokenRequest {
    @NotBlank @Size(max = 4096)
    private String refreshToken;
}
