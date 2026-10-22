/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块控制器
 * 文件名：StatController.java
 * 创建时间：2026-10-22
 * 描述：统计接口控制器。
 */
package com.example.studycopilot.controller.stat;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.resp.stat.StatSummaryResponse;
import com.example.studycopilot.service.stat.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 统计接口控制器。
 */
@RestController
@Profile("future-features")
@RequestMapping("/api/analytics")
@RequiredArgsConstructor
public class StatController {

    private final StatService statService;

    @GetMapping("/summary")
    public Result<StatSummaryResponse> summary(
            @RequestParam String type,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(statService.summary(type, date));
    }

    @GetMapping("/heatmap")
    public Result<Object> heatmap(@RequestParam Integer year) {
        return Result.success(statService.heatmap(year));
    }
}
