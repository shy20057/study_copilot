package com.example.studycopilot.service.focus;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.domain.time.TimeSliceService;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.support.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import static com.example.studycopilot.support.db.OwnerStore.*;

/** 补录和真实计时共用区间约束与学习日切片，补录始终不发经验。 */
@Service @RequiredArgsConstructor
public class FocusRecordService {
    private final OwnerStore db;
    private final FocusService focus;
    private final LearningDayService days;
    private final TimeSliceService slicer;
    private final IdempotencyService keys;

    public Map<String,Object> list(LocalDate day,Long subject,int page,int size) {
        long uid=db.owner();
        if (day==null) day=days.resolve(uid,Instant.now()).learningDate();
        if(page<1 || size<1 || size>100) throw error(ScErrorCode.VALIDATION_ERROR);
        String predicate=" from study_copilot.t_focus_slice f join study_copilot.t_focus_session s on s.id=f.session_id and s.user_id=f.user_id where f.user_id=? and s.deleted=0 and f.learning_date=? and (?::bigint is null or f.subject_id=?)";
        long count=db.jdbc().queryForObject("select count(*)"+predicate,Long.class,uid,day,subject,subject);
        var items=db.jdbc().queryForList("""
                select s.id,s.source,s.mode,s.status,s.start_at session_start_at,s.end_at session_end_at,
                  s.actual_seconds session_actual_seconds,s.valid_seconds session_valid_seconds,s.planned_seconds,
                  s.subject_id,s.subject_name_snapshot,s.subject_color_snapshot,s.task_id,s.task_title_snapshot,
                  s.version,s.note,s.end_reason,s.settled_at,f.learning_date,f.slice_start_at,f.slice_end_at,f.valid_seconds,
                  coalesce((select sum(awarded_exp) from study_copilot.t_reward_ledger r where r.user_id=s.user_id and r.session_id=s.id and r.learning_date=f.learning_date),0) awarded_exp,
                  coalesce((select sum(awarded_exp) from study_copilot.t_reward_ledger r where r.user_id=s.user_id and r.session_id=s.id),0) session_awarded_exp
                """+predicate+" order by f.slice_end_at desc,s.id desc limit ? offset ?",uid,day,subject,subject,size,(long)(page-1)*size);
        return Map.of("items",items,"total",count,"page",page,"pageSize",size,"learningDay",day);
    }

    @Transactional public Map<String,Object> create(Map<String,Object> body,String key) {
        return keys.execute("MANUAL_CREATE",key,body,Map.class,()->write(null,body,key));
    }
    @Transactional public Map<String,Object> update(long id,Map<String,Object> body) { return write(id,body,null); }

    public Map<String,Object> preview(Map<String,Object> body) {
        long uid=db.owner();
        Instant start=instant(body.get("startAt")).truncatedTo(ChronoUnit.MICROS);
        Instant end=instant(body.get("endAt")).truncatedTo(ChronoUnit.MICROS);
        validateTime(start,end);
        long subject=number(body.get("subjectId"));
        Long excluded=body.get("recordId")==null?null:number(body.get("recordId"));
        if(excluded!=null) {
            var original=focus.session(uid,excluded);
            manual(original);
            db.version(original,body.get("expectedVersion"));
            if(number(original.get("subject_id"))!=subject)db.subject(uid,subject);
        } else db.subject(uid,subject);
        focus.overlap(uid,start,end,excluded);
        var rule=days.resolve(uid,start);
        var parts=slicer.allocate(start,end,rule.zone(),rule.dayStartHour(),rule.ruleVersion()).stream()
                .map(part->Map.of("learningDate",part.interval().learningDate(),"startAt",part.interval().start(),
                        "endAt",part.interval().end(),"validSeconds",part.seconds())).toList();
        return Map.of("validSeconds",Duration.between(start,end).getSeconds(),"slices",parts,"awardedExp",0,"completedPomodoros",0);
    }

    private Map<String,Object> write(Long id,Map<String,Object> body,String key) {
        long uid=db.owner();
        db.lock(uid);
        var old=id==null?null:focus.session(uid,id);
        if(old!=null) { manual(old); db.version(old,body.get("expectedVersion")); }
        Instant start=instant(body.containsKey("startAt")?body.get("startAt"):old==null?null:old.get("start_at")).truncatedTo(ChronoUnit.MICROS);
        Instant end=instant(body.containsKey("endAt")?body.get("endAt"):old==null?null:old.get("end_at")).truncatedTo(ChronoUnit.MICROS);
        validateTime(start,end);
        int seconds=Math.toIntExact(Duration.between(start,end).getSeconds());
        long subject=number(body.containsKey("subjectId")?body.get("subjectId"):old==null?null:old.get("subject_id"));
        boolean sameSubject=old!=null && number(old.get("subject_id"))==subject;
        Map<String,Object> sub;
        if(sameSubject) {
            sub=new LinkedHashMap<>();
            sub.put("name",old.get("subject_name_snapshot"));
            sub.put("color",old.get("subject_color_snapshot"));
        } else sub=db.subject(uid,subject);
        Long task=body.containsKey("taskId")?(body.get("taskId")==null?null:number(body.get("taskId")))
                :old==null||old.get("task_id")==null?null:number(old.get("task_id"));
        String title=null;
        if(task!=null) {
            boolean unchanged=old!=null && Objects.equals(task,old.get("task_id")) && sameSubject;
            if(unchanged) title=Objects.toString(old.get("task_title_snapshot"),null);
            else {
                var linked=db.task(uid,task);
                if(number(linked.get("deleted"))!=0 || linked.get("subject_id")==null || number(linked.get("subject_id"))!=subject)
                    throw error(ScErrorCode.TASK_STATUS_INVALID);
                title=linked.get("title").toString();
            }
        }
        String note=body.containsKey("note")?(body.get("note")==null?null:body.get("note").toString().strip())
                :old==null?null:Objects.toString(old.get("note"),null);
        if(note!=null && note.length()>500) throw error(ScErrorCode.VALIDATION_ERROR);
        focus.overlap(uid,start,end,id);
        var rule=days.resolve(uid,start);
        String config=db.encode(Map.of("timezone",rule.zone().getId(),"dayStartHour",rule.dayStartHour()));
        if(id==null) id=db.jdbc().queryForObject("""
                insert into study_copilot.t_focus_session(user_id,source,status,request_id,subject_id,subject_name_snapshot,
                  subject_color_snapshot,task_id,task_title_snapshot,config_snapshot,planned_seconds,actual_seconds,valid_seconds,
                  start_at,scheduled_end_at,end_at,rule_version,settled_at,end_reason,note)
                values (?,'MANUAL','COMPLETED',?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,now(),'MANUAL',?) returning id
                """,Long.class,uid,key,subject,sub.get("name"),sub.get("color"),task,title,config,seconds,seconds,seconds,
                java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),java.sql.Timestamp.from(end),rule.ruleVersion(),note);
        else db.jdbc().update("""
                update study_copilot.t_focus_session set subject_id=?,subject_name_snapshot=?,subject_color_snapshot=?,task_id=?,
                  task_title_snapshot=?,config_snapshot=?::jsonb,planned_seconds=?,actual_seconds=?,valid_seconds=?,start_at=?,
                  scheduled_end_at=?,end_at=?,rule_version=?,note=?,version=version+1,update_time=now() where user_id=? and id=?
                """,subject,sub.get("name"),sub.get("color"),task,title,config,seconds,seconds,seconds,java.sql.Timestamp.from(start),
                java.sql.Timestamp.from(end),java.sql.Timestamp.from(end),rule.ruleVersion(),note,uid,id);
        db.jdbc().update("delete from study_copilot.t_focus_slice where user_id=? and session_id=?",uid,id);
        for(var part:slicer.allocate(start,end,rule.zone(),rule.dayStartHour(),rule.ruleVersion())) {
            var interval=part.interval();
            db.jdbc().update("""
                    insert into study_copilot.t_focus_slice(user_id,session_id,learning_date,rule_version,slice_start_at,slice_end_at,valid_seconds,subject_id)
                    values (?,?,?,?,?,?,?,?)
                    """,uid,id,interval.learningDate(),interval.ruleVersion(),java.sql.Timestamp.from(interval.start()),
                    java.sql.Timestamp.from(interval.end()),part.seconds(),subject);
        }
        return focus.publicSession(uid,id);
    }

    private void validateTime(Instant start,Instant end) {
        long seconds=Duration.between(start,end).getSeconds();
        if(seconds<60 || Duration.between(start,end).compareTo(Duration.ofHours(4))>0 || end.isAfter(Instant.now()))
            throw error(ScErrorCode.VALIDATION_ERROR);
    }
    private void manual(Map<String,Object> row) { if(!"MANUAL".equals(row.get("source"))) throw error(ScErrorCode.FORBIDDEN); }

    @Transactional public Map<String,Object> remove(long id,long version) {
        long uid=db.owner(); db.lock(uid);
        var row=focus.session(uid,id); manual(row); db.version(row,version);
        var deleted=db.jdbc().queryForMap("update study_copilot.t_focus_session set deleted=1,deleted_at=clock_timestamp(),version=version+1,update_time=now() where user_id=? and id=? returning deleted_at,version",uid,id);
        return Map.of("undoUntil",instant(deleted.get("deleted_at")).plusSeconds(5),"recordId",id,"version",deleted.get("version"));
    }
    @Transactional public Map<String,Object> restore(long id,long version) {
        long uid=db.owner(); db.lock(uid);
        var row=db.one("select * from study_copilot.t_focus_session where user_id=? and id=?",uid,id);
        manual(row); db.version(row,version);
        if(number(row.get("deleted"))!=1 || instant(row.get("deleted_at")).plusSeconds(5).isBefore(Instant.now()))
            throw error(ScErrorCode.POMODORO_STATUS_INVALID);
        focus.overlap(uid,instant(row.get("start_at")),instant(row.get("end_at")),id);
        db.jdbc().update("update study_copilot.t_focus_session set deleted=0,deleted_at=null,version=version+1,update_time=now() where user_id=? and id=?",uid,id);
        return focus.publicSession(uid,id);
    }
}
