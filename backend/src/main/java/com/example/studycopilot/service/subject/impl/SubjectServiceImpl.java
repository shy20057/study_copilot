/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块服务实现
 * 文件名：SubjectServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：科目服务实现。
 */
package com.example.studycopilot.service.subject.impl;

import com.example.studycopilot.dto.req.subject.CreateSubjectRequest;
import com.example.studycopilot.dto.resp.subject.SubjectResponse;
import com.example.studycopilot.service.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 科目服务实现。
 */
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {

    @Override
    public List<SubjectResponse> list() {
        // TODO: 实现查询科目列表逻辑
        return null;
    }

    @Override
    public SubjectResponse create(CreateSubjectRequest request) {
        // TODO: 实现创建科目逻辑
        return null;
    }

    @Override
    public SubjectResponse update(Long id, CreateSubjectRequest request) {
        // TODO: 实现更新科目逻辑
        return null;
    }

    @Override
    public void delete(Long id) {
        // TODO: 实现删除科目逻辑
    }
}
