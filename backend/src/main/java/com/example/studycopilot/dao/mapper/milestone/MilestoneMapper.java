/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：阶段模块 Mapper
 * 文件名：MilestoneMapper.java
 * 创建时间：2026-10-22
 * 描述：阶段时间轴表 Mapper。
 */
package com.example.studycopilot.dao.mapper.milestone;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.milestone.MilestoneDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 阶段时间轴表 Mapper。
 */
@Mapper
public interface MilestoneMapper extends BaseMapper<MilestoneDO> {
}
