/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块控制器
 * 文件名：MilestoneController.java
 * 创建时间：2026-10-22
 * 描述：阶段接口控制器。
 */
package com.example.studycopilot.controller.milestone;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.milestone.CreateMilestoneRequest;
import com.example.studycopilot.dto.req.milestone.UpdateMilestoneRequest;
import com.example.studycopilot.dto.resp.milestone.MilestoneResponse;
import com.example.studycopilot.service.milestone.MilestoneService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
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
 * 阶段接口控制器。
 */
@RestController
@RequestMapping("/api/milestones")
@RequiredArgsConstructor
public class MilestoneController {

    private final MilestoneService milestoneService;

    @GetMapping
    public Result<List<MilestoneResponse>> list(@RequestParam(required = false) String monthTag) {
        return Result.success(milestoneService.list(monthTag));
    }

    @PostMapping
    public Result<MilestoneResponse> create(@Valid @RequestBody CreateMilestoneRequest request) {
        return Result.success(milestoneService.create(request));
    }

    @PutMapping("/{id}")
    public Result<MilestoneResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateMilestoneRequest request) {
        return Result.success(milestoneService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        milestoneService.delete(id);
        return Result.success();
    }
}
