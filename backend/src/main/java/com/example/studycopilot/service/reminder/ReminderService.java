/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块服务接口
 * 文件名：ReminderService.java
 * 创建时间：2026-10-22
 * 描述：提醒服务接口。
 */
package com.example.studycopilot.service.reminder;

import com.example.studycopilot.dto.resp.reminder.ReminderSettingResponse;

import java.util.List;

/**
 * 提醒服务接口。
 */
public interface ReminderService {

    /**
     * 查询当前用户提醒设置列表。
     *
     * @return 提醒设置列表
     */
    List<ReminderSettingResponse> list();

    /**
     * 更新提醒设置。
     *
     * @param type 类型
     * @param enabled 是否开启
     * @param remindTime 提醒时间
     */
    void update(String type, Integer enabled, String remindTime);
}
