/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块服务接口
 * 文件名：MistakeService.java
 * 创建时间：2026-10-22
 * 描述：错题服务接口。
 */
package com.example.studycopilot.service.mistake;

import com.example.studycopilot.dto.req.mistake.CreateMistakeRequest;
import com.example.studycopilot.dto.req.mistake.ReviewMistakeRequest;
import com.example.studycopilot.dto.resp.mistake.MistakeResponse;

import java.time.LocalDate;
import java.util.List;

/**
 * 错题服务接口。
 */
public interface MistakeService {

    /**
     * 查询错题列表。
     *
     * @param subjectId 科目ID
     * @param mastery 掌握度
     * @param errorReason 错因
     * @return 错题列表
     */
    List<MistakeResponse> list(Long subjectId, Integer mastery, String errorReason);

    /**
     * 创建错题。
     *
     * @param request 错题请求
     * @return 错题响应
     */
    MistakeResponse create(CreateMistakeRequest request);

    /**
     * 更新错题。
     *
     * @param id 错题ID
     * @param request 错题请求
     * @return 错题响应
     */
    MistakeResponse update(Long id, CreateMistakeRequest request);

    /**
     * 删除错题。
     *
     * @param id 错题ID
     */
    void delete(Long id);

    /**
     * 今日待复习队列。
     *
     * @return 错题列表
     */
    List<MistakeResponse> todayReview();

    /**
     * 提交复习自评。
     *
     * @param id 错题ID
     * @param request 复习请求
     */
    void review(Long id, ReviewMistakeRequest request);

    /**
     * 获取错题统计。
     *
     * @return 统计信息
     */
    Object stats();
}
