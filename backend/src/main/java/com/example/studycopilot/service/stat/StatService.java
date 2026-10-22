/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块服务接口
 * 文件名：StatService.java
 * 创建时间：2026-10-22
 * 描述：统计服务接口。
 */
package com.example.studycopilot.service.stat;

import com.example.studycopilot.dto.resp.stat.StatSummaryResponse;

import java.time.LocalDate;

/**
 * 统计服务接口。
 */
public interface StatService {

    /**
     * 获取统计汇总。
     *
     * @param type 类型：day/week/month/year
     * @param date 日期
     * @return 统计汇总响应
     */
    StatSummaryResponse summary(String type, LocalDate date);

    /**
     * 获取年度热力图数据。
     *
     * @param year 年份
     * @return 热力图数据
     */
    Object heatmap(Integer year);
}
