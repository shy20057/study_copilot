/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块请求 DTO
 * 文件名：ReviewMistakeRequest.java
 * 创建时间：2026-10-22
 * 描述：错题复习自评请求。
 */
package com.example.studycopilot.dto.req.mistake;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 错题复习自评请求。
 */
@Data
public class ReviewMistakeRequest {

    /** 自评结果：MASTERED/BLURRY/UNKNOWN */
    @NotNull
    private String result;
}
