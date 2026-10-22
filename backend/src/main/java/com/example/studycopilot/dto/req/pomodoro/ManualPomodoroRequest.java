/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块请求 DTO
 * 文件名：ManualPomodoroRequest.java
 * 创建时间：2026-10-22
 * 描述：手动补录番茄钟请求。
 */
package com.example.studycopilot.dto.req.pomodoro;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

/**
 * 手动补录番茄钟请求。
 */
@Data
public class ManualPomodoroRequest {

    @NotNull
    private Long subjectId;

    private Long taskId;

    @NotNull
    private LocalDate statDate;

    @NotNull
    private LocalTime startTime;

    @NotNull
    private LocalTime endTime;
}
