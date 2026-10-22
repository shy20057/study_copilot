/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块数据对象
 * 文件名：MilestoneDO.java
 * 创建时间：2026-10-22
 * 描述：阶段时间轴表数据对象。
 */
package com.example.studycopilot.dao.entity.milestone;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 阶段时间轴表数据对象。
 */
@Data
@TableName("study_copilot.t_milestone")
public class MilestoneDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 阶段标题 */
    private String title;

    /** 月份标识 */
    private String monthTag;

    /** 阶段开始日期 */
    private LocalDate startDate;

    /** 阶段结束日期 */
    private LocalDate endDate;

    /** 状态：TODO/IN_PROGRESS/DONE/REVIEW */
    private String status;

    /** 阶段目标、教材章节、重点考点 */
    private String content;

    /** 排序 */
    private Integer sortOrder;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
