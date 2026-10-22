/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：AI对话模块控制器
 * 文件名：ChatController.java
 * 创建时间：2026-10-22
 * 描述：AI对话接口控制器。
 */
package com.example.studycopilot.controller.chat;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.chat.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * AI对话接口控制器。
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping("/chat")
    public Result<String> chat(@RequestBody String content, @RequestParam(required = false) String sessionId) {
        return Result.success(chatService.chat(sessionId, content));
    }

    @GetMapping("/chat/history")
    public Result<Object> history(@RequestParam String sessionId) {
        return Result.success(chatService.history(sessionId));
    }

    @PostMapping("/daily-review")
    public Result<String> dailyReview(@RequestParam String date) {
        return Result.success(chatService.dailyReview(date));
    }
}
