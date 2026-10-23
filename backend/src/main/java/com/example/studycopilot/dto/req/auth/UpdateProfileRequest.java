package com.example.studycopilot.dto.req.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/** 个人资料修改使用独立版本，避免覆盖其他设备的更新。 */
@Getter
@Setter
public class UpdateProfileRequest {
    @NotBlank
    @Size(max = 50)
    private String nickname;

    @NotNull
    @PositiveOrZero
    private Long expectedVersion;
}
