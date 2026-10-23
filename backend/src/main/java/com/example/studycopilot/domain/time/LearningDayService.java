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
        if(rules.isEmpty()) throw new com.example.studycopilot.common.exception.BusinessException(
            com.example.studycopilot.common.errorcode.ScErrorCode.SETTING_NOT_FOUND);
        return rules.get(0);
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
