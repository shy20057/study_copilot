/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：AI对话模块服务接口
 * 文件名：ChatService.java
 * 创建时间：2026-10-22
 * 描述：AI对话服务接口。
 */
package com.example.studycopilot.service.chat;

import com.example.studycopilot.dao.entity.chat.ChatMessageDO;

import java.util.List;

/**
 * AI对话服务接口。
 */
public interface ChatService {

    /**
     * 发送消息（SSE 流式）。
     *
     * @param sessionId 会话ID
     * @param content 消息内容
     * @return 会话ID
     */
    String chat(String sessionId, String content);

    /**
     * 获取会话历史。
     *
     * @param sessionId 会话ID
     * @return 消息列表
     */
    List<ChatMessageDO> history(String sessionId);

    /**
     * AI 日志复盘。
     *
     * @param date 日期
     * @return 复盘报告
     */
    String dailyReview(String date);
}
