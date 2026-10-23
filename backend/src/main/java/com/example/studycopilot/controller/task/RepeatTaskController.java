package com.example.studycopilot.controller.task;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.task.TaskScheduleService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class RepeatTaskController {

    private final TaskScheduleService service;

    @GetMapping("/repeat-templates")
    public Result<?> list() {
        return Result.success(service.templates());
    }

    @PostMapping("/repeat-templates")
    public Result<?> create(@RequestBody Map<String, Object> body) {
        return Result.success(service.save(null, body));
    }

    @PutMapping("/repeat-templates/{id}")
    public Result<?> update(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return Result.success(service.save(id, body));
    }

    @PostMapping("/repeat/ensure")
    public Result<?> ensure() {
        service.ensureToday();
        return Result.success();
    }

    @PostMapping("/carry-over")
    public Result<?> carry(@RequestBody Map<String, Object> body) {
        return Result.success(service.carry(body));
    }

    @PostMapping("/carry-over/{batchId}/undo")
    public Result<?> undo(@PathVariable("batchId") UUID id) {
        service.undo(id);
        return Result.success();
    }
}
