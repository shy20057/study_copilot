/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块响应 DTO
 * 文件名：MilestoneResponse.java
 * 创建时间：2026-10-22
 * 描述：阶段响应。
 */
package com.example.studycopilot.dto.resp.milestone;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 阶段响应。
 */
@Data
public class MilestoneResponse {

    private Long id;
    private String title;
    private String monthTag;
    private LocalDate startDate;
    private LocalDate endDate;
    private String status;
    private String content;
    private Integer sortOrder;
    private Instant createTime;
    private Instant updateTime;
}
