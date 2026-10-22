/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：统计模块 Mapper
 * 文件名：DailyStatMapper.java
 * 创建时间：2026-10-22
 * 描述：每日统计汇总表 Mapper。
 */
package com.example.studycopilot.dao.mapper.stat;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.stat.DailyStatDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 每日统计汇总表 Mapper。
 */
@Mapper
public interface DailyStatMapper extends BaseMapper<DailyStatDO> {
}
