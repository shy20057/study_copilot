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
import com.example.studycopilot.service.attachment.AvatarImage;
import com.example.studycopilot.service.attachment.AvatarObjectStore;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;

/**
 * 附件服务实现。
 */
@Service
@RequiredArgsConstructor
public class AttachmentServiceImpl implements AttachmentService {
    private static final Logger log = LoggerFactory.getLogger(AttachmentServiceImpl.class);
    private final OwnerStore db;
    private final AvatarObjectStore objects;

    @Override
    @Transactional
    public String uploadImage(MultipartFile file) {
        long userId = db.owner();
        db.lock(userId);
        AvatarImage image = AvatarImage.read(file);
        String key = AvatarObjectStore.prefix(userId) + UUID.randomUUID() + "." + image.extension();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_ROLLED_BACK) {
                    try {
                        objects.remove(userId, key);
                    } catch (RuntimeException ex) {
                        // The account cleanup also lists the full owner prefix, including orphan objects.
                        log.warn("Avatar rollback cleanup deferred: storage unavailable");
                    }
                }
            }
        });
        objects.put(userId, key, image);
        Long id = db.jdbc().queryForObject("""
                insert into study_copilot.t_attachment(user_id,url,size_bytes,object_key,media_type)
                values (?,?,?,?,?) returning id
                """, Long.class, userId, key, image.bytes().length, key, image.mediaType());
        String url = "/api/upload/images/" + id;
        db.jdbc().update("update study_copilot.t_attachment set url=? where user_id=? and id=?", url, userId, id);
        db.jdbc().update("""
                update study_copilot.t_user set avatar=?,profile_version=profile_version+1,update_time=now()
                where id=?
                """, url, userId);
        return url;
    }

    @Override
    @Transactional
    public AvatarDownload readImage(long attachmentId) {
        long userId = db.owner();
        db.lock(userId);
        var row = db.one("""
                select object_key,media_type from study_copilot.t_attachment
                where user_id=? and id=? and object_key is not null and media_type is not null
                """, userId, attachmentId);
        return new AvatarDownload(objects.read(userId, row.get("object_key").toString()), row.get("media_type").toString());
    }
}
