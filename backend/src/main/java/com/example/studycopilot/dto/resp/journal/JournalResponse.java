/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块响应 DTO
 * 文件名：JournalResponse.java
 * 创建时间：2026-10-22
 * 描述：日志响应。
 */
package com.example.studycopilot.dto.resp.journal;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 日志响应。
 */
@Data
public class JournalResponse {

    private Long id;
    private LocalDate logDate;
    private String mood;
    private String contentMarkdown;
    private String breakthrough;
    private String subjectHours;
    private String aiReviewReport;
    private Instant aiReviewTime;
    private Integer aiFeedback;
    private Instant createTime;
    private Instant updateTime;
}
