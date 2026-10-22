/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：附件模块控制器
 * 文件名：AttachmentController.java
 * 创建时间：2026-10-22
 * 描述：附件接口控制器。
 */
package com.example.studycopilot.controller.attachment;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.attachment.AttachmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 附件接口控制器。
 */
@RestController
@RequestMapping("/api/upload")
@RequiredArgsConstructor
public class AttachmentController {

    private final AttachmentService attachmentService;

    @PostMapping("/image")
    public Result<String> uploadImage(@RequestParam("file") MultipartFile file) {
        return Result.success(attachmentService.uploadImage(file));
    }
}
