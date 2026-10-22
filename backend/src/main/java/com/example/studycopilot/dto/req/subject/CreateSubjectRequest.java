/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块请求 DTO
 * 文件名：CreateSubjectRequest.java
 * 创建时间：2026-10-22
 * 描述：创建科目请求。
 */
package com.example.studycopilot.dto.req.subject;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建科目请求。
 */
@Data
public class CreateSubjectRequest {

    @NotBlank
    @Size(max = 50)
    private String name;

    private String color;

    private Integer sortOrder;
}
