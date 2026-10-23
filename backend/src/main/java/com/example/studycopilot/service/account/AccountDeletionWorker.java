package com.example.studycopilot.service.account;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.service.attachment.AvatarObjectStore;
import com.example.studycopilot.security.LoginAttemptGuard;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

/** 持久化注销作业：先锁用户再锁作业，外部清理失败可重复执行。 */
@Component
@RequiredArgsConstructor
public class AccountDeletionWorker {
    private static final Logger log = LoggerFactory.getLogger(AccountDeletionWorker.class);
    // Dependents precede referenced rows, including the settings -> default subject reference.
    private static final List<String> OWNED_TABLES = List.of(
            "t_result_presentation", "t_reward_ledger", "t_focus_slice", "t_focus_session",
            "t_task_schedule_log", "t_task", "t_task_repeat_template", "t_template_import_batch",
            "t_onboarding_progress", "t_user_setting", "t_learning_day_rule", "t_subject", "t_pet_status",
            "t_attachment", "t_refresh_session", "t_idempotency_record", "t_pomodoro", "t_daily_stat",
            "t_daily_journal", "t_chat_message", "t_mistake", "t_knowledge_point", "t_milestone",
            "t_reminder_setting", "t_notification");

    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final AvatarObjectStore objects;
    private final UserCacheCleanup caches;
    private final MeterRegistry metrics;
    private final LoginAttemptGuard loginGuard;

    @Scheduled(fixedDelayString = "${study-copilot.account-deletion.scan-delay-ms:30000}")
    public void cleanPendingAccounts() {
        var owners = jdbc.queryForList("""
                select user_id from study_copilot.t_account_deletion_job
                where status in ('PENDING','RETRY') and next_attempt_at<=now()
                order by next_attempt_at,id limit 20
                """, Long.class);
        for (long userId : owners) {
            try {
                Boolean completed = transactions.execute(status -> cleanOwner(userId));
                if (Boolean.TRUE.equals(completed)) {
                    metrics.counter("study_copilot.account.cleanup", "outcome", "completed").increment();
                }
            } catch (RuntimeException ex) {
                String code = ex instanceof BusinessException business
                        && ScErrorCode.STORAGE_UNAVAILABLE.code().equals(business.getErrorCode())
                        ? "STORAGE_UNAVAILABLE" : "CLEANUP_UNAVAILABLE";
                metrics.counter("study_copilot.account.cleanup", "outcome", "retry").increment();
                log.warn("Account cleanup deferred code={}", code);
                // The failed transaction rolled RUNNING back; record backoff in a separate transaction.
                try {
                    transactions.executeWithoutResult(status -> retry(userId, code));
                } catch (RuntimeException retryFailure) {
                    log.warn("Account cleanup retry persistence unavailable");
                }
            }
        }
    }

    private boolean cleanOwner(long userId) {
        if (jdbc.queryForList("""
                select id from study_copilot.t_user where id=? and deleted=1 and status=2
                for update skip locked
                """, userId).isEmpty()) {
            return false;
        }
        var jobs = jdbc.queryForList("""
                select id from study_copilot.t_account_deletion_job
                where user_id=? and status in ('PENDING','RETRY') and next_attempt_at<=now() for update
                """, userId);
        if (jobs.isEmpty()) {
            return false;
        }
        jdbc.update("""
                update study_copilot.t_account_deletion_job set status='RUNNING',version=version+1,update_time=now()
                where user_id=?
                """, userId);

        // Both operations are idempotent; never commit personal-row removal before external cleanup succeeds.
        objects.removeOwner(userId);
        caches.removeOwner(userId);
        String username = jdbc.queryForObject("select username from study_copilot.t_user where id=?",String.class,userId);
        loginGuard.forget(username);
        for (String table : OWNED_TABLES) {
            // Only names in the fixed allowlist above are interpolated; owner IDs remain bound parameters.
            jdbc.update("delete from study_copilot." + table + " where user_id=?", userId);
        }
        String tombstone = "deleted-" + UUID.randomUUID();
        jdbc.update("""
                update study_copilot.t_user set username=?,normalized_username=?,password='!',email=null,
                    nickname=null,avatar=null,last_seen_at=null,status=0,update_time=now() where id=? and deleted=1
                """, tombstone, tombstone, userId);
        jdbc.update("""
                update study_copilot.t_account_deletion_job
                set status='COMPLETED',completed_at=now(),last_error_code=null,
                    final_result='{"personalDataRemoved":true,"auditTombstoneRetained":true}'::jsonb,
                    version=version+1,update_time=now() where user_id=?
                """, userId);
        return true;
    }

    private void retry(long userId, String code) {
        // Do not overwrite a job another worker completed after our failed transaction rolled back.
        if (jdbc.queryForList("""
                select id from study_copilot.t_user where id=? and deleted=1 and status=2 for update
                """, userId).isEmpty()) {
            return;
        }
        jdbc.update("""
                update study_copilot.t_account_deletion_job
                set status='RETRY',retry_count=retry_count+1,last_error_code=?,
                    next_attempt_at=now()+make_interval(secs => least(3600,power(2,least(retry_count,7))*30)::integer),
                    version=version+1,update_time=now()
                where user_id=? and status in ('PENDING','RETRY')
                """, code, userId);
    }
}
