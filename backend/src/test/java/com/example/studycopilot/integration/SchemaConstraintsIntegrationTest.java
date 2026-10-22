package com.example.studycopilot.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class SchemaConstraintsIntegrationTest {
    @Autowired JdbcTemplate jdbc;
    long userId;
    long subjectId;

    @BeforeEach
    void createOwner() {
        userId = user("owner_" + UUID.randomUUID());
        subjectId = jdbc.queryForObject("""
                insert into study_copilot.t_subject(user_id,name) values (?, 'Math') returning id
                """, Long.class, userId);
        jdbc.update("""
                insert into study_copilot.t_learning_day_rule(user_id,version,timezone,day_start_hour,effective_at)
                values (?,1,'Asia/Shanghai',0,'2026-01-01T00:00:00Z')
                """, userId);
    }

    @Test
    void containsEveryPhaseATable() {
        assertThat(jdbc.queryForList("""
                select table_name from information_schema.tables where table_schema='study_copilot'
                """, String.class)).contains("t_refresh_session", "t_learning_day_rule", "t_onboarding_progress",
                "t_template_import_batch", "t_task_repeat_template", "t_task_schedule_log", "t_focus_session",
                "t_focus_slice", "t_idempotency_record", "t_reward_ledger", "t_result_presentation",
                "t_account_deletion_job");
    }

    @Test
    void usernamesAreGloballyUniqueAfterTrimmingAndCaseNormalization() {
        user("Alice");
        assertThatThrownBy(() -> user(" ALICE ")).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void focusAndBreakShareOneRunningSlotEvenWhenTheirTimesDoNotOverlap() {
        session("FOCUS", "TIMER", "RUNNING", "2026-10-22T08:00:00Z", "2026-10-22T08:25:00Z", 0);
        assertThatThrownBy(() -> session("SHORT", "TIMER", "RUNNING",
                "2026-10-22T10:00:00Z", "2026-10-22T10:05:00Z", 0))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uk_focus_user_running");
    }

    @Test
    void halfOpenRangesAllowAdjacencyButRejectTimerManualOverlap() {
        session("FOCUS", "TIMER", "COMPLETED", "2026-10-22T08:00:00Z", "2026-10-22T08:25:00Z", 1500);
        session("FOCUS", "MANUAL", "COMPLETED", "2026-10-22T08:25:00Z", "2026-10-22T08:30:00Z", 300);
        assertThatThrownBy(() -> session("FOCUS", "MANUAL", "COMPLETED",
                "2026-10-22T08:24:59Z", "2026-10-22T08:26:00Z", 61))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_focus_effective_range");
    }

    @Test
    void runningFocusReservesItsPlannedInterval() {
        session("FOCUS", "TIMER", "RUNNING", "2026-10-22T08:00:00Z", "2026-10-22T08:25:00Z", 0);
        assertThatThrownBy(() -> session("FOCUS", "MANUAL", "COMPLETED",
                "2026-10-22T08:05:00Z", "2026-10-22T08:06:00Z", 60))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ex_focus_effective_range");
    }

    @Test
    void deletedManualIntervalsCanBeReused() {
        long id = session("FOCUS", "MANUAL", "COMPLETED", "2026-10-22T08:00:00Z", "2026-10-22T08:01:00Z", 60);
        jdbc.update("update study_copilot.t_focus_session set deleted=1 where id=?", id);
        session("FOCUS", "MANUAL", "COMPLETED", "2026-10-22T08:00:00Z", "2026-10-22T08:01:00Z", 60);
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_focus_session where user_id=? and deleted=0",
                Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void idempotencyKeysAreScopedToUserAndOperation() {
        String sql = "insert into study_copilot.t_idempotency_record(user_id,operation_type,request_id,request_hash) values (?,?,?,?)";
        jdbc.update(sql, userId, "START", "one-request", "hash");
        jdbc.update(sql, userId, "COMPLETE", "one-request", "hash");
        jdbc.update(sql, user("other_" + UUID.randomUUID()), "START", "one-request", "hash");
        assertThatThrownBy(() -> jdbc.update(sql, userId, "START", "one-request", "hash"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void repeatedTaskIsUniquePerUserTemplateAndLearningDate() {
        Long template = jdbc.queryForObject("""
                insert into study_copilot.t_task_repeat_template(user_id,title,subject_id,repeat_rule,start_date)
                values (?, 'Daily math', ?, 'DAILY', '2026-10-01') returning id
                """, Long.class, userId, subjectId);
        String sql = """
                insert into study_copilot.t_task(user_id,title,subject_id,repeat_template_id,learning_date)
                values (?, 'Math', ?, ?, '2026-10-22')
                """;
        jdbc.update(sql, userId, subjectId, template);
        assertThatThrownBy(() -> jdbc.update(sql, userId, subjectId, template))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void sessionCannotReferenceAnotherUsersSubject() {
        subjectId = jdbc.queryForObject("""
                insert into study_copilot.t_subject(user_id,name) values (?, 'Private') returning id
                """, Long.class, user("other_" + UUID.randomUUID()));
        assertThatThrownBy(() -> session("FOCUS", "TIMER", "RUNNING",
                "2026-10-22T08:00:00Z", "2026-10-22T08:25:00Z", 0))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rewardCannotBeAwardedTwiceForTheSameSessionAndDay() {
        long id = session("FOCUS", "TIMER", "COMPLETED", "2026-10-22T08:00:00Z", "2026-10-22T08:25:00Z", 1500);
        String sql = """
                insert into study_copilot.t_reward_ledger(user_id,session_id,learning_date,rule_version,
                  reward_factor,base_exp,awarded_exp,daily_cap)
                values (?,?,'2026-10-22',1,1,250,250,9600)
                """;
        jdbc.update(sql, userId, id);
        assertThatThrownBy(() -> jdbc.update(sql, userId, id)).isInstanceOf(DataIntegrityViolationException.class);
    }

    private long user(String username) {
        return jdbc.queryForObject("""
                insert into study_copilot.t_user(username,normalized_username,password)
                values (?,lower(btrim(?)), 'test-hash') returning id
                """, Long.class, username, username);
    }

    private long session(String mode, String source, String status, String start, String end, int seconds) {
        return jdbc.queryForObject("""
                insert into study_copilot.t_focus_session(user_id,subject_id,mode,source,status,request_id,
                  planned_seconds,actual_seconds,valid_seconds,start_at,scheduled_end_at,end_at,rule_version)
                values (?,?,?,?,?,?,1500,?,?,?::timestamptz,?::timestamptz,
                  case when ?='RUNNING' then null else ?::timestamptz end,1) returning id
                """, Long.class, userId, subjectId, mode, source, status, UUID.randomUUID().toString(),
                seconds, seconds, start, end, status, end);
    }
}
