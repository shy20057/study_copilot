/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：通知模块数据对象
 * 文件名：NotificationDO.java
 * 创建时间：2026-10-22
 * 描述：站内消息表数据对象。
 */
package com.example.studycopilot.dao.entity.notification;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 站内消息表数据对象。
 */
@Data
@TableName("study_copilot.t_notification")
public class NotificationDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 消息类型 */
    private String type;

    /** 消息内容 */
    private String content;

    /** 是否已读 */
    private Integer isRead;

    /** 创建时间 */
    private Instant createTime;
}
