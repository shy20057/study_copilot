package com.example.studycopilot.dto.req.onboarding;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.*;

public record CompleteOnboardingRequest(@NotNull @PositiveOrZero Long version, boolean skip,
    @Size(max=100) String templateId, @Size(max=128) String previewVersion, LocalDate learningDate,
    @Size(max=100) Set<@NotBlank String> itemIds,
    @Size(max=100) Map<@NotBlank String,@NotNull @Valid TemplateItemOverride> overrides) {
    public CompleteOnboardingRequest {
        itemIds=itemIds==null?Collections.emptySortedSet():Collections.unmodifiableSortedSet(new TreeSet<>(itemIds));
        overrides=overrides==null?Map.of():Collections.unmodifiableSortedMap(new TreeMap<>(overrides));
    }
}
