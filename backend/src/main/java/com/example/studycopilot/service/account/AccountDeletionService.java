package com.example.studycopilot.service.account;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dto.req.auth.DeleteAccountRequest;
import com.example.studycopilot.dto.req.auth.ValidPassword;
import com.example.studycopilot.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 在同一事务中封禁账号、停止会话、撤销令牌并登记清理作业。 */
@Service
@RequiredArgsConstructor
public class AccountDeletionService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;

    @Transactional
    public DeletionReceipt request(DeleteAccountRequest request) {
        long userId = UserContext.requireUserId();
        var users = jdbc.queryForList("""
                select password,status,deleted from study_copilot.t_user where id=? for update
                """, userId);
        if (users.isEmpty() || ((Number) users.get(0).get("status")).intValue() != 1
                || ((Number) users.get(0).get("deleted")).intValue() != 0) {
            throw new BusinessException(ScErrorCode.TOKEN_REVOKED);
        }
        if (!request.isConfirmDeletion()) {
            throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        }
        if (!ValidPassword.Validator.valid(request.getCurrentPassword())
                || !passwords.matches(request.getCurrentPassword(), (String) users.get(0).get("password"))) {
            throw new BusinessException(ScErrorCode.USER_PASSWORD_ERROR);
        }

        // Deletion stops both focus and break sessions; it never creates rewards or a result presentation.
        // An owner lock serializes this with both request-driven and scheduled settlement.
        jdbc.update("""
                update study_copilot.t_focus_session
                set status='CANCELLED',end_at=greatest(clock_timestamp(),start_at+interval '1 microsecond'),
                    actual_seconds=greatest(0,floor(extract(epoch from clock_timestamp()-start_at)))::integer,
                    valid_seconds=0,end_reason='ACCOUNT_DELETION',settled_at=now(),
                    settlement_version=settlement_version+1,version=version+1,update_time=now()
                where user_id=? and status='RUNNING' and deleted=0
                """, userId);
        jdbc.update("""
                update study_copilot.t_user
                set status=2,deleted=1,deleted_at=now(),session_version=session_version+1,update_time=now()
                where id=?
                """, userId);
        jdbc.update("""
                update study_copilot.t_refresh_session set revoked_at=now()
                where user_id=? and revoked_at is null
                """, userId);
        Long jobId = jdbc.queryForObject("""
                insert into study_copilot.t_account_deletion_job(user_id) values (?) returning id
                """, Long.class, userId);
        return new DeletionReceipt(jobId, "PENDING");
    }

    public record DeletionReceipt(long jobId, String status) { }
}
