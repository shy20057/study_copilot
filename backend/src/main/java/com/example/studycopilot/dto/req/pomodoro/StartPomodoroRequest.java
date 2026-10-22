/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块请求 DTO
 * 文件名：StartPomodoroRequest.java
 * 创建时间：2026-10-22
 * 描述：开始番茄钟请求。
 */
package com.example.studycopilot.dto.req.pomodoro;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 开始番茄钟请求。
 */
@Data
public class StartPomodoroRequest {

    @NotNull
    private Long subjectId;

    private Long taskId;

    @NotNull
    private Integer durationMinutes;
}
