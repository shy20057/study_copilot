package com.example.studycopilot.dto.req.onboarding;

import jakarta.validation.constraints.*;

/** Templates create top-level tasks only; hierarchy and owner IDs cannot be supplied. */
public record TemplateItemOverride(@Size(min=1,max=200) String title,
    @Positive Long subjectId, @PositiveOrZero Long subjectVersion,
    @Min(1) @Max(16) Integer plannedPomodoros) {
    public TemplateItemOverride { if(title!=null) title=title.trim(); }
}
