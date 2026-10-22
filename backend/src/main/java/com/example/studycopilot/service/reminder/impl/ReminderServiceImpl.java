/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块服务实现
 * 文件名：ReminderServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：提醒服务实现。
 */
package com.example.studycopilot.service.reminder.impl;

import com.example.studycopilot.dto.resp.reminder.ReminderSettingResponse;
import com.example.studycopilot.service.reminder.ReminderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 提醒服务实现。
 */
@Service
@RequiredArgsConstructor
public class ReminderServiceImpl implements ReminderService {

    @Override
    public List<ReminderSettingResponse> list() {
        // TODO: 实现查询提醒设置列表逻辑
        return null;
    }

    @Override
    public void update(String type, Integer enabled, String remindTime) {
        // TODO: 实现更新提醒设置逻辑
    }
}
