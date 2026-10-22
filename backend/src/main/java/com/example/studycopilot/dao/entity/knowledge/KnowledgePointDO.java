/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块数据对象
 * 文件名：KnowledgePointDO.java
 * 创建时间：2026-10-22
 * 描述：知识点表数据对象。
 */
package com.example.studycopilot.dao.entity.knowledge;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 知识点表数据对象。
 */
@Data
@TableName("study_copilot.t_knowledge_point")
public class KnowledgePointDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 科目 ID */
    private Long subjectId;

    /** 父知识点 ID */
    private Long parentId;

    /** 知识点名称 */
    private String name;

    /** 掌握度：0未学 1学习中 2已掌握 */
    private Integer mastery;

    /** 排序 */
    private Integer sortOrder;

    /** 更新时间 */
    private Instant updateTime;
}
