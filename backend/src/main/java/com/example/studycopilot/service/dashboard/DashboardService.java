package com.example.studycopilot.service.dashboard;

import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.service.focus.FocusRecordService;
import com.example.studycopilot.service.focus.FocusService;
import com.example.studycopilot.service.pet.PetService;
import com.example.studycopilot.service.task.TaskScheduleService;
import com.example.studycopilot.service.task.TaskWorkflowService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import java.util.function.Supplier;
import static com.example.studycopilot.support.db.OwnerStore.*;

@Service @RequiredArgsConstructor
public class DashboardService {
    private final OwnerStore db;
    private final LearningDayService days;
    private final FocusService focus;
    private final FocusRecordService records;
    private final TaskScheduleService repeats;
    private final TaskWorkflowService tasks;
    private final PetService pets;

    // Each module is isolated so one failing section degrades to an ERROR
    // entry instead of failing the whole dashboard.
    @Transactional public Map<String,Object> get() {
        long u=db.owner();
        db.lock(u);
        focus.compensate(u);
        var day=days.resolve(u,Instant.now());
        repeats.ensureInstance(u,day.learningDate());

        var out=new LinkedHashMap<String,Object>();
        out.put("learningDay",day.learningDate());
        out.put("ruleVersion",day.ruleVersion());
        out.put("serverNow",Instant.now());
        out.put("currentSession",focus.current().get("session"));

        long seconds=db.jdbc().queryForObject("select coalesce(sum(f.valid_seconds),0) from study_copilot.t_focus_slice f join study_copilot.t_focus_session s on s.id=f.session_id where f.user_id=? and f.learning_date=? and s.deleted=0",Long.class,u,day.learningDate());
        int complete=db.jdbc().queryForObject("select count(*) from study_copilot.t_focus_session where user_id=? and source='TIMER' and mode='FOCUS' and status='COMPLETED' and deleted=0 and end_at>? and end_at<=?",Integer.class,u,java.sql.Timestamp.from(day.start()),java.sql.Timestamp.from(day.end()));
        out.put("stats",Map.of("status",seconds==0?"EMPTY":"OK","retryable",false,"focusSeconds",seconds,"focusMinutes",seconds/60,"completedPomodoros",complete));

        var setting=db.one("select weekly_goal_min,exam_date from study_copilot.t_user_setting where user_id=?",u);
        LocalDate monday=day.learningDate().minusDays(day.learningDate().getDayOfWeek().getValue()-1);
        long week=db.jdbc().queryForObject("select coalesce(sum(f.valid_seconds),0) from study_copilot.t_focus_slice f join study_copilot.t_focus_session s on s.id=f.session_id where f.user_id=? and f.learning_date>=? and f.learning_date<? and s.deleted=0",Long.class,u,monday,monday.plusDays(7));
        var goal=new LinkedHashMap<String,Object>();
        goal.put("targetMinutes",setting.get("weekly_goal_min"));
        goal.put("actualSeconds",week);
        goal.put("status",setting.get("weekly_goal_min")==null?"EMPTY":"OK");
        goal.put("retryable",false);
        out.put("weeklyGoal",goal);
        out.put("examDate",setting.get("exam_date"));

        var taskData=new LinkedHashMap<>(tasks.list(day.learningDate(),"ALL",null));
        var items=(List<?>)taskData.get("items");
        taskData.put("items",items.subList(0,Math.min(6,items.size())));
        taskData.put("status",items.isEmpty()?"EMPTY":"OK");
        out.put("tasks",taskData);

        out.put("subjectDistribution",module(()->db.jdbc().queryForList("select f.subject_id,s.subject_name_snapshot name,s.subject_color_snapshot color,sum(f.valid_seconds) seconds from study_copilot.t_focus_slice f join study_copilot.t_focus_session s on s.id=f.session_id where f.user_id=? and f.learning_date=? and s.deleted=0 group by f.subject_id,s.subject_name_snapshot,s.subject_color_snapshot order by seconds desc",u,day.learningDate())));
        out.put("recentRecords",module(()->records.list(day.learningDate(),null,1,3)));
        out.put("pet",module(pets::status));
        out.put("dataVersion",db.one("select data_version from study_copilot.t_user where id=?",u).get("data_version"));
        return out;
    }

    private Map<String,Object> module(Supplier<Object> supplier) {
        try {
            Object result=supplier.get();
            return Map.of("status",result instanceof Collection<?> c&&c.isEmpty()?"EMPTY":"OK","retryable",false,"data",result);
        }catch(RuntimeException e) {
            return Map.of("status","ERROR","retryable",true);
        }
    }
}
