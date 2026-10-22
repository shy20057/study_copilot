/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块请求 DTO
 * 文件名：CreateMilestoneRequest.java
 * 创建时间：2026-10-22
 * 描述：创建阶段请求。
 */
package com.example.studycopilot.dto.req.milestone;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建阶段请求。
 */
@Data
public class CreateMilestoneRequest {

    @NotBlank
    @Size(max = 100)
    private String title;

    @NotBlank
    @Size(max = 7)
    private String monthTag;

    private LocalDate startDate;

    private LocalDate endDate;

    private String content;
}
