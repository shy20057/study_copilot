/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块控制器
 * 文件名：SettingController.java
 * 创建时间：2026-10-22
 * 描述：用户设置接口控制器。
 */
package com.example.studycopilot.controller.setting;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.req.setting.UpdateSettingRequest;
import com.example.studycopilot.dto.resp.setting.SettingResponse;
import com.example.studycopilot.service.setting.SettingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户设置接口控制器。
 */
@RestController
@RequestMapping("/api/settings")
@RequiredArgsConstructor
public class SettingController {

    private final SettingService settingService;

    @GetMapping
    public Result<SettingResponse> get() {
        return Result.success(settingService.get());
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody UpdateSettingRequest request) {
        settingService.update(request);
        return Result.success();
    }
}
