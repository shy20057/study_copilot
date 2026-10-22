/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：附件模块服务接口
 * 文件名：AttachmentService.java
 * 创建时间：2026-10-22
 * 描述：附件服务接口。
 */
package com.example.studycopilot.service.attachment;

import org.springframework.web.multipart.MultipartFile;

/**
 * 附件服务接口。
 */
public interface AttachmentService {

    /**
     * 上传图片。
     *
     * @param file 文件
     * @return 附件 URL
     */
    String uploadImage(MultipartFile file);
}
