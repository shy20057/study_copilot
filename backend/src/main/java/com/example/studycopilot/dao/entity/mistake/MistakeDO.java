/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块数据对象
 * 文件名：MistakeDO.java
 * 创建时间：2026-10-22
 * 描述：错题表数据对象。
 */
package com.example.studycopilot.dao.entity.mistake;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;
import java.time.LocalDate;

/**
 * 错题表数据对象。
 */
@Data
@TableName("study_copilot.t_mistake")
public class MistakeDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目 ID */
    private Long subjectId;

    /** 关联知识点 ID */
    private Long knowledgePointId;

    /** 标题 */
    private String title;

    /** 题目文字(Markdown/LaTeX) */
    private String contentMarkdown;

    /** 题目/解析图片 URL 数组(JSON) */
    private String imageUrls;

    /** 错因：CARELESS/CONCEPT/UNKNOWN/CALC */
    private String errorReason;

    /** 解析与心得 */
    private String analysis;

    /** 掌握度：0未掌握 1复习中 2已掌握 */
    private Integer mastery;

    /** 当前复习间隔阶段(0-6) */
    private Integer reviewStage;

    /** 下次复习日期 */
    private LocalDate nextReviewDate;

    /** 最后复习时间 */
    private Instant lastReviewTime;

    /** 来源日期 */
    private LocalDate sourceDate;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
