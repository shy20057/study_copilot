/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：日志模块 Mapper
 * 文件名：DailyJournalMapper.java
 * 创建时间：2026-10-22
 * 描述：每日日志表 Mapper。
 */
package com.example.studycopilot.dao.mapper.journal;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.journal.DailyJournalDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日日志表 Mapper。
 */
@Mapper
public interface DailyJournalMapper extends BaseMapper<DailyJournalDO> {
}
