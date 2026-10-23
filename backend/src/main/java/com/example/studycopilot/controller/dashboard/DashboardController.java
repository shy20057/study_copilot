package com.example.studycopilot.controller.dashboard;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.dashboard.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService service;

    @GetMapping
    public Result<?> get() {
        return Result.success(service.get());
    }
}
