/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：通知模块服务接口
 * 文件名：NotificationService.java
 * 创建时间：2026-10-22
 * 描述：通知服务接口。
 */
package com.example.studycopilot.service.notification;

import com.example.studycopilot.dao.entity.notification.NotificationDO;

import java.util.List;

/**
 * 通知服务接口。
 */
public interface NotificationService {

    /**
     * 查询当前用户通知列表。
     *
     * @return 通知列表
     */
    List<NotificationDO> list();

    /**
     * 标记已读。
     *
     * @param id 通知ID
     */
    void markRead(Long id);
}
