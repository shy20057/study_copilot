package com.example.studycopilot.api;

import com.example.studycopilot.security.JwtProperties;
import com.example.studycopilot.security.UserContext;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real HTTP/security/PostgreSQL tests; each fixture owns its database rows. */
@SpringBootTest
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@ActiveProfiles("test")
class AuthApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProperties jwt;
    @Autowired PasswordEncoder encoder;
    private static final String PASSWORD = "Study-pass_123";

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        var ids = jdbc.queryForList("select id from study_copilot.t_user where normalized_username like 'auth_t3_%'", Long.class);
        for (Long id : ids) {
            jdbc.update("update study_copilot.t_user_setting set default_subject_id=null where user_id=?", id);
            for (String table : List.of("t_refresh_session", "t_idempotency_record", "t_onboarding_progress",
                    "t_pet_status", "t_learning_day_rule", "t_subject", "t_user_setting")) {
                jdbc.update("delete from study_copilot." + table + " where user_id=?", id);
            }
            jdbc.update("delete from study_copilot.t_user where id=?", id);
        }
    }

    @Test
    void normalizedRegistrationCreatesEveryDefaultAtomicallyAndReturnsTokens() throws Exception {
        String name = name();
        JsonNode response = registered("  " + name.toUpperCase(java.util.Locale.ROOT) + "  ", PASSWORD, key());
        long id = response.path("userId").asLong();
        assertThat(id).isPositive();
        assertThat(response.path("username").asText()).isEqualTo(name);
        assertThat(response.path("nextRoute").asText()).isEqualTo("/onboarding");
        var user = jdbc.queryForMap("select * from study_copilot.t_user where id=?", id);
        assertThat(user).containsEntry("normalized_username", name).containsEntry("session_version", 0L);
        assertThat(encoder.matches(PASSWORD, (String) user.get("password"))).isTrue();
        assertThat(user.get("password")).isNotEqualTo(PASSWORD);
        var settings = jdbc.queryForMap("select * from study_copilot.t_user_setting where user_id=?", id);
        assertThat(settings).containsEntry("timezone", "Asia/Shanghai").containsEntry("day_start_hour", 0)
                .containsEntry("theme", "SYSTEM").containsEntry("focus_minutes", 25)
                .containsEntry("weekly_goal_min", null).containsEntry("muted", false)
                .containsEntry("notifications_enabled", false).containsEntry("notification_permission", "DEFAULT")
                .containsEntry("reduce_motion", false).containsEntry("reduce_transparency", false);
        assertThat(jdbc.queryForMap("select version,timezone,day_start_hour from study_copilot.t_learning_day_rule where user_id=?", id))
                .containsEntry("version", 1L).containsEntry("timezone", "Asia/Shanghai").containsEntry("day_start_hour", 0);
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_subject where user_id=? and id=? and archived=0", Integer.class,
                id, settings.get("default_subject_id"))).isEqualTo(1);
        assertThat(jdbc.queryForMap("select pet_type,total_exp,version from study_copilot.t_pet_status where user_id=?", id))
                .containsEntry("pet_type", 1).containsEntry("total_exp", 0L).containsEntry("version", 0L);
        assertThat(jdbc.queryForMap("select current_step,status,version from study_copilot.t_onboarding_progress where user_id=?", id))
                .containsEntry("current_step", "WELCOME").containsEntry("status", "IN_PROGRESS").containsEntry("version", 0L);
        Claims access = claims(response.path("accessToken").asText());
        assertThat(access.getIssuer()).isEqualTo("study-copilot");
        assertThat(access.getSubject()).isEqualTo(Long.toString(id));
        assertThat(access.get("type")).isEqualTo("access");
        assertThat(((Number) access.get("sessionVersion")).longValue()).isZero();
        assertThat(access.getIssuedAt()).isBefore(access.getExpiration());
        assertThat(claims(response.path("refreshToken").asText()).get("type")).isEqualTo("refresh");
    }

    @Test
    void registrationUsesLocaleRootAndValidatesTheNormalizedNameLength() throws Exception {
        String name = "auth_t3_" + "i".repeat(42);
        java.util.Locale previous = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"));
            JsonNode result = registered("  " + name.toUpperCase(java.util.Locale.ROOT) + "  ", PASSWORD, key());
            assertThat(result.path("username").asText()).isEqualTo(name);
        } finally { java.util.Locale.setDefault(previous); }
    }

    @Test
    void missingAndBlankLoginCredentialsUseTheSameGenericFailure() throws Exception {
        for (Object input : List.of(Map.of(), Map.of("username", " ", "password", ""))) {
            postJson("/api/auth/login", input).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SC_002003"))
                    .andExpect(jsonPath("$.message").value("账号或密码不正确"));
        }
    }

    @Test
    void databaseFailureDuringLastInitializationWriteRollsBackEveryRegistrationRow() throws Exception {
        String name = name();
        // A request-scoped database trigger causes a real failure after all initialization writes.
        jdbc.execute("""
                create or replace function study_copilot.auth_t3_reject_registration() returns trigger
                language plpgsql as $$ begin
                  if new.operation_type='REGISTER' and new.request_id='auth-t3-force-rollback' then
                    raise exception 'fixture registration failure';
                  end if; return new;
                end $$
                """);
        jdbc.execute("""
                create trigger auth_t3_reject_registration before insert on study_copilot.t_idempotency_record
                for each row execute function study_copilot.auth_t3_reject_registration()
                """);
        try {
            register(name, PASSWORD, "auth-t3-force-rollback").andExpect(status().isInternalServerError());
            assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_user where normalized_username=?", Integer.class, name)).isZero();
            // A clean retry must initialize exactly once after the transaction rolled back.
            registered(name, PASSWORD, key());
        } finally {
            jdbc.execute("drop trigger auth_t3_reject_registration on study_copilot.t_idempotency_record");
            jdbc.execute("drop function study_copilot.auth_t3_reject_registration()");
        }
    }

    @Test
    void durableRefreshExpiryAndDigestAreAuthoritativeEvenForValidSignedTokens() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        long id = first.path("userId").asLong();
        jdbc.update("update study_copilot.t_refresh_session set create_time=now()-interval '2 hours',expires_at=now()-interval '1 hour' where user_id=?", id);
        postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isUnauthorized());
        JsonNode second = registered(name(), PASSWORD, key());
        jdbc.update("update study_copilot.t_refresh_session set token_hash=? where user_id=?", "0".repeat(64), second.path("userId").asLong());
        postJson("/api/auth/refresh", Map.of("refreshToken", second.path("refreshToken").asText())).andExpect(status().isUnauthorized());
        assertThat(count("t_refresh_session", "user_id", second.path("userId").asLong())).isEqualTo(1);
    }

    @Test
    void passwordChangeValidationPreservesEveryOldSession() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        mvc.perform(put("/api/account/password").header("Authorization", "Bearer " + first.path("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "学".repeat(25)))))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.fieldErrors[0].field").value("newPassword"));
        protectedRequest(first.path("accessToken").asText()).andExpect(status().isOk());
        postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isOk());
    }

    @Test
    void registrationReplayIsStableWithoutPersistingRawTokensAndConflictDoesNotWrite() throws Exception {
        String name = name(), requestId = key();
        JsonNode first = registered(name, PASSWORD, requestId);
        assertThat(registered(" " + name.toUpperCase(java.util.Locale.ROOT) + " ", PASSWORD, requestId)).isEqualTo(first);
        register(name, "Different_123", requestId).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SC_001006"));
        long id = first.path("userId").asLong();
        assertThat(count("t_user", "id", id)).isEqualTo(1);
        assertThat(count("t_refresh_session", "user_id", id)).isEqualTo(1);
        assertThat(count("t_idempotency_record", "user_id", id)).isEqualTo(1);
        String persisted = jdbc.queryForObject("select result::text from study_copilot.t_idempotency_record where user_id=?", String.class, id);
        assertThat(persisted).doesNotContain(PASSWORD, first.path("accessToken").asText(), first.path("refreshToken").asText());
    }

    @Test
    void duplicateCaseInsensitiveUsernameUsesDeterministicConflict() throws Exception {
        String name = name();
        registered(name, PASSWORD, key());
        register(name.toUpperCase(java.util.Locale.ROOT), PASSWORD, key()).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SC_001003"));
    }

    @Test
    void concurrentRegistrationsHaveOneWinnerAndOneStableReplay() throws Exception {
        String name = name(), requestId = key();
        var responses = concurrent(() -> register(name, PASSWORD, requestId).andReturn().getResponse().getContentAsString());
        assertThat(json.readTree(responses.get(0)).path("code").asText()).isEqualTo("0");
        assertThat(json.readTree(responses.get(1)).path("data")).isEqualTo(json.readTree(responses.get(0)).path("data"));
        var duplicate = concurrent(() -> register(name + "x", PASSWORD, key()).andReturn().getResponse().getStatus());
        assertThat(duplicate).containsExactlyInAnyOrder(200, 409);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 7, 65})
    void passwordCharacterLimitsAreEnforced(int length) throws Exception {
        register(name(), "a".repeat(length), key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void utf8PasswordsAreNeverTruncatedAndBoundaryPasswordsWork() throws Exception {
        register(name(), "学".repeat(25), key()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
        for (String password : List.of("a".repeat(8), "a".repeat(64), "学".repeat(24), "😀".repeat(18))) {
            String name = name();
            registered(name, password, key());
            postJson("/api/auth/login", Map.of("username", name, "password", password)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").isNotEmpty());
        }
    }

    @Test
    void missingIdempotencyKeyAndInvalidNamesAreValidationErrors() throws Exception {
        postJson("/api/auth/register", Map.of("username", name(), "password", PASSWORD)).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("SC_001001"));
        register(" ", PASSWORD, key()).andExpect(status().isBadRequest());
        register("a".repeat(51), PASSWORD, key()).andExpect(status().isBadRequest());
    }

    @Test
    void loginNormalizesUsernameAndAllCredentialFailuresAreGeneric() throws Exception {
        String name = name();
        JsonNode user = registered(name, PASSWORD, key());
        postJson("/api/auth/login", Map.of("username", " " + name.toUpperCase(java.util.Locale.ROOT) + " ", "password", PASSWORD))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value(user.path("userId").asLong()));
        for (Map<String, String> input : List.of(Map.of("username", name + "missing", "password", PASSWORD),
                Map.of("username", name, "password", "Incorrect_123"), Map.of("username", name, "password", "学".repeat(25)))) {
            postJson("/api/auth/login", input).andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.code").value("SC_002003"))
                    .andExpect(jsonPath("$.message").value("账号或密码不正确"));
        }
        jdbc.update("update study_copilot.t_user set status=0 where id=?", user.path("userId").asLong());
        postJson("/api/auth/login", Map.of("username", name, "password", PASSWORD)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("账号或密码不正确"));
    }

    @Test
    void refreshDigestIsDurableAndRotationAndReplayAreOneTime() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        String refresh = first.path("refreshToken").asText();
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(refresh.getBytes(StandardCharsets.UTF_8)));
        assertThat(jdbc.queryForObject("select token_hash from study_copilot.t_refresh_session where user_id=?", String.class,
                first.path("userId").asLong())).isEqualTo(digest).doesNotContain(refresh);
        JsonNode second = data(postJson("/api/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isOk()));
        assertThat(second.path("refreshToken").asText()).isNotEqualTo(refresh);
        postJson("/api/auth/refresh", Map.of("refreshToken", refresh)).andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("select count(*) from study_copilot.t_refresh_session where user_id=? and revoked_at is null", Integer.class,
                first.path("userId").asLong())).isEqualTo(1);
        postJson("/api/auth/refresh", Map.of("refreshToken", second.path("refreshToken").asText())).andExpect(status().isOk());
    }

    @Test
    void concurrentRefreshMintsExactlyOneReplacement() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        List<Integer> statuses = concurrent(() -> postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText()))
                .andReturn().getResponse().getStatus());
        assertThat(statuses).containsExactlyInAnyOrder(200, 401);
        assertThat(count("t_refresh_session", "user_id", first.path("userId").asLong())).isEqualTo(2);
    }

    @Test
    void accessAndRefreshTypesCannotBeConfusedAndMalformedTokensAreSanitized() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        postJson("/api/auth/refresh", Map.of("refreshToken", first.path("accessToken").asText()))
                .andExpect(status().isUnauthorized());
        for (String token : List.of(first.path("refreshToken").asText(), "private-malformed-token")) {
            mvc.perform(get("/api/subjects").header("Authorization", "Bearer " + token).header("X-Trace-Id", "auth-trace"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("SC_002006"))
                    .andExpect(header().string("X-Trace-Id", "auth-trace"))
                    .andExpect(jsonPath("$.traceId").value("auth-trace"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(token))));
        }
    }

    @Test
    void issuerExpirySubjectAndSessionVersionAreCheckedOnEveryAccess() throws Exception {
        JsonNode first = registered(name(), PASSWORD, key());
        long id = first.path("userId").asLong();
        for (String token : List.of(signed(id, "wrong-issuer", Instant.now().plusSeconds(60), 0),
                signed(id, "study-copilot", Instant.now().minusSeconds(60), 0),
                signed(id, "study-copilot", Instant.now().plusSeconds(60), 99),
                signed(id + 99999, "study-copilot", Instant.now().plusSeconds(60), 0))) {
            protectedRequest(token).andExpect(status().isUnauthorized());
        }
        protectedRequest(first.path("accessToken").asText()).andExpect(status().isOk());
        jdbc.update("update study_copilot.t_user set session_version=1 where id=?", id);
        protectedRequest(first.path("accessToken").asText()).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isUnauthorized());
    }

    @Test
    void disabledAndDeletedAccountsLoseAccessAndRefreshImmediately() throws Exception {
        for (String field : List.of("status=0", "deleted=1")) {
            JsonNode first = registered(name(), PASSWORD, key());
            jdbc.update("update study_copilot.t_user set " + field + " where id=?", first.path("userId").asLong());
            protectedRequest(first.path("accessToken").asText()).andExpect(status().isUnauthorized());
            postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isUnauthorized());
        }
    }

    @Test
    void logoutRevokesOnlyTheSuppliedDurableSession() throws Exception {
        String name = name();
        JsonNode first = registered(name, PASSWORD, key());
        JsonNode other = data(postJson("/api/auth/login", Map.of("username", name, "password", PASSWORD)));
        postJson("/api/auth/logout", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isOk());
        postJson("/api/auth/logout", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isOk());
        postJson("/api/auth/refresh", Map.of("refreshToken", first.path("refreshToken").asText())).andExpect(status().isUnauthorized());
        postJson("/api/auth/refresh", Map.of("refreshToken", other.path("refreshToken").asText())).andExpect(status().isOk());
    }

    @Test
    void passwordChangeRevokesAllOldCredentialsAndCannotAffectAnotherUser() throws Exception {
        String name = name();
        JsonNode first = registered(name, PASSWORD, key()), other = registered(name(), PASSWORD, key());
        JsonNode second = data(postJson("/api/auth/login", Map.of("username", name, "password", PASSWORD)));
        mvc.perform(put("/api/account/password").header("Authorization", "Bearer " + first.path("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword", "Wrong_123", "newPassword", "New-pass_123"))))
                .andExpect(status().isUnauthorized());
        mvc.perform(put("/api/account/password").header("Authorization", "Bearer " + first.path("accessToken").asText())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "New-pass_123",
                                "userId", other.path("userId").asLong()))))
                .andExpect(status().isOk());
        for (JsonNode old : List.of(first, second)) {
            protectedRequest(old.path("accessToken").asText()).andExpect(status().isUnauthorized());
            postJson("/api/auth/refresh", Map.of("refreshToken", old.path("refreshToken").asText())).andExpect(status().isUnauthorized());
        }
        assertThat(jdbc.queryForObject("select session_version from study_copilot.t_user where id=?", Long.class, first.path("userId").asLong())).isEqualTo(1L);
        postJson("/api/auth/login", Map.of("username", name, "password", PASSWORD)).andExpect(status().isUnauthorized());
        postJson("/api/auth/login", Map.of("username", name, "password", "New-pass_123")).andExpect(status().isOk());
        protectedRequest(other.path("accessToken").asText()).andExpect(status().isOk());
        postJson("/api/auth/refresh", Map.of("refreshToken", other.path("refreshToken").asText())).andExpect(status().isOk());
    }

    @Test
    void recoveryIsStableAccountIndependentAndUsesConfiguredSupportChannel() throws Exception {
        String name = name();
        registered(name, PASSWORD, key());
        JsonNode first = data(mvc.perform(get("/api/auth/recovery-info").param("username", name)).andExpect(status().isOk()));
        JsonNode missing = data(mvc.perform(get("/api/auth/recovery-info").param("username", name + "missing")).andExpect(status().isOk()));
        assertThat(first).isEqualTo(missing);
        assertThat(first.path("supportChannel").asText()).isEqualTo("test-only-support-channel");
        assertThat(first.path("message").asText()).isNotBlank();
        postJson("/api/auth/reset-password", Map.of("username", name)).andExpect(status().isNotFound());
    }

    @Test
    void filterChainErrorsHaveCompleteResultAndTraceEnvelope() throws Exception {
        mvc.perform(get("/api/subjects").header("X-Trace-Id", "security-trace")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("SC_002006"))
                .andExpect(jsonPath("$.message").value("未登录或登录已过期"))
                .andExpect(jsonPath("$.retryable").value(false)).andExpect(jsonPath("$.fieldErrors").isArray())
                .andExpect(jsonPath("$.traceId").value("security-trace")).andExpect(header().string("X-Trace-Id", "security-trace"));
        JsonNode first = registered(name(), PASSWORD, key());
        mvc.perform(get("/actuator/info").header("Authorization", "Bearer " + first.path("accessToken").asText())
                        .header("X-Trace-Id", "forbidden-trace")).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("SC_002007")).andExpect(jsonPath("$.traceId").value("forbidden-trace"))
                .andExpect(header().string("X-Trace-Id", "forbidden-trace"));
    }

    @Test
    void requireUserIdFailsClosedForMissingAnonymousAndUnauthenticatedPrincipals() throws Exception {
        var method = UserContext.class.getMethod("requireUserId");
        for (var auth : java.util.Arrays.asList(null,
                new org.springframework.security.authentication.AnonymousAuthenticationToken("test", "anonymousUser", List.of(new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ANONYMOUS"))),
                new UsernamePasswordAuthenticationToken(12L, null))) {
            SecurityContextHolder.getContext().setAuthentication(auth);
            assertThatThrownBy(() -> method.invoke(null)).hasCauseInstanceOf(org.springframework.security.core.AuthenticationException.class);
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(12L, null, List.of()));
        assertThat(method.invoke(null)).isEqualTo(12L);
    }

    private String name() { return "auth_t3_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20); }
    private String key() { return UUID.randomUUID().toString(); }
    private int count(String table, String column, long id) {
        return jdbc.queryForObject("select count(*) from study_copilot." + table + " where " + column + "=?", Integer.class, id);
    }
    private ResultActions postJson(String path, Object body) throws Exception {
        return mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body)));
    }
    private ResultActions register(String name, String password, String key) throws Exception {
        return mvc.perform(post("/api/auth/register").header("Idempotency-Key", key).contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("username", name, "password", password))));
    }
    private JsonNode registered(String name, String password, String key) throws Exception {
        return data(register(name, password, key).andExpect(status().isOk()).andExpect(jsonPath("$.data.accessToken").isNotEmpty()));
    }
    private JsonNode data(ResultActions action) throws Exception {
        return json.readTree(action.andReturn().getResponse().getContentAsString()).path("data");
    }
    private ResultActions protectedRequest(String accessToken) throws Exception {
        return mvc.perform(get("/api/subjects").header("Authorization", "Bearer " + accessToken));
    }
    private Claims claims(String token) {
        return Jwts.parserBuilder().setSigningKey(Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8))).build().parseClaimsJws(token).getBody();
    }
    private String signed(long id, String issuer, Instant expiry, long version) {
        return Jwts.builder().setIssuer(issuer).setSubject(Long.toString(id)).setIssuedAt(Date.from(Instant.now().minusSeconds(120)))
                .setExpiration(Date.from(expiry)).claim("type", "access").claim("sessionVersion", version)
                .signWith(Keys.hmacShaKeyFor(jwt.getSecret().getBytes(StandardCharsets.UTF_8)), SignatureAlgorithm.HS256).compact();
    }
    private <T> List<T> concurrent(Callable<T> action) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Callable<T> call = () -> { if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("barrier timeout"); return action.call(); };
            var first = pool.submit(call);
            var second = pool.submit(call);
            start.countDown();
            return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
}
