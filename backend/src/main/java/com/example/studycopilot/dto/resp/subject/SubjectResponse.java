/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块响应 DTO
 * 文件名：SubjectResponse.java
 * 创建时间：2026-10-22
 * 描述：科目响应。
 */
package com.example.studycopilot.dto.resp.subject;

import lombok.Data;

/**
 * 科目响应。
 */
@Data
public class SubjectResponse {

    private Long id;
    private String name;
    private String color;
    private Integer sortOrder;
    private Integer archived;
}
