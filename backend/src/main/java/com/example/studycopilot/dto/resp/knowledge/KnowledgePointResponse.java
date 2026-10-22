/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块响应 DTO
 * 文件名：KnowledgePointResponse.java
 * 创建时间：2026-10-22
 * 描述：知识点响应。
 */
package com.example.studycopilot.dto.resp.knowledge;

import lombok.Data;

/**
 * 知识点响应。
 */
@Data
public class KnowledgePointResponse {

    private Long id;
    private Long subjectId;
    private Long parentId;
    private String name;
    private Integer mastery;
    private Integer sortOrder;
}
