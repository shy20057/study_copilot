/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：科目模块服务实现
 * 文件名：SubjectServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：科目服务实现。
 */
package com.example.studycopilot.service.subject.impl;

import com.example.studycopilot.dto.req.subject.CreateSubjectRequest;
import com.example.studycopilot.dto.resp.subject.SubjectResponse;
import com.example.studycopilot.service.subject.SubjectService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dao.entity.subject.SubjectDO;
import com.example.studycopilot.dto.req.subject.SubjectStateRequest;
import com.example.studycopilot.security.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

import java.util.List;

/**
 * 科目服务实现。
 */
@Service
@RequiredArgsConstructor
public class SubjectServiceImpl implements SubjectService {
    private final JdbcTemplate jdbc;

    @Override
    public List<SubjectResponse> list() {
        long uid=UserContext.requireUserId();
        return jdbc.query("select * from study_copilot.t_subject where user_id=? order by archived,sort_order,id",this::row,uid)
            .stream().map(this::response).toList();
    }

    @Override @Transactional
    public SubjectResponse create(CreateSubjectRequest request) {
        long uid=lockOwner();
        String name=request.getName().trim(); unique(uid,name,null);
        try {
            long id=jdbc.queryForObject("insert into study_copilot.t_subject(user_id,name,color,sort_order) values (?,?,?,?) returning id",
                Long.class,uid,name,request.getColor(),request.getSortOrder()==null?0:request.getSortOrder());
            return response(find(uid,id));
        } catch(DuplicateKeyException ex) {throw new BusinessException(ScErrorCode.SUBJECT_DUPLICATE);}
    }

    @Override @Transactional
    public SubjectResponse update(Long id, CreateSubjectRequest request) {
        long uid=lockOwner(); var current=find(uid,id); version(current,request.getVersion());
        if(current.getArchived()!=0) throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        unique(uid,request.getName().trim(),id);
        try { jdbc.update("update study_copilot.t_subject set name=?,color=?,sort_order=?,version=version+1,update_time=clock_timestamp() where user_id=? and id=? and version=?",
            request.getName().trim(),request.getColor()==null?current.getColor():request.getColor(),request.getSortOrder()==null?current.getSortOrder():request.getSortOrder(),uid,id,current.getVersion()); }
        catch(DuplicateKeyException ex) { throw new BusinessException(ScErrorCode.SUBJECT_DUPLICATE); }
        return response(find(uid,id));
    }

    @Override @Transactional public SubjectResponse archive(Long id,SubjectStateRequest request) {
        long uid=lockOwner(); var current=find(uid,id); version(current,request.getVersion());
        if(current.getArchived()!=0) throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        if(jdbc.queryForObject("select count(*) from study_copilot.t_subject where user_id=? and archived=0",Integer.class,uid)<=1)
            throw new BusinessException(ScErrorCode.LAST_ACTIVE_SUBJECT);
        if(jdbc.queryForObject("select exists(select 1 from study_copilot.t_focus_session where user_id=? and subject_id=? and status='RUNNING' and deleted=0)",Boolean.class,uid,id))
            throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        boolean pending=jdbc.queryForObject("select exists(select 1 from study_copilot.t_task where user_id=? and subject_id=? and status='TODO' and deleted=0) or exists(select 1 from study_copilot.t_task_repeat_template where user_id=? and subject_id=? and status in ('ACTIVE','PAUSED') and deleted=0)",Boolean.class,uid,id,uid,id);
        if(pending && request.getStrategy()==null) throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        Long target=null;
        if("MIGRATE".equals(request.getStrategy())) {
            if(request.getTargetSubjectId()==null || id.equals(request.getTargetSubjectId())) throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
            var replacement=find(uid,request.getTargetSubjectId());
            if(replacement.getArchived()!=0) throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
            target=replacement.getId();
            jdbc.update("update study_copilot.t_task set subject_id=?,version=version+1,update_time=clock_timestamp() where user_id=? and subject_id=? and status='TODO' and deleted=0",target,uid,id);
            jdbc.update("update study_copilot.t_task_repeat_template set subject_id=?,version=version+1,update_time=clock_timestamp() where user_id=? and subject_id=? and status in ('ACTIVE','PAUSED') and deleted=0",target,uid,id);
        } else if("CANCEL".equals(request.getStrategy())) {
            jdbc.update("update study_copilot.t_task set status='CANCELLED',cancelled_at=clock_timestamp(),version=version+1,update_time=clock_timestamp() where user_id=? and subject_id=? and status='TODO' and deleted=0",uid,id);
            jdbc.update("update study_copilot.t_task_repeat_template set status='CANCELLED',version=version+1,update_time=clock_timestamp() where user_id=? and subject_id=? and status in ('ACTIVE','PAUSED') and deleted=0",uid,id);
        }
        jdbc.update("update study_copilot.t_subject set archived=1,version=version+1,update_time=clock_timestamp() where user_id=? and id=? and version=?",uid,id,current.getVersion());
        if(target==null) target=jdbc.queryForObject("select id from study_copilot.t_subject where user_id=? and archived=0 order by sort_order,id limit 1",Long.class,uid);
        jdbc.update("update study_copilot.t_user_setting set default_subject_id=?,version=version+1,update_time=clock_timestamp() where user_id=? and default_subject_id=?",target,uid,id);
        return response(find(uid,id));
    }
    @Override @Transactional public SubjectResponse restore(Long id,SubjectStateRequest request) {
        long uid=lockOwner(); var current=find(uid,id); version(current,request.getVersion());
        if(current.getArchived()==0) throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        unique(uid,current.getName(),id);
        try { jdbc.update("update study_copilot.t_subject set archived=0,version=version+1,update_time=clock_timestamp() where user_id=? and id=? and version=?",uid,id,current.getVersion()); }
        catch(DuplicateKeyException ex) {throw new BusinessException(ScErrorCode.SUBJECT_DUPLICATE);}
        return response(find(uid,id));
    }
    private long lockOwner() {
        long uid=UserContext.requireUserId(); jdbc.queryForObject("select id from study_copilot.t_user where id=? for update",Long.class,uid); return uid;
    }
    private SubjectDO find(long uid,long id) {
        var matches=jdbc.query("select * from study_copilot.t_subject where user_id=? and id=?",this::row,uid,id);
        if(matches.isEmpty()) throw new BusinessException(ScErrorCode.SUBJECT_NOT_FOUND); return matches.get(0);
    }
    private void version(SubjectDO subject,Long version) {
        if(version==null) throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        if(!Objects.equals(version,subject.getVersion())) throw new BusinessException(ScErrorCode.VERSION_CONFLICT,subject.getVersion(),null);
    }
    private void unique(long uid,String name,Long excluded) {
        if(name.isBlank()) throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        if(jdbc.queryForObject("select exists(select 1 from study_copilot.t_subject where user_id=? and archived=0 and lower(btrim(name))=lower(btrim(?)) and (?::bigint is null or id<>?))",Boolean.class,uid,name,excluded,excluded)) throw new BusinessException(ScErrorCode.SUBJECT_DUPLICATE);
    }
    private SubjectResponse response(SubjectDO subject) {var result=new SubjectResponse(); BeanUtils.copyProperties(subject,result); return result;}
    private SubjectDO row(java.sql.ResultSet rs,int index) throws java.sql.SQLException {
        var subject=new SubjectDO();
        subject.setId(rs.getLong("id")); subject.setUserId(rs.getLong("user_id"));
        subject.setName(rs.getString("name")); subject.setColor(rs.getString("color"));
        subject.setSortOrder(rs.getInt("sort_order")); subject.setArchived(rs.getInt("archived"));
        subject.setVersion(rs.getLong("version"));
        subject.setCreateTime(rs.getTimestamp("create_time").toInstant());
        subject.setUpdateTime(rs.getTimestamp("update_time").toInstant());
        return subject;
    }
}
