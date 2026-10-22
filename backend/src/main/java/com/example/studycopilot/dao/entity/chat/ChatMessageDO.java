/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：AI对话模块数据对象
 * 文件名：ChatMessageDO.java
 * 创建时间：2026-10-22
 * 描述：AI对话消息表数据对象。
 */
package com.example.studycopilot.dao.entity.chat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * AI对话消息表数据对象。
 */
@Data
@TableName("study_copilot.t_chat_message")
public class ChatMessageDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 会话 ID */
    private String sessionId;

    /** 角色：user/assistant */
    private String role;

    /** 消息内容 */
    private String content;

    /** 创建时间 */
    private Instant createTime;
}
