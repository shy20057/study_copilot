/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块数据对象
 * 文件名：PomodoroDO.java
 * 创建时间：2026-10-22
 * 描述：番茄钟记录表数据对象。
 */
package com.example.studycopilot.dao.entity.pomodoro;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 番茄钟记录表数据对象。
 */
@Data
@TableName("study_copilot.t_pomodoro")
public class PomodoroDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 关联任务 ID */
    private Long taskId;

    /** 任务名称快照 */
    private String taskName;

    /** 科目 ID */
    private Long subjectId;

    /** 模式：FOCUS/SHORT/LONG */
    private String mode;

    /** 状态：RUNNING/COMPLETED/ABANDONED */
    private String status;

    /** 来源：TIMER/MANUAL */
    private String source;

    /** 开始时间 */
    private Instant startTime;

    /** 结束时间 */
    private Instant endTime;

    /** 实际专注分钟 */
    private Integer durationMinutes;

    /** 获得经验值 */
    private Integer expEarned;

    /** 归属统计日 */
    private LocalDate statDate;

    /** 创建时间 */
    private Instant createTime;
}
