package com.example.studycopilot.service.task;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.support.db.OwnerStore;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.example.studycopilot.support.db.OwnerStore.*;

@Service @RequiredArgsConstructor
public class TaskWorkflowService {
    private final OwnerStore db;
    private final LearningDayService days;

    public Map<String,Object> list(LocalDate day,String filter,Long subject) {
        long uid=db.owner();
        if(!Set.of("ALL","TODO","DONE","CANCELLED").contains(filter))throw error(ScErrorCode.VALIDATION_ERROR);
        var rows=db.jdbc().queryForList("select * from study_copilot.t_task where user_id=? and deleted=0 and (?::date is null or target_date=?) and (?::bigint is null or subject_id=?) order by case status when 'TODO' then 0 when 'DONE' then 1 else 2 end,sort_order,id",uid,day,day,subject,subject);

        var roots=rows.stream().filter(r->r.get("parent_id")==null).toList();
        Map<String,Long> counts=new LinkedHashMap<>();
        for(String s:List.of("TODO","DONE","CANCELLED"))counts.put(s,roots.stream().filter(r->s.equals(r.get("status"))).count());

        var items=new ArrayList<Map<String,Object>>();
        for(var r:roots)if(filter.equals("ALL")||filter.equals(r.get("status"))) {
            var v=new LinkedHashMap<>(r);
            v.put("children",rows.stream().filter(c->Objects.equals(c.get("parent_id"),r.get("id"))).toList());
            items.add(v);
        }

        long valid=counts.get("TODO")+counts.get("DONE");
        return Map.of("items",items,"counts",counts,"completedCount",counts.get("DONE"),"validCount",valid,"completionRate",valid==0?0:counts.get("DONE")*100.0/valid);
    }

    @Transactional public Map<String,Object> create(Map<String,Object> req) {
        long uid=db.owner();
        db.lock(uid);
        return createFor(uid,req);
    }

    public Map<String,Object> createFor(long uid,Map<String,Object> req) {
        String title=text(req,"title","").trim();
        if(title.isEmpty()||title.length()>200)throw error(ScErrorCode.VALIDATION_ERROR);

        long sid=req.get("subjectId")==null?number(db.one("select default_subject_id from study_copilot.t_user_setting where user_id=?",uid).get("default_subject_id")):number(req.get("subjectId"));
        var sub=db.subject(uid,sid);

        Long parent=req.get("parentId")==null?null:number(req.get("parentId"));
        if(parent!=null) {
            var p=db.task(uid,parent);
            if(p.get("parent_id")!=null||!"TODO".equals(p.get("status"))||number(p.get("deleted"))!=0||number(p.get("subject_id"))!=sid)
                throw error(ScErrorCode.TASK_STATUS_INVALID);
        }

        int pom=integer(req,"plannedPomodoros",1),pri=integer(req,"priority",1);
        if(pom<1||pom>16||pri<1||pri>3)throw error(ScErrorCode.VALIDATION_ERROR);

        LocalDate day=req.get("targetDate")==null?days.resolve(uid,Instant.now()).learningDate():date(req.get("targetDate"));

        Long id=db.jdbc().queryForObject("insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot,parent_id,planned_pomodoros,priority,target_date,learning_date,original_planned_date,estimated_focus_minutes,sort_order) values (?,?,?,?,?,?,?,?,?,?,?*(select focus_minutes from study_copilot.t_user_setting where user_id=?),(select coalesce(max(sort_order),0)+1 from study_copilot.t_task where user_id=?)) returning id",
            Long.class,uid,title,sid,sub.get("name"),parent,pom,pri,day,day,day,pom,uid,uid);
        return db.task(uid,id);
    }

    @Transactional public Map<String,Object> update(long id,Map<String,Object> req) {
        long uid=db.owner();
        db.lock(uid);
        var t=db.task(uid,id);
        db.version(t,req.get("expectedVersion"));
        active(t);

        String title=text(req,"title",t.get("title").toString()).trim();
        if(title.isEmpty()||title.length()>200)throw error(ScErrorCode.VALIDATION_ERROR);

        long sid=req.get("subjectId")==null?number(t.get("subject_id")):number(req.get("subjectId"));
        var sub=db.subject(uid,sid);
        if(sid!=number(t.get("subject_id"))) {
            running(uid,id);
            if(t.get("parent_id")!=null||db.jdbc().queryForObject("select count(*) from study_copilot.t_task where user_id=? and parent_id=? and deleted=0",Integer.class,uid,id)>0)
                throw error(ScErrorCode.TASK_STATUS_INVALID);
        }

        int pom=integer(req,"plannedPomodoros",t.get("planned_pomodoros")==null?1:((Number)t.get("planned_pomodoros")).intValue());
        int pri=integer(req,"priority",((Number)t.get("priority")).intValue());
        if(pom<1||pom>16||pri<1||pri>3)throw error(ScErrorCode.VALIDATION_ERROR);

        LocalDate target=req.containsKey("targetDate")?date(req.get("targetDate")):date(t.get("target_date"));

        db.jdbc().update("update study_copilot.t_task set title=?,subject_id=?,subject_name_snapshot=?,planned_pomodoros=?,priority=?,target_date=?,learning_date=?,version=version+1,update_time=now() where user_id=? and id=?",
            title,sid,sub.get("name"),pom,pri,target,target,uid,id);
        if(req.containsKey("status"))setStatus(uid,id,text(req,"status","TODO"),Boolean.TRUE.equals(req.get("completeChildren")));
        return db.task(uid,id);
    }

    @Transactional public Map<String,Object> state(long id,String status,Map<String,Object> req) {
        long uid=db.owner();
        db.lock(uid);
        var t=db.task(uid,id);
        active(t);
        db.version(t,req.get("expectedVersion"));
        setStatus(uid,id,status,Boolean.TRUE.equals(req.get("completeChildren")));
        return db.task(uid,id);
    }

    private void setStatus(long uid,long id,String status,boolean cascade) {
        if(!Set.of("TODO","DONE","CANCELLED").contains(status))throw error(ScErrorCode.TASK_STATUS_INVALID);
        var t=db.task(uid,id);

        if(status.equals("CANCELLED"))running(uid,id);

        if(status.equals("DONE")) {
            int todo=db.jdbc().queryForObject("select count(*) from study_copilot.t_task where user_id=? and parent_id=? and deleted=0 and status='TODO'",Integer.class,uid,id);
            if(todo>0&&!cascade)throw new BusinessException(ScErrorCode.TASK_STATUS_INVALID,null,Map.of("incompleteCount",todo));
            if(cascade)db.jdbc().update("update study_copilot.t_task set status='DONE',done_time=now(),version=version+1,update_time=now() where user_id=? and parent_id=? and status='TODO' and deleted=0",uid,id);
        }
        if(status.equals("CANCELLED"))db.jdbc().update("update study_copilot.t_task set status='CANCELLED',cancelled_at=now(),version=version+1,update_time=now() where user_id=? and parent_id=? and status='TODO' and deleted=0",uid,id);

        db.jdbc().update("update study_copilot.t_task set status=?,done_time=case when ?='DONE' then now() else null end,cancelled_at=case when ?='CANCELLED' then now() else null end,version=version+1,update_time=now() where user_id=? and id=?",status,status,status,uid,id);

        // Reopening a child also reopens its parent; completing a child completes
        // the parent once no child is left open.
        if(t.get("parent_id")!=null) {
            long pid=number(t.get("parent_id"));
            if(status.equals("TODO"))db.jdbc().update("update study_copilot.t_task set status='TODO',done_time=null,version=version+1,update_time=now() where user_id=? and id=? and status='DONE'",uid,pid);
            else if(status.equals("DONE")&&db.jdbc().queryForObject("select count(*) from study_copilot.t_task where user_id=? and parent_id=? and deleted=0 and status='TODO'",Integer.class,uid,pid)==0)
                db.jdbc().update("update study_copilot.t_task set status='DONE',done_time=now(),version=version+1,update_time=now() where user_id=? and id=? and status='TODO'",uid,pid);
        }
    }

    private void active(Map<String,Object> t){if(number(t.get("deleted"))!=0)throw error(ScErrorCode.TASK_NOT_FOUND);}

    public void running(long uid,long id) {
        if(db.jdbc().queryForObject("select count(*) from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0 and (task_id=? or task_id in(select id from study_copilot.t_task where user_id=? and parent_id=?))",Integer.class,uid,id,uid,id)>0)
            throw error(ScErrorCode.SESSION_EXISTS);
    }

    @Transactional public Map<String,Object> delete(long id,long version) {
        long uid=db.owner();
        db.lock(uid);
        var t=db.task(uid,id);
        active(t);
        db.version(t,version);
        running(uid,id);
        db.jdbc().update("update study_copilot.t_task set deleted=1,deleted_at=now(),version=version+1,update_time=now() where user_id=? and (id=? or parent_id=?) and deleted=0",uid,id,id);
        return Map.of("undoUntil",Instant.now().plusSeconds(5),"task",db.task(uid,id));
    }

    @Transactional public Map<String,Object> restore(long id,long version) {
        long uid=db.owner();
        db.lock(uid);
        var t=db.task(uid,id);
        db.version(t,version);
        if(number(t.get("deleted"))!=1||instant(t.get("deleted_at")).plusSeconds(5).isBefore(Instant.now()))throw error(ScErrorCode.TASK_STATUS_INVALID);
        db.subject(uid,number(t.get("subject_id")));
        db.jdbc().update("update study_copilot.t_task set deleted=0,deleted_at=null,version=version+1,update_time=now() where user_id=? and (id=? or parent_id=?) and deleted_at=?",uid,id,id,t.get("deleted_at"));
        return db.task(uid,id);
    }

    @Transactional public void reorder(Map<String,Object> req) {
        long uid=db.owner();
        db.lock(uid);
        LocalDate day=date(req.get("targetDate"));
        String status=text(req,"status","ALL");
        Long parent=req.get("parentId")==null?null:number(req.get("parentId"));

        var current=db.jdbc().queryForList("select id,version from study_copilot.t_task where user_id=? and deleted=0 and target_date is not distinct from ?::date and parent_id is not distinct from ?::bigint and (?='ALL' or status=?)",uid,day,parent,status,status);
        if(!(req.get("items") instanceof List<?> items)||items.size()!=current.size())throw error(ScErrorCode.VERSION_CONFLICT);

        Set<Long> seen=new HashSet<>();
        int order=0;
        for(Object item:items) {
            if(!(item instanceof Map<?,?> m))throw error(ScErrorCode.VALIDATION_ERROR);
            long id=number(m.get("id"));
            if(!seen.add(id))throw error(ScErrorCode.VERSION_CONFLICT);
            var row=current.stream().filter(r->number(r.get("id"))==id).findFirst().orElseThrow(()->error(ScErrorCode.VERSION_CONFLICT));
            db.version(row,m.get("expectedVersion"));
            db.jdbc().update("update study_copilot.t_task set sort_order=?,version=version+1,update_time=now() where user_id=? and id=?",order++,uid,id);
        }
    }
}
