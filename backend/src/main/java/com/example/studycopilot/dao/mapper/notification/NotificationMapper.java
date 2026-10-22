/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：通知模块 Mapper
 * 文件名：NotificationMapper.java
 * 创建时间：2026-10-22
 * 描述：站内消息表 Mapper。
 */
package com.example.studycopilot.dao.mapper.notification;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.notification.NotificationDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 站内消息表 Mapper。
 */
@Mapper
public interface NotificationMapper extends BaseMapper<NotificationDO> {
}
