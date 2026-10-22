/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块响应 DTO
 * 文件名：TaskResponse.java
 * 创建时间：2026-10-22
 * 描述：任务响应。
 */
package com.example.studycopilot.dto.resp.task;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 任务响应。
 */
@Data
public class TaskResponse {

    private Long id;
    private String title;
    private Long subjectId;
    private Long milestoneId;
    private Long parentId;
    private Integer plannedPomodoros;
    private Integer priority;
    private LocalDate targetDate;
    private String status;
    private Integer sortOrder;
    private Instant doneTime;
    private Instant createTime;
}
