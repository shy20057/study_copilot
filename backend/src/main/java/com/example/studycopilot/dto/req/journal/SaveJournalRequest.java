/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块请求 DTO
 * 文件名：SaveJournalRequest.java
 * 创建时间：2026-10-22
 * 描述：保存日志请求。
 */
package com.example.studycopilot.dto.req.journal;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 保存日志请求。
 */
@Data
public class SaveJournalRequest {

    @NotNull
    private LocalDate logDate;

    private String mood;

    private String contentMarkdown;

    private String breakthrough;

    /** 各科目耗时(JSON 字符串) */
    private String subjectHours;
}
