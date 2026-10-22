/*
 * Copyright (c) 2026 StudyCopilot Authors.
 * 产品：考研与长期学习智能驾驶舱 (Study Copilot)
 * 模块：认证模块服务实现
 * 文件名：AuthServiceImpl.java
 * 创建时间：2026-10-22
 * 描述：认证服务实现。
 */
package com.example.studycopilot.service.user.impl;

import com.example.studycopilot.dto.req.auth.LoginRequest;
import com.example.studycopilot.dto.req.auth.RegisterRequest;
import com.example.studycopilot.dto.req.auth.RefreshTokenRequest;
import com.example.studycopilot.dto.req.auth.LogoutRequest;
import com.example.studycopilot.dto.req.auth.ValidPassword;
import com.example.studycopilot.dto.resp.auth.LoginResponse;
import com.example.studycopilot.service.user.AuthService;
import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.security.JwtProperties;
import com.example.studycopilot.security.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 认证服务实现。
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final PasswordEncoder passwords;
    private final JwtTokenProvider tokens;
    private final JwtProperties properties;
    private final ObjectMapper json;

    // Valid BCrypt hash used for absent users, keeping password work on both login branches.
    private static final String DUMMY_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    @Override
    public LoginResponse register(RegisterRequest request, String requestId) {
        String name = normalize(request.getUsername());
        if (name.isBlank() || name.codePointCount(0, name.length()) > 50 || !ValidPassword.Validator.valid(request.getPassword())
                || requestId == null || requestId.isBlank() || requestId.length() > 100) {
            throw new BusinessException(ScErrorCode.VALIDATION_ERROR);
        }
        String hash = requestHash(name, request);
        return transactions.execute(status -> {
            // PostgreSQL coordinates even requests handled by different application instances.
            jdbc.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))", Object.class, "auth-register:" + name);
            var existing = jdbc.queryForList("select * from study_copilot.t_user where normalized_username=? for update", name);
            if (!existing.isEmpty()) return replay(existing.get(0), requestId, hash);
            var inserted = jdbc.queryForList("""
                    insert into study_copilot.t_user(username,normalized_username,password,email)
                    values (?,?,?,?) on conflict do nothing returning id
                    """, name, name, passwords.encode(request.getPassword()), request.getEmail());
            if (inserted.isEmpty()) {
                // An external writer can race the advisory-lock protocol; ON CONFLICT keeps this transaction usable.
                var winner = jdbc.queryForList("select * from study_copilot.t_user where normalized_username=? for update", name);
                if (!winner.isEmpty()) return replay(winner.get(0), requestId, hash);
                throw new BusinessException(ScErrorCode.BUSINESS_ERROR);
            }
            long userId = ((Number) inserted.get(0).get("id")).longValue();
            long subjectId = jdbc.queryForObject("""
                    insert into study_copilot.t_subject(user_id,name,color) values (?,'默认科目','#7C8CF8') returning id
                    """, Long.class, userId);
            jdbc.update("insert into study_copilot.t_user_setting(user_id,default_subject_id) values (?,?)", userId, subjectId);
            jdbc.update("""
                    insert into study_copilot.t_learning_day_rule(user_id,version,timezone,day_start_hour,effective_at)
                    select id,1,'Asia/Shanghai',0,create_time from study_copilot.t_user where id=?
                    """, userId);
            jdbc.update("insert into study_copilot.t_pet_status(user_id) values (?)", userId);
            jdbc.update("insert into study_copilot.t_onboarding_progress(user_id) values (?)", userId);
            TokenGrant grant = newSession(userId, name, 0, "/onboarding");
            jdbc.update("""
                    insert into study_copilot.t_idempotency_record
                    (user_id,operation_type,request_id,request_hash,resource_type,resource_id,response_status,result)
                    values (?,'REGISTER',?,?,'USER',?,200,cast(? as jsonb))
                    """, userId, requestId, hash, Long.toString(userId), serialize(grant));
            return response(grant);
        });
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        return transactions.execute(status -> {
            var found = jdbc.queryForList("select * from study_copilot.t_user where normalized_username=? for update", normalize(request.getUsername()));
            Map<String, Object> user = found.isEmpty() ? null : found.get(0);
            boolean matches = passwords.matches(ValidPassword.Validator.valid(request.getPassword()) ? request.getPassword() : "invalid-password",
                    user == null ? DUMMY_HASH : (String) user.get("password"));
            if (user == null || !active(user) || !ValidPassword.Validator.valid(request.getPassword()) || !matches)
                throw new BusinessException(ScErrorCode.USER_PASSWORD_ERROR);
            long userId = number(user, "id");
            return response(newSession(userId, (String) user.get("username"), number(user, "session_version"), nextRoute(userId)));
        });
    }

    @Override
    public LoginResponse refresh(RefreshTokenRequest request) {
        var claims = tokens.parseRefresh(request.getRefreshToken());
        long userId = Long.parseLong(claims.getSubject());
        long sessionId = ((Number) claims.get("refreshSessionId")).longValue();
        return transactions.execute(status -> {
            Map<String, Object> user = lockActiveUser(userId);
            var sessions = jdbc.queryForList("select * from study_copilot.t_refresh_session where id=? and user_id=? for update", sessionId, userId);
            if (sessions.isEmpty()) throw unauthorized();
            var session = sessions.get(0);
            if (session.get("revoked_at") != null || !((Timestamp) session.get("expires_at")).toInstant().isAfter(Instant.now())
                    || number(session, "session_version") != number(user, "session_version")
                    || !MessageDigest.isEqual(digest(request.getRefreshToken()).getBytes(StandardCharsets.US_ASCII),
                            ((String) session.get("token_hash")).getBytes(StandardCharsets.US_ASCII))) throw unauthorized();
            jdbc.update("update study_copilot.t_refresh_session set revoked_at=now() where id=?", sessionId);
            return response(newSession(userId, (String) user.get("username"), number(user, "session_version"), nextRoute(userId)));
        });
    }

    @Override
    public void logout(LogoutRequest request) {
        var claims = tokens.parseRefresh(request.getRefreshToken());
        long userId = Long.parseLong(claims.getSubject());
        long sessionId = ((Number) claims.get("refreshSessionId")).longValue();
        transactions.executeWithoutResult(status -> {
            lockActiveUser(userId);
            jdbc.update("""
                    update study_copilot.t_refresh_session set revoked_at=coalesce(revoked_at,now())
                    where id=? and user_id=? and token_hash=?
                    """, sessionId, userId, digest(request.getRefreshToken()));
        });
    }

    private LoginResponse replay(Map<String, Object> user, String requestId, String hash) {
        var records = jdbc.queryForList("""
                select request_hash,result::text from study_copilot.t_idempotency_record
                where user_id=? and operation_type='REGISTER' and request_id=?
                """, number(user, "id"), requestId);
        if (records.isEmpty()) throw new BusinessException(ScErrorCode.BUSINESS_ERROR);
        if (!MessageDigest.isEqual(hash.getBytes(StandardCharsets.US_ASCII),
                ((String) records.get(0).get("request_hash")).getBytes(StandardCharsets.US_ASCII)))
            throw new BusinessException(ScErrorCode.IDEMPOTENCY_CONFLICT);
        if (!active(user)) throw unauthorized();
        try { return response(json.readValue((String) records.get(0).get("result"), TokenGrant.class)); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Invalid registration metadata"); }
    }

    private TokenGrant newSession(long userId, String username, long version, String route) {
        long id = jdbc.queryForObject("select nextval('study_copilot.t_refresh_session_id_seq')", Long.class);
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        TokenGrant grant = new TokenGrant(userId, username, version, id, now.getEpochSecond(),
                now.plusSeconds(properties.getAccessTtlSeconds()).getEpochSecond(),
                now.plusSeconds(properties.getRefreshTtlSeconds()).getEpochSecond(), route);
        String refresh = response(grant).getRefreshToken();
        jdbc.update("""
                insert into study_copilot.t_refresh_session(id,user_id,token_hash,session_version,expires_at)
                values (?,?,?,?,?)
                """, id, userId, digest(refresh), version, Timestamp.from(Instant.ofEpochSecond(grant.refreshExpiry())));
        return grant;
    }

    private LoginResponse response(TokenGrant grant) {
        LoginResponse result = new LoginResponse();
        Instant issued = Instant.ofEpochSecond(grant.issuedAt());
        result.setUserId(grant.userId());
        result.setUsername(grant.username());
        result.setNextRoute(grant.nextRoute());
        result.setAccessToken(tokens.generateAccessToken(grant.userId(), grant.version(), grant.sessionId(), issued, Instant.ofEpochSecond(grant.accessExpiry())));
        result.setRefreshToken(tokens.generateRefreshToken(grant.userId(), grant.sessionId(), issued, Instant.ofEpochSecond(grant.refreshExpiry())));
        return result;
    }

    private Map<String, Object> lockActiveUser(long userId) {
        var users = jdbc.queryForList("select * from study_copilot.t_user where id=? for update", userId);
        if (users.isEmpty() || !active(users.get(0))) throw unauthorized();
        return users.get(0);
    }

    private String nextRoute(long userId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                select exists(select 1 from study_copilot.t_onboarding_progress
                where user_id=? and status in ('COMPLETED','SKIPPED'))
                """, Boolean.class, userId)) ? "/dashboard" : "/onboarding";
    }

    private String requestHash(String name, RegisterRequest request) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            // Length-safe structured encoding; the keyed digest cannot be used as an offline password verifier.
            byte[] payload = json.writeValueAsBytes(java.util.Arrays.asList("register-v1", name, request.getPassword(), request.getEmail()));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (java.security.GeneralSecurityException | com.fasterxml.jackson.core.JsonProcessingException ex) {
            throw new IllegalStateException("Unable to fingerprint registration");
        }
    }

    private String serialize(TokenGrant grant) {
        try { return json.writeValueAsString(grant); }
        catch (com.fasterxml.jackson.core.JsonProcessingException ex) { throw new IllegalStateException("Unable to persist registration metadata"); }
    }

    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException ex) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private static boolean active(Map<String, Object> user) { return number(user, "status") == 1 && number(user, "deleted") == 0; }
    private static String normalize(String name) { return name == null ? "" : name.trim().toLowerCase(Locale.ROOT); }
    private static BusinessException unauthorized() { return new BusinessException(ScErrorCode.UNAUTHORIZED); }

    /** Only replay metadata is persisted, never a raw token or password. */
    public record TokenGrant(long userId, String username, long version, long sessionId, long issuedAt,
                             long accessExpiry, long refreshExpiry, String nextRoute) {
    }
}
