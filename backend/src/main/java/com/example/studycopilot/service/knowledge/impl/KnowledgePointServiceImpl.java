/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块服务实现
 * 文件名：KnowledgePointServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：知识点服务实现。
 */
package com.example.studycopilot.service.knowledge.impl;

import com.example.studycopilot.dto.resp.knowledge.KnowledgePointResponse;
import com.example.studycopilot.service.knowledge.KnowledgePointService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 知识点服务实现。
 */
@Service
@RequiredArgsConstructor
public class KnowledgePointServiceImpl implements KnowledgePointService {

    @Override
    public List<KnowledgePointResponse> tree(Long subjectId) {
        // TODO: 实现查询知识点树逻辑
        return null;
    }

    @Override
    public KnowledgePointResponse create(Long subjectId, Long parentId, String name) {
        // TODO: 实现创建知识点逻辑
        return null;
    }

    @Override
    public void updateMastery(Long id, Integer mastery) {
        // TODO: 实现更新知识点掌握度逻辑
    }

    @Override
    public void delete(Long id) {
        // TODO: 实现删除知识点逻辑
    }
}
