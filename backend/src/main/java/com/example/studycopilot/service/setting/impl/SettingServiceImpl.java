/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：设置模块服务实现
 * 文件名：SettingServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：用户设置服务实现。
 */
package com.example.studycopilot.service.setting.impl;

import com.example.studycopilot.dto.req.setting.UpdateSettingRequest;
import com.example.studycopilot.dto.resp.setting.SettingResponse;
import com.example.studycopilot.service.setting.SettingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dao.entity.setting.UserSettingDO;
import com.example.studycopilot.dao.mapper.setting.UserSettingMapper;
import com.example.studycopilot.security.UserContext;
import com.example.studycopilot.domain.time.LearningDayService;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

/**
 * 用户设置服务实现。
 */
@Service
@RequiredArgsConstructor
public class SettingServiceImpl implements SettingService {
    private final JdbcTemplate jdbc;
    private final UserSettingMapper settings;
    private final LearningDayService days;

    @Override @Transactional(readOnly=true, isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public SettingResponse get() {
        long uid=UserContext.requireUserId();
        var response=new SettingResponse(); BeanUtils.copyProperties(find(uid),response);
        response.setRuleVersion(jdbc.queryForObject("select max(version) from study_copilot.t_learning_day_rule where user_id=?",Long.class,uid));
        var active=days.resolve(uid,Instant.now());
        response.setActiveTimezone(active.zone().getId());
        response.setActiveDayStartHour(active.dayStartHour());
        response.setActiveRuleVersion(active.ruleVersion());
        var pending=jdbc.queryForList("select version,timezone,day_start_hour,effective_at from study_copilot.t_learning_day_rule where user_id=? and effective_at>now() order by effective_at limit 1",uid);
        response.setPendingLearningRule(pending.isEmpty()?null:pending.get(0));
        return response;
    }

    @Override @Transactional
    public void update(UpdateSettingRequest request) {
        long uid=UserContext.requireUserId();
        // All cross-resource writers lock the owner before settings, subjects, tasks or sessions.
        if(jdbc.queryForList("select id from study_copilot.t_user where id=? and status=1 and deleted=0 for update",uid).isEmpty())
            throw new BusinessException(ScErrorCode.TOKEN_REVOKED);
        var current=find(uid);
        if(request.getVersion()==null) throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        if(!Objects.equals(current.getVersion(),request.getVersion())) throw new BusinessException(ScErrorCode.VERSION_CONFLICT,current.getVersion(),null);
        String zone=request.getTimezone()==null?current.getTimezone():request.getTimezone();
        try { ZoneId.of(zone); } catch(DateTimeException ex) { throw new BusinessException(ScErrorCode.VALIDATION_ERROR); }
        int hour=request.getDayStartHour()==null?current.getDayStartHour():request.getDayStartHour();
        boolean ruleChanged=!zone.equals(current.getTimezone()) || hour!=current.getDayStartHour();
        if(ruleChanged && jdbc.queryForObject("select exists(select 1 from study_copilot.t_focus_session where user_id=? and status='RUNNING' and deleted=0)",Boolean.class,uid))
            throw new BusinessException(ScErrorCode.RULE_CHANGE_BLOCKED);
        if(request.getDefaultSubjectId()!=null && !jdbc.queryForObject("select exists(select 1 from study_copilot.t_subject where user_id=? and id=? and archived=0)",Boolean.class,uid,request.getDefaultSubjectId()))
            throw new BusinessException(ScErrorCode.SUBJECT_NOT_FOUND);
        var updates=new LinkedHashMap<String,Object>();
        put(updates,"goal_name",request.getGoalName());
        if(request.isExamDateProvided()) {
            if(request.getExamDate()!=null && request.getExamDate().isBefore(Instant.now().atZone(ZoneId.of(zone)).toLocalDate()))
                throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
            updates.put("exam_date",request.getExamDate());
        }
        put(updates,"timezone",request.getTimezone()); put(updates,"day_start_hour",request.getDayStartHour());
        if(request.isWeeklyGoalMinProvided()) updates.put("weekly_goal_min",request.getWeeklyGoalMin());
        put(updates,"focus_minutes",request.getFocusMinutes()); put(updates,"theme",request.getTheme());
        put(updates,"ai_consent",request.getAiConsent());
        if(request.isDailyAvailMinProvided()) updates.put("daily_avail_min",request.getDailyAvailMin());
        put(updates,"muted",request.getMuted()); put(updates,"notifications_enabled",request.getNotificationsEnabled());
        put(updates,"notification_permission",request.getNotificationPermission()); put(updates,"reduce_motion",request.getReduceMotion());
        put(updates,"reduce_transparency",request.getReduceTransparency()); put(updates,"default_subject_id",request.getDefaultSubjectId());
        put(updates,"partial_rewards_enabled",request.getPartialRewardsEnabled());
        put(updates,"sound_enabled",request.getSoundEnabled()); put(updates,"animations_enabled",request.getAnimationsEnabled());
        var args=new ArrayList<Object>(updates.values()); args.add(uid); args.add(current.getVersion());
        String assignments=String.join(",",updates.keySet().stream().map(k->k+"=?").toList());
        jdbc.update("update study_copilot.t_user_setting set "+(assignments.isEmpty()?"":assignments+",")+"version=version+1,update_time=clock_timestamp() where user_id=? and version=?",args.toArray());
        if(ruleChanged) {
            Instant ruleNow=Instant.now();
            var active=days.resolve(uid,ruleNow);
            // Pending rules have never governed a running or historical session; replace them atomically.
            jdbc.update("delete from study_copilot.t_learning_day_rule where user_id=? and effective_at>?",uid,java.sql.Timestamp.from(ruleNow));
            if(!zone.equals(active.zone().getId()) || hour!=active.dayStartHour()) {
                Long version=jdbc.queryForObject("update study_copilot.t_user set learning_rule_sequence=learning_rule_sequence+1 where id=? returning learning_rule_sequence",Long.class,uid);
                jdbc.update("insert into study_copilot.t_learning_day_rule(user_id,version,timezone,day_start_hour,effective_at) values (?,?,?,?,?)",
                    uid,version,zone,hour,java.sql.Timestamp.from(active.end()));
            }
            jdbc.update("update study_copilot.t_task_repeat_template set next_check_at=least(next_check_at,?) where user_id=? and deleted=0 and status='ACTIVE'",
                java.sql.Timestamp.from(active.end()),uid);
        }
    }
    private UserSettingDO find(long uid) {
        var result=settings.selectOne(Wrappers.<UserSettingDO>lambdaQuery().eq(UserSettingDO::getUserId,uid));
        if(result==null) throw new BusinessException(ScErrorCode.SETTING_NOT_FOUND);
        return result;
    }
    private void put(Map<String,Object> target,String key,Object value) {if(value!=null) target.put(key,value);}
}
