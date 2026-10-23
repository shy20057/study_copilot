/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：萌宠模块控制器
 * 文件名：PetController.java
 * 创建时间：2026-10-22
 * 描述：萌宠接口控制器。
 */
package com.example.studycopilot.controller.pet;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.resp.pet.PetStatusResponse;
import com.example.studycopilot.service.pet.PetService;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 萌宠接口控制器。
 */
@RestController
@RequestMapping("/api/pet")
@RequiredArgsConstructor
public class PetController {

    private final PetService petService;

    @GetMapping("/options")
    public Result<?> options() {
        return Result.success(java.util.List.of(
            java.util.Map.of("id",1,"name","粉粉喵"),java.util.Map.of("id",2,"name","汪汪柴"),
            java.util.Map.of("id",3,"name","软软兔"),java.util.Map.of("id",4,"name","熊熊帕"),java.util.Map.of("id",5,"name","啾啾鸡")));
    }

    @GetMapping("/status")
    public Result<PetStatusResponse> status() {
        return Result.success(petService.status());
    }

    @PostMapping("/switch")
    public Result<Void> switchType(@RequestBody @NotNull Integer petType) {
        petService.switchType(petType);
        return Result.success();
    }
}
