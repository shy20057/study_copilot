/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块服务接口
 * 文件名：PetService.java
 * 创建时间：2026-10-22
 * 描述：萌宠服务接口。
 */
package com.example.studycopilot.service.pet;

import com.example.studycopilot.dto.resp.pet.PetStatusResponse;

/**
 * 萌宠服务接口。
 */
public interface PetService {

    /**
     * 获取当前用户萌宠状态。
     *
     * @return 萌宠状态
     */
    PetStatusResponse status();

    /**
     * 切换宠物类型。
     *
     * @param petType 宠物类型
     */
    void switchType(Integer petType);
}
