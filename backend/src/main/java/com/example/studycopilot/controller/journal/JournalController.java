/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块控制器
 * 文件名：JournalController.java
 * 创建时间：2026-10-22
 * 描述：日志接口控制器。
 */
package com.example.studycopilot.controller.journal;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.journal.SaveJournalRequest;
import com.example.studycopilot.dto.resp.journal.JournalResponse;
import com.example.studycopilot.service.journal.JournalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 日志接口控制器。
 */
@RestController
@RequestMapping("/api/journal")
@RequiredArgsConstructor
public class JournalController {

    private final JournalService journalService;

    @GetMapping("/{date}")
    public Result<JournalResponse> get(@PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success(journalService.get(date));
    }

    @PostMapping("/save")
    public Result<JournalResponse> save(@Valid @RequestBody SaveJournalRequest request) {
        return Result.success(journalService.save(request));
    }

    @GetMapping("/calendar")
    public Result<Object> calendar(@RequestParam String month) {
        return Result.success(journalService.calendar(month));
    }

    @GetMapping("/search")
    public Result<List<JournalResponse>> search(@RequestParam String keyword) {
        return Result.success(journalService.search(keyword));
    }
}
