/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：用户模块 Mapper
 * 文件名：UserMapper.java
 * 创建时间：2026-10-22
 * 描述：用户表 Mapper。
 */
package com.example.studycopilot.dao.mapper.user;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.user.UserDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper。
 */
@Mapper
public interface UserMapper extends BaseMapper<UserDO> {
}
