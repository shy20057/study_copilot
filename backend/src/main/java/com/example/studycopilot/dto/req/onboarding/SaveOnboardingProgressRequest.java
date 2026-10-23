package com.example.studycopilot.dto.req.onboarding;

import jakarta.validation.constraints.*;
import java.util.Map;

public record SaveOnboardingProgressRequest(
    @NotNull @PositiveOrZero Long version,
    @NotBlank @Pattern(regexp="WELCOME|PET|GOAL|SUBJECTS|TEMPLATE") String currentStep,
    @NotNull Map<String,Object> confirmedData) {}
