/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块服务实现
 * 文件名：MilestoneServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：阶段服务实现。
 */
package com.example.studycopilot.service.milestone.impl;

import com.example.studycopilot.dto.req.milestone.CreateMilestoneRequest;
import com.example.studycopilot.dto.req.milestone.UpdateMilestoneRequest;
import com.example.studycopilot.dto.resp.milestone.MilestoneResponse;
import com.example.studycopilot.service.milestone.MilestoneService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 阶段服务实现。
 */
@Service
@RequiredArgsConstructor
public class MilestoneServiceImpl implements MilestoneService {

    @Override
    public List<MilestoneResponse> list(String monthTag) {
        // TODO: 实现查询阶段列表逻辑
        return null;
    }

    @Override
    public MilestoneResponse create(CreateMilestoneRequest request) {
        // TODO: 实现创建阶段逻辑
        return null;
    }

    @Override
    public MilestoneResponse update(Long id, UpdateMilestoneRequest request) {
        // TODO: 实现更新阶段逻辑
        return null;
    }

    @Override
    public void delete(Long id) {
        // TODO: 实现删除阶段逻辑
    }
}
