/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块响应 DTO
 * 文件名：MistakeResponse.java
 * 创建时间：2026-10-22
 * 描述：错题响应。
 */
package com.example.studycopilot.dto.resp.mistake;

import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 错题响应。
 */
@Data
public class MistakeResponse {

    private Long id;
    private Long subjectId;
    private Long knowledgePointId;
    private String title;
    private String contentMarkdown;
    private String imageUrls;
    private String errorReason;
    private String analysis;
    private Integer mastery;
    private Integer reviewStage;
    private LocalDate nextReviewDate;
    private Instant lastReviewTime;
    private LocalDate sourceDate;
    private Instant createTime;
    private Instant updateTime;
}
