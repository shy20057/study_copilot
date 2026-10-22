/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块响应 DTO
 * 文件名：PomodoroResponse.java
 * 创建时间：2026-10-22
 * 描述：番茄钟响应。
 */
package com.example.studycopilot.dto.resp.pomodoro;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 番茄钟响应。
 */
@Data
public class PomodoroResponse {

    private Long id;
    private Long taskId;
    private String taskName;
    private Long subjectId;
    private String mode;
    private String status;
    private String source;
    private Instant startTime;
    private Instant endTime;
    private Integer durationMinutes;
    private Integer expEarned;
    private LocalDate statDate;
    private Instant createTime;
}
