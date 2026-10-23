package com.example.studycopilot.domain.time;
import java.time.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
@Service @RequiredArgsConstructor
public class LearningDayService {
    private final JdbcTemplate jdbc;
    /** Internal user-scoped calculation; callers supply an authenticated or job-owned user ID. */
    public LearningDayContext resolve(long userId, Instant instant) {
        var rules=jdbc.query("""
            select timezone,day_start_hour,version from study_copilot.t_learning_day_rule
            where user_id=? and effective_at<=? order by effective_at desc limit 1
            """,(rs,n)->resolve(instant,ZoneId.of(rs.getString(1)),rs.getInt(2),rs.getLong(3)),userId,java.sql.Timestamp.from(instant));
        // Manual records may predate account creation: apply the first known rule to that history.
        if(rules.isEmpty()) rules=jdbc.query("""
            select timezone,day_start_hour,version from study_copilot.t_learning_day_rule
            where user_id=? order by effective_at limit 1
            """,(rs,n)->resolve(instant,ZoneId.of(rs.getString(1)),rs.getInt(2),rs.getLong(3)),userId);
        if(rules.isEmpty()) throw new com.example.studycopilot.common.exception.BusinessException(
            com.example.studycopilot.common.errorcode.ScErrorCode.SETTING_NOT_FOUND);
        var context=rules.get(0);
        var interval=jdbc.queryForList("""
            select effective_at,(select min(effective_at) from study_copilot.t_learning_day_rule n
              where n.user_id=r.user_id and n.effective_at>r.effective_at) next_at
            from study_copilot.t_learning_day_rule r where user_id=? and version=?
            """,userId,context.ruleVersion());
        Instant start=context.start(),end=context.end();
        if(!interval.isEmpty()) {
            var row=interval.get(0);
            Instant effective=((java.sql.Timestamp)row.get("effective_at")).toInstant();
            if(context.ruleVersion()>1 && !instant.isBefore(effective) && effective.isAfter(start)) start=effective;
            if(row.get("next_at") instanceof java.sql.Timestamp next && next.toInstant().isBefore(end)) end=next.toInstant();
        }
        return new LearningDayContext(context.learningDate(),context.zone(),context.dayStartHour(),context.ruleVersion(),start,end);
    }
    public LearningDayContext resolve(Instant instant, ZoneId zone, int dayStartHour, long ruleVersion) {
        if(dayStartHour<0 || dayStartHour>6 || ruleVersion<1) throw new IllegalArgumentException("Invalid learning-day rule");
        LocalDate date=instant.atZone(zone).toLocalDate();
        Instant start=boundary(date,zone,dayStartHour);
        // Compare instants, not local hours: a repeated clock hour still belongs to the new day.
        if(instant.isBefore(start)) { date=date.minusDays(1); start=boundary(date,zone,dayStartHour); }
        return new LearningDayContext(date,zone,dayStartHour,ruleVersion,start,boundary(date.plusDays(1),zone,dayStartHour));
    }
    private Instant boundary(LocalDate date,ZoneId zone,int hour) {
        // Java resolves gaps forward and uses the earlier offset for overlaps.
        return date.atTime(hour,0).atZone(zone).toInstant();
    }
}
