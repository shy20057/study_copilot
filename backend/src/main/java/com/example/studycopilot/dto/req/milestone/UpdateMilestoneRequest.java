/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块请求 DTO
 * 文件名：UpdateMilestoneRequest.java
 * 创建时间：2026-10-22
 * 描述：更新阶段请求。
 */
package com.example.studycopilot.dto.req.milestone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新阶段请求。
 */
@Data
public class UpdateMilestoneRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 7)
    private String monthTag;

    private LocalDate startDate;

    private LocalDate endDate;

    @NotBlank
    private String status;

    private String content;

    private Integer sortOrder;
}
