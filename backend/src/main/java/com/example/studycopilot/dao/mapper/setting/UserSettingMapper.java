/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块 Mapper
 * 文件名：UserSettingMapper.java
 * 创建时间：2026-10-22
 * 描述：用户设置表 Mapper。
 */
package com.example.studycopilot.dao.mapper.setting;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.setting.UserSettingDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户设置表 Mapper。
 */
@Mapper
public interface UserSettingMapper extends BaseMapper<UserSettingDO> {
}
