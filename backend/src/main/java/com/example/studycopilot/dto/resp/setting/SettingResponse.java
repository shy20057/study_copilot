/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块响应 DTO
 * 文件名：SettingResponse.java
 * 创建时间：2026-10-22
 * 描述：用户设置响应。
 */
package com.example.studycopilot.dto.resp.setting;

import lombok.Data;

import java.time.LocalDate;

/**
 * 用户设置响应。
 */
@Data
public class SettingResponse {

    private LocalDate examDate;
    private String timezone;
    private Integer dayStartHour;
    private Integer weeklyGoalMin;
    private Integer focusMinutes;
    private String theme;
    private Integer aiConsent;
    private Integer onboardingDone;
    private Integer dailyAvailMin;
}
