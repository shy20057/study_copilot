/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块请求 DTO
 * 文件名：UpdateTaskRequest.java
 * 创建时间：2026-10-22
 * 描述：更新任务请求。
 */
package com.example.studycopilot.dto.req.task;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新任务请求。
 */
@Data
public class UpdateTaskRequest {

    @NotBlank
    @Size(max = 200)
    private String title;

    private Long subjectId;

    private Integer plannedPomodoros;

    private Integer priority;

    private LocalDate targetDate;

    private String status;

    private Integer sortOrder;
}
