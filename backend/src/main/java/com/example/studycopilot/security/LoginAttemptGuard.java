package com.example.studycopilot.security;

import com.example.studycopilot.common.errorcode.ScErrorCode;
import com.example.studycopilot.common.exception.BusinessException;
import com.example.studycopilot.common.exception.RateLimitException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

/** 登录尝试在独立事务中预留，失败回滚不能消除限流事实。Redis 失效不放开边界。 */
@Component
@RequiredArgsConstructor
public class LoginAttemptGuard {
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final IdempotencyProperties properties;

    public String begin(String username) {
        String account = fingerprint("ACCOUNT", normalize(username));
        String source = "internal";
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes request)
            source = request.getRequest().getRemoteAddr(); // Never trust a client-supplied forwarding header.
        String address = fingerprint("SOURCE", source);
        independent().executeWithoutResult(status -> {
            reserve("SOURCE", address, 300);
            reserve("ACCOUNT", account, 5);
        });
        return account;
    }

    public void succeeded(String account) {
        try {
            independent().executeWithoutResult(status -> jdbc.update(
                    "delete from study_copilot.t_auth_throttle where scope='ACCOUNT' and identity_hash=?", account));
        } catch (org.springframework.dao.DataAccessException ex) {
            // A committed login must still return its tokens if only the best-effort counter reset fails.
            org.slf4j.LoggerFactory.getLogger(LoginAttemptGuard.class).warn("Login throttle reset deferred");
        }
    }

    public void forget(String username) {
        jdbc.update("delete from study_copilot.t_auth_throttle where scope='ACCOUNT' and identity_hash=?",
                fingerprint("ACCOUNT", normalize(username)));
    }

    private void reserve(String scope, String identity, int threshold) {
        jdbc.update("insert into study_copilot.t_auth_throttle(scope,identity_hash) values (?,?) on conflict do nothing", scope,identity);
        var row = jdbc.queryForMap("select * from study_copilot.t_auth_throttle where scope=? and identity_hash=? for update", scope,identity);
        Instant now = Instant.now();
        if (row.get("blocked_until") instanceof Timestamp blocked && blocked.toInstant().isAfter(now))
            throw new RateLimitException((java.time.Duration.between(now,blocked.toInstant()).toMillis()+999)/1000);
        boolean fresh = ((Timestamp) row.get("window_start")).toInstant().plusSeconds(900).isBefore(now);
        int attempts = fresh ? 1 : Math.min(10000, ((Number) row.get("failures")).intValue()+1);
        long wait = attempts < threshold ? 0 : Math.min(scope.equals("SOURCE")?60L:900L, 1L << Math.min(10,attempts-threshold));
        jdbc.update("""
                update study_copilot.t_auth_throttle set failures=?,window_start=case when ? then ? else window_start end,
                  blocked_until=?,update_time=? where scope=? and identity_hash=?
                """,attempts,fresh,Timestamp.from(now),wait==0?null:Timestamp.from(now.plusSeconds(wait)),Timestamp.from(now),scope,identity);
    }

    private TransactionTemplate independent() {
        var transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return transaction;
    }

    private String fingerprint(String scope, String value) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal((scope+":"+value).getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Authentication throttle unavailable");
        }
    }

    private String normalize(String username) { return username==null?"":username.trim().toLowerCase(Locale.ROOT); }

    @Scheduled(fixedDelayString="${study-copilot.auth.throttle-cleanup-delay-ms:3600000}")
    public void expire() { jdbc.update("delete from study_copilot.t_auth_throttle where update_time<now()-interval '24 hours'"); }
}
