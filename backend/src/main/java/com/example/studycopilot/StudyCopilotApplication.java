/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：后端启动入口
 * 文件名：StudyCopilotApplication.java
 * 创建时间：2026-10-22
 * 描述：StudyCopilot 单服务应用启动类。
 */
package com.example.studycopilot;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * StudyCopilot 应用启动类。
 */
@SpringBootApplication
@EnableCaching
@EnableScheduling
@MapperScan("com.example.studycopilot.dao.mapper")
@ComponentScan(basePackages = {"com.example.studycopilot"})
public class StudyCopilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(StudyCopilotApplication.class, args);
    }
}
