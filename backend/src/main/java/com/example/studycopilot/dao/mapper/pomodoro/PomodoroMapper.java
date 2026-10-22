/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：番茄钟模块 Mapper
 * 文件名：PomodoroMapper.java
 * 创建时间：2026-10-22
 * 描述：番茄钟记录表 Mapper。
 */
package com.example.studycopilot.dao.mapper.pomodoro;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.pomodoro.PomodoroDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 番茄钟记录表 Mapper。
 */
@Mapper
public interface PomodoroMapper extends BaseMapper<PomodoroDO> {
}
