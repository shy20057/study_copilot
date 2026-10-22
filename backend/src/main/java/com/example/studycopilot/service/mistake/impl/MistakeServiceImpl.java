/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块服务实现
 * 文件名：MistakeServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：错题服务实现。
 */
package com.example.studycopilot.service.mistake.impl;

import com.example.studycopilot.dto.req.mistake.CreateMistakeRequest;
import com.example.studycopilot.dto.req.mistake.ReviewMistakeRequest;
import com.example.studycopilot.dto.resp.mistake.MistakeResponse;
import com.example.studycopilot.service.mistake.MistakeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 错题服务实现。
 */
@Service
@RequiredArgsConstructor
public class MistakeServiceImpl implements MistakeService {

    @Override
    public List<MistakeResponse> list(Long subjectId, Integer mastery, String errorReason) {
        // TODO: 实现查询错题列表逻辑
        return null;
    }

    @Override
    public MistakeResponse create(CreateMistakeRequest request) {
        // TODO: 实现创建错题逻辑
        return null;
    }

    @Override
    public MistakeResponse update(Long id, CreateMistakeRequest request) {
        // TODO: 实现更新错题逻辑
        return null;
    }

    @Override
    public void delete(Long id) {
        // TODO: 实现删除错题逻辑
    }

    @Override
    public List<MistakeResponse> todayReview() {
        // TODO: 实现今日待复习队列逻辑
        return null;
    }

    @Override
    public void review(Long id, ReviewMistakeRequest request) {
        // TODO: 实现提交复习自评逻辑
    }

    @Override
    public Object stats() {
        // TODO: 实现错题统计逻辑
        return null;
    }
}
