/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：通知模块服务实现
 * 文件名：NotificationServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：通知服务实现。
 */
package com.example.studycopilot.service.notification.impl;

import com.example.studycopilot.dao.entity.notification.NotificationDO;
import com.example.studycopilot.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 通知服务实现。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    @Override
    public List<NotificationDO> list() {
        // TODO: 实现查询通知列表逻辑
        return null;
    }

    @Override
    public void markRead(Long id) {
        // TODO: 实现标记已读逻辑
    }
}
