/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块响应 DTO
 * 文件名：StatSummaryResponse.java
 * 创建时间：2026-10-22
 * 描述：统计汇总响应。
 */
package com.example.studycopilot.dto.resp.stat;

import lombok.Data;

/**
 * 统计汇总响应。
 */
@Data
public class StatSummaryResponse {

    /** 总专注分钟数 */
    private Integer totalFocusMinutes;

    /** 总番茄数 */
    private Integer totalPomodoros;

    /** 任务完成率 */
    private Double taskCompletionRate;

    /** 各科目分钟数 */
    private Object subjectMinutes;

    /** 小时分桶分钟数 */
    private Object hourMinutes;
}
