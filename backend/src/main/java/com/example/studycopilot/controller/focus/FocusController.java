package com.example.studycopilot.controller.focus;

import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.focus.FocusRecordService;
import com.example.studycopilot.service.focus.FocusService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;

@RestController
@RequestMapping("/api/focus")
@RequiredArgsConstructor
public class FocusController {

    private final FocusService service;
    private final FocusRecordService records;

    @PostMapping("/start")
    public Result<?> start(@RequestBody Map<String, Object> b, @RequestHeader("Idempotency-Key") String key) {
        return Result.success(service.start(b, key, false));
    }

    @GetMapping("/current")
    public Result<?> current() {
        return Result.success(service.current());
    }

    @PostMapping("/break/start")
    public Result<?> rest(@RequestBody Map<String, Object> b, @RequestHeader("Idempotency-Key") String key) {
        return Result.success(service.start(b, key, true));
    }

    @PostMapping("/break/end-and-start")
    public Result<?> transition(@RequestBody Map<String, Object> b, @RequestHeader("Idempotency-Key") String key) {
        return Result.success(service.endBreakAndStart(b, key));
    }

    @PostMapping("/{id}/complete")
    public Result<?> complete(
            @PathVariable("id") long id,
            @RequestBody(required = false) Map<String, Object> b,
            @RequestHeader("Idempotency-Key") String key) {
        return Result.success(service.finish(id, b == null ? Map.of() : b, key, false));
    }

    @PostMapping("/{id}/end-early")
    public Result<?> early(
            @PathVariable("id") long id,
            @RequestBody Map<String, Object> b,
            @RequestHeader("Idempotency-Key") String key) {
        return Result.success(service.finish(id, b, key, true));
    }

    @PostMapping("/{id}/presentation/claim")
    public Result<?> claim(@PathVariable("id") long id, @RequestBody Map<String, Object> b) {
        return Result.success(service.presentation(id, Objects.toString(b.get("deviceId"), null), false));
    }

    @PostMapping("/{id}/presentation/ack")
    public Result<?> ack(@PathVariable("id") long id, @RequestBody Map<String, Object> b) {
        return Result.success(service.presentation(id, Objects.toString(b.get("deviceId"), null), true));
    }

    @GetMapping("/records")
    public Result<?> records(
            @RequestParam(value = "learningDate", required = false) LocalDate day,
            @RequestParam(value = "subjectId", required = false) Long subject,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "20") int size) {
        return Result.success(records.list(day, subject, page, size));
    }

    @PostMapping("/records/manual")
    public Result<?> manual(@RequestBody Map<String, Object> b, @RequestHeader("Idempotency-Key") String key) {
        return Result.success(records.create(b, key));
    }

    @PutMapping("/records/{id}")
    public Result<?> edit(@PathVariable("id") long id, @RequestBody Map<String, Object> b) {
        return Result.success(records.update(id, b));
    }

    @DeleteMapping("/records/{id}")
    public Result<?> delete(@PathVariable("id") long id, @RequestParam("expectedVersion") long version) {
        return Result.success(records.remove(id, version));
    }

    @PostMapping("/records/{id}/restore")
    public Result<?> restore(@PathVariable("id") long id, @RequestBody Map<String, Object> b) {
        return Result.success(records.restore(id, OwnerStore.number(b.get("expectedVersion"))));
    }
}
