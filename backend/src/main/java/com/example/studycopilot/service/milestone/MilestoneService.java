/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块服务接口
 * 文件名：MilestoneService.java
 * 创建时间：2026-10-22
 * 描述：阶段服务接口。
 */
package com.example.studycopilot.service.milestone;

import com.example.studycopilot.dto.req.milestone.CreateMilestoneRequest;
import com.example.studycopilot.dto.req.milestone.UpdateMilestoneRequest;
import com.example.studycopilot.dto.resp.milestone.MilestoneResponse;

import java.util.List;

/**
 * 阶段服务接口。
 */
public interface MilestoneService {

    /**
     * 查询阶段列表。
     *
     * @param monthTag 月份标识
     * @return 阶段列表
     */
    List<MilestoneResponse> list(String monthTag);

    /**
     * 创建阶段。
     *
     * @param request 阶段请求
     * @return 阶段响应
     */
    MilestoneResponse create(CreateMilestoneRequest request);

    /**
     * 更新阶段。
     *
     * @param id 阶段ID
     * @param request 阶段请求
     * @return 阶段响应
     */
    MilestoneResponse update(Long id, UpdateMilestoneRequest request);

    /**
     * 删除阶段。
     *
     * @param id 阶段ID
     */
    void delete(Long id);
}
