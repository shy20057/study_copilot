/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：附件模块 Mapper
 * 文件名：AttachmentMapper.java
 * 创建时间：2026-10-22
 * 描述：附件表 Mapper。
 */
package com.example.studycopilot.dao.mapper.attachment;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.studycopilot.dao.entity.attachment.AttachmentDO;
import org.apache.ibatis.annotations.Mapper;

/**
 * 附件表 Mapper。
 */
@Mapper
public interface AttachmentMapper extends BaseMapper<AttachmentDO> {
}
