package com.example.studycopilot.service.account;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.dto.req.auth.ChangePasswordRequest;
import com.example.studycopilot.dto.req.auth.UpdateProfileRequest;
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

    public Profile profile() {
        long userId = UserContext.requireUserId();
        var profiles = jdbc.query("""
                select username,nickname,avatar,profile_version from study_copilot.t_user
                where id=? and status=1 and deleted=0
                """, (rs, row) -> new Profile(rs.getString("username"), rs.getString("nickname"),
                        rs.getString("avatar"), rs.getLong("profile_version")), userId);
        if (profiles.isEmpty()) {
            throw new BusinessException(ScErrorCode.TOKEN_REVOKED);
        }
        return profiles.get(0);
    }

    public Profile updateProfile(UpdateProfileRequest request) {
        long userId = UserContext.requireUserId();
        return transactions.execute(status -> {
            var users = jdbc.queryForList("""
                    select profile_version from study_copilot.t_user
                    where id=? and status=1 and deleted=0 for update
                    """, userId);
            if (users.isEmpty()) {
                throw new BusinessException(ScErrorCode.TOKEN_REVOKED);
            }
            long version = ((Number) users.get(0).get("profile_version")).longValue();
            if (request.getExpectedVersion() == null) {
                throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
            }
            if (version != request.getExpectedVersion()) {
                throw new BusinessException(ScErrorCode.VERSION_CONFLICT, version, null);
            }
            String nickname = request.getNickname() == null ? "" : request.getNickname().strip();
            if (nickname.isEmpty() || nickname.length() > 50) {
                throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
            }
            jdbc.update("""
                    update study_copilot.t_user
                    set nickname=?,profile_version=profile_version+1,data_version=data_version+1,update_time=now()
                    where id=?
                    """, nickname, userId);
            return profile();
        });
    }

    public record Profile(String username, String nickname, String avatarUrl, long version) { }

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
