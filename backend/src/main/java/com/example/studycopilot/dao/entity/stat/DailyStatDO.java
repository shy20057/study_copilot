/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块数据对象
 * 文件名：DailyStatDO.java
 * 创建时间：2026-10-22
 * 描述：每日学习统计汇总表数据对象。
 */
package com.example.studycopilot.dao.entity.stat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 每日学习统计汇总表数据对象。
 */
@Data
@TableName("study_copilot.t_daily_stat")
public class DailyStatDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 统计日期 */
    private LocalDate statDate;

    /** 专注分钟数 */
    private Integer focusMinutes;

    /** 番茄完成数 */
    private Integer pomodoroCount;

    /** 各科目分钟数(JSON) */
    private String subjectMinutes;

    /** 24小时分桶分钟数(JSON) */
    private String hourMinutes;
}
