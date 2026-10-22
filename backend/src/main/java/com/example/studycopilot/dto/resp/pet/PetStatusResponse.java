/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块响应 DTO
 * 文件名：PetStatusResponse.java
 * 创建时间：2026-10-22
 * 描述：萌宠状态响应。
 */
package com.example.studycopilot.dto.resp.pet;

import lombok.Data;

/**
 * 萌宠状态响应。
 */
@Data
public class PetStatusResponse {

    private Integer petType;
    private Integer level;
    private Integer totalExp;
    private Integer nextLevelExp;
    private String stage;
}
