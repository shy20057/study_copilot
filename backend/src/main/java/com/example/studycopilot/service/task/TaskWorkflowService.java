package com.example.studycopilot.service.task;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.support.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static com.example.studycopilot.support.db.OwnerStore.*;

/** 一期任务内核：用户锁、父子一致性、可追溯撤销与版本冲突。 */
@Service @RequiredArgsConstructor
public class TaskWorkflowService {
    private final OwnerStore db;
    private final LearningDayService days;
    private final IdempotencyService keys;

    public Map<String,Object> list(LocalDate day, String filter, Long subject) {
        return list(day, filter, subject, 1, 50, false);
    }

    public Map<String,Object> list(LocalDate day, String filter, Long subject, int page, int size, boolean overdue) {
        return listScope(day,filter,subject,page,size,overdue?1:0);
    }

    public Map<String,Object> unplanned(Long subject,int page,int size) {
        return listScope(null,"ALL",subject,page,size,2);
    }

    private Map<String,Object> listScope(LocalDate day, String filter, Long subject, int page, int size, int scope) {
        long uid = db.owner();
        if (!Set.of("ALL", "TODO", "DONE", "CANCELLED", "ACTIVE").contains(filter)
                || page < 1 || size < 1 || size > 100) throw error(ScErrorCode.VALIDATION_ERROR);
        LocalDate selectedDay = day == null ? days.resolve(uid, Instant.now()).learningDate() : day;
        String where = " where user_id=? and deleted=0 and parent_id is null and "
                + (scope==2 ? "target_date is null" : scope==1 ? "target_date<? and status='TODO'" : "target_date=?")
                + " and (?::bigint is null or subject_id=?)";
        var args = new ArrayList<Object>();
        args.add(uid);
        if(scope!=2) args.add(selectedDay);
        args.add(subject);
        args.add(subject);
        var counts = new LinkedHashMap<String,Long>();
        for (String state : List.of("TODO", "DONE", "CANCELLED")) counts.put(state, 0L);
        for (var count : db.jdbc().queryForList("select status,count(*) count from study_copilot.t_task" + where + " group by status", args.toArray()))
            counts.put(count.get("status").toString(), number(count.get("count")));
        String filtered = filter.equals("ALL") ? "" : filter.equals("ACTIVE") ? " and status<>'CANCELLED'" : " and status=?";
        var selected = new ArrayList<>(args);
        if (!filter.equals("ALL") && !filter.equals("ACTIVE")) selected.add(filter);
        long total = db.jdbc().queryForObject("select count(*) from study_copilot.t_task" + where + filtered, Long.class, selected.toArray());
        selected.add(size);
        selected.add((long) (page - 1) * size);
        var rows = db.jdbc().queryForList("select * from study_copilot.t_task" + where + filtered
                + " order by case status when 'TODO' then 0 when 'DONE' then 1 else 2 end,sort_order,id limit ? offset ?", selected.toArray());
        var items = rows.stream().map(row -> publicTask(uid, row, true)).toList();
        long valid = counts.get("TODO") + counts.get("DONE");
        var result = new LinkedHashMap<String,Object>();
        result.put("items", items);
        result.put("counts", counts);
        result.put("completedCount", counts.get("DONE"));
        result.put("validCount", valid);
        result.put("completionRate", valid == 0 ? null : counts.get("DONE") * 100.0 / valid);
        result.put("total", total);
        result.put("page", page);
        result.put("pageSize", size);
        result.put("learningDay", selectedDay);
        return result;
    }

    public Map<String,Object> get(long id) {
        long uid = db.owner();
        var row = db.task(uid, id);
        active(row);
        return publicTask(uid, row, true);
    }

    private Map<String,Object> publicTask(long uid, Map<String,Object> row, boolean children) {
        var result = new LinkedHashMap<>(row);
        result.remove("cancel_batch_id");
        result.remove("deletion_batch_id");
        long id = number(row.get("id"));
        var actual = db.one("""
                select coalesce(sum(valid_seconds),0) actual_seconds,
                  count(*) filter(where source='TIMER' and mode='FOCUS' and status='COMPLETED') actual_pomodoros
                from study_copilot.t_focus_session
                where user_id=? and deleted=0 and mode='FOCUS' and status in ('COMPLETED','ENDED_EARLY')
                  and task_id in (select id from study_copilot.t_task where user_id=? and (id=? or parent_id=?))
                """, uid, uid, id, id);
        result.putAll(actual);
        if (children && row.get("parent_id") == null) {
            var childRows = db.jdbc().queryForList("select * from study_copilot.t_task where user_id=? and parent_id=? and deleted=0 order by sort_order,id", uid, id);
            var totals=db.jdbc().queryForList("""
                    select s.task_id,coalesce(sum(s.valid_seconds),0) actual_seconds,
                      count(*) filter(where s.source='TIMER' and s.status='COMPLETED') actual_pomodoros
                    from study_copilot.t_focus_session s join study_copilot.t_task t on t.user_id=s.user_id and t.id=s.task_id
                    where s.user_id=? and t.parent_id=? and s.mode='FOCUS' and s.deleted=0
                      and s.status in ('COMPLETED','ENDED_EARLY') group by s.task_id
                    """,uid,id);
            result.put("children", childRows.stream().map(child->{
                var item=new LinkedHashMap<>(child);
                item.remove("cancel_batch_id"); item.remove("deletion_batch_id");
                var total=totals.stream().filter(value->Objects.equals(value.get("task_id"),child.get("id"))).findFirst();
                item.put("actual_seconds",total.map(value->value.get("actual_seconds")).orElse(0L));
                item.put("actual_pomodoros",total.map(value->value.get("actual_pomodoros")).orElse(0L));
                return item;
            }).toList());
            if (childRows.stream().anyMatch(child -> !"CANCELLED".equals(child.get("status")))) {
                var planned = childRows.stream().filter(child -> !"CANCELLED".equals(child.get("status")))
                        .filter(child -> child.get("planned_pomodoros") != null).toList();
                result.put("planned_pomodoros", planned.isEmpty() ? null : planned.stream().mapToLong(child -> number(child.get("planned_pomodoros"))).sum());
                result.put("estimated_focus_minutes", planned.isEmpty() ? null : planned.stream()
                        .filter(child -> child.get("estimated_focus_minutes") != null).mapToLong(child -> number(child.get("estimated_focus_minutes"))).sum());
            }
        }
        return result;
    }

    @Transactional
    public Map<String,Object> create(Map<String,Object> request) { return create(request, null); }

    @Transactional
    public Map<String,Object> create(Map<String,Object> request, String key) {
        long uid = db.owner();
        db.lock(uid);
        return key == null ? createFor(uid, request) : keys.execute("TASK_CREATE", key, request, Map.class, () -> createFor(uid, request));
    }

    /** Trusted internal caller holds the owner lock. */
    public Map<String,Object> createFor(long uid, Map<String,Object> request) {
        String title = title(request.get("title"));
        Long parent = request.get("parentId") == null ? null : number(request.get("parentId"));
        Long subject = request.get("subjectId") == null ? null : number(request.get("subjectId"));
        LocalDate date = request.containsKey("targetDate") ? date(request.get("targetDate")) : days.resolve(uid, Instant.now()).learningDate();
        if (parent != null) {
            var owner = db.task(uid, parent);
            active(owner);
            if (owner.get("parent_id") != null || "CANCELLED".equals(owner.get("status")) || request.containsKey("repeatRule"))
                throw error(ScErrorCode.TASK_STATUS_INVALID);
            subject = owner.get("subject_id") == null ? null : number(owner.get("subject_id"));
            date = date(owner.get("target_date"));
        }
        String subjectName = subject == null ? null : db.subject(uid, subject).get("name").toString();
        Integer pomodoros = optionalPomodoros(request.get("plannedPomodoros"));
        int priority = integer(request, "priority", 2);
        if (priority < 1 || priority > 3) throw error(ScErrorCode.VALIDATION_ERROR);
        Long id = db.jdbc().queryForObject("""
                insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot,parent_id,
                  planned_pomodoros,priority,target_date,learning_date,original_planned_date,estimated_focus_minutes,sort_order)
                values (?,?,?,?,?,?,?,?,?,?,?::integer*(select focus_minutes from study_copilot.t_user_setting where user_id=?),
                  (select coalesce(max(sort_order),0)+1 from study_copilot.t_task where user_id=? and deleted=0)) returning id
                """, Long.class, uid, title, subject, subjectName, parent, pomodoros, priority, date, date, date, pomodoros, uid, uid);
        if (parent != null) syncParent(uid, parent);
        return publicTask(uid, db.task(uid, id), true);
    }

    @Transactional
    public Map<String,Object> update(long id, Map<String,Object> request) {
        long uid = db.owner();
        db.lock(uid);
        var task = db.task(uid, id);
        active(task);
        db.version(task, request.get("expectedVersion"));
        String title = request.containsKey("title") ? title(request.get("title")) : task.get("title").toString();
        Long subject = request.containsKey("subjectId") ? nullableId(request.get("subjectId")) : nullableId(task.get("subject_id"));
        LocalDate target = request.containsKey("targetDate") ? date(request.get("targetDate")) : date(task.get("target_date"));
        if (task.get("parent_id") != null && (!Objects.equals(subject, nullableId(task.get("subject_id")))
                || !Objects.equals(target, date(task.get("target_date"))))) throw error(ScErrorCode.TASK_STATUS_INVALID);
        String subjectName = subject == null ? null : db.subject(uid, subject).get("name").toString();
        boolean moved = !Objects.equals(target, date(task.get("target_date")));
        if (!Objects.equals(subject, nullableId(task.get("subject_id")))) running(uid, id);
        Integer pomodoros = request.containsKey("plannedPomodoros") ? optionalPomodoros(request.get("plannedPomodoros"))
                : task.get("planned_pomodoros") == null ? null : ((Number) task.get("planned_pomodoros")).intValue();
        int priority = integer(request, "priority", ((Number) task.get("priority")).intValue());
        if (priority < 1 || priority > 3) throw error(ScErrorCode.VALIDATION_ERROR);
        if (moved) log(uid, task, "RESCHEDULE", UUID.randomUUID(), target, false);
        db.jdbc().update("""
                update study_copilot.t_task set title=?,subject_id=?,subject_name_snapshot=?,planned_pomodoros=?,priority=?,
                  target_date=?,estimated_focus_minutes=case when planned_pomodoros is not distinct from ?::smallint
                  then estimated_focus_minutes else ?::integer*(select focus_minutes from study_copilot.t_user_setting where user_id=?) end,
                  repeat_overridden=case when repeat_template_id is not null then true else repeat_overridden end,
                  version=version+1,update_time=now() where user_id=? and id=?
                """, title, subject, subjectName, pomodoros, priority, target, pomodoros, pomodoros, uid, uid, id);
        if (task.get("parent_id") == null) db.jdbc().update("""
                update study_copilot.t_task set subject_id=?,subject_name_snapshot=?,target_date=?,version=version+1,update_time=now()
                where user_id=? and parent_id=? and deleted=0
                  and (subject_id is distinct from ?::bigint or target_date is distinct from ?::date)
                """, subject, subjectName, target, uid, id, subject, target);
        if (request.containsKey("status")) setStatus(uid, id, text(request,"status",""), Boolean.TRUE.equals(request.get("completeChildren")));
        return publicTask(uid, db.task(uid, id), true);
    }

    @Transactional
    public Map<String,Object> state(long id, String state, Map<String,Object> request) {
        long uid = db.owner();
        db.lock(uid);
        var task = db.task(uid, id);
        active(task);
        db.version(task, request.get("expectedVersion"));
        setStatus(uid, id, state, Boolean.TRUE.equals(request.get("completeChildren")));
        return publicTask(uid, db.task(uid, id), true);
    }

    private void setStatus(long uid, long id, String state, boolean cascade) {
        if (!Set.of("TODO", "DONE", "CANCELLED").contains(state)) throw error(ScErrorCode.TASK_STATUS_INVALID);
        var task = db.task(uid, id);
        if (state.equals(task.get("status"))) return;
        if(task.get("parent_id")!=null && !state.equals("CANCELLED")) {
            var parent=db.task(uid,number(task.get("parent_id")));
            active(parent);
            if("CANCELLED".equals(parent.get("status")))throw error(ScErrorCode.TASK_STATUS_INVALID);
        }
        if (!state.equals("CANCELLED") && task.get("subject_id")!=null) db.subject(uid,number(task.get("subject_id")));
        if (state.equals("CANCELLED")) {
            running(uid, id);
            UUID batch = UUID.randomUUID();
            var affected = db.jdbc().queryForList("select * from study_copilot.t_task where user_id=? and deleted=0 and (id=? or parent_id=? and status='TODO') order by id", uid, id, id);
            for (var row : affected) {
                log(uid, row, "CANCEL", batch, date(row.get("target_date")), false);
                db.jdbc().update("update study_copilot.t_task set status='CANCELLED',cancelled_at=now(),done_time=null,cancel_batch_id=?,version=version+1,update_time=now() where user_id=? and id=?", batch, uid, row.get("id"));
            }
        } else {
            if ("CANCELLED".equals(task.get("status")) && task.get("cancel_batch_id") != null)
                restoreCancellation(uid, id, task.get("cancel_batch_id"));
            if (state.equals("DONE")) {
                int remaining = db.jdbc().queryForObject("select count(*) from study_copilot.t_task where user_id=? and parent_id=? and deleted=0 and status='TODO'", Integer.class, uid, id);
                if (remaining > 0 && !cascade) throw new BusinessException(ScErrorCode.TASK_STATUS_INVALID, null, Map.of("incompleteCount",remaining));
                if (cascade) db.jdbc().update("update study_copilot.t_task set status='DONE',done_time=now(),cancelled_at=null,cancel_batch_id=null,version=version+1,update_time=now() where user_id=? and parent_id=? and status='TODO' and deleted=0", uid, id);
            }
            db.jdbc().update("update study_copilot.t_task set status=?,done_time=case when ?='DONE' then now() else null end,cancelled_at=null,cancel_batch_id=null,version=version+1,update_time=now() where user_id=? and id=?", state, state, uid, id);
        }
        if (task.get("parent_id") != null) syncParent(uid, number(task.get("parent_id")));
    }

    private void restoreCancellation(long uid, long root, Object batch) {
        var logs = db.jdbc().queryForList("select * from study_copilot.t_task_schedule_log where user_id=? and batch_id=? and operation_type='CANCEL' and undone_at is null order by id", uid, batch);
        for (var entry : logs) {
            long id = number(entry.get("task_id"));
            if (id == root) continue;
            var task = db.task(uid, id);
            // Preserve independent deletion or a later cancellation, and never overwrite edited content.
            if(number(task.get("deleted"))!=0 || !"CANCELLED".equals(task.get("status"))
                    || !Objects.equals(Objects.toString(task.get("cancel_batch_id"),null),batch.toString())) continue;
            var previous = db.decode(entry.get("previous_state"));
            db.jdbc().update("update study_copilot.t_task set status=?,done_time=?::timestamptz,cancelled_at=?::timestamptz,cancel_batch_id=?::uuid,version=version+1,update_time=now() where user_id=? and id=?",
                    previous.get("status"), previous.get("doneAt"), previous.get("cancelledAt"), previous.get("cancelBatchId"), uid, id);
        }
        db.jdbc().update("update study_copilot.t_task_schedule_log set undone_at=now() where user_id=? and batch_id=? and operation_type='CANCEL'", uid, batch);
    }

    public void syncParent(long uid, long id) {
        var parent = db.task(uid, id);
        if (number(parent.get("deleted")) != 0 || "CANCELLED".equals(parent.get("status"))) return;
        var counts = db.one("select count(*) filter(where status<>'CANCELLED') active,count(*) filter(where status='TODO') todo from study_copilot.t_task where user_id=? and parent_id=? and deleted=0", uid, id);
        String next = number(counts.get("active")) > 0 && number(counts.get("todo")) == 0 ? "DONE" : "TODO";
        if (!next.equals(parent.get("status"))) db.jdbc().update("update study_copilot.t_task set status=?,done_time=case when ?='DONE' then now() else null end,version=version+1,update_time=now() where user_id=? and id=?", next, next, uid, id);
    }

    public void running(long uid, long id) {
        if (Boolean.TRUE.equals(db.jdbc().queryForObject("select exists(select 1 from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0 and (task_id=? or task_id in(select id from study_copilot.t_task where user_id=? and parent_id=?)))", Boolean.class, uid, id, uid, id)))
            throw error(ScErrorCode.SESSION_EXISTS);
    }

    @Transactional
    public Map<String,Object> delete(long id, long version) {
        long uid = db.owner();
        db.lock(uid);
        var task = db.task(uid, id);
        active(task);
        db.version(task, version);
        running(uid, id);
        UUID batch = UUID.randomUUID();
        var affected = db.jdbc().queryForList("select * from study_copilot.t_task where user_id=? and deleted=0 and (id=? or parent_id=?) order by id", uid, id, id);
        for (var row : affected) log(uid, row, "DELETE", batch, date(row.get("target_date")), true);
        db.jdbc().update("update study_copilot.t_task set deleted=1,deleted_at=clock_timestamp(),deletion_batch_id=?,version=version+1,update_time=now() where user_id=? and (id=? or parent_id=?) and deleted=0", batch, uid, id, id);
        if (task.get("parent_id") != null) syncParent(uid, number(task.get("parent_id")));
        Instant until=Instant.now().plusSeconds(5);
        db.jdbc().update("update study_copilot.t_task_schedule_log set undo_expires_at=? where user_id=? and batch_id=?",java.sql.Timestamp.from(until),uid,batch);
        return Map.of("undoUntil", until, "task", db.task(uid,id));
    }

    @Transactional
    public Map<String,Object> restore(long id, long version) {
        long uid = db.owner();
        db.lock(uid);
        var task = db.task(uid, id);
        db.version(task, version);
        if (number(task.get("deleted")) != 1 || task.get("deletion_batch_id") == null) throw error(ScErrorCode.TASK_STATUS_INVALID);
        if (task.get("parent_id") != null) active(db.task(uid, number(task.get("parent_id"))));
        var logs = db.jdbc().queryForList("select * from study_copilot.t_task_schedule_log where user_id=? and batch_id=? and operation_type='DELETE' order by id", uid, task.get("deletion_batch_id"));
        if (logs.isEmpty()) throw error(ScErrorCode.TASK_STATUS_INVALID);
        for (var entry : logs) {
            if (entry.get("undone_at") != null || instant(entry.get("undo_expires_at")).isBefore(Instant.now())) throw error(ScErrorCode.TASK_STATUS_INVALID);
            var row = db.task(uid, number(entry.get("task_id")));
            db.version(row, entry.get("resulting_version"));
            if (number(row.get("deleted")) != 1) throw error(ScErrorCode.TASK_STATUS_INVALID);
            if (row.get("subject_id") != null) db.subject(uid, number(row.get("subject_id")));
        }
        db.jdbc().update("update study_copilot.t_task set deleted=0,deleted_at=null,deletion_batch_id=null,version=version+1,update_time=now() where user_id=? and deletion_batch_id=?", uid, task.get("deletion_batch_id"));
        if(task.get("parent_id")!=null) {
            var parent=db.task(uid,number(task.get("parent_id")));
            db.jdbc().update("update study_copilot.t_task set subject_id=?,subject_name_snapshot=?,target_date=? where user_id=? and id=?",
                parent.get("subject_id"),parent.get("subject_name_snapshot"),parent.get("target_date"),uid,id);
        }
        db.jdbc().update("update study_copilot.t_task_schedule_log set undone_at=now() where user_id=? and batch_id=? and operation_type='DELETE'", uid, task.get("deletion_batch_id"));
        if (task.get("parent_id") != null) syncParent(uid, number(task.get("parent_id")));
        return publicTask(uid, db.task(uid,id), true);
    }

    @Transactional
    public void reorder(Map<String,Object> request) {
        long uid = db.owner();
        db.lock(uid);
        String state = text(request,"status","ALL");
        if (!Set.of("ALL","TODO","DONE","CANCELLED").contains(state)) throw error(ScErrorCode.VALIDATION_ERROR);
        var current = db.jdbc().queryForList("select id,version from study_copilot.t_task where user_id=? and deleted=0 and target_date is not distinct from ?::date and parent_id is not distinct from ?::bigint and (?='ALL' or status=?) and (?::bigint is null or subject_id=?)",
                uid, date(request.get("targetDate")), nullableId(request.get("parentId")), state, state, nullableId(request.get("subjectId")), nullableId(request.get("subjectId")));
        if (!(request.get("items") instanceof List<?> items) || items.size() != current.size()) throw error(ScErrorCode.VERSION_CONFLICT);
        var seen = new HashSet<Long>();
        int order = 0;
        for (Object item : items) {
            if (!(item instanceof Map<?,?> values)) throw error(ScErrorCode.VALIDATION_ERROR);
            long id = number(values.get("id"));
            if (!seen.add(id)) throw error(ScErrorCode.VERSION_CONFLICT);
            var row = current.stream().filter(value -> number(value.get("id")) == id).findFirst().orElseThrow(() -> error(ScErrorCode.VERSION_CONFLICT));
            db.version(row, values.get("expectedVersion"));
            db.jdbc().update("update study_copilot.t_task set sort_order=?,version=version+1,update_time=now() where user_id=? and id=?", order++, uid, id);
        }
    }

    public void log(long uid, Map<String,Object> task, String operation, UUID batch, LocalDate target, boolean undo) {
        var previous = new LinkedHashMap<String,Object>();
        previous.put("status", task.get("status"));
        previous.put("targetDate", Objects.toString(task.get("target_date"), null));
        previous.put("doneAt", task.get("done_time") == null ? null : instant(task.get("done_time")).toString());
        previous.put("cancelledAt", task.get("cancelled_at") == null ? null : instant(task.get("cancelled_at")).toString());
        previous.put("cancelBatchId", Objects.toString(task.get("cancel_batch_id"), null));
        db.jdbc().update("""
                insert into study_copilot.t_task_schedule_log(user_id,task_id,operation_type,batch_id,previous_date,new_date,
                  previous_version,resulting_version,previous_state,undo_expires_at)
                values (?,?,?,?,?,?,?,?,?::jsonb,case when ? then clock_timestamp()+interval '5 seconds' else null end)
                """, uid, task.get("id"), operation, batch, task.get("target_date"), target, task.get("version"), number(task.get("version"))+1, db.encode(previous), undo);
    }

    private void active(Map<String,Object> task) { if (number(task.get("deleted")) != 0) throw error(ScErrorCode.TASK_NOT_FOUND); }
    private Long nullableId(Object value) { return value == null ? null : number(value); }
    private String title(Object value) {
        if(!(value instanceof String)) throw error(ScErrorCode.VALIDATION_ERROR);
        String title = value == null ? "" : value.toString().strip();
        if (title.isEmpty() || title.length() > 200) throw error(ScErrorCode.VALIDATION_ERROR);
        return title;
    }
    private Integer optionalPomodoros(Object value) {
        if (value == null) return null;
        long count = number(value);
        if (count < 1 || count > 16) throw error(ScErrorCode.VALIDATION_ERROR);
        return (int) count;
    }
}
