/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块服务实现
 * 文件名：SettingServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：用户设置服务实现。
 */
package com.example.studycopilot.service.setting.impl;

import com.example.studycopilot.dto.req.setting.UpdateSettingRequest;
import com.example.studycopilot.dto.resp.setting.SettingResponse;
import com.example.studycopilot.service.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户设置服务实现。
 */
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {

    @Override
    public SettingResponse get() {
        // TODO: 实现获取用户设置逻辑
        return null;
    }

    @Override
    public void update(UpdateSettingRequest request) {
        // TODO: 实现更新用户设置逻辑
    }
}
