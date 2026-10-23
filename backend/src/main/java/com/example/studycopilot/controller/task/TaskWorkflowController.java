package com.example.studycopilot.controller.task;
import com.example.studycopilot.common.result.Result;
import com.example.studycopilot.service.task.TaskWorkflowService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDate;
import java.util.Map;
@RestController @RequestMapping("/api/tasks") @RequiredArgsConstructor
public class TaskWorkflowController {
 private final TaskWorkflowService tasks;
 private final com.example.studycopilot.service.task.TaskScheduleService schedules;
 @GetMapping public Result<?> list(@RequestParam(value="targetDate",required=false) LocalDate date,@RequestParam(value="status",defaultValue="ALL") String status,@RequestParam(value="subjectId",required=false) Long subject){schedules.ensureToday();return Result.success(tasks.list(date,status,subject));}
 @PostMapping public Result<?> create(@RequestBody Map<String,Object> body){return Result.success(tasks.create(body));}
 @PutMapping("/{id}") public Result<?> update(@PathVariable("id") long id,@RequestBody Map<String,Object> body){return Result.success(tasks.update(id,body));}
 @DeleteMapping("/{id}") public Result<?> delete(@PathVariable("id") long id,@RequestParam("expectedVersion") long version){return Result.success(tasks.delete(id,version));}
 @PostMapping("/{id}/restore") public Result<?> restore(@PathVariable("id") long id,@RequestBody Map<String,Object> body){return Result.success(tasks.restore(id,com.example.studycopilot.support.db.OwnerStore.number(body.get("expectedVersion"))));}
 @PostMapping("/{id}/complete") public Result<?> complete(@PathVariable("id") long id,@RequestBody Map<String,Object> body){return Result.success(tasks.state(id,"DONE",body));}
 @PostMapping("/{id}/reopen") public Result<?> reopen(@PathVariable("id") long id,@RequestBody Map<String,Object> body){return Result.success(tasks.state(id,"TODO",body));}
 @PostMapping("/{id}/cancel") public Result<?> cancel(@PathVariable("id") long id,@RequestBody Map<String,Object> body){return Result.success(tasks.state(id,"CANCELLED",body));}
 @PutMapping("/reorder") public Result<?> reorder(@RequestBody Map<String,Object> body){tasks.reorder(body);return Result.success();}
}
