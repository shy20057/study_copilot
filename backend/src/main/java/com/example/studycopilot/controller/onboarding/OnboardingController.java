package com.example.studycopilot.controller.onboarding;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.onboarding.*;
import com.example.studycopilot.dto.resp.onboarding.*;
import com.example.studycopilot.service.onboarding.OnboardingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/onboarding") @RequiredArgsConstructor
public class OnboardingController {
    private final OnboardingService service;
    @GetMapping("/progress") public Result<OnboardingProgress> progress(){return Result.success(service.progress());}
    @PutMapping("/progress") public Result<OnboardingProgress> save(@Valid @RequestBody SaveOnboardingProgressRequest request){return Result.success(service.save(request));}
    @PostMapping("/complete") public Result<ImportResult> complete(@Valid @RequestBody CompleteOnboardingRequest request,@RequestHeader(value="Idempotency-Key",required=false) String requestId){return Result.success(service.complete(request,requestId));}
}
