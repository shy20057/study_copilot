/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块控制器
 * 文件名：MistakeController.java
 * 创建时间：2026-10-22
 * 描述：错题接口控制器。
 */
package com.example.studycopilot.controller.mistake;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.mistake.CreateMistakeRequest;
import com.example.studycopilot.dto.req.mistake.ReviewMistakeRequest;
import com.example.studycopilot.dto.resp.mistake.MistakeResponse;
import com.example.studycopilot.service.mistake.MistakeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 错题接口控制器。
 */
@RestController
@Profile("future-features")
@RequestMapping("/api/mistakes")
@RequiredArgsConstructor
public class MistakeController {

    private final MistakeService mistakeService;

    @GetMapping
    public Result<List<MistakeResponse>> list(
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Integer mastery,
            @RequestParam(required = false) String errorReason) {
        return Result.success(mistakeService.list(subjectId, mastery, errorReason));
    }

    @PostMapping
    public Result<MistakeResponse> create(@Valid @RequestBody CreateMistakeRequest request) {
        return Result.success(mistakeService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MistakeResponse> update(@PathVariable Long id, @Valid @RequestBody CreateMistakeRequest request) {
        return Result.success(mistakeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        mistakeService.delete(id);
        return Result.success();
    }

    @GetMapping("/review/today")
    public Result<List<MistakeResponse>> todayReview() {
        return Result.success(mistakeService.todayReview());
    }

    @PostMapping("/{id}/review")
    public Result<Void> review(@PathVariable Long id, @Valid @RequestBody ReviewMistakeRequest request) {
        mistakeService.review(id, request);
        return Result.success();
    }

    @GetMapping("/stats")
    public Result<Object> stats() {
        return Result.success(mistakeService.stats());
    }
}
