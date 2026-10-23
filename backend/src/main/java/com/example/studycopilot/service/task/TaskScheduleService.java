package com.example.studycopilot.service.task;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.scheduling.annotation.Scheduled;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;

import static com.example.studycopilot.support.db.OwnerStore.*;

/** 重复实例的不可变生成身份、范围编辑和原子顺延撤销。 */
@Service @RequiredArgsConstructor
public class TaskScheduleService {
    private final OwnerStore db;
    private final TaskWorkflowService tasks;
    private final LearningDayService days;
    private final TransactionTemplate transactions;

    public List<Map<String,Object>> templates() {
        return db.jdbc().queryForList("select r.*,weekdays::text weekday_text from study_copilot.t_task_repeat_template r where user_id=? and deleted=0 order by id", db.owner()).stream().map(this::view).toList();
    }

    private Map<String,Object> template(long uid, long id) {
        return db.one("select r.*,weekdays::text weekday_text from study_copilot.t_task_repeat_template r where user_id=? and id=? and deleted=0", uid, id);
    }

    @Transactional
    public Map<String,Object> save(Long id, Map<String,Object> body) {
        long uid = db.owner();
        db.lock(uid);
        LocalDate today = days.resolve(uid, Instant.now()).learningDate();
        var values = new LinkedHashMap<String,Object>();
        if (id != null) {
            var old = template(uid,id);
            db.version(old, body.get("expectedVersion"));
            values.put("title", old.get("title"));
            values.put("subjectId", old.get("subject_id"));
            values.put("plannedPomodoros", old.get("planned_pomodoros"));
            values.put("priority", old.get("priority"));
            values.put("repeatRule", old.get("repeat_rule"));
            values.put("weekdays", weekdayList(old.get("weekday_text").toString()));
            values.put("startDate", old.get("start_date"));
            values.put("endDate", old.get("end_date"));
            values.put("status", old.get("status"));
        }
        values.putAll(body);
        String title = text(values,"title","").strip();
        String rule = text(values,"repeatRule","DAILY");
        String state = text(values,"status","ACTIVE");
        int priority = integer(values,"priority",2);
        Integer pomodoros = values.get("plannedPomodoros") == null ? null : integer(values,"plannedPomodoros",1);
        if (title.isEmpty() || title.length()>200 || !Set.of("DAILY","WEEKDAYS","WEEKLY").contains(rule)
                || !Set.of("ACTIVE","PAUSED","CANCELLED").contains(state) || priority<1 || priority>3
                || pomodoros != null && (pomodoros<1 || pomodoros>16)) throw error(ScErrorCode.VALIDATION_ERROR);
        long subject = number(values.get("subjectId"));
        db.subject(uid,subject);
        LocalDate start = values.get("startDate") == null ? today : date(values.get("startDate"));
        LocalDate end = date(values.get("endDate"));
        if (end != null && end.isBefore(start)) throw error(ScErrorCode.VALIDATION_ERROR);
        var selected = new TreeSet<Integer>();
        if (values.get("weekdays") instanceof List<?> list) for (Object day : list) {
            long value = number(day);
            if (value<1 || value>7) throw error(ScErrorCode.VALIDATION_ERROR);
            selected.add((int)value);
        }
        if (rule.equals("WEEKLY") && selected.isEmpty()) throw error(ScErrorCode.VALIDATION_ERROR);
        String array = "{" + String.join(",", selected.stream().map(Object::toString).toList()) + "}";
        boolean created = id == null;
        if (created) id = db.jdbc().queryForObject("""
                insert into study_copilot.t_task_repeat_template(user_id,title,subject_id,planned_pomodoros,priority,repeat_rule,weekdays,start_date,end_date,status)
                values (?,?,?,?,?,?,?::smallint[],?,?,?) returning id
                """, Long.class, uid,title,subject,pomodoros,priority,rule,array,start,end,state);
        else db.jdbc().update("""
                update study_copilot.t_task_repeat_template set title=?,subject_id=?,planned_pomodoros=?,priority=?,repeat_rule=?,
                  weekdays=?::smallint[],start_date=?,end_date=?,status=?,version=version+1,update_time=now() where user_id=? and id=?
                """, title,subject,pomodoros,priority,rule,array,start,end,state,uid,id);
        LocalDate effective = body.get("effectiveDate") == null ? today.plusDays(1) : date(body.get("effectiveDate"));
        if (effective.isBefore(today)) throw error(ScErrorCode.VALIDATION_ERROR);
        if (!created && state.equals("ACTIVE")) applyFuture(uid,id,effective);
        ensureInstance(uid,today);
        return view(template(uid,id));
    }

    @Transactional
    public Map<String,Object> editInstance(long id, Map<String,Object> request) {
        long uid = db.owner();
        db.lock(uid);
        var task = db.task(uid,id);
        db.version(task,request.get("expectedVersion"));
        LocalDate today = days.resolve(uid,Instant.now()).learningDate();
        if (task.get("repeat_template_id") == null || date(task.get("target_date")).isBefore(today)) throw error(ScErrorCode.TASK_STATUS_INVALID);
        if (request.get("templateExpectedVersion") == null) throw error(ScErrorCode.VALIDATION_ERROR);
        var change = new LinkedHashMap<>(request);
        change.remove("status");
        change.remove("targetDate");
        change.put("expectedVersion",request.get("templateExpectedVersion"));
        change.put("effectiveDate",date(task.get("target_date")).plusDays(1));
        if (!"TODO".equals(task.get("status")) || Boolean.TRUE.equals(db.jdbc().queryForObject(
                "select exists(select 1 from study_copilot.t_focus_session where user_id=? and task_id=?)",Boolean.class,uid,id)))
            throw error(ScErrorCode.TASK_STATUS_INVALID);
        var individual = new LinkedHashMap<>(request);
        var result = tasks.update(id,individual);
        save(number(task.get("repeat_template_id")),change);
        db.jdbc().update("update study_copilot.t_task set repeat_overridden=false where user_id=? and id=?",uid,id);
        return result;
    }

    private void applyFuture(long uid,long id,LocalDate effective) {
        var rule = template(uid,id);
        var instances = db.jdbc().queryForList("""
                select * from study_copilot.t_task t where user_id=? and repeat_template_id=? and deleted=0 and status='TODO'
                  and target_date>=? and repeat_overridden=false
                  and not exists(select 1 from study_copilot.t_focus_session s where s.user_id=t.user_id and s.task_id=t.id)
                order by id
                """,uid,id,effective);
        for (var instance : instances) {
            LocalDate date = date(instance.get("target_date"));
            if (!matches(rule,date)) {
                tasks.state(number(instance.get("id")),"CANCELLED",Map.of("expectedVersion",instance.get("version")));
                continue;
            }
            var changes = new LinkedHashMap<String,Object>();
            changes.put("expectedVersion",instance.get("version"));
            changes.put("title",rule.get("title"));
            changes.put("subjectId",rule.get("subject_id"));
            changes.put("priority",rule.get("priority"));
            changes.put("plannedPomodoros",rule.get("planned_pomodoros"));
            tasks.update(number(instance.get("id")),changes);
            db.jdbc().update("update study_copilot.t_task set repeat_overridden=false where user_id=? and id=?",uid,instance.get("id"));
        }
    }

    @Transactional public void ensureToday() {
        long uid=db.owner();
        db.lock(uid);
        ensureInstance(uid,days.resolve(uid,Instant.now()).learningDate());
    }

    /** Caller holds the owner lock. Only today's instances are generated; no historical backlog. */
    public void ensureInstance(long uid,LocalDate day) {
        var rows=db.jdbc().queryForList("""
                select r.*,s.name subject_name from study_copilot.t_task_repeat_template r
                join study_copilot.t_subject s on s.id=r.subject_id and s.user_id=r.user_id and s.archived=0
                where r.user_id=? and r.deleted=0 and r.status='ACTIVE' and start_date<=? and (end_date is null or end_date>=?)
                  and (repeat_rule='DAILY' or repeat_rule='WEEKDAYS' and extract(isodow from ?::date)<=5
                    or repeat_rule='WEEKLY' and extract(isodow from ?::date)::smallint=any(weekdays))
                """,uid,day,day,day,day);
        for(var row:rows) db.jdbc().update("""
                insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot,planned_pomodoros,priority,target_date,
                  learning_date,original_planned_date,repeat_template_id,estimated_focus_minutes,sort_order,backlog_skipped)
                values (?,?,?,?,?,?,?,?,?,?,?::integer*(select focus_minutes from study_copilot.t_user_setting where user_id=?),
                  (select coalesce(max(sort_order),0)+1 from study_copilot.t_task where user_id=? and deleted=0),?)
                on conflict(user_id,repeat_template_id,learning_date) do nothing
                """,uid,row.get("title"),row.get("subject_id"),row.get("subject_name"),row.get("planned_pomodoros"),row.get("priority"),day,day,day,row.get("id"),row.get("planned_pomodoros"),uid,uid,
                    row.get("last_generated_day")==null?date(row.get("start_date")).isBefore(day):date(row.get("last_generated_day")).isBefore(day.minusDays(1)));
        var next=days.resolve(uid,Instant.now()).end();
        db.jdbc().update("update study_copilot.t_task_repeat_template set last_generated_day=?,next_check_at=? where user_id=? and deleted=0 and status='ACTIVE' and (last_generated_day is distinct from ?::date or next_check_at<=now())",
            day,java.sql.Timestamp.from(next),uid,day);
    }

    @Scheduled(fixedDelayString="${study-copilot.tasks.repeat-scan-delay-ms:30000}")
    public void generateDue() {
        var owners=db.jdbc().queryForList("select distinct r.user_id from study_copilot.t_task_repeat_template r join study_copilot.t_user u on u.id=r.user_id where r.deleted=0 and r.status='ACTIVE' and r.next_check_at<=now() and u.status=1 and u.deleted=0 and u.last_seen_at>now()-interval '5 minutes' limit 100",Long.class);
        for(long uid:owners) try {
            transactions.executeWithoutResult(status->{
                if(!db.jdbc().queryForList("select id from study_copilot.t_user where id=? and status=1 and deleted=0 for update skip locked",uid).isEmpty())
                    ensureInstance(uid,days.resolve(uid,Instant.now()).learningDate());
            });
        } catch(RuntimeException ex) {
            // The due database row remains eligible for the next pass.
        }
    }

    @Transactional
    public Map<String,Object> carry(Map<String,Object> body) {
        long uid=db.owner();
        db.lock(uid);
        LocalDate today=days.resolve(uid,Instant.now()).learningDate();
        ensureInstance(uid,today);
        if (!(body.get("items") instanceof List<?> list) || list.isEmpty() || list.size()>500) throw error(ScErrorCode.VALIDATION_ERROR);
        UUID batch=UUID.randomUUID();
        var seen=new HashSet<Long>();
        for (Object raw:list) {
            if (!(raw instanceof Map<?,?> item)) throw error(ScErrorCode.VALIDATION_ERROR);
            long id=number(item.get("id"));
            if (!seen.add(id)) throw error(ScErrorCode.VALIDATION_ERROR);
            var root=db.task(uid,id);
            db.version(root,item.get("expectedVersion"));
            if (root.get("parent_id")!=null || number(root.get("deleted"))!=0 || !"TODO".equals(root.get("status"))
                    || root.get("target_date")==null || !date(root.get("target_date")).isBefore(today)) throw error(ScErrorCode.TASK_STATUS_INVALID);
            tasks.running(uid,id);
            boolean cancel=false;
            if (root.get("repeat_template_id")!=null && Boolean.TRUE.equals(db.jdbc().queryForObject("""
                    select exists(select 1 from study_copilot.t_task where user_id=? and repeat_template_id=? and learning_date=? and deleted=0 and id<>?)
                    """,Boolean.class,uid,root.get("repeat_template_id"),today,id))) {
                String choice=text(body,"conflictChoice","");
                if (!Set.of("KEEP_BOTH","KEEP_TODAY_CANCEL_OLD").contains(choice)) throw error(ScErrorCode.VERSION_CONFLICT);
                cancel=choice.equals("KEEP_TODAY_CANCEL_OLD");
            }
            var affected=db.jdbc().queryForList("select * from study_copilot.t_task where user_id=? and deleted=0 and (id=? or parent_id=?) order by id",uid,id,id);
            for (var row:affected) {
                if (cancel && !"TODO".equals(row.get("status"))) continue;
                tasks.log(uid,row,"CARRY_OVER",batch,cancel?date(row.get("target_date")):today,true);
                if (!cancel) db.jdbc().update("update study_copilot.t_task set target_date=?,version=version+1,update_time=now() where user_id=? and id=?",today,uid,row.get("id"));
            }
            if(cancel) tasks.state(id,"CANCELLED",Map.of("expectedVersion",root.get("version")));
        }
        Instant until=Instant.now().plusSeconds(5);
        db.jdbc().update("update study_copilot.t_task_schedule_log set undo_expires_at=? where user_id=? and batch_id=? and operation_type='CARRY_OVER'",java.sql.Timestamp.from(until),uid,batch);
        return Map.of("batchId",batch,"undoUntil",until,"changed",seen.size());
    }

    @Transactional
    public void undo(UUID batch) {
        long uid=db.owner();
        db.lock(uid);
        var logs=db.jdbc().queryForList("select * from study_copilot.t_task_schedule_log where user_id=? and batch_id=? and operation_type='CARRY_OVER' order by id",uid,batch);
        if(logs.isEmpty()) throw error(ScErrorCode.NOT_FOUND);
        for(var log:logs) {
            if(log.get("undone_at")!=null || instant(log.get("undo_expires_at")).isBefore(Instant.now())) throw error(ScErrorCode.TASK_STATUS_INVALID);
            long id=number(log.get("task_id"));
            var task=db.task(uid,id);
            db.version(task,log.get("resulting_version"));
            if (number(task.get("deleted"))!=0) throw error(ScErrorCode.TASK_STATUS_INVALID);
            tasks.running(uid,id);
            var old=db.decode(log.get("previous_state"));
            db.jdbc().update("update study_copilot.t_task set target_date=?::date,status=?,done_time=?::timestamptz,cancelled_at=?::timestamptz,cancel_batch_id=?::uuid,version=version+1,update_time=now() where user_id=? and id=?",
                    old.get("targetDate"),old.get("status"),old.get("doneAt"),old.get("cancelledAt"),old.get("cancelBatchId"),uid,id);
            db.jdbc().update("update study_copilot.t_task_schedule_log set undone_at=now() where user_id=? and id=?",uid,log.get("id"));
        }
    }

    private boolean matches(Map<String,Object> rule,LocalDate date) {
        if (date.isBefore(date(rule.get("start_date"))) || rule.get("end_date")!=null && date.isAfter(date(rule.get("end_date")))) return false;
        return switch(rule.get("repeat_rule").toString()) {
            case "DAILY" -> true;
            case "WEEKDAYS" -> date.getDayOfWeek().getValue()<=5;
            default -> weekdayList(rule.get("weekday_text").toString()).contains(date.getDayOfWeek().getValue());
        };
    }
    private List<Integer> weekdayList(String value) {
        var result=new ArrayList<Integer>();
        String content=value.replace("{","").replace("}","");
        if (!content.isBlank()) for(String day:content.split(",")) result.add(Integer.parseInt(day.strip()));
        return result;
    }
    private Map<String,Object> view(Map<String,Object> row) {
        var result=new LinkedHashMap<>(row);
        result.put("weekdays",weekdayList(result.remove("weekday_text").toString()));
        return result;
    }
}
