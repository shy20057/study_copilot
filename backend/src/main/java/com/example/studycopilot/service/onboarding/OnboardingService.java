package com.example.studycopilot.service.onboarding;
import com.example.studycopilot.dto.req.onboarding.*;
import com.example.studycopilot.dto.resp.onboarding.*;
import java.time.LocalDate;
import java.util.*;
public interface OnboardingService {
    OnboardingProgress progress();
    OnboardingProgress save(SaveOnboardingProgressRequest request);
    List<TemplateCatalog.Definition> templates();
    TemplatePreview preview(String templateId,Set<String> itemIds,LocalDate learningDate);
    ImportResult complete(CompleteOnboardingRequest request,String requestId);
}
