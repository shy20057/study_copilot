/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块数据对象
 * 文件名：DailyJournalDO.java
 * 创建时间：2026-10-22
 * 描述：每日日志表数据对象。
 */
package com.example.studycopilot.dao.entity.journal;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 每日日志表数据对象。
 */
@Data
@TableName("study_copilot.t_daily_journal")
public class DailyJournalDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 日志日期 */
    private LocalDate logDate;

    /** 情绪：PUMPED/STEADY/TIRED/ANXIOUS */
    private String mood;

    /** Markdown 日志正文 */
    private String contentMarkdown;

    /** 今日核心突破 */
    private String breakthrough;

    /** 手动录入各科目耗时(JSON) */
    private String subjectHours;

    /** AI 复盘报告 */
    private String aiReviewReport;

    /** AI 复盘生成时间 */
    private Instant aiReviewTime;

    /** AI 反馈：1赞 -1踩 */
    private Integer aiFeedback;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
