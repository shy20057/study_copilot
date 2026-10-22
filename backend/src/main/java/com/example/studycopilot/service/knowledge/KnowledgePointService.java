/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块服务接口
 * 文件名：KnowledgePointService.java
 * 创建时间：2026-10-22
 * 描述：知识点服务接口。
 */
package com.example.studycopilot.service.knowledge;

import com.example.studycopilot.dto.resp.knowledge.KnowledgePointResponse;

import java.util.List;

/**
 * 知识点服务接口。
 */
public interface KnowledgePointService {

    /**
     * 查询知识点树。
     *
     * @param subjectId 科目ID
     * @return 知识点列表
     */
    List<KnowledgePointResponse> tree(Long subjectId);

    /**
     * 创建知识点。
     *
     * @param subjectId 科目ID
     * @param parentId 父知识点ID
     * @param name 名称
     * @return 知识点响应
     */
    KnowledgePointResponse create(Long subjectId, Long parentId, String name);

    /**
     * 更新知识点掌握度。
     *
     * @param id 知识点ID
     * @param mastery 掌握度
     */
    void updateMastery(Long id, Integer mastery);

    /**
     * 删除知识点。
     *
     * @param id 知识点ID
     */
    void delete(Long id);
}
