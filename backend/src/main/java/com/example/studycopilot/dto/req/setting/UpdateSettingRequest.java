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
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

import java.time.LocalDate;

/**
 * 更新用户设置请求。
 */
@Data
public class UpdateSettingRequest {
    @NotNull @Min(0) private Long version;
    @Size(max=100) private String goalName;
    private Boolean muted;
    private Boolean notificationsEnabled;
    @Pattern(regexp="DEFAULT|GRANTED|DENIED") private String notificationPermission;
    private Boolean reduceMotion;
    private Boolean reduceTransparency;
    private Long defaultSubjectId;
    @com.fasterxml.jackson.annotation.JsonIgnore private boolean weeklyGoalMinProvided;
    public void setWeeklyGoalMin(Integer value) { this.weeklyGoalMin=value; this.weeklyGoalMinProvided=true; }

    private LocalDate examDate;

    @Size(max=50) private String timezone;

    @Min(0)
    @Max(6)
    private Integer dayStartHour;

    @Min(1)
    private Integer weeklyGoalMin;

    @Min(1)
    @Max(240)
    private Integer focusMinutes;

    @Pattern(regexp="SYSTEM|LIGHT|DARK") private String theme;

    @Min(0) @Max(1)
    private Integer aiConsent;

    @Min(1) @Max(1440) private Integer dailyAvailMin;
}
