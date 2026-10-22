/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：提醒模块 Mapper
 * 文件名：ReminderSettingMapper.java
 * 创建时间：2026-10-22
 * 描述：提醒设置表 Mapper。
 */
package com.example.studycopilot.dao.mapper.reminder;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.reminder.ReminderSettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提醒设置表 Mapper。
 */
@Mapper
public interface ReminderSettingMapper extends BaseMapper<ReminderSettingDO> {
}
