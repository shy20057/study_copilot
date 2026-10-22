/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块请求 DTO
 * 文件名：UpdateSettingRequest.java
 * 创建时间：2026-10-22
 * 描述：更新用户设置请求。
 */
package com.example.studycopilot.dto.req.setting;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新用户设置请求。
 */
@Data
public class UpdateSettingRequest {

    private LocalDate examDate;

    private String timezone;

    @Min(0)
    private Integer dayStartHour;

    @Min(0)
    private Integer weeklyGoalMin;

    @Min(1)
    private Integer focusMinutes;

    private String theme;

    @NotNull
    private Integer aiConsent;

    private Integer dailyAvailMin;
}
