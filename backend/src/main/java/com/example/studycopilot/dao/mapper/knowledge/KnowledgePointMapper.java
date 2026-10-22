/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块 Mapper
 * 文件名：KnowledgePointMapper.java
 * 创建时间：2026-10-22
 * 描述：知识点表 Mapper。
 */
package com.example.studycopilot.dao.mapper.knowledge;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.knowledge.KnowledgePointDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 知识点表 Mapper。
 */
@Mapper
public interface KnowledgePointMapper extends BaseMapper<KnowledgePointDO> {
}
