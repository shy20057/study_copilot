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
     * 上传并设置私有头像。
     *
     * @param file 文件
     * @return 需要 Bearer 鉴权的同源图片路由，前端应 fetch 后通过 Blob URL 展示
     */
    String uploadImage(MultipartFile file);

    AvatarDownload readImage(long attachmentId);

    record AvatarDownload(byte[] bytes, String mediaType) { }
}
