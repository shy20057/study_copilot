/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块请求 DTO
 * 文件名：CreateMistakeRequest.java
 * 创建时间：2026-10-22
 * 描述：创建错题请求。
 */
package com.example.studycopilot.dto.req.mistake;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建错题请求。
 */
@Data
public class CreateMistakeRequest {

    @NotNull
    private Long subjectId;

    private Long knowledgePointId;

    private String title;

    private String contentMarkdown;

    /** 图片 URL 数组(JSON) */
    private String imageUrls;

    private String errorReason;

    private String analysis;

    private LocalDate sourceDate;
}
