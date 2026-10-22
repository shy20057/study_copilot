/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块服务接口
 * 文件名：TaskService.java
 * 创建时间：2026-10-22
 * 描述：任务服务接口。
 */
package com.example.studycopilot.service.task;

import com.example.studycopilot.dto.req.task.CreateTaskRequest;
import com.example.studycopilot.dto.req.task.UpdateTaskRequest;
import com.example.studycopilot.dto.resp.task.TaskResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 任务服务接口。
 */
public interface TaskService {

    /**
     * 查询任务列表。
     *
     * @param targetDate 目标日期
     * @param status 状态
     * @return 任务列表
     */
    List<TaskResponse> list(LocalDate targetDate, String status);

    /**
     * 创建任务。
     *
     * @param request 任务请求
     * @return 任务响应
     */
    TaskResponse create(CreateTaskRequest request);

    /**
     * 更新任务。
     *
     * @param id 任务ID
     * @param request 任务请求
     * @return 任务响应
     */
    TaskResponse update(Long id, UpdateTaskRequest request);

    /**
     * 删除任务。
     *
     * @param id 任务ID
     */
    void delete(Long id);
}
