/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块数据对象
 * 文件名：UserSettingDO.java
 * 创建时间：2026-10-22
 * 描述：用户设置表数据对象。
 */
package com.example.studycopilot.dao.entity.setting;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 用户设置表数据对象。
 */
@Data
@TableName("study_copilot.t_user_setting")
public class UserSettingDO {
    private String goalName;
    private Boolean muted;
    private Boolean notificationsEnabled;
    private String notificationPermission;
    private Boolean reduceMotion;
    private Boolean reduceTransparency;
    private Long defaultSubjectId;
    private Long version;
    private Boolean partialRewardsEnabled;
    private Boolean soundEnabled;
    private Boolean animationsEnabled;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 考研初试日期 */
    private LocalDate examDate;

    /** 时区 */
    private String timezone;

    /** 一天的起点(0-6点) */
    private Integer dayStartHour;

    /** 周专注目标(分钟) */
    private Integer weeklyGoalMin;

    /** 默认专注时长 */
    private Integer focusMinutes;

    /** 主题：SYSTEM/LIGHT/DARK */
    private String theme;

    /** 是否同意AI处理日志内容 */
    private Integer aiConsent;

    /** 是否完成新手引导 */
    private Integer onboardingDone;

    /** 每天可用学习时长(分钟) */
    private Integer dailyAvailMin;

    /** 更新时间 */
    private Instant updateTime;
}
