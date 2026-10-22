/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：知识点模块控制器
 * 文件名：KnowledgePointController.java
 * 创建时间：2026-10-22
 * 描述：知识点接口控制器。
 */
package com.example.studycopilot.controller.knowledge;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.dto.resp.knowledge.KnowledgePointResponse;
import com.example.studycopilot.service.knowledge.KnowledgePointService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识点接口控制器。
 */
@RestController
@RequestMapping("/api/knowledge-points")
@RequiredArgsConstructor
public class KnowledgePointController {

    private final KnowledgePointService knowledgePointService;

    @GetMapping
    public Result<List<KnowledgePointResponse>> tree(@RequestParam Long subjectId) {
        return Result.success(knowledgePointService.tree(subjectId));
    }

    @PostMapping
    public Result<KnowledgePointResponse> create(@RequestParam Long subjectId, @RequestParam(required = false) Long parentId, @RequestParam String name) {
        return Result.success(knowledgePointService.create(subjectId, parentId, name));
    }

    @PutMapping("/{id}/mastery")
    public Result<Void> updateMastery(@PathVariable Long id, @RequestBody Integer mastery) {
        knowledgePointService.updateMastery(id, mastery);
        return Result.success();
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgePointService.delete(id);
        return Result.success();
    }
}
