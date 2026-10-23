/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块数据对象
 * 文件名：SubjectDO.java
 * 创建时间：2026-10-22
 * 描述：科目表数据对象。
 */
package com.example.studycopilot.dao.entity.subject;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 科目表数据对象。
 */
@Data
@TableName("study_copilot.t_subject")
public class SubjectDO {
    private Long version;
    private java.time.Instant createTime;
    private java.time.Instant updateTime;

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目名称 */
    private String name;

    /** 图表颜色 */
    private String color;

    /** 排序 */
    private Integer sortOrder;

    /** 是否归档 */
    private Integer archived;
}
