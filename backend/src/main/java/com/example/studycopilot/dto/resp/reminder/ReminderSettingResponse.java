/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块响应 DTO
 * 文件名：ReminderSettingResponse.java
 * 创建时间：2026-10-22
 * 描述：提醒设置响应。
 */
package com.example.studycopilot.dto.resp.reminder;

import lombok.Data;

/**
 * 提醒设置响应。
 */
@Data
public class ReminderSettingResponse {

    private String type;
    private Integer enabled;
    private String remindTime;
    private String channel;
}
