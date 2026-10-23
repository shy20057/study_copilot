# Study Copilot Phase A Backend Implementation Plan

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the complete Phase A backend for Study Copilot: invited-beta authentication, onboarding, tasks, trustworthy focus sessions, manual records, rewards, pets, and the dashboard aggregate.

**Architecture:** PostgreSQL 16 is the source of truth, Redis is disposable coordination/cache infrastructure, and MinIO is isolated to avatars. Each vertical package adds migrations, domain services, HTTP contracts, and PostgreSQL-backed tests before the next package begins; the frontend remains out of scope until the separate UI design approval gate.

**Tech Stack:** Java 17, Spring Boot 3.x, Spring Security, MyBatis-Plus, Flyway, PostgreSQL 16, Redis 8.2, MinIO, JUnit 5, Spring MockMvc.

**Spec:** `docs/superpowers/specs/2026-10-22-study-copilot-phase-a-backend-design.md`

## Global Constraints

- Keep PostgreSQL; do not migrate to MySQL or use H2 for integration tests.
- Reuse middleware from `E:\zahuo\实习上简历\procurement-实习的项目\deploy\docker-compose.yml`.
- Use database `study_copilot` for development and `study_copilot_test` for integration tests.
- Prefix Redis keys with `study-copilot:` and test keys with `study-copilot:test:`.
- Store all instants in UTC; derive learning dates using the session's persisted rule version.
- PostgreSQL remains authoritative when Redis is unavailable or cleared.
- A user may have at most one `RUNNING` focus or break session.
- Every mutation must filter by the authenticated `user_id`.
- Critical mutations use request idempotency; updates use optimistic versions.
- Do not expose B/C endpoints in the default profile.
- Do not commit credentials, generated `target/` files, IDE files, logs, or frontend dependencies.
- Backend tests are explicitly approved for this task.
- Do not begin frontend implementation after this plan; stop at the UI skills consultation gate.

---

## File Structure

### Existing files to retain and evolve

- `backend/pom.xml`: dependency and build configuration.
- `backend/src/main/resources/application.yml`: environment-only runtime configuration.
- `backend/src/main/resources/db/migration/V1__init_schema.sql`: immutable historical migration.
- `backend/src/main/java/com/example/studycopilot/common/**`: result, errors, exceptions, tracing.
- `backend/src/main/java/com/example/studycopilot/security/**`: authentication filter and user context.
- `backend/src/main/java/com/example/studycopilot/controller/**`: Phase A HTTP adapters; B/C adapters receive a non-default profile.
- `backend/src/main/java/com/example/studycopilot/service/**`: orchestration interfaces and implementations.
- `backend/src/main/java/com/example/studycopilot/dao/entity/**`: persistence records.
- `backend/src/main/java/com/example/studycopilot/dao/mapper/**`: MyBatis-Plus mappers.
- `backend/src/main/java/com/example/studycopilot/dto/**`: validated request and response contracts.

### New focused packages

- `domain/time`: learning-day calculation and interval slicing.
- `domain/focus`: session lifecycle and settlement policy.
- `domain/reward`: experience calculation, cap allocation, and level derivation.
- `domain/task`: task hierarchy, status, repeat, and carry-over rules.
- `service/dashboard`: dashboard orchestration only; it does not own facts.
- `service/onboarding`: onboarding progress and template import.
- `service/account`: password change and deletion workflow.
- `support/idempotency`: request fingerprinting and replay.
- `support/trace`: trace-id filter and response propagation.

### Tests

- `backend/src/test/java/com/example/studycopilot/domain/**`: pure unit tests.
- `backend/src/test/java/com/example/studycopilot/integration/**`: PostgreSQL/Redis integration tests.
- `backend/src/test/java/com/example/studycopilot/api/**`: MockMvc API tests.
- `backend/src/test/resources/application-test.yml`: isolated middleware profile.

---

### Task 1: Repository, Runtime Profiles, and Phase A Schema

**Files:**
- Create: `.gitignore`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/main/resources/db/migration/V2__phase_a_core.sql`
- Create: `backend/src/test/java/com/example/studycopilot/integration/MigrationSmokeTest.java`
- Create: `docs/backend-local-development.md`

**Interfaces:**
- Consumes: PostgreSQL and Redis addresses supplied through environment variables.
- Produces: the complete Phase A relational schema and repeatable local/test configuration.

- [ ] **Step 1: Add repository ignores**

```gitignore
.idea/
.vscode/
*.iml
*.log
.env
backend/target/
web/node_modules/
web/dist/
logs/
```

- [ ] **Step 2: Add required dependencies and test configuration**

Add PostgreSQL runtime, Flyway PostgreSQL support if required by the selected Spring/Flyway version, Spring Security test, and MinIO SDK. Keep dependency versions centralized in `pom.xml`; do not add H2 or Testcontainers because the approved test environment is the shared PostgreSQL container with an isolated database.

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.minio</groupId>
  <artifactId>minio</artifactId>
  <version>8.5.17</version>
</dependency>
```

- [ ] **Step 3: Define environment-only profiles**

```yaml
spring:
  datasource:
    url: ${SC_DB_URL}
    username: ${SC_DB_USER}
    password: ${SC_DB_PASSWORD}
  data:
    redis:
      host: ${SC_REDIS_HOST:127.0.0.1}
      port: ${SC_REDIS_PORT:6381}
      password: ${SC_REDIS_PASSWORD}
study-copilot:
  redis-key-prefix: ${SC_REDIS_PREFIX:study-copilot:}
  storage:
    endpoint: ${SC_MINIO_ENDPOINT:http://127.0.0.1:9000}
    bucket: ${SC_MINIO_BUCKET:study-copilot}
    access-key: ${SC_MINIO_ACCESS_KEY}
    secret-key: ${SC_MINIO_SECRET_KEY}
```

The test profile uses `SC_TEST_DB_URL`, `SC_TEST_DB_USER`, `SC_TEST_DB_PASSWORD`, and prefix `study-copilot:test:`. It must refuse to start if the JDBC URL does not contain `study_copilot_test`.

- [ ] **Step 4: Write the migration smoke test first**

```java
@SpringBootTest
@ActiveProfiles("test")
class MigrationSmokeTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void phaseASchemaExists() {
        Integer count = jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema = 'study_copilot'
              and table_name in ('t_focus_session','t_focus_slice','t_reward_ledger')
            """, Integer.class);
        assertThat(count).isEqualTo(3);
    }
}
```

- [ ] **Step 5: Run the migration test and confirm the expected failure**

Run:

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=MigrationSmokeTest test
```

Expected: failure because V2 and the required tables do not exist.

- [ ] **Step 6: Implement V2 schema evolution**

The migration must:

- enable `btree_gist` in schema `study_copilot`;
- extend users/settings/subjects/tasks/pets with the fields from the spec;
- create refresh sessions, learning-day rules, onboarding progress, import batches, repeat templates, schedule logs, focus sessions, focus slices, idempotency records, reward ledger, result presentation, and account-deletion jobs;
- add a partial unique index on `t_focus_session(user_id)` where `status = 'RUNNING'` and `deleted = 0`;
- add unique keys for `(user_id, operation_type, request_id)`, `(user_id, repeat_template_id, learning_date)`, `(session_id, learning_date)`, and `(user_id, normalized_username)`;
- use a stored `tstzrange` or equivalent exclusion constraint for overlapping effective learning ranges;
- add ownership/query indexes for every user-scoped list.

The running-session index is exactly:

```sql
create unique index uk_focus_user_running
on study_copilot.t_focus_session (user_id)
where status = 'RUNNING' and deleted = 0;
```

- [ ] **Step 7: Run the migration test**

Run the command from Step 5.

Expected: PASS with all three tables detected.

- [ ] **Step 8: Document database creation without secrets**

Document the commands that an authorized operator runs inside the existing PostgreSQL container to create `study_copilot` and `study_copilot_test`; reference environment-variable names instead of copying values from the procurement project `.env` files.

- [ ] **Step 9: Commit**

```powershell
git add .gitignore backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/db/migration/V2__phase_a_core.sql backend/src/test docs/backend-local-development.md
git commit -m "build: establish phase A backend foundation"
```

---

### Task 2: Result Envelope, Trace IDs, and Domain Errors

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/common/result/Result.java`
- Modify: `backend/src/main/java/com/example/studycopilot/common/errorcode/ScErrorCode.java`
- Modify: `backend/src/main/java/com/example/studycopilot/common/global/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/example/studycopilot/common/result/FieldViolation.java`
- Create: `backend/src/main/java/com/example/studycopilot/support/trace/TraceIdFilter.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/ErrorContractTest.java`

**Interfaces:**
- Consumes: exceptions raised by every later task.
- Produces: `Result<T>` with `code`, `message`, `data`, `traceId`, `retryable`, `fieldErrors`, `currentVersion`, and `currentSession`.

- [ ] **Step 1: Write API error contract tests**

```java
@WebMvcTest(controllers = ErrorProbeController.class)
@Import({GlobalExceptionHandler.class, TraceIdFilter.class})
class ErrorContractTest {
    @Autowired MockMvc mvc;

    @Test
    void validationReturns400AndFieldErrors() throws Exception {
        mvc.perform(post("/test/error/validation").contentType(APPLICATION_JSON).content("{}"))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("SC_001001"))
            .andExpect(jsonPath("$.traceId").isNotEmpty())
            .andExpect(jsonPath("$.fieldErrors[0].field").value("title"));
    }
}
```

- [ ] **Step 2: Run the contract test and confirm failure**

Run:

```powershell
mvn -f backend/pom.xml -Dtest=ErrorContractTest test
```

Expected: failure because the current envelope lacks the required metadata and status mapping.

- [ ] **Step 3: Extend the envelope and error codes**

Add exact Phase A codes for session existence/not-due, version conflict, time overlap, idempotency conflict, subject-in-use, last-active-subject, learning-rule-change-blocked, revoked token, and rate limiting. Map validation to 400, authentication to 401, forbidden to 403, missing resources to 404, conflicts to 409, rate limits to 429, and unexpected failures to 500.

- [ ] **Step 4: Add trace propagation**

`TraceIdFilter` accepts a safe incoming `X-Trace-Id` or generates a UUID, places it in MDC, response headers, and the result envelope, and clears MDC after the request.

- [ ] **Step 5: Run the error contract test**

Run the Step 2 command.

Expected: PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/common backend/src/main/java/com/example/studycopilot/support/trace backend/src/test/java/com/example/studycopilot/api/ErrorContractTest.java
git commit -m "feat: define phase A API error contract"
```

---

### Task 3: Authentication, Refresh Sessions, and Account Initialization

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/security/JwtTokenProvider.java`
- Modify: `backend/src/main/java/com/example/studycopilot/security/JwtAuthenticationFilter.java`
- Modify: `backend/src/main/java/com/example/studycopilot/config/SecurityConfig.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/user/AuthService.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/user/impl/AuthServiceImpl.java`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/user/AuthController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/req/auth/RegisterRequest.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/req/auth/LoginRequest.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/auth/RefreshTokenRequest.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/auth/LogoutRequest.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/resp/auth/LoginResponse.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/user/RefreshSessionDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/user/RefreshSessionMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/account/AccountService.java`
- Create: `backend/src/main/java/com/example/studycopilot/controller/account/AccountController.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/AuthApiTest.java`

**Interfaces:**
- Produces: `AuthService.register(RegisterRequest, String requestId)`, `login(LoginRequest)`, `refresh(RefreshTokenRequest)`, and `logout(LogoutRequest)`.
- Produces: an authenticated `UserContext.requireUserId()` that never returns null.

- [ ] **Step 1: Write auth API tests**

Cover username normalization, duplicate case-insensitive usernames, UTF-8 password byte limits, default initialization, generic login errors, refresh rotation, logout revocation, and registration idempotency.

```java
@Test
void chinesePasswordOver72BytesIsRejected() throws Exception {
    String password = "学".repeat(25);
    mvc.perform(post("/api/auth/register")
            .header("Idempotency-Key", UUID.randomUUID())
            .contentType(APPLICATION_JSON)
            .content(json.writeValueAsString(Map.of("username", "reader_01", "password", password))))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
}
```

- [ ] **Step 2: Run auth tests and confirm failure**

Run:

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=AuthApiTest test
```

Expected: failure because auth services are stubs.

- [ ] **Step 3: Implement token semantics**

Access tokens contain `type=access`, `sessionVersion`, issuer, subject, issued-at, and expiry. Refresh tokens contain `type=refresh` and a refresh-session id. Reject an access token passed to refresh and any token whose session version is stale.

- [ ] **Step 4: Implement transactional registration**

Normalize username with `trim().toLowerCase(Locale.ROOT)`. In one transaction insert the user, settings, learning-day rule version 1, default subject, pet status, and onboarding progress. Persist idempotency before returning the created account.

- [ ] **Step 5: Implement login, refresh rotation, logout, change-password revocation, and recovery info**

The invited-beta recovery response is stable and contains the configured support channel without revealing whether an account exists.

- [ ] **Step 6: Run auth tests**

Run Step 2.

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/security backend/src/main/java/com/example/studycopilot/config/SecurityConfig.java backend/src/main/java/com/example/studycopilot/controller/user backend/src/main/java/com/example/studycopilot/controller/account backend/src/main/java/com/example/studycopilot/service/user backend/src/main/java/com/example/studycopilot/service/account backend/src/main/java/com/example/studycopilot/dao/entity/user backend/src/main/java/com/example/studycopilot/dao/mapper/user backend/src/main/java/com/example/studycopilot/dto backend/src/test/java/com/example/studycopilot/api/AuthApiTest.java
git commit -m "feat: implement invited beta authentication"
```

---

### Task 4: Settings, Subjects, and Learning-Day Rules

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/service/setting/**`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/setting/SettingController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/subject/**`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/subject/SubjectController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/setting/UserSettingDO.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/subject/SubjectDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/setting/LearningDayRuleDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/setting/LearningDayRuleMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/time/LearningDayService.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/time/TimeSliceService.java`
- Create: `backend/src/test/java/com/example/studycopilot/domain/time/LearningDayServiceTest.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/SettingsSubjectApiTest.java`

**Interfaces:**
- Produces: `LearningDayContext resolve(long userId, Instant instant)` for persisted user rules.
- Produces: `LearningDayContext resolve(Instant instant, ZoneId zone, int dayStartHour, long ruleVersion)` for deterministic domain calculation.
- Produces: `List<LearningSlice> split(Instant start, Instant end, ZoneId zone, int dayStartHour, long ruleVersion)`.

- [ ] **Step 1: Write learning-day unit tests**

```java
@Test
void threeAmBelongsToPreviousLearningDayWhenBoundaryIsFour() {
    LearningDayContext result = service.resolve(
        Instant.parse("2026-10-22T19:00:00Z"), ZoneId.of("Asia/Shanghai"), 4, 7L);
    assertThat(result.learningDate()).isEqualTo(LocalDate.of(2026, 10, 22));
    assertThat(result.ruleVersion()).isEqualTo(7L);
}

@Test
void intervalAcrossFourAmSplitsIntoTwoHalfOpenSlices() {
    var slices = slicer.split(
        Instant.parse("2026-10-22T19:50:00Z"),
        Instant.parse("2026-10-22T20:15:00Z"),
        ZoneId.of("Asia/Shanghai"), 4, 7L);
    assertThat(slices).extracting(LearningSlice::seconds).containsExactly(600L, 900L);
}
```

- [ ] **Step 2: Run time tests and confirm failure**

```powershell
mvn -f backend/pom.xml -Dtest=LearningDayServiceTest test
```

- [ ] **Step 3: Implement time rules and slicing**

Use half-open intervals `[start,end)`. A rule change creates a new version with `effective_at`; it never rewrites previous slices. Reject timezone/day-start changes while a session is running.

- [ ] **Step 4: Write and run subject/settings API tests**

Test at least one active subject, case-insensitive active-name uniqueness, version conflicts, final-subject archive rejection, and persisted `SYSTEM`/motion/transparency preferences.

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=SettingsSubjectApiTest test
```

Expected before implementation: FAIL.

- [ ] **Step 5: Implement settings and subject services**

All queries filter by user. Archive never deletes history. If unfinished tasks exist, require an explicit migration/cancel strategy. Persist transparency and reduced-motion independently.

- [ ] **Step 6: Run both test classes**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=LearningDayServiceTest,SettingsSubjectApiTest test
```

Expected: PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/domain/time backend/src/main/java/com/example/studycopilot/controller/setting backend/src/main/java/com/example/studycopilot/controller/subject backend/src/main/java/com/example/studycopilot/service/setting backend/src/main/java/com/example/studycopilot/service/subject backend/src/main/java/com/example/studycopilot/dao backend/src/main/java/com/example/studycopilot/dto backend/src/test
git commit -m "feat: add settings subjects and learning day rules"
```

---

### Task 5: Onboarding and Idempotent Template Import

**Files:**
- Create: `backend/src/main/resources/templates/phase-a-study-templates.v1.json`
- Create: `backend/src/main/java/com/example/studycopilot/controller/onboarding/OnboardingController.java`
- Create: `backend/src/main/java/com/example/studycopilot/controller/onboarding/TemplateController.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/onboarding/OnboardingService.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/onboarding/impl/OnboardingServiceImpl.java`
- Create: `backend/src/main/java/com/example/studycopilot/support/idempotency/IdempotencyService.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/OnboardingApiTest.java`

**Interfaces:**
- Produces: `TemplatePreview preview(String templateId, Set<String> itemIds, LocalDate learningDate)`.
- Produces: `ImportResult complete(CompleteOnboardingRequest request, String requestId)`.

- [ ] **Step 1: Write onboarding tests**

Cover skip, progress restore, preview versioning, maximum seven learning days, maximum three default tasks today, atomic import, response-timeout retry, and different-payload idempotency conflict.

- [ ] **Step 2: Run tests and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=OnboardingApiTest test
```

- [ ] **Step 3: Add versioned built-in templates**

The resource contains stable template and item ids, subject aliases, relative learning-day offsets, estimated pomodoros, and display copy. Preview always returns `previewVersion` and never writes tasks.

- [ ] **Step 4: Implement idempotency service**

Fingerprint the authenticated user, operation type, normalized request body, and request id. Identical retries replay the stored response; different payloads return `IDEMPOTENCY_CONFLICT`.

- [ ] **Step 5: Implement onboarding and atomic import**

Completion imports only selected preview items, marks the batch, and updates onboarding state in one transaction. User-created same-title tasks remain legal because deduplication uses template item id plus learning date.

- [ ] **Step 6: Run onboarding tests**

Run Step 2; expect PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/resources/templates backend/src/main/java/com/example/studycopilot/controller/onboarding backend/src/main/java/com/example/studycopilot/service/onboarding backend/src/main/java/com/example/studycopilot/support/idempotency backend/src/test/java/com/example/studycopilot/api/OnboardingApiTest.java
git commit -m "feat: implement onboarding and template imports"
```

---

### Task 6: Task Core, Hierarchy, Ordering, and Optimistic Versions

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/dao/entity/task/TaskDO.java`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/req/task/**`
- Modify: `backend/src/main/java/com/example/studycopilot/dto/resp/task/TaskResponse.java`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/task/TaskController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/task/**`
- Create: `backend/src/main/java/com/example/studycopilot/domain/task/TaskPolicy.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/task/ReorderTasksRequest.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/TaskApiTest.java`

**Interfaces:**
- Produces: task list filters `ALL`, `TODO`, `DONE`, `CANCELLED` with complete counts independent of dashboard truncation.
- Produces: versioned mutations whose request includes `expectedVersion`.

- [ ] **Step 1: Write task tests**

Test create, IME-safe API semantics, ownership, parent/child completion, reopening a child, cancel/restore, soft-delete restore window metadata, reorder conflicts, top-level completion rates, and session-linked task restrictions.

```java
@Test
void reopeningChildReopensCompletedParent() {
    TaskAggregate aggregate = completedParentWithTwoChildren();
    aggregate.reopenChild(CHILD_ONE);
    assertThat(aggregate.parent().status()).isEqualTo(TaskStatus.TODO);
}
```

- [ ] **Step 2: Run tests and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=TaskApiTest test
```

- [ ] **Step 3: Implement task policy and persistence**

Allow one child level only. Completing a parent with incomplete children requires `completeChildren=true`; otherwise return the incomplete count. Completing all non-cancelled children completes the parent. Cancelling all children does not auto-complete the parent.

- [ ] **Step 4: Implement ordering and version conflicts**

Reorder accepts the complete ordered id list for the current filter plus each expected version. Apply in one transaction or return `VERSION_CONFLICT` without a partial reorder.

- [ ] **Step 5: Run task tests**

Run Step 2; expect PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/domain/task backend/src/main/java/com/example/studycopilot/controller/task backend/src/main/java/com/example/studycopilot/service/task backend/src/main/java/com/example/studycopilot/dao/entity/task backend/src/main/java/com/example/studycopilot/dto/req/task backend/src/main/java/com/example/studycopilot/dto/resp/task backend/src/test/java/com/example/studycopilot/api/TaskApiTest.java
git commit -m "feat: implement versioned task workflows"
```

---

### Task 7: Repeat Templates and Carry-Over Batches

**Files:**
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/task/TaskRepeatTemplateDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/task/TaskScheduleLogDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/task/TaskRepeatTemplateMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/task/TaskScheduleLogMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/task/RepeatTaskService.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/task/CarryOverService.java`
- Create: `backend/src/main/java/com/example/studycopilot/controller/task/RepeatTaskController.java`
- Create: `backend/src/test/java/com/example/studycopilot/integration/TaskSchedulingIntegrationTest.java`

**Interfaces:**
- Produces: `ensureInstance(long userId, LocalDate learningDate)` shared by scheduled and first-visit compensation.
- Produces: `CarryOverResult carryOver(CarryOverRequest)` and `undo(UUID batchId)`.

- [ ] **Step 1: Write scheduling integration tests**

Test scheduler/first-visit concurrency, ten-day absence, weekdays and selected weekdays, this-instance versus future edits, today's duplicate conflict, selected-only carry-over, and batch undo.

- [ ] **Step 2: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=TaskSchedulingIntegrationTest test
```

- [ ] **Step 3: Implement repeat generation**

Use the database unique key `(user_id, repeat_template_id, learning_date)` as the final concurrency guard. Generate only the current learning date during compensation; do not backfill missed dates.

- [ ] **Step 4: Implement carry-over conflict choices and undo**

When today's repeat instance exists, require `KEEP_TODAY_CANCEL_OLD` or `KEEP_BOTH`; never silently merge. Store every changed task's before/after values under one batch id so undo restores the batch atomically.

- [ ] **Step 5: Run tests**

Run Step 2; expect PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/dao backend/src/main/java/com/example/studycopilot/service/task backend/src/main/java/com/example/studycopilot/controller/task backend/src/test/java/com/example/studycopilot/integration/TaskSchedulingIntegrationTest.java
git commit -m "feat: add repeat tasks and carry over batches"
```

---

### Task 8: Unique Focus/Break Sessions and Server-Time Recovery

**Files:**
- Replace Phase A usage of: `backend/src/main/java/com/example/studycopilot/controller/pomodoro/PomodoroController.java`
- Replace Phase A usage of: `backend/src/main/java/com/example/studycopilot/service/pomodoro/**`
- Create: `backend/src/main/java/com/example/studycopilot/controller/focus/FocusController.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/focus/FocusSessionService.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/focus/impl/FocusSessionServiceImpl.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/focus/FocusSessionPolicy.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/focus/FocusSessionDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/focus/FocusSessionMapper.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/focus/StartFocusRequest.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/focus/StartBreakRequest.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/resp/focus/FocusSessionResponse.java`
- Create: `backend/src/test/java/com/example/studycopilot/integration/FocusSessionIntegrationTest.java`

**Interfaces:**
- Produces: `start(StartFocusRequest request, String requestId)`.
- Produces: `current()` that first compensates overdue sessions.
- Produces: `startBreak(StartBreakRequest request, String requestId)` and atomic `endBreakAndStartFocus(...)`.

- [ ] **Step 1: Write session integration tests**

Test idempotent start, lost start response, two-device concurrent start, focus/break mutual exclusion, early complete at 95%, device clock changes having no effect, service restart recovery, and Redis-clear recovery.

- [ ] **Step 2: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=FocusSessionIntegrationTest test
```

- [ ] **Step 3: Implement session start and current**

Persist server `startAt` and `scheduledEndAt`. On unique-index conflict, load and return the existing session as `SESSION_EXISTS`. A current query locks and settles an overdue session before returning.

Move the old `/api/pomodoro` controller behind `@Profile("legacy-api")` so the default profile exposes only the reviewed `/api/focus` contract.

- [ ] **Step 4: Implement break lifecycle**

Breaks use the same table and running-session constraint, never generate focus slices or rewards, and never automatically start focus when complete.

- [ ] **Step 5: Add bounded compensation scan**

Use a Spring scheduled job that selects overdue running sessions with `FOR UPDATE SKIP LOCKED` in small batches and calls the same settlement service used by current/complete. XXL-JOB is not used.

- [ ] **Step 6: Run session tests**

Run Step 2; expect PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/controller/focus backend/src/main/java/com/example/studycopilot/service/focus backend/src/main/java/com/example/studycopilot/domain/focus backend/src/main/java/com/example/studycopilot/dao/entity/focus backend/src/main/java/com/example/studycopilot/dao/mapper/focus backend/src/test/java/com/example/studycopilot/integration/FocusSessionIntegrationTest.java
git commit -m "feat: add recoverable unique focus sessions"
```

---

### Task 9: Settlement, Time Slices, Rewards, and Pets

**Files:**
- Create: `backend/src/main/java/com/example/studycopilot/service/focus/FocusSettlementService.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/reward/RewardPolicy.java`
- Create: `backend/src/main/java/com/example/studycopilot/domain/reward/PetLevelPolicy.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/focus/FocusSliceDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/pet/RewardLedgerDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/pet/ResultPresentationDO.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/pet/**`
- Modify: `backend/src/main/java/com/example/studycopilot/controller/pet/PetController.java`
- Create: `backend/src/test/java/com/example/studycopilot/domain/reward/RewardPolicyTest.java`
- Create: `backend/src/test/java/com/example/studycopilot/integration/SettlementIntegrationTest.java`

**Interfaces:**
- Produces: `SettlementResult settleDue(long userId, long sessionId, String requestId)`.
- Produces: `SettlementResult endEarly(long userId, long sessionId, boolean claimPartialReward, String requestId)`.
- Produces: `RewardAllocation allocate(List<LearningSlice> slices, RewardKind kind, Map<LocalDate,Integer> remainingCaps)`.

- [ ] **Step 1: Write reward unit tests**

```java
@ParameterizedTest
@CsvSource({"30,0", "480,0", "750,62", "1500,125"})
void partialRewardUsesWholeSecondsAndHalfCoefficient(long seconds, int expected) {
    assertThat(policy.partialReward(seconds)).isEqualTo(expected);
}

@Test
void petLevelUsesCumulativeThresholds() {
    assertThat(levelPolicy.levelFor(280000)).isEqualTo(10);
    assertThat(levelPolicy.stageFor(90000)).isEqualTo(PetStage.MATURE);
}
```

- [ ] **Step 2: Write settlement integration tests**

Cover 30 seconds, 8 minutes, 12m30s, exact 25 minutes, 95% early complete, simultaneous due/early-end, duplicate settlement, transaction rollback, midnight split, 04:00 split, daily-cap concurrency, multi-level result, and one presentation claim.

- [ ] **Step 3: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=RewardPolicyTest,SettlementIntegrationTest test
```

- [ ] **Step 4: Implement one transactional settlement path**

Lock the session, return stored results for an existing terminal state, generate slices with the session's rule version, lock per-user/per-learning-day reward rows in sorted date order, apply caps, write the ledger, update pet cumulative experience, mark the terminal state, and create one presentation row before commit.

- [ ] **Step 5: Implement pet endpoints**

Return five stable options, cumulative experience, derived level/stage, current threshold, next threshold, and max-level behavior. Switching validates types 1–5 and changes only current type.

- [ ] **Step 6: Run tests**

Run Step 3; expect PASS.

- [ ] **Step 7: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/service/focus backend/src/main/java/com/example/studycopilot/domain/reward backend/src/main/java/com/example/studycopilot/dao backend/src/main/java/com/example/studycopilot/service/pet backend/src/main/java/com/example/studycopilot/controller/pet backend/src/test
git commit -m "feat: settle focus sessions and award pet growth"
```

---

### Task 10: Manual Records, Overlap Protection, and Record Queries

**Files:**
- Create: `backend/src/main/java/com/example/studycopilot/controller/focus/FocusRecordController.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/focus/FocusRecordService.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/req/focus/ManualFocusRequest.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/resp/focus/FocusRecordResponse.java`
- Create: `backend/src/test/java/com/example/studycopilot/integration/ManualFocusIntegrationTest.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/FocusRecordApiTest.java`

**Interfaces:**
- Produces: paged record queries by learning date and subject.
- Produces: create/update/delete/restore for `MANUAL` records only.

- [ ] **Step 1: Write overlap and record tests**

Cover exact adjacency, overlap with TIMER, overlap with MANUAL, two concurrent manual inserts, running reserved interval, future end rejection, 1-minute/4-hour limits, cross-learning-day edits, deletion restore, historical task/subject snapshots, and page size 20.

- [ ] **Step 2: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=ManualFocusIntegrationTest,FocusRecordApiTest test
```

- [ ] **Step 3: Implement manual mutation transactions**

Use full instants, not separate local date/time columns. Return `TIME_OVERLAP` with conflict id, start, end, and subject; preserve request values in the error response. Rebuild only the affected session's slices in the same transaction.

- [ ] **Step 4: Implement record pagination**

Return source, result, seconds, slice interval, full-session interval, task/subject snapshots, reward, and version. TIMER mutations are rejected in Phase A.

- [ ] **Step 5: Run tests**

Run Step 2; expect PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/controller/focus backend/src/main/java/com/example/studycopilot/service/focus backend/src/main/java/com/example/studycopilot/dto/req/focus backend/src/main/java/com/example/studycopilot/dto/resp/focus backend/src/test
git commit -m "feat: add auditable manual focus records"
```

---

### Task 11: Dashboard Aggregate and Consistent Metrics

**Files:**
- Create: `backend/src/main/java/com/example/studycopilot/controller/dashboard/DashboardController.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/dashboard/DashboardService.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/dashboard/impl/DashboardServiceImpl.java`
- Create: `backend/src/main/java/com/example/studycopilot/dto/resp/dashboard/DashboardResponse.java`
- Create: `backend/src/test/java/com/example/studycopilot/api/DashboardApiTest.java`

**Interfaces:**
- Produces: `GET /api/dashboard` with `learningDay`, `ruleVersion`, `dataVersion`, `currentSession`, `stats`, `weeklyGoal`, `tasks`, `subjectDistribution`, `recentRecords`, and `pet`.

- [ ] **Step 1: Write dashboard tests**

Cover no-data states, six-task truncation with complete counts, TODO-before-DONE ordering, manual and early-ended time inclusion, complete-pomodoro exclusion, cross-day slices, more than five subjects, archived historical subjects, three recent records, unset targets, partial module failure, and data-version monotonicity.

- [ ] **Step 2: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=DashboardApiTest test
```

- [ ] **Step 3: Implement aggregate queries**

Query settled `t_focus_slice` rows for all duration totals. Count complete pomodoros from completed TIMER FOCUS sessions by the learning date containing the final instant. Count only top-level non-cancelled/non-deleted tasks in completion rates. Group subject seconds before formatting; do not sum rounded minutes.

- [ ] **Step 4: Implement module statuses**

Each group returns `OK`, `EMPTY`, or `ERROR` with a retryable flag. A failing optional pet/subject query does not replace successful metrics with zero.

- [ ] **Step 5: Run dashboard tests**

Run Step 2; expect PASS.

- [ ] **Step 6: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/controller/dashboard backend/src/main/java/com/example/studycopilot/service/dashboard backend/src/main/java/com/example/studycopilot/dto/resp/dashboard backend/src/test/java/com/example/studycopilot/api/DashboardApiTest.java
git commit -m "feat: add consistent phase A dashboard aggregate"
```

---

### Task 12: Avatar Storage, Account Deletion, Profiles, and Operational Hardening

**Files:**
- Modify: `backend/src/main/java/com/example/studycopilot/controller/attachment/AttachmentController.java`
- Modify: `backend/src/main/java/com/example/studycopilot/service/attachment/**`
- Create: `backend/src/main/java/com/example/studycopilot/config/StorageProperties.java`
- Create: `backend/src/main/java/com/example/studycopilot/service/account/AccountDeletionService.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/entity/user/AccountDeletionJobDO.java`
- Create: `backend/src/main/java/com/example/studycopilot/dao/mapper/user/AccountDeletionJobMapper.java`
- Modify: all B/C controllers under `controller/chat`, `journal`, `knowledge`, `milestone`, `mistake`, `notification`, `reminder`, and `stat`
- Create: `backend/src/test/java/com/example/studycopilot/api/PhaseABoundaryTest.java`
- Create: `backend/src/test/java/com/example/studycopilot/integration/AccountDeletionIntegrationTest.java`
- Create: `docs/backend-operations.md`

**Interfaces:**
- Produces: validated avatar upload returning an owned object reference.
- Produces: account deletion request that revokes tokens, settles/stops the running session, and performs retryable cleanup.

- [ ] **Step 1: Write boundary and deletion tests**

Verify unsupported media type/size/signature rejection, cross-user avatar access denial, B/C routes returning 404 in the default profile, token revocation at deletion start, no post-deletion rewards, and retryable cleanup.

- [ ] **Step 2: Run and confirm failure**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=PhaseABoundaryTest,AccountDeletionIntegrationTest test
```

- [ ] **Step 3: Implement MinIO avatar isolation**

Accept only jpg/png/webp after magic-byte validation, enforce 2MB, write keys as `users/{userId}/avatars/{uuid}.{ext}`, and return a short-lived signed URL or authenticated download route.

- [ ] **Step 4: Disable B/C controllers by profile**

Annotate B/C controllers with `@Profile("future-features")`; the default and test profiles must not register them.

- [ ] **Step 5: Implement deletion workflow and operational metrics**

Set account status to deletion-pending, revoke sessions, settle or stop the running session, enqueue a database-backed cleanup job, delete owned MinIO objects, and retry failed cleanup. Add Micrometer counters/timers for auth, task writes, focus start, settlement delay, restore, idempotent replay, and core error codes.

- [ ] **Step 6: Document operations**

Document health endpoints, environment variables, backup/restore steps, Redis-loss recovery, MinIO failure behavior, cleanup retry, and the fact that backup expiry follows the actual seven-day retention policy.

- [ ] **Step 7: Run hardening tests**

Run Step 2; expect PASS.

- [ ] **Step 8: Commit**

```powershell
git add backend/src/main/java/com/example/studycopilot/controller backend/src/main/java/com/example/studycopilot/service backend/src/main/java/com/example/studycopilot/config backend/src/test docs/backend-operations.md
git commit -m "feat: harden phase A backend operations"
```

---

### Task 13: Full Backend Acceptance and Handoff

**Files:**
- Create: `docs/phase-a-backend-acceptance.md`
- Modify: `docs/backend-local-development.md`
- Modify: `docs/backend-operations.md`

**Interfaces:**
- Consumes: all Phase A backend modules.
- Produces: reproducible verification evidence and the explicit frontend UI-design handoff.

- [ ] **Step 1: Run the complete backend test suite**

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test test
```

Expected: build success with zero failed tests and zero errors.

- [ ] **Step 2: Run the production-profile package check**

```powershell
mvn -f backend/pom.xml -DskipTests package
```

Expected: build success and an executable jar under `backend/target/`.

- [ ] **Step 3: Verify repository hygiene**

```powershell
git status --short
git ls-files | rg '(^|/)(target|node_modules|dist|logs)/|\.env$|\.log$'
```

Expected: no generated artifacts or secret-bearing files are tracked.

- [ ] **Step 4: Record acceptance evidence**

Map each implemented endpoint and automated test class to the applicable A.16 acceptance cases. Record any requirement that still needs browser/UI validation as “frontend gate,” not as passed.

- [ ] **Step 5: Stop before frontend implementation**

Invoke `ui-ux-pro-max`, `interface-design`, and `frontend-design`; present the UI architecture, visual direction, responsive behavior, states, and accessibility treatment to the user. Do not scaffold or edit frontend files until the user explicitly approves that design.

- [ ] **Step 6: Commit backend acceptance documentation**

```powershell
git add docs/phase-a-backend-acceptance.md docs/backend-local-development.md docs/backend-operations.md
git commit -m "docs: record phase A backend acceptance"
```
