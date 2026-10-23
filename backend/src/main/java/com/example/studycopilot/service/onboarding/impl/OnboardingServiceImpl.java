package com.example.studycopilot.service.onboarding.impl;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.domain.time.LearningDayService;
import com.example.studycopilot.dto.req.onboarding.*;
import com.example.studycopilot.dto.resp.onboarding.*;
import com.example.studycopilot.security.UserContext;
import com.example.studycopilot.service.onboarding.*;
import com.example.studycopilot.support.idempotency.IdempotencyService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class OnboardingServiceImpl implements OnboardingService {
    private final JdbcTemplate jdbc;
    private final ObjectMapper json;
    private final LearningDayService days;
    private final TemplateCatalog catalog;
    private final TemplateTaskWriter tasks;
    private final IdempotencyService idempotency;

    @Override public OnboardingProgress progress(){return readProgress(UserContext.requireUserId());}
    @Override @Transactional public OnboardingProgress save(SaveOnboardingProgressRequest request) {
        long uid=lockOwner(); var progress=readProgress(uid); version(progress,request.version());
        var confirmed=new LinkedHashMap<>(progress.confirmedData()); confirmed.putAll(request.confirmedData());
        String data=idempotency.serialize(confirmed);
        if(data.length()>32768)throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        jdbc.update("update study_copilot.t_onboarding_progress set current_step=?,confirmed_data=?::jsonb,status='IN_PROGRESS',completed_at=null,version=version+1,update_time=clock_timestamp() where user_id=?",request.currentStep(),data,uid);
        jdbc.update("update study_copilot.t_user_setting set onboarding_done=0 where user_id=?",uid);
        return readProgress(uid);
    }
    @Override public List<TemplateCatalog.Definition> templates(){UserContext.requireUserId();return catalog.list();}
    @Override public TemplatePreview preview(String templateId,Set<String> itemIds,LocalDate learningDate) {
        return buildPreview(UserContext.requireUserId(),templateId,itemIds,learningDate);
    }
    @Override @Transactional public ImportResult complete(CompleteOnboardingRequest request,String requestId) {
        // Replay precedes current-version/preview checks so a lost response remains recoverable.
        return idempotency.execute("ONBOARDING_COMPLETE",requestId,request,ImportResult.class,()->importSelected(request,requestId));
    }
    private ImportResult importSelected(CompleteOnboardingRequest request,String requestId) {
        long uid=UserContext.requireUserId(); var progress=readProgress(uid); version(progress,request.version());
        boolean hasTemplate=request.templateId()!=null;
        if((request.skip()&&hasTemplate) || (!hasTemplate&&(!request.itemIds().isEmpty() || !request.overrides().isEmpty() || request.previewVersion()!=null || request.learningDate()!=null))
            || !request.itemIds().containsAll(request.overrides().keySet()))throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        var ids=new ArrayList<Long>(); var skipped=new ArrayList<String>(); UUID batch=null;
        TemplatePreview preview=null;
        if(hasTemplate) {
            preview=buildPreview(uid,request.templateId(),null,request.learningDate());
            if(!Objects.equals(request.previewVersion(),preview.previewVersion()))throw new BusinessException(ScErrorCode.VERSION_CONFLICT);
            var known=new HashSet<String>();preview.items().forEach(i->known.add(i.itemId()));
            if(!known.containsAll(request.itemIds()))throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
            batch=UUID.randomUUID();
            jdbc.update("insert into study_copilot.t_template_import_batch(user_id,request_id,import_batch_id,template_id,template_version,preview_version,request_hash) values (?,?,?,?,?,?,?)",uid,requestId,batch,preview.templateId(),preview.templateVersion(),preview.previewVersion(),idempotency.fingerprint(request));
            int focus=jdbc.queryForObject("select focus_minutes from study_copilot.t_user_setting where user_id=?",Integer.class,uid);
            for(var item:preview.items()) if(request.itemIds().contains(item.itemId())) {
                if(item.alreadyImported()){skipped.add(item.itemId());continue;}
                var edit=request.overrides().get(item.itemId());
                String title=edit!=null&&edit.title()!=null?edit.title():item.title();
                long subject=edit!=null&&edit.subjectId()!=null?edit.subjectId():item.subjectId();
                if(edit!=null&&edit.subjectId()!=null&&edit.subjectVersion()==null)throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
                long subjectVersion=edit!=null&&edit.subjectVersion()!=null?edit.subjectVersion():item.subjectVersion();
                int pomodoros=edit!=null&&edit.plannedPomodoros()!=null?edit.plannedPomodoros():item.plannedPomodoros();
                ids.add(tasks.create(uid,preview.templateId(),preview.templateVersion(),batch,item,title,subject,subjectVersion,pomodoros,focus));
            }
        }
        String status=request.skip()?"SKIPPED":"COMPLETED";
        jdbc.update("update study_copilot.t_onboarding_progress set status=?,current_step=?,version=version+1,completed_at=clock_timestamp(),update_time=clock_timestamp() where user_id=?",status,request.skip()?progress.currentStep():"DONE",uid);
        jdbc.update("update study_copilot.t_user_setting set onboarding_done=1 where user_id=?",uid);
        var result=new ImportResult(status,progress.version()+1,batch,List.copyOf(ids),List.copyOf(skipped));
        if(batch!=null)jdbc.update("update study_copilot.t_template_import_batch set import_result=?::jsonb where user_id=? and import_batch_id=?",idempotency.serialize(Map.of("result",result,"stages",preview.stages())),uid,batch);
        return result;
    }
    private TemplatePreview buildPreview(long uid,String templateId,Set<String> selected,LocalDate requestedDate) {
        var definition=catalog.find(templateId);
        var context=days.resolve(uid,Instant.now()); LocalDate today=context.learningDate();
        LocalDate base=requestedDate==null?today:requestedDate;
        // A preview is anchored in the current seven-learning-day window.
        if(base.isBefore(today)||base.isAfter(today.plusDays(6)))throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        var setting=jdbc.queryForMap("select version,focus_minutes,daily_avail_min,exam_date,default_subject_id from study_copilot.t_user_setting where user_id=?",uid);
        LocalDate target=setting.get("exam_date")==null?null:((java.sql.Date)setting.get("exam_date")).toLocalDate();
        if(target!=null&&target.isBefore(base.plusDays(definition.minimumDays()-1)))
            throw new BusinessException(ScErrorCode.TEMPLATE_RANGE_INVALID);
        var subjects=jdbc.query("select id,name,version from study_copilot.t_subject where user_id=? and archived=0 order by sort_order,id",(rs,n)->new Subject(rs.getLong(1),rs.getString(2),rs.getLong(3)),uid);
        if(subjects.isEmpty())throw new BusinessException(ScErrorCode.SUBJECT_NOT_FOUND);
        var defaultSubject=subjects.stream().filter(s->Objects.equals(s.id(),setting.get("default_subject_id"))).findFirst().orElse(subjects.get(0));
        Set<String> known=new HashSet<>();catalog.items().forEach(i->known.add(i.id()));
        if(selected!=null&&!known.containsAll(selected))throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        var imported=new HashSet<>(jdbc.query("select template_item_id||'@'||template_learning_date from study_copilot.t_task where user_id=? and template_id=?",(rs,n)->rs.getString(1),uid,templateId));
        int focus=((Number)setting.get("focus_minutes")).intValue();
        int budget=setting.get("daily_avail_min")==null?Integer.MAX_VALUE:((Number)setting.get("daily_avail_min")).intValue()*70/100;
        var items=new ArrayList<TemplatePreview.Item>(); var dailyMinutes=new HashMap<LocalDate,Integer>();int defaultToday=0;
        for(var item:catalog.items()) {
            LocalDate date=base.plusDays(item.learningDayOffset());
            if(date.isAfter(today.plusDays(6))||(target!=null&&date.isAfter(target)))continue;
            var subject=subjects.stream().filter(s->s.name().equalsIgnoreCase(item.subjectAlias())).findFirst().orElse(defaultSubject);
            boolean exists=imported.contains(item.id()+"@"+date); int minutes=item.plannedPomodoros()*focus;
            boolean chosen=!exists&&(selected==null?(!date.equals(today)||defaultToday<3)&&dailyMinutes.getOrDefault(date,0)+minutes<=budget:selected.contains(item.id()));
            if(chosen){dailyMinutes.merge(date,minutes,Integer::sum);if(date.equals(today))defaultToday++;}
            items.add(new TemplatePreview.Item(item.id(),item.title(),item.subjectAlias(),subject.id(),subject.version(),date,item.plannedPomodoros(),minutes,exists,chosen));
        }
        if(selected!=null&&!items.stream().map(TemplatePreview.Item::itemId).collect(java.util.stream.Collectors.toSet()).containsAll(selected))
            throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        var stages=new ArrayList<TemplatePreview.Stage>();
        LocalDate end=target==null?base.plusDays(definition.minimumDays()-1):target;
        long span=java.time.temporal.ChronoUnit.DAYS.between(base,end)+1;
        for(int i=0;i<definition.stages().size();i++) {
            LocalDate start=base.plusDays(span*i/definition.stages().size());
            LocalDate stop=base.plusDays(span*(i+1)/definition.stages().size()-1);
            stages.add(new TemplatePreview.Stage(definition.stages().get(i),start,stop,i==0));
        }
        // Full resource content is fingerprinted, so accidental edits without version bumps still invalidate previews.
        var versionData=new HashMap<String,Object>();versionData.put("owner",uid);versionData.put("definition",definition);versionData.put("catalogVersion",catalog.version());versionData.put("items",catalog.items());versionData.put("settings",setting);versionData.put("subjects",subjects);versionData.put("base",base);versionData.put("currentLearningDate",today);versionData.put("ruleVersion",context.ruleVersion());
        String previewVersion=idempotency.fingerprint(versionData);
        return new TemplatePreview(templateId,catalog.version(),previewVersion,definition.name(),definition.description(),base,List.copyOf(stages),List.copyOf(items),items.size(),(int)items.stream().filter(i->i.learningDate().equals(today)).count(),items.stream().mapToInt(TemplatePreview.Item::estimatedFocusMinutes).sum(),true);
    }
    private long lockOwner(){long uid=UserContext.requireUserId();jdbc.queryForObject("select id from study_copilot.t_user where id=? for update",Long.class,uid);return uid;}
    private OnboardingProgress readProgress(long uid) {
        return jdbc.queryForObject("select * from study_copilot.t_onboarding_progress where user_id=?",(rs,n)->{
            try {return new OnboardingProgress(rs.getString("current_step"),json.readValue(rs.getString("confirmed_data"),new TypeReference<Map<String,Object>>(){}),rs.getString("status"),rs.getLong("version"),rs.getTimestamp("completed_at")==null?null:rs.getTimestamp("completed_at").toInstant());}
            catch(java.io.IOException ex){throw new IllegalStateException("Invalid onboarding data",ex);}
        },uid);
    }
    private void version(OnboardingProgress progress,Long version){if(version==null)throw new BusinessException(ScErrorCode.VALIDATION_ERROR);if(progress.version()!=version)throw new BusinessException(ScErrorCode.VERSION_CONFLICT,progress.version(),null);}
    private record Subject(Long id,String name,long version) {}
}
