/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块数据对象
 * 文件名：TaskDO.java
 * 创建时间：2026-10-22
 * 描述：待办任务表数据对象。
 */
package com.example.studycopilot.dao.entity.task;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 待办任务表数据对象。
 */
@Data
@TableName("study_copilot.t_task")
public class TaskDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 关联阶段 ID */
    private Long milestoneId;

    /** 父任务 ID */
    private Long parentId;

    /** 重复规则 */
    private String repeatRule;

    /** 由哪个重复任务模板生成 */
    private Long repeatSrcId;

    /** 预计番茄数 */
    private Integer plannedPomodoros;

    /** 排序 */
    private Integer sortOrder;

    /** 任务内容 */
    private String title;

    /** 关联科目 ID */
    private Long subjectId;

    /** 状态：TODO/DONE */
    private String status;

    /** 优先级：1低 2中 3高 */
    private Integer priority;

    /** 计划完成日期 */
    private LocalDate targetDate;

    /** 完成时间 */
    private Instant doneTime;

    /** 创建时间 */
    private Instant createTime;
}
