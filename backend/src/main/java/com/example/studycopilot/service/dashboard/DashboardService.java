package com.example.studycopilot.service.dashboard;

import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.service.focus.FocusRecordService;
import com.example.studycopilot.service.focus.FocusService;
import com.example.studycopilot.service.pet.PetService;
import com.example.studycopilot.service.onboarding.OnboardingService;
import com.example.studycopilot.service.task.TaskScheduleService;
import com.example.studycopilot.service.task.TaskWorkflowService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static com.example.studycopilot.support.db.OwnerStore.*;

/** 控制台复用事实账本；可选模块通过保存点隔离失败，不返回伪造的零。 */
@Service @RequiredArgsConstructor
public class DashboardService {
    private final OwnerStore db;
    private final LearningDayService days;
    private final FocusService focus;
    private final FocusRecordService records;
    private final TaskScheduleService repeats;
    private final TaskWorkflowService tasks;
    private final PetService pets;
    private final PlatformTransactionManager manager;
    private final OnboardingService onboarding;

    @Transactional
    public Map<String,Object> get() {
        long uid=db.owner();
        db.lock(uid);
        focus.compensate(uid);
        Instant now=Instant.now();
        var day=days.resolve(uid,now);
        repeats.ensureInstance(uid,day.learningDate());
        var current=focus.current();
        var result=new LinkedHashMap<String,Object>();
        result.put("learningDay",day.learningDate());
        result.put("ruleVersion",day.ruleVersion());
        result.put("serverNow",now);
        result.put("updatedAt",now);
        result.put("learningDayInfo",Map.of("date",day.learningDate(),"timezone",day.zone().getId(),
                "dayStartHour",day.dayStartHour(),"startAt",day.start(),"endAt",day.end(),
                "durationSeconds",Duration.between(day.start(),day.end()).getSeconds()));
        result.put("currentSession",current.get("session"));
        result.put("pendingResults",current.get("pendingResults"));
        long seconds=db.jdbc().queryForObject("""
                select coalesce(sum(f.valid_seconds),0) from study_copilot.t_focus_slice f
                join study_copilot.t_focus_session s on s.user_id=f.user_id and s.id=f.session_id
                where f.user_id=? and f.learning_date=? and s.deleted=0
                """,Long.class,uid,day.learningDate());
        int completed=db.jdbc().queryForObject("""
                select count(*) from study_copilot.t_focus_session where user_id=? and deleted=0
                  and source='TIMER' and mode='FOCUS' and status='COMPLETED' and completed_learning_date=?
                """,Integer.class,uid,day.learningDate());
        var stats=new LinkedHashMap<String,Object>();
        stats.put("status",seconds==0?"EMPTY":"OK");
        stats.put("retryable",false);
        stats.put("focusSeconds",seconds);
        stats.put("focusMinutes",seconds/60);
        stats.put("completedPomodoros",completed);
        long runningSeconds=0;
        if(current.get("session") instanceof Map<?,?> session && "FOCUS".equals(session.get("mode")))
            runningSeconds=Math.max(0,Math.min(number(session.get("planned_seconds")),Duration.between(instant(session.get("start_at")),now).getSeconds()));
        stats.put("runningEstimatedSeconds",runningSeconds);
        result.put("stats",stats);

        var setting=db.one("select weekly_goal_min,exam_date,goal_name from study_copilot.t_user_setting where user_id=?",uid);
        LocalDate monday=day.learningDate().minusDays(day.learningDate().getDayOfWeek().getValue()-1);
        long week=db.jdbc().queryForObject("""
                select coalesce(sum(f.valid_seconds),0) from study_copilot.t_focus_slice f
                join study_copilot.t_focus_session s on s.user_id=f.user_id and s.id=f.session_id
                where f.user_id=? and f.learning_date>=? and f.learning_date<? and s.deleted=0
                """,Long.class,uid,monday,monday.plusDays(7));
        var weekly=new LinkedHashMap<String,Object>();
        weekly.put("status",setting.get("weekly_goal_min")==null?"EMPTY":"OK");
        weekly.put("retryable",false);
        weekly.put("startDate",monday);
        weekly.put("endDate",monday.plusDays(6));
        weekly.put("targetMinutes",setting.get("weekly_goal_min"));
        weekly.put("actualSeconds",week);
        Double percentage=setting.get("weekly_goal_min")==null?null:week*100.0/(number(setting.get("weekly_goal_min"))*60);
        weekly.put("percentage",percentage);
        weekly.put("progressPercentage",percentage==null?null:Math.min(100,percentage));
        result.put("weeklyGoal",weekly);
        result.put("examDate",setting.get("exam_date"));
        var goal=new LinkedHashMap<String,Object>();
        goal.put("name",setting.get("goal_name"));
        goal.put("date",setting.get("exam_date"));
        goal.put("status",setting.get("exam_date")==null?"EMPTY":"OK");
        LocalDate target=date(setting.get("exam_date"));
        Long remaining=target==null?null:ChronoUnit.DAYS.between(now.atZone(day.zone()).toLocalDate(),target);
        goal.put("daysRemaining",remaining==null?null:Math.max(0,remaining));
        goal.put("expired",remaining!=null && remaining<0);
        result.put("goal",goal);

        var taskModule=module(()->tasks.list(day.learningDate(),"ACTIVE",null,1,6,false));
        var taskData=new LinkedHashMap<String,Object>();
        if(taskModule.get("data") instanceof Map<?,?> data) data.forEach((key,value)->taskData.put(key.toString(),value));
        taskData.put("status",taskModule.get("status"));
        taskData.put("retryable",taskModule.get("retryable"));
        result.put("tasks",taskData);
        if(taskData.containsKey("validCount")) {
            stats.put("completedTaskCount",taskData.get("completedCount"));
            stats.put("validTaskCount",taskData.get("validCount"));
            stats.put("taskCompletionRate",taskData.get("completionRate"));
        }
        var distribution=module(()->distribution(uid,day.learningDate()));
        if(distribution.get("data") instanceof List<?> data) {
            distribution.put("visibleItems",data.subList(0,Math.min(5,data.size())));
            distribution.put("otherItems",data.subList(Math.min(5,data.size()),data.size()));
            distribution.put("totalSeconds",seconds);
        }
        result.put("subjectDistribution",distribution);
        result.put("recentRecords",module(()->records.list(day.learningDate(),null,1,3)));
        result.put("pet",module(pets::status));
        result.put("plan",module(onboarding::planSummary));
        result.put("dataVersion",db.one("select data_version from study_copilot.t_user where id=?",uid).get("data_version"));
        return result;
    }

    private List<Map<String,Object>> distribution(long uid,LocalDate date) {
        var rows=db.jdbc().queryForList("""
                select f.subject_id,coalesce(sub.name,max(s.subject_name_snapshot)) name,
                  coalesce(sub.color,max(s.subject_color_snapshot)) color,coalesce(sub.archived,1) archived,
                  sum(f.valid_seconds) seconds from study_copilot.t_focus_slice f
                join study_copilot.t_focus_session s on s.user_id=f.user_id and s.id=f.session_id
                left join study_copilot.t_subject sub on sub.user_id=f.user_id and sub.id=f.subject_id
                where f.user_id=? and f.learning_date=? and s.deleted=0
                group by f.subject_id,sub.name,sub.color,sub.archived order by seconds desc,f.subject_id
                """,uid,date);
        long total=rows.stream().mapToLong(row->number(row.get("seconds"))).sum();
        var result=new ArrayList<Map<String,Object>>();
        for(var row:rows) {
            var item=new LinkedHashMap<>(row);
            item.put("percentage",total==0?0:number(row.get("seconds"))*100.0/total);
            result.add(item);
        }
        return result;
    }

    private Map<String,Object> module(Supplier<Object> supplier) {
        var nested=new TransactionTemplate(manager);
        nested.setPropagationBehavior(TransactionDefinition.PROPAGATION_NESTED);
        try {
            Object data=nested.execute(status->supplier.get());
            boolean empty=data==null || data instanceof Collection<?> collection && collection.isEmpty()
                    || data instanceof Map<?,?> map && map.get("items") instanceof Collection<?> items && items.isEmpty()
                    || data instanceof Map<?,?> plan && plan.get("stages") instanceof Collection<?> stages && stages.isEmpty();
            var result=new LinkedHashMap<String,Object>();
            result.put("status",empty?"EMPTY":"OK");
            result.put("retryable",false);
            result.put("data",data);
            return result;
        } catch(RuntimeException ex) {
            return new LinkedHashMap<>(Map.of("status","ERROR","retryable",true));
        }
    }
}
