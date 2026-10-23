/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块控制器
 * 文件名：PomodoroController.java
 * 创建时间：2026-10-22
 * 描述：番茄钟接口控制器。
 */
package com.example.studycopilot.controller.pomodoro;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.pomodoro.ManualPomodoroRequest;
import com.example.studycopilot.dto.req.pomodoro.StartPomodoroRequest;
import com.example.studycopilot.dto.resp.pomodoro.PomodoroResponse;
import com.example.studycopilot.service.pomodoro.PomodoroService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 番茄钟接口控制器。
 */
@org.springframework.context.annotation.Profile("legacy-api")
@RestController
@RequestMapping("/api/pomodoro")
@RequiredArgsConstructor
public class PomodoroController {

    private final PomodoroService pomodoroService;

    @PostMapping("/start")
    public Result<PomodoroResponse> start(@Valid @RequestBody StartPomodoroRequest request) {
        return Result.success(pomodoroService.start(request));
    }

    @GetMapping("/current")
    public Result<PomodoroResponse> current() {
        return Result.success(pomodoroService.current());
    }

    @PostMapping("/complete/{id}")
    public Result<PomodoroResponse> complete(@PathVariable Long id) {
        return Result.success(pomodoroService.complete(id));
    }

    @PostMapping("/abandon/{id}")
    public Result<PomodoroResponse> abandon(@PathVariable Long id) {
        return Result.success(pomodoroService.abandon(id));
    }

    @PostMapping("/manual")
    public Result<PomodoroResponse> manual(@Valid @RequestBody ManualPomodoroRequest request) {
        return Result.success(pomodoroService.manual(request));
    }

    @GetMapping
    public Result<List<PomodoroResponse>> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate statDate) {
        return Result.success(pomodoroService.list(statDate));
    }
}
