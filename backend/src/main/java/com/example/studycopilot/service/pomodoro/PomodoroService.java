/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块服务接口
 * 文件名：PomodoroService.java
 * 创建时间：2026-10-22
 * 描述：番茄钟服务接口。
 */
package com.example.studycopilot.service.pomodoro;

import com.example.studycopilot.dto.req.pomodoro.StartPomodoroRequest;
import com.example.studycopilot.dto.req.pomodoro.ManualPomodoroRequest;
import com.example.studycopilot.dto.resp.pomodoro.PomodoroResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 番茄钟服务接口。
 */
public interface PomodoroService {

    /**
     * 开始番茄钟。
     *
     * @param request 开始请求
     * @return 番茄钟响应
     */
    PomodoroResponse start(StartPomodoroRequest request);

    /**
     * 获取当前进行中的番茄钟。
     *
     * @return 番茄钟响应
     */
    PomodoroResponse current();

    /**
     * 完成番茄钟。
     *
     * @param id 番茄钟ID
     * @return 番茄钟响应
     */
    PomodoroResponse complete(Long id);

    /**
     * 放弃番茄钟。
     *
     * @param id 番茄钟ID
     * @return 番茄钟响应
     */
    PomodoroResponse abandon(Long id);

    /**
     * 手动补录番茄钟。
     *
     * @param request 补录请求
     * @return 番茄钟响应
     */
    PomodoroResponse manual(ManualPomodoroRequest request);

    /**
     * 查询番茄钟记录。
     *
     * @param statDate 统计日期
     * @return 番茄钟列表
     */
    List<PomodoroResponse> list(LocalDate statDate);
}
