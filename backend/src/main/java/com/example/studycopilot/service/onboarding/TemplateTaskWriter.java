package com.example.studycopilot.service.onboarding;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dto.resp.onboarding.TemplatePreview;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.UUID;

/** Narrow import seam for Task 6: trusted owner, active versioned subject, root tasks only.
 * Caller holds t_user lock. Stable import identity must survive edits and soft deletion. */
@Component @RequiredArgsConstructor
public class TemplateTaskWriter {
    private final JdbcTemplate jdbc;
    @Transactional(propagation=Propagation.MANDATORY)
    public long create(long uid,String templateId,String templateVersion,UUID batch,TemplatePreview.Item item,String title,long subjectId,long subjectVersion,int pomodoros,int focusMinutes) {
        var subjects=jdbc.query("select name,archived,version from study_copilot.t_subject where user_id=? and id=?",(rs,n)->new Subject(rs.getString(1),rs.getInt(2),rs.getLong(3)),uid,subjectId);
        if(subjects.isEmpty())throw new BusinessException(ScErrorCode.SUBJECT_NOT_FOUND);
        var subject=subjects.get(0);
        if(subject.archived()!=0)throw new BusinessException(ScErrorCode.SUBJECT_IN_USE);
        if(subject.version()!=subjectVersion)throw new BusinessException(ScErrorCode.VERSION_CONFLICT,subject.version(),null);
        if(title==null || title.isBlank() || title.length()>200 || pomodoros<1 || pomodoros>16)throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        return jdbc.queryForObject("""
            insert into study_copilot.t_task(user_id,title,subject_id,subject_name_snapshot,planned_pomodoros,
              estimated_focus_minutes,target_date,original_planned_date,learning_date,import_batch_id,
              template_id,template_version,template_item_id,template_learning_date)
            values (?,?,?,?,?,?,?,?,?,?,?,?,?,?) returning id
            """,Long.class,uid,title,subjectId,subject.name(),pomodoros,pomodoros*focusMinutes,item.learningDate(),item.learningDate(),item.learningDate(),batch,templateId,templateVersion,item.itemId(),item.learningDate());
    }
    private record Subject(String name,int archived,long version) {}
}
