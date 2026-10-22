/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块服务接口
 * 文件名：JournalService.java
 * 创建时间：2026-10-22
 * 描述：日志服务接口。
 */
package com.example.studycopilot.service.journal;

import com.example.studycopilot.dto.req.journal.SaveJournalRequest;
import com.example.studycopilot.dto.resp.journal.JournalResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 日志服务接口。
 */
public interface JournalService {

    /**
     * 获取指定日期日志。
     *
     * @param date 日期
     * @return 日志响应
     */
    JournalResponse get(LocalDate date);

    /**
     * 保存/更新日志。
     *
     * @param request 日志请求
     * @return 日志响应
     */
    JournalResponse save(SaveJournalRequest request);

    /**
     * 获取月历数据。
     *
     * @param month 月份，格式 yyyy-MM
     * @return 月历数据
     */
    Object calendar(String month);

    /**
     * 搜索日志。
     *
     * @param keyword 关键字
     * @return 日志列表
     */
    List<JournalResponse> search(String keyword);
}
