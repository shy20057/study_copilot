package com.example.studycopilot.dto.resp.onboarding;
import java.time.Instant;
import java.util.Map;
public record OnboardingProgress(String currentStep,Map<String,Object> confirmedData,String status,long version,Instant completedAt) {}
