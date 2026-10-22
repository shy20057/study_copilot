/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：附件模块数据对象
 * 文件名：AttachmentDO.java
 * 创建时间：2026-10-22
 * 描述：附件表数据对象。
 */
package com.example.studycopilot.dao.entity.attachment;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 附件表数据对象。
 */
@Data
@TableName("study_copilot.t_attachment")
public class AttachmentDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户 ID */
    private Long userId;

    /** 附件 URL */
    private String url;

    /** 文件大小(字节) */
    private Integer sizeBytes;

    /** 创建时间 */
    private Instant createTime;
}
