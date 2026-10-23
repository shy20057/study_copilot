package com.example.studycopilot.dto.req.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 注销前重新验证密码，并显式确认数据删除。 */
@Getter
@Setter
public class DeleteAccountRequest {
    @NotBlank
    @Size(max = 4096)
    private String currentPassword;

    @AssertTrue
    private boolean confirmDeletion;
}
