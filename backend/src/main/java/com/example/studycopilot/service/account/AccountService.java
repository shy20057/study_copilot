package com.example.studycopilot.service.account;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dto.req.auth.ChangePasswordRequest;
import com.example.studycopilot.dto.req.auth.ValidPassword;
import com.example.studycopilot.security.UserContext;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
@RequiredArgsConstructor
public class AccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder passwords;
    private final TransactionTemplate transactions;

    public void changePassword(ChangePasswordRequest request) {
        long userId = UserContext.requireUserId();
        if (!ValidPassword.Validator.valid(request.getNewPassword())) throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        transactions.executeWithoutResult(status -> {
            // Lock order is always user then refresh session, shared with login/rotation/logout.
            var users = jdbc.queryForList("select password,status,deleted from study_copilot.t_user where id=? for update", userId);
            if (users.isEmpty() || ((Number) users.get(0).get("status")).intValue() != 1
                    || ((Number) users.get(0).get("deleted")).intValue() != 0) throw new BusinessException(ScErrorCode.UNAUTHORIZED);
            if (!ValidPassword.Validator.valid(request.getCurrentPassword())
                    || !passwords.matches(request.getCurrentPassword(), (String) users.get(0).get("password")))
                throw new BusinessException(ScErrorCode.USER_PASSWORD_ERROR);
            jdbc.update("""
                    update study_copilot.t_user set password=?,session_version=session_version+1,update_time=now() where id=?
                    """, passwords.encode(request.getNewPassword()), userId);
            jdbc.update("update study_copilot.t_refresh_session set revoked_at=now() where user_id=? and revoked_at is null", userId);
        });
    }
}
