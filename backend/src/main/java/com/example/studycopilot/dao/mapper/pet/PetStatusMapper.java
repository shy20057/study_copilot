/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块 Mapper
 * 文件名：PetStatusMapper.java
 * 创建时间：2026-10-22
 * 描述：萌宠状态表 Mapper。
 */
package com.example.studycopilot.dao.mapper.pet;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.pet.PetStatusDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 萌宠状态表 Mapper。
 */
@Mapper
public interface PetStatusMapper extends BaseMapper<PetStatusDO> {
}
