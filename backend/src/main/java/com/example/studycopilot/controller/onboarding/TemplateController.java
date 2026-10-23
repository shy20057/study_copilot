package com.example.studycopilot.controller.onboarding;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.resp.onboarding.TemplatePreview;
import com.example.studycopilot.service.onboarding.*;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.*;
@RestController @RequestMapping("/api/templates") @RequiredArgsConstructor
public class TemplateController {
    private final OnboardingService service;
    @GetMapping public Result<List<TemplateCatalog.Definition>> list(){return Result.success(service.templates());}
    @GetMapping("/{id}/preview") public Result<TemplatePreview> preview(@PathVariable("id") String id,
        @RequestParam(value="itemIds",required=false) Set<String> itemIds,
        @RequestParam(value="learningDate",required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date){return Result.success(service.preview(id,itemIds,date));}
}
