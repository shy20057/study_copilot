package com.example.studycopilot.service.focus;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.support.idempotency.IdempotencyService;
import com.example.studycopilot.domain.time.*;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import static com.example.studycopilot.support.db.OwnerStore.*;
@Service @RequiredArgsConstructor
public class FocusRecordService {
 private final OwnerStore db;private final FocusService focus;private final LearningDayService days;private final TimeSliceService slicer;private final IdempotencyService keys;
 public Map<String,Object> list(LocalDate day,Long subject,int page,int size){
  long u=db.owner();page=Math.max(1,page);size=Math.max(1,Math.min(100,size));
  String predicate=" from study_copilot.t_focus_slice f join study_copilot.t_focus_session s on s.id=f.session_id and s.user_id=f.user_id where f.user_id=? and s.deleted=0 and (?::date is null or f.learning_date=?) and (?::bigint is null or f.subject_id=?)";
  long count=db.jdbc.queryForObject("select count(*)"+predicate,Long.class,u,day,day,subject,subject);
  var items=db.jdbc.queryForList("select s.id,s.source,s.status,s.start_at session_start_at,s.end_at session_end_at,s.subject_id,s.subject_name_snapshot,s.subject_color_snapshot,s.task_id,s.task_title_snapshot,s.version,f.learning_date,f.slice_start_at,f.slice_end_at,f.valid_seconds,coalesce((select sum(awarded_exp) from study_copilot.t_reward_ledger r where r.session_id=s.id),0) awarded_exp"+predicate+" order by f.slice_end_at desc,s.id desc limit ? offset ?",u,day,day,subject,subject,size,(page-1)*size);
  return Map.of("items",items,"total",count,"page",page,"pageSize",size);
 }
 @Transactional public Map<String,Object> create(Map<String,Object> body,String key){return keys.execute("MANUAL_CREATE",key,body,Map.class,()->write(null,body,key));}
 @Transactional public Map<String,Object> update(long id,Map<String,Object> body){return write(id,body,null);}
 private Map<String,Object> write(Long id,Map<String,Object> b,String key){
  long u=db.owner();db.lock(u);Instant start=instant(b.get("startAt")).truncatedTo(ChronoUnit.SECONDS),end=instant(b.get("endAt")).truncatedTo(ChronoUnit.SECONDS);long seconds=Duration.between(start,end).getSeconds();
  if(seconds<60||seconds>14400||end.isAfter(Instant.now()))throw error(ScErrorCode.VALIDATION_ERROR);
  long sid=number(b.get("subjectId"));var sub=db.subject(u,sid);Long tid=b.get("taskId")==null?null:number(b.get("taskId"));String title=null;if(tid!=null){var t=db.task(u,tid);if(number(t.get("deleted"))!=0||number(t.get("subject_id"))!=sid)throw error(ScErrorCode.TASK_STATUS_INVALID);title=t.get("title").toString();}
  if(id!=null){var old=focus.session(u,id);manual(old);db.version(old,b.get("expectedVersion"));}
  focus.overlap(u,start,end,id);var rule=days.resolve(u,start);String config=db.encode(Map.of("timezone",rule.zone().getId(),"dayStartHour",rule.dayStartHour()));
  if(id==null)id=db.jdbc.queryForObject("insert into study_copilot.t_focus_session(user_id,source,status,request_id,subject_id,subject_name_snapshot,subject_color_snapshot,task_id,task_title_snapshot,config_snapshot,planned_seconds,actual_seconds,valid_seconds,start_at,scheduled_end_at,end_at,rule_version,settled_at,end_reason) values (?,'MANUAL','COMPLETED',?,?,?,?,?,?,?::jsonb,?,?,?,?,?,?,?,now(),'MANUAL') returning id",Long.class,u,key,sid,sub.get("name"),sub.get("color"),tid,title,config,seconds,seconds,seconds,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),java.sql.Timestamp.from(end),rule.ruleVersion());
  else db.jdbc.update("update study_copilot.t_focus_session set subject_id=?,subject_name_snapshot=?,subject_color_snapshot=?,task_id=?,task_title_snapshot=?,config_snapshot=?::jsonb,planned_seconds=?,actual_seconds=?,valid_seconds=?,start_at=?,scheduled_end_at=?,end_at=?,rule_version=?,version=version+1,update_time=now() where user_id=? and id=?",sid,sub.get("name"),sub.get("color"),tid,title,config,seconds,seconds,seconds,java.sql.Timestamp.from(start),java.sql.Timestamp.from(end),java.sql.Timestamp.from(end),rule.ruleVersion(),u,id);
  db.jdbc.update("delete from study_copilot.t_focus_slice where user_id=? and session_id=?",u,id);
  for(var part:slicer.split(start,end,rule.zone(),rule.dayStartHour(),rule.ruleVersion()))if(part.seconds()>0)db.jdbc.update("insert into study_copilot.t_focus_slice(user_id,session_id,learning_date,rule_version,slice_start_at,slice_end_at,valid_seconds,subject_id) values (?,?,?,?,?,?,?,?)",u,id,part.learningDate(),part.ruleVersion(),java.sql.Timestamp.from(part.start()),java.sql.Timestamp.from(part.end()),part.seconds(),sid);
  return focus.session(u,id);
 }
 private void manual(Map<String,Object> row){if(!"MANUAL".equals(row.get("source")))throw error(ScErrorCode.FORBIDDEN);}
 @Transactional public Map<String,Object> remove(long id,long version){long u=db.owner();db.lock(u);var row=focus.session(u,id);manual(row);db.version(row,version);db.jdbc.update("update study_copilot.t_focus_session set deleted=1,deleted_at=now(),version=version+1,update_time=now() where user_id=? and id=?",u,id);return Map.of("undoUntil",Instant.now().plusSeconds(5));}
 @Transactional public Map<String,Object> restore(long id,long version){long u=db.owner();db.lock(u);var row=db.one("select * from study_copilot.t_focus_session where user_id=? and id=?",u,id);manual(row);db.version(row,version);if(number(row.get("deleted"))!=1||instant(row.get("deleted_at")).plusSeconds(5).isBefore(Instant.now()))throw error(ScErrorCode.POMODORO_STATUS_INVALID);focus.overlap(u,instant(row.get("start_at")),instant(row.get("end_at")),id);db.jdbc.update("update study_copilot.t_focus_session set deleted=0,deleted_at=null,version=version+1,update_time=now() where user_id=? and id=?",u,id);return focus.session(u,id);}
}
