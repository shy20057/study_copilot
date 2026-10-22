/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：任务模块 Mapper
 * 文件名：TaskMapper.java
 * 创建时间：2026-10-22
 * 描述：待办任务表 Mapper。
 */
package com.example.studycopilot.dao.mapper.task;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.task.TaskDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 待办任务表 Mapper。
 */
@Mapper
public interface TaskMapper extends BaseMapper<TaskDO> {
}
