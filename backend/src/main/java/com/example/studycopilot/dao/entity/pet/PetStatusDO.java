/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块数据对象
 * 文件名：PetStatusDO.java
 * 创建时间：2026-10-22
 * 描述：萌宠状态表数据对象。
 */
package com.example.studycopilot.dao.entity.pet;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 萌宠状态表数据对象。
 */
@Data
@TableName("study_copilot.t_pet_status")
public class PetStatusDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 宠物类型：1粉粉喵 2汪汪柴 3软软兔 4熊熊帕 5啾啾鸡 */
    private Integer petType;

    /** 等级 1-10 */
    private Integer level;

    /** 历史累计经验值 */
    private Integer totalExp;

    /** 更新时间 */
    private Instant updateTime;
}
