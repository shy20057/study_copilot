/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：错题模块 Mapper
 * 文件名：MistakeMapper.java
 * 创建时间：2026-10-22
 * 描述：错题表 Mapper。
 */
package com.example.studycopilot.dao.mapper.mistake;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.mistake.MistakeDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 错题表 Mapper。
 */
@Mapper
public interface MistakeMapper extends BaseMapper<MistakeDO> {
}
