/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：AI对话模块 Mapper
 * 文件名：ChatMessageMapper.java
 * 创建时间：2026-10-22
 * 描述：AI对话消息表 Mapper。
 */
package com.example.studycopilot.dao.mapper.chat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.chat.ChatMessageDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI对话消息表 Mapper。
 */
@Mapper
public interface ChatMessageMapper extends BaseMapper<ChatMessageDO> {
}
