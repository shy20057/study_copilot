package com.example.studycopilot.controller.task;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.task.TaskScheduleService;
import com.example.studycopilot.service.task.TaskWorkflowService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;

@RestController
@RequestMapping("/api/tasks")
@RequiredArgsConstructor
public class TaskWorkflowController {

    private final TaskWorkflowService tasks;
    private final TaskScheduleService schedules;

    @GetMapping
    public Result<?> list(
            @RequestParam(value = "targetDate", required = false) LocalDate date,
            @RequestParam(value = "status", defaultValue = "ALL") String status,
            @RequestParam(value = "subjectId", required = false) Long subject,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "50") int size) {
        schedules.ensureToday();
        return Result.success(tasks.list(date, status, subject, page, size, false));
    }

    @GetMapping("/overdue")
    public Result<?> overdue(@RequestParam(value="subjectId",required=false) Long subject,
            @RequestParam(value="page",defaultValue="1") int page,
            @RequestParam(value="pageSize",defaultValue="50") int size) {
        return Result.success(tasks.list(null,"TODO",subject,page,size,true));
    }

    @GetMapping("/{id}")
    public Result<?> get(@PathVariable("id") long id) { return Result.success(tasks.get(id)); }

    @GetMapping("/unplanned")
    public Result<?> unplanned(@RequestParam(value="subjectId",required=false) Long subject,
            @RequestParam(value="page",defaultValue="1") int page,
            @RequestParam(value="pageSize",defaultValue="50") int size) {
        return Result.success(tasks.unplanned(subject,page,size));
    }

    @PostMapping
    public Result<?> create(@RequestBody Map<String, Object> body,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return Result.success(tasks.create(body,key));
    }

    @PutMapping("/{id}")
    public Result<?> update(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        String scope = OwnerStore.text(body,"scope","THIS_ONLY");
        if (scope.equals("THIS_AND_FUTURE")) return Result.success(schedules.editInstance(id,body));
        if (!scope.equals("THIS_ONLY")) throw OwnerStore.error(com.example.studycopilot.common.errorcode.ScErrorCode.VALIDATION_ERROR);
        return Result.success(tasks.update(id, body));
    }

    @DeleteMapping("/{id}")
    public Result<?> delete(@PathVariable("id") long id, @RequestParam("expectedVersion") long version) {
        return Result.success(tasks.delete(id, version));
    }

    @PostMapping("/{id}/restore")
    public Result<?> restore(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return Result.success(tasks.restore(id, OwnerStore.number(body.get("expectedVersion"))));
    }

    @PostMapping("/{id}/complete")
    public Result<?> complete(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return Result.success(tasks.state(id, "DONE", body));
    }

    @PostMapping("/{id}/reopen")
    public Result<?> reopen(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return Result.success(tasks.state(id, "TODO", body));
    }

    @PostMapping("/{id}/cancel")
    public Result<?> cancel(@PathVariable("id") long id, @RequestBody Map<String, Object> body) {
        return Result.success(tasks.state(id, "CANCELLED", body));
    }

    @PutMapping("/reorder")
    public Result<?> reorder(@RequestBody Map<String, Object> body) {
        tasks.reorder(body);
        return Result.success();
    }
}
