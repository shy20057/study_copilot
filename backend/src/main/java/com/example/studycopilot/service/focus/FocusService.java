package com.example.studycopilot.service.focus;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.domain.time.TimeSliceService;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.support.idempotency.IdempotencyService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.example.studycopilot.support.db.OwnerStore.*;

@Service @RequiredArgsConstructor
public class FocusService {
    private final OwnerStore db;
    private final LearningDayService days;
    private final TimeSliceService slices;
    private final IdempotencyService keys;
    private final TransactionTemplate tx;

    @Transactional public Map<String,Object> start(Map<String,Object> b,String key,boolean rest) {
        return keys.execute(rest?"BREAK_START":"FOCUS_START",key,b,Map.class,()->startInside(db.owner(),b,key,rest));
    }

    private Map<String,Object> startInside(long u,Map<String,Object> b,String key,boolean rest) {
        db.lock(u);
        compensate(u);
        var running=db.jdbc().queryForList("select * from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0",u);
        if(!running.isEmpty())throw new BusinessException(ScErrorCode.SESSION_EXISTS,null,running.get(0));

        Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var rule=days.resolve(u,now);
        var setting=db.one("select * from study_copilot.t_user_setting where user_id=?",u);

        String mode=rest?text(b,"mode","SHORT"):"FOCUS";
        if(!Set.of("FOCUS","SHORT","LONG").contains(mode)||rest&&mode.equals("FOCUS"))throw error(ScErrorCode.VALIDATION_ERROR);

        int minutes=integer(b,"minutes",rest?(mode.equals("LONG")?15:5):((Number)setting.get("focus_minutes")).intValue());
        if(minutes<1||minutes>(rest?30:120))throw error(ScErrorCode.VALIDATION_ERROR);

        Long sid=rest?null:(b.get("subjectId")==null?number(setting.get("default_subject_id")):number(b.get("subjectId")));
        Long task=b.get("taskId")==null?null:number(b.get("taskId"));
        Map<String,Object> sub=rest?Map.of():db.subject(u,sid);
        String title=null;
        if(task!=null) {
            var t=db.task(u,task);
            if(rest||number(t.get("deleted"))!=0||!"TODO".equals(t.get("status"))||number(t.get("subject_id"))!=sid)
                throw error(ScErrorCode.TASK_STATUS_INVALID);
            title=t.get("title").toString();
        }
        if(!rest)overlap(u,now,now.plusSeconds(minutes*60L),null);

        var config=Map.of("timezone",rule.zone().getId(),"dayStartHour",rule.dayStartHour(),"minutes",minutes);
        Long id=db.jdbc().queryForObject("insert into study_copilot.t_focus_session(user_id,mode,request_id,subject_id,subject_name_snapshot,subject_color_snapshot,task_id,task_title_snapshot,config_snapshot,planned_seconds,start_at,scheduled_end_at,rule_version) values (?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?) returning id",
            Long.class,u,mode,key,sid,sub.get("name"),sub.get("color"),task,title,db.encode(config),minutes*60,
            java.sql.Timestamp.from(now),java.sql.Timestamp.from(now.plusSeconds(minutes*60L)),rule.ruleVersion());
        return view(session(u,id));
    }

    @Transactional public Map<String,Object> current() {
        long u=db.owner();
        db.lock(u);
        compensate(u);
        var rows=db.jdbc().queryForList("select * from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0",u);
        var out=new LinkedHashMap<String,Object>();
        out.put("serverNow",Instant.now());
        out.put("session",rows.isEmpty()?null:view(rows.get(0)));
        out.put("pendingResults",db.jdbc().queryForList("select session_id,result,version from study_copilot.t_result_presentation where user_id=? and acknowledged_at is null order by id desc limit 5",u));
        return out;
    }

    public Map<String,Object> session(long u,long id){return db.one("select * from study_copilot.t_focus_session where user_id=? and id=? and deleted=0",u,id);}

    private Map<String,Object> view(Map<String,Object> row) {
        var v=new LinkedHashMap<>(row);
        v.remove("effective_range");
        v.put("serverNow",Instant.now());
        return v;
    }

    @Transactional public Map<String,Object> finish(long id,Map<String,Object> b,String key,boolean early) {
        var request=new TreeMap<String,Object>(b);
        request.put("sessionId",id);
        return keys.execute(early?"FOCUS_END_EARLY":"FOCUS_COMPLETE",key,request,Map.class,()->{
            long u=db.owner();
            db.lock(u);
            var s=session(u,id);
            if(!"RUNNING".equals(s.get("status")))return result(u,id);
            Instant now=Instant.now().truncatedTo(ChronoUnit.SECONDS),due=instant(s.get("scheduled_end_at"));
            if(!early&&now.isBefore(due))throw error(ScErrorCode.SESSION_NOT_DUE);
            return settle(u,s,now.isBefore(due)?now:due,early&&now.isBefore(due),Boolean.TRUE.equals(b.get("claimPartialReward")));
        });
    }

    private Map<String,Object> result(long u,long id) {
        var found=db.jdbc().queryForList("select result::text from study_copilot.t_result_presentation where user_id=? and session_id=?",u,id);
        return found.isEmpty()?view(session(u,id)):db.decode(found.get(0).get("result"));
    }

    public void compensate(long u) {
        for(var s:db.jdbc().queryForList("select * from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0 and scheduled_end_at<=now() for update",u))
            settle(u,s,instant(s.get("scheduled_end_at")),false,false);
    }

    private Map<String,Object> settle(long u,Map<String,Object> s,Instant end,boolean early,boolean claim) {
        long id=number(s.get("id"));
        Instant start=instant(s.get("start_at"));
        if(!end.isAfter(start))end=start.plusNanos(1);

        int seconds=Math.max(0,(int)Duration.between(start,end).getSeconds());
        boolean focus="FOCUS".equals(s.get("mode"));
        int valid=focus&&seconds>=60?seconds:0;
        double factor=focus?(!early?1:(claim&&seconds>=600?.5:0)):0;

        var config=db.decode(s.get("config_snapshot"));
        ZoneId zone=ZoneId.of(text(config,"timezone","Asia/Shanghai"));
        int hour=integer(config,"dayStartHour",0);
        long rule=number(s.get("rule_version"));

        long before=number(db.one("select total_exp from study_copilot.t_pet_status where user_id=?",u).get("total_exp"));
        int awarded=0;
        boolean capped=false;

        db.jdbc().update("update study_copilot.t_focus_session set status=?,end_at=?,end_reason=?,actual_seconds=?,valid_seconds=?,settlement_version=settlement_version+1,settled_at=now(),version=version+1,update_time=now() where user_id=? and id=?",
            ""+(early?"ENDED_EARLY":"COMPLETED"),java.sql.Timestamp.from(end),early?"USER_ENDED":"DUE",seconds,valid,u,id);

        if(valid>0)for(var slice:slices.split(start,end,zone,hour,rule)) {
            int part=(int)Duration.between(slice.start(),slice.end()).getSeconds();
            if(part<=0)continue;
            db.jdbc().update("insert into study_copilot.t_focus_slice(user_id,session_id,learning_date,rule_version,slice_start_at,slice_end_at,valid_seconds,subject_id) values (?,?,?,?,?,?,?,?)",
                u,id,slice.learningDate(),rule,java.sql.Timestamp.from(slice.start()),java.sql.Timestamp.from(slice.end()),part,s.get("subject_id"));
            int base=part*10/60,desired=(int)Math.floor(base*factor);
            int used=db.jdbc().queryForObject("select coalesce(sum(awarded_exp),0) from study_copilot.t_reward_ledger where user_id=? and learning_date=?",Integer.class,u,slice.learningDate());
            int gain=Math.min(desired,Math.max(0,9600-used));
            awarded+=gain;
            capped|=gain<desired;
            db.jdbc().update("insert into study_copilot.t_reward_ledger(user_id,session_id,learning_date,rule_version,reward_factor,base_exp,awarded_exp,capped) values (?,?,?,?,?,?,?,?)",
                u,id,slice.learningDate(),rule,factor,base,gain,gain<desired);
        }
        if(awarded>0)db.jdbc().update("update study_copilot.t_pet_status set total_exp=total_exp+?,version=version+1,update_time=now() where user_id=?",awarded,u);

        var result=new LinkedHashMap<String,Object>();
        result.put("sessionId",id);
        result.put("status",early?"ENDED_EARLY":"COMPLETED");
        result.put("actualSeconds",seconds);
        result.put("validSeconds",valid);
        result.put("awardedExp",awarded);
        result.put("capped",capped);
        result.put("oldLevel",level(before));
        result.put("newLevel",level(before+awarded));
        result.put("totalExp",before+awarded);

        db.jdbc().update("insert into study_copilot.t_result_presentation(user_id,session_id,result) values (?,?,?::jsonb)",u,id,db.encode(result));
        return result;
    }

    public static final long[] THRESHOLDS={0,4000,10000,20000,36000,58000,90000,135000,195000,280000};

    public static int level(long exp){int l=1;while(l<10&&exp>=THRESHOLDS[l])l++;return l;}

    public void overlap(long u,Instant start,Instant end,Long except) {
        var conflict=db.jdbc().queryForList("select id,start_at,coalesce(end_at,scheduled_end_at) end_at,subject_name_snapshot from study_copilot.t_focus_session where user_id=? and deleted=0 and effective_range && tstzrange(?,?,'[)') and (?::bigint is null or id<>?) limit 1",
            u,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),except,except);
        if(!conflict.isEmpty())throw new BusinessException(ScErrorCode.TIME_OVERLAP,null,conflict.get(0));
    }

    @Transactional public Map<String,Object> endBreakAndStart(Map<String,Object> b,String key) {
        return keys.execute("BREAK_TO_FOCUS",key,b,Map.class,()->{
            long u=db.owner();
            db.lock(u);
            var running=db.jdbc().queryForList("select * from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0",u);
            if(!running.isEmpty()) {
                var s=running.get(0);
                if("FOCUS".equals(s.get("mode")))throw error(ScErrorCode.SESSION_EXISTS);
                settle(u,s,Instant.now().truncatedTo(ChronoUnit.SECONDS),true,false);
            }
            return startInside(u,b,key,false);
        });
    }

    @Transactional public Map<String,Object> presentation(long id,String device,boolean ack) {
        long u=db.owner();
        db.lock(u);
        if(device==null||!device.matches("[A-Za-z0-9._-]{1,100}"))throw error(ScErrorCode.VALIDATION_ERROR);
        if(ack)db.jdbc().update("update study_copilot.t_result_presentation set acknowledged_at=now(),version=version+1 where user_id=? and session_id=? and claimed_by_device=?",u,id,device);
        else db.jdbc().update("update study_copilot.t_result_presentation set claimed_by_device=?,claimed_at=now(),version=version+1 where user_id=? and session_id=? and claimed_at is null",device,u,id);
        var p=db.one("select * from study_copilot.t_result_presentation where user_id=? and session_id=?",u,id);
        var out=new LinkedHashMap<>(p);
        out.put("canPresent",device.equals(p.get("claimed_by_device"))&&p.get("acknowledged_at")==null);
        return out;
    }

    // Bounded sweep of overdue sessions. Each owner row is locked with SKIP LOCKED
    // so concurrent instances never settle the same session twice.
    @Scheduled(fixedDelay=15000) public void settleOverdue() {
        var owners=db.jdbc().queryForList("select distinct user_id from study_copilot.t_focus_session where status='RUNNING' and deleted=0 and scheduled_end_at<=now() limit 50",Long.class);
        for(long u:owners)try {
            tx.executeWithoutResult(status->{
                if(!db.jdbc().queryForList("select id from study_copilot.t_user where id=? and status=1 and deleted=0 for update skip locked",u).isEmpty())
                    compensate(u);
            });
        }catch(RuntimeException ignored) {
            // durable RUNNING row is retried on the next bounded scan
        }
    }
}
