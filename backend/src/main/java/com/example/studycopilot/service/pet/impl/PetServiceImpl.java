/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块服务实现
 * 文件名：PetServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：萌宠服务实现。
 */
package com.example.studycopilot.service.pet.impl;

import com.example.studycopilot.dto.resp.pet.PetStatusResponse;
import com.example.studycopilot.service.pet.PetService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 萌宠服务实现。
 */
@Service
@RequiredArgsConstructor
public class PetServiceImpl implements PetService {

    @Override
    public PetStatusResponse status() {
        // TODO: 实现获取萌宠状态逻辑
        return null;
    }

    @Override
    public void switchType(Integer petType) {
        // TODO: 实现切换宠物类型逻辑
    }
}
