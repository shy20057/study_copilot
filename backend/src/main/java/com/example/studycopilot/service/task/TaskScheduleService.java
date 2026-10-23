package com.example.studycopilot.service.task;
import com.example.studycopilot.support.db.OwnerStore;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
import static com.example.studycopilot.support.db.OwnerStore.*;
@Service @RequiredArgsConstructor
public class TaskScheduleService {
 private final OwnerStore db;private final TaskWorkflowService tasks;private final LearningDayService days;
 public List<Map<String,Object>> templates(){return db.jdbc.queryForList("select * from study_copilot.t_task_repeat_template where user_id=? and deleted=0 order by id",db.owner());}
 @Transactional public Map<String,Object> save(Long id,Map<String,Object> b){
  long u=db.owner();db.lock(u);String title=text(b,"title","").trim(),rule=text(b,"repeatRule","DAILY");int pom=integer(b,"plannedPomodoros",1),priority=integer(b,"priority",1);
  if(title.isEmpty()||title.length()>200||!Set.of("DAILY","WEEKDAYS","WEEKLY").contains(rule)||pom<1||pom>16||priority<1||priority>3)throw error(ScErrorCode.VALIDATION_ERROR);
  long sid=number(b.get("subjectId"));db.subject(u,sid);LocalDate start=b.get("startDate")==null?days.resolve(u,Instant.now()).learningDate():date(b.get("startDate")),end=date(b.get("endDate"));if(end!=null&&end.isBefore(start))throw error(ScErrorCode.VALIDATION_ERROR);
  var selected=new TreeSet<Integer>();if(b.get("weekdays") instanceof List<?> list)for(var d:list){int v=Math.toIntExact(number(d));if(v<1||v>7)throw error(ScErrorCode.VALIDATION_ERROR);selected.add(v);}if(rule.equals("WEEKLY")&&selected.isEmpty())throw error(ScErrorCode.VALIDATION_ERROR);
  String array="{"+String.join(",",selected.stream().map(Object::toString).toList())+"}",status=text(b,"status","ACTIVE");if(!Set.of("ACTIVE","PAUSED","CANCELLED").contains(status))throw error(ScErrorCode.VALIDATION_ERROR);
  if(id==null)id=db.jdbc.queryForObject("insert into study_copilot.t_task_repeat_template(user_id,title,subject_id,planned_pomodoros,priority,repeat_rule,weekdays,start_date,end_date,status) values (?,?,?,?,?,?,?::smallint[],?,?,?) returning id",Long.class,u,title,sid,pom,priority,rule,array,start,end,status);
  else {var row=db.one("select * from study_copilot.t_task_repeat_template where user_id=? and id=? and deleted=0",u,id);db.version(row,b.get("expectedVersion"));db.jdbc.update("update study_copilot.t_task_repeat_template set title=?,subject_id=?,planned_pomodoros=?,priority=?,repeat_rule=?,weekdays=?::smallint[],start_date=?,end_date=?,status=?,version=version+1,update_time=now() where user_id=? and id=?",title,sid,pom,priority,rule,array,start,end,status,u,id);}
  ensureInstance(u,days.resolve(u,Instant.now()).learningDate());return db.one("select * from study_copilot.t_task_repeat_template where user_id=? and id=?",u,id);
 }
 @Transactional public void ensureToday(){long u=db.owner();db.lock(u);ensureInstance(u,days.resolve(u,Instant.now()).learningDate());}
 public void ensureInstance(long uid,LocalDate day){
  var rows=db.jdbc.queryForList("select r.*,s.name subject_name from study_copilot.t_task_repeat_template r join study_copilot.t_subject s on s.id=r.subject_id and s.user_id=r.user_id and s.archived=0 where r.user_id=? and r.deleted=0 and r.status='ACTIVE' and start_date<=? and (end_date is null or end_date>=?) and (repeat_rule='DAILY' or repeat_rule='WEEKDAYS' and extract(isodow from ?::date)<=5 or repeat_rule='WEEKLY' and extract(isodow from ?::date)::smallint=any(weekdays))",uid,day,day,day,day);
  for(var r:rows)db.jdbc.update("insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot,planned_pomodoros,priority,target_date,learning_date,original_planned_date,repeat_template_id,estimated_focus_minutes) values (?,?,?,?,?,?,?,?,?,?,?*(select focus_minutes from study_copilot.t_user_setting where user_id=?)) on conflict(user_id,repeat_template_id,learning_date) do nothing",uid,r.get("title"),r.get("subject_id"),r.get("subject_name"),r.get("planned_pomodoros"),r.get("priority"),day,day,day,r.get("id"),r.get("planned_pomodoros"),uid);
 }
 @Transactional public Map<String,Object> carry(Map<String,Object> b){
  long u=db.owner();db.lock(u);LocalDate today=days.resolve(u,Instant.now()).learningDate();UUID batch=UUID.randomUUID();var ids=new HashSet<Long>();
  if(!(b.get("items") instanceof List<?> list)||list.isEmpty())throw error(ScErrorCode.VALIDATION_ERROR);
  for(var raw:list){if(!(raw instanceof Map<?,?> item))throw error(ScErrorCode.VALIDATION_ERROR);long id=number(item.get("id"));if(!ids.add(id))throw error(ScErrorCode.VALIDATION_ERROR);var t=db.task(u,id);db.version(t,item.get("expectedVersion"));if(!"TODO".equals(t.get("status"))||number(t.get("deleted"))!=0||date(t.get("target_date"))==null||!date(t.get("target_date")).isBefore(today))throw error(ScErrorCode.TASK_STATUS_INVALID);tasks.running(u,id);
   String status="TODO";Object repeat=t.get("repeat_template_id");
   if(repeat!=null&&db.jdbc.queryForObject("select count(*) from study_copilot.t_task where user_id=? and repeat_template_id=? and learning_date=?",Integer.class,u,repeat,today)>0){String choice=text(b,"conflictChoice","");if(choice.equals("KEEP_TODAY_CANCEL_OLD"))status="CANCELLED";else if(!choice.equals("KEEP_BOTH"))throw error(ScErrorCode.VERSION_CONFLICT);}
   var snapshot=new LinkedHashMap<String,Object>();snapshot.put("status",t.get("status"));snapshot.put("targetDate",Objects.toString(t.get("target_date"),null));snapshot.put("learningDate",Objects.toString(t.get("learning_date"),null));snapshot.put("repeatTemplateId",repeat);
   db.jdbc.update("insert into study_copilot.t_task_schedule_log(user_id,task_id,operation_type,batch_id,previous_date,new_date,previous_version,resulting_version,previous_state,undo_expires_at) values (?,?,'CARRY_OVER',?,?,?,?,?,?::jsonb,now()+interval '5 seconds')",u,id,batch,t.get("target_date"),today,t.get("version"),number(t.get("version"))+1,db.encode(snapshot));
   db.jdbc.update("update study_copilot.t_task set target_date=?,learning_date=?,repeat_template_id=?,status=?,version=version+1,update_time=now() where user_id=? and id=?",today,status.equals("CANCELLED")?t.get("learning_date"):today,repeat==null?null:(status.equals("CANCELLED")?repeat:null),status,u,id);
  }return Map.of("batchId",batch,"undoUntil",Instant.now().plusSeconds(5),"changed",ids.size());
 }
 @Transactional public void undo(UUID batch){
  long u=db.owner();db.lock(u);var logs=db.jdbc.queryForList("select * from study_copilot.t_task_schedule_log where user_id=? and batch_id=? and operation_type='CARRY_OVER' order by id",u,batch);if(logs.isEmpty())throw error(ScErrorCode.NOT_FOUND);
  for(var l:logs){if(l.get("undone_at")!=null||instant(l.get("undo_expires_at")).isBefore(Instant.now()))throw error(ScErrorCode.TASK_STATUS_INVALID);var t=db.task(u,number(l.get("task_id")));db.version(t,l.get("resulting_version"));tasks.running(u,number(t.get("id")));var old=db.decode(l.get("previous_state"));db.jdbc.update("update study_copilot.t_task set target_date=?::date,learning_date=?::date,repeat_template_id=?,status=?,version=version+1,update_time=now() where user_id=? and id=?",old.get("targetDate"),old.get("learningDate"),old.get("repeatTemplateId"),old.get("status"),u,t.get("id"));db.jdbc.update("update study_copilot.t_task_schedule_log set undone_at=now() where user_id=? and id=?",u,l.get("id"));}
 }
}
