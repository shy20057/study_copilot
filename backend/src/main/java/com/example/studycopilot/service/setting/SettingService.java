/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块服务接口
 * 文件名：SettingService.java
 * 创建时间：2026-10-22
 * 描述：用户设置服务接口。
 */
package com.example.studycopilot.service.setting;

import com.example.studycopilot.dto.req.setting.UpdateSettingRequest;
import com.example.studycopilot.dto.resp.setting.SettingResponse;

/**
 * 用户设置服务接口。
 */
public interface SettingService {

    /**
     * 获取当前用户设置。
     *
     * @return 设置响应
     */
    SettingResponse get();

    /**
     * 更新当前用户设置。
     *
     * @param request 设置请求
     */
    void update(UpdateSettingRequest request);
}
