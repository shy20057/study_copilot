/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块控制器
 * 文件名：ReminderController.java
 * 创建时间：2026-10-22
 * 描述：提醒接口控制器。
 */
package com.example.studycopilot.controller.reminder;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.resp.reminder.ReminderSettingResponse;
import com.example.studycopilot.service.reminder.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 提醒接口控制器。
 */
@RestController
@Profile("future-features")
@RequestMapping("/api/reminders")
@RequiredArgsConstructor
public class ReminderController {

    private final ReminderService reminderService;

    @GetMapping
    public Result<List<ReminderSettingResponse>> list() {
        return Result.success(reminderService.list());
    }

    @PutMapping
    public Result<Void> update(@RequestParam String type, @RequestBody Integer enabled, @RequestParam String remindTime) {
        reminderService.update(type, enabled, remindTime);
        return Result.success();
    }
}
