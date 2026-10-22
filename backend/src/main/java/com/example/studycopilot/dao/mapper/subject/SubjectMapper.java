/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块 Mapper
 * 文件名：SubjectMapper.java
 * 创建时间：2026-10-22
 * 描述：科目表 Mapper。
 */
package com.example.studycopilot.dao.mapper.subject;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.subject.SubjectDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 科目表 Mapper。
 */
@Mapper
public interface SubjectMapper extends BaseMapper<SubjectDO> {
}
