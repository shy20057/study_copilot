/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：附件模块服务实现
 * 文件名：AttachmentServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：附件服务实现。
 */
package com.example.studycopilot.service.attachment.impl;

import com.example.studycopilot.service.attachment.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件服务实现。
 */
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {

    @Override
    public String uploadImage(MultipartFile file) {
        // TODO: 实现图片上传逻辑
        return null;
    }
}
