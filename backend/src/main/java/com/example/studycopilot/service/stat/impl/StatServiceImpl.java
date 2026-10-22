/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块服务实现
 * 文件名：StatServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：统计服务实现。
 */
package com.example.studycopilot.service.stat.impl;

import com.example.studycopilot.dto.resp.stat.StatSummaryResponse;
import com.example.studycopilot.service.stat.StatService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 统计服务实现。
 */
@Service
@RequiredArgsConstructor
public class StatServiceImpl implements StatService {

    @Override
    public StatSummaryResponse summary(String type, LocalDate date) {
        // TODO: 实现统计汇总逻辑
        return null;
    }

    @Override
    public Object heatmap(Integer year) {
        // TODO: 实现热力图数据逻辑
        return null;
    }
}
