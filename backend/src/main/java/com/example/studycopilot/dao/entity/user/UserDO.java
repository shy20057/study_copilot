/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：用户模块数据对象
 * 文件名：UserDO.java
 * 创建时间：2026-10-22
 * 描述：用户表数据对象。
 */
package com.example.studycopilot.dao.entity.user;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.Instant;

/**
 * 用户表数据对象。
 */
@Data
@TableName("study_copilot.t_user")
public class UserDO {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 用户名 */
    private String username;

    /** BCrypt 加密密码 */
    private String password;

    /** 邮箱 */
    private String email;

    /** 头像 URL */
    private String avatar;

    /** 状态：1正常 0禁用 */
    private Integer status;

    /** 创建时间 */
    private Instant createTime;

    /** 更新时间 */
    private Instant updateTime;
}
