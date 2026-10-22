/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块控制器
 * 文件名：SubjectController.java
 * 创建时间：2026-10-22
 * 描述：科目接口控制器。
 */
package com.example.studycopilot.controller.subject;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.subject.CreateSubjectRequest;
import com.example.studycopilot.dto.resp.subject.SubjectResponse;
import com.example.studycopilot.service.subject.SubjectService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 科目接口控制器。
 */
@RestController
@RequestMapping("/api/subjects")
@RequiredArgsConstructor
public class SubjectController {

    private final SubjectService subjectService;

    @GetMapping
    public Result<List<SubjectResponse>> list() {
        return Result.success(subjectService.list());
    }

    @PostMapping
    public Result<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
        return Result.success(subjectService.create(request));
    }

    @PutMapping("/{id}")
    public Result<SubjectResponse> update(@PathVariable Long id, @Valid @RequestBody CreateSubjectRequest request) {
        return Result.success(subjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        subjectService.delete(id);
        return Result.success();
    }
}
