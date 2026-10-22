/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块数据对象
 * 文件名：ReminderSettingDO.java
 * 创建时间：2026-10-22
 * 描述：提醒设置表数据对象。
 */
package com.example.studycopilot.dao.entity.reminder;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalTime;

/**
 * 提醒设置表数据对象。
 */
@Data
@TableName("study_copilot.t_reminder_setting")
public class ReminderSettingDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 提醒类型：STUDY_START/MISTAKE_REVIEW/JOURNAL */
    private String type;

    /** 是否开启 */
    private Integer enabled;

    /** 提醒时间(用户时区) */
    private LocalTime remindTime;

    /** 渠道：IN_APP/BROWSER */
    private String channel;
}
