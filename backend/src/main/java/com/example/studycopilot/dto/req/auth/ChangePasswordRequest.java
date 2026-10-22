package com.example.studycopilot.dto.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter @Setter
public class ChangePasswordRequest {
    @NotBlank @Size(max = 4096)
    private String currentPassword;
    @ValidPassword
    private String newPassword;
}
