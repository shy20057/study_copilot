/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块服务接口
 * 文件名：SubjectService.java
 * 创建时间：2026-10-22
 * 描述：科目服务接口。
 */
package com.example.studycopilot.service.subject;

import com.example.studycopilot.dto.req.subject.CreateSubjectRequest;
import com.example.studycopilot.dto.resp.subject.SubjectResponse;

import java.util.List;

/**
 * 科目服务接口。
 */
public interface SubjectService {

    /**
     * 查询当前用户科目列表。
     *
     * @return 科目列表
     */
    List<SubjectResponse> list();

    /**
     * 创建科目。
     *
     * @param request 科目请求
     * @return 科目响应
     */
    SubjectResponse create(CreateSubjectRequest request);

    /**
     * 更新科目。
     *
     * @param id 科目ID
     * @param request 科目请求
     * @return 科目响应
     */
    SubjectResponse update(Long id, CreateSubjectRequest request);

    /**
     * 删除科目。
     *
     * @param id 科目ID
     */
    void delete(Long id);
}
