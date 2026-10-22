/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块控制器
 * 文件名：TaskController.java
 * 创建时间：2026-10-22
 * 描述：任务接口控制器。
 */
package com.example.studycopilot.controller.task;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.task.CreateTaskRequest;
import com.example.studycopilot.dto.req.task.UpdateTaskRequest;
import com.example.studycopilot.dto.resp.task.TaskResponse;
import com.example.studycopilot.service.task.TaskService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务接口控制器。
 */
@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @GetMapping
    public Result<List<TaskResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate targetDate,
            @RequestParam(required = false) String status) {
        return Result.success(taskService.list(targetDate, status));
    }

    @PostMapping
    public Result<TaskResponse> create(@Valid @RequestBody CreateTaskRequest request) {
        return Result.success(taskService.create(request));
    }

    @PutMapping("/{id}")
    public Result<TaskResponse> update(@PathVariable Long id, @Valid @RequestBody UpdateTaskRequest request) {
        return Result.success(taskService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        taskService.delete(id);
        return Result.success();
    }
}
