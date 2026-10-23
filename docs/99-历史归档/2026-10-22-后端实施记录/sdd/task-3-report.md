# Task 3: Authentication, Refresh Sessions, and Account Initialization

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

Status: DONE

Base: `ba3aee63c4321d9b0cc6c09d74ebf1a63108940a`

Worktree: `E:\a-ProjectNew\my_codingrepo\study_copilot\.worktrees\phase-a-backend`

Commit: `a1a0da2765f26d32727fe16b132d6d21779b1584 feat: implement invited beta authentication`

## Implementation

- Registration normalizes usernames with trim and Locale.ROOT lowercase. Validation uses the normalized 1–50 character name. Passwords require 8–64 Unicode code points and at most 72 UTF-8 bytes; invalid surrogate pairs are rejected and BCrypt is never supplied an overlong password.
- One PostgreSQL transaction creates the user, default subject, settings pointing at that subject, learning-day rule v1, pet status, onboarding progress, durable refresh session, and REGISTER idempotency record. A late-write database failure demonstrably rolls the entire transaction back.
- Registration serializes requests for the same normalized username using a PostgreSQL transaction advisory lock. INSERT ON CONFLICT avoids poisoning the transaction if an external writer races the existing unique constraints. Different request keys against an existing username deterministically return BUSINESS_ERROR/409. Same user/key with a differing payload returns IDEMPOTENCY_CONFLICT/409.
- Registration replays reconstruct the identical original response from session/user/issue/expiry/route metadata. Idempotency records contain a keyed HMAC-SHA256 payload fingerprint and replay metadata, never a password or raw token. The refresh table stores only SHA-256 token digests. Replays do not create new accounts, dependencies, or sessions.
- Access JWTs carry issuer, subject, type=access, sessionVersion, iat, exp and a session-derived jti. Refresh JWTs carry issuer, subject, type=refresh, refreshSessionId, iat and exp. Parsing enforces type, issuer, positive subject/session id, numeric version, issue time, and expiry. Parser details never become public errors or logged exceptions.
- Every authenticated access checks the current PostgreSQL user's status, deletion flag, and sessionVersion. UserContext.requireUserId fails with an AuthenticationException for missing, anonymous, or unauthenticated principals and never returns null.
- Login normalizes usernames and uses one generic 401/code/message for nonexistent, disabled, missing/blank, malformed-length, and incorrect credentials. An absent user still takes BCrypt verification work.
- Refresh rotation locks the user before the durable refresh row, verifies digest, status, version, expiry and revocation, revokes the old row and creates the replacement in one transaction. Concurrent replay yields exactly one replacement and one 401. Logout idempotently revokes the supplied refresh session. Password changes lock the user, verify the current password, increment sessionVersion, and revoke all refresh sessions atomically; user identity is always taken from UserContext.
- Recovery information ignores account lookup input and returns a stable configured human support channel. Added SC_RECOVERY_SUPPORT_CHANNEL without a production default/contact; test profile supplies test-only-support-channel. No email verification or reset endpoint was added.
- Spring Security's AuthenticationEntryPoint and AccessDeniedHandler emit Task 2's Result envelope and correlated trace. Management endpoints other than public health require ADMIN, providing an actual filter-chain forbidden boundary.

## TDD evidence

User approval for backend tests was inherited explicitly in this task assignment. No H2, Testcontainers, new containers, or new middleware was used.

Initial RED, completed 2026-10-22 23:00:35 +08:00:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=AuthApiTest test
```

20 executions, 18 assertion failures, 1 error, 0 skipped; BUILD FAILURE. The stubs accepted over-72-byte/invalid-length passwords, returned no registration tokens, lacked account/session behavior, and lacked requireUserId (the reflection-based contract test reported NoSuchMethodException). A bounded payload-free initial RED summary is in task-3-red-summary.log. MockMvc diagnostic printing is now disabled to prevent credential/token payload diagnostics.

First GREEN, 23:07:08: 20 tests, 0 failures, 0 errors, BUILD SUCCESS.

Self-review regression RED, 23:09:16: 25 executions, 2 failures, 0 errors. Trimmed 50-character usernames failed raw-size validation (200 expected, 400 actual), and missing/blank login credentials bypassed the generic credential failure (401 expected, 400 actual). Both were corrected after this RED. See task-3-review-red.log.

Final focused GREEN, completed 23:11:18:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=AuthApiTest test
```

25 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. See task-3-green-final.log.

Full current backend suite, run once after focused GREEN, completed 23:11:57:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test test
```

136 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS in 35.474 seconds. AuthApiTest 25; ErrorContractTest 56; TestDatabaseSafetyTest 44; MigrationSmokeTest 1; SchemaConstraintsIntegrationTest 10. See task-3-full.log. Expected diagnostics include the deliberately injected registration rollback, unsafe-datasource startup rejections, and the pre-existing JVM class-sharing warning.

## DB and Redis usage

- Reused only procurement-stack-postgres-1, PostgreSQL 16.15, database study_copilot_test, and dedicated role study_copilot_test_runner. The explicitly authorized transient role password was supplied through Docker psql stdin and Maven's process environment, never printed or persisted. No unrelated role, database, container, deployment .env, or schema was changed.
- The rollback test temporarily installs a request-scoped trigger/function in the isolated test schema, forces failure on the final idempotency insert, and drops both in finally. Fixture cleanup targets only auth_t3_ users and their owned rows. No global table truncate, migration clean, or Redis flush occurs.
- Redis is not a correctness dependency for this task and was not contacted or changed. Existing test Redis and cache prefixes remain study-copilot:test: and study-copilot:test:cache:. Rate limiting remains the later operational-hardening task; no Redis-only security boundary was introduced.
- V1/V2 are unchanged and Flyway validated existing schema version 2. All required Task 3 invariants fit the approved V2; no migration flaw needed escalation.

## Files changed

Modified:

- backend/src/main/java/com/example/studycopilot/common/errorcode/ScErrorCode.java
- backend/src/main/java/com/example/studycopilot/config/SecurityConfig.java
- backend/src/main/java/com/example/studycopilot/controller/user/AuthController.java
- backend/src/main/java/com/example/studycopilot/dao/entity/user/UserDO.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/LoginRequest.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/RegisterRequest.java
- backend/src/main/java/com/example/studycopilot/dto/resp/auth/LoginResponse.java
- backend/src/main/java/com/example/studycopilot/security/JwtAuthenticationFilter.java
- backend/src/main/java/com/example/studycopilot/security/JwtTokenProvider.java
- backend/src/main/java/com/example/studycopilot/security/UserContext.java
- backend/src/main/java/com/example/studycopilot/service/user/AuthService.java
- backend/src/main/java/com/example/studycopilot/service/user/impl/AuthServiceImpl.java
- backend/src/main/resources/application.yml
- backend/src/test/resources/application-test.yml
- docs/backend-local-development.md

Created:

- backend/src/main/java/com/example/studycopilot/controller/account/AccountController.java
- backend/src/main/java/com/example/studycopilot/dao/entity/user/RefreshSessionDO.java
- backend/src/main/java/com/example/studycopilot/dao/mapper/user/RefreshSessionMapper.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/ChangePasswordRequest.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/LogoutRequest.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/RefreshTokenRequest.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/ValidPassword.java
- backend/src/main/java/com/example/studycopilot/dto/req/auth/ValidUsername.java
- backend/src/main/java/com/example/studycopilot/security/SecurityErrorHandler.java
- backend/src/main/java/com/example/studycopilot/service/account/AccountService.java
- backend/src/test/java/com/example/studycopilot/api/AuthApiTest.java

This report and bounded execution logs reside in the existing ignored .superpowers orchestration area, following Tasks 1–2's convention.

## Self-review and carry-forward

- Read the approved auth/account/schema brief and spec, reviewed transaction/lock ordering, SQL uniqueness branches, token parsing, error sanitization, session revocation and user isolation. Explicit JDBC statements make PostgreSQL lock and ON CONFLICT behavior visible; the requested refresh entity/mapper are also present for later modules.
- All concurrency checks use two real simultaneous HTTP calls against committed PostgreSQL transactions. No auth, password, token, database, or security collaborator is mocked. Tests use literal HTTP/error/claim/default expectations and an independently calculated SHA-256 digest.
- Mutation review covers removing normalization, byte/character checks, dependency inserts, idempotency fingerprint checks, token-type/issuer/version checks, revocation/expiry/hash checks, user locks, global password revocation, and trace/envelope wiring.
- git diff --check and staged whitespace checks passed. Tracked worktree status is clean after commit. Frontend and migrations have no diff. B/C controller profile gating remains untouched. Account deletion is intentionally left to Task 12.
- No blocking concern remains. Production must supply SC_RECOVERY_SUPPORT_CHANNEL. Existing access tokens remain usable after logout until expiry; logout's requested boundary is refresh-session revocation. Password change immediately invalidates access tokens via sessionVersion.
- Replaying registration returns the original pair, including original expiry/revocation state, and never mints fresh credentials. Signing-key/issuer rotation invalidates original credentials and replay reconstruction; recovery after expiry/revocation uses login. No new auth-specific rate limiter is claimed here.

## Review round 1 fix — completed 2026-10-23

Status: IMPLEMENTED; ready for scoped re-review. Base: `a1a0da2765f26d32727fe16b132d6d21779b1584`. Fix commit: `92da68988eeddaa5133986eb3ed444b6c83fe74d fix: harden registration idempotency and replay validation`. Tracked worktree is clean after commit; report and logs remain in the existing ignored orchestration area.

The interrupted implementer's six-file change was preserved and critically inspected before resuming. No additional production changes were needed after inspection.

- REGISTER now takes a PostgreSQL transaction advisory lock on the global request ID before the normalized-username lock or any username lookup. It reads REGISTER records globally by request ID before resolving a user. Sequential or simultaneous reuse with a different username returns IDEMPOTENCY_CONFLICT and creates only one account. Existing-username conflicts under a different request ID retain BUSINESS_ERROR. Lock order is request ID → username → user → refresh session, with separate advisory namespaces.
- Payload fingerprints use dedicated `SC_IDEMPOTENCY_HMAC_SECRET`, independently configured from JWT signing material. Startup rejects a missing/blank or under-32-byte key with a fixed diagnostic that does not include the supplied value. Runtime documentation requires keeping it stable across ordinary JWT key/issuer rotation.
- Persisted replay metadata now includes metadata version 1, original issuer and SHA-256 signing-key fingerprint. Replay compares the stable request fingerprint, validates supported metadata/configuration, active account and session version, original access/refresh expiry, durable session ownership/version/expiry/revocation and reconstructed refresh-token digest before returning credentials. Invalid originals return TOKEN_REVOKED (`SC_002008`) without creating a session. Valid same-payload replays preserve the exact original token strings and response data. Legacy metadata is rejected and requires login.
- Auth fixtures register each exact normalized username they own and clean up only those usernames and their owned rows. No LIKE wildcard cleanup remains. The regression fixture confirms a separate wildcard-matching username survives cleanup.
- V2, frontend, B/C profile boundaries and unrelated files have no changes. The global REGISTER contract is application-enforced over the approved V2 user-scoped idempotency table; every registration writer must follow this advisory-lock protocol. No migration was added.

### RED/GREEN evidence

Backend test authorization was explicitly inherited from the parent task. No additional unapproved tests were run.

- Review regression RED, completed **2026-10-22 23:22:02 +08:00**: 37 tests, **12 failures**, 0 errors, 0 skipped; BUILD FAILURE. `task-3-fix-1-red.log` captures cross-username request-ID reuse, issuer/key rotation, invalid original sessions and unsafe fixture cleanup failures.
- Earlier focused GREEN, completed **2026-10-22 23:25:13 +08:00**: 37 tests, 0 failures/errors/skips; BUILD SUCCESS (`task-3-fix-1-green.log`). The test source changed afterward, and `task-3-fix-1-green-final.log` stops before a final result; those partial logs were not accepted as final-code verification.
- Final-code focused GREEN, completed **2026-10-23 14:58:33 +08:00**: `mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=AuthApiTest test`; **40 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS. Evidence: `task-3-fix-1-green-resumed.log`.
- Full current backend suite, run **once** immediately after that focused GREEN on the same six-file code, completed **2026-10-23 14:59:14 +08:00**: `mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test test`; **151 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS in 38.836 seconds. AuthApiTest 40; ErrorContractTest 56; TestDatabaseSafetyTest 44; MigrationSmokeTest 1; SchemaConstraintsIntegrationTest 10. Evidence: `task-3-fix-1-full.log`.

Final checks cover sequential/concurrent changed usernames, issuer/key drift without false payload conflicts, valid byte-identical token replay, revoked/expired/version-stale/disabled/deleted/hash-mismatched/missing original sessions, unsupported/legacy metadata, original access expiry and exact cleanup ownership. Expected diagnostics remain the injected transaction rollback, deliberately unsafe-datasource startup failures and the existing JVM class-sharing warning.

Only existing `procurement-stack-postgres-1`, isolated database `study_copilot_test` and dedicated role `study_copilot_test_runner` were used. A transient password was generated, supplied to ALTER only that role through Docker stdin and to Maven through process environment, then cleared from the shell environment; it was never echoed or persisted. The container's admin username alone was read to connect; deployment .env files were not read. No Redis or unrelated database/container was modified. Flyway validated the unchanged schema at version 2. `git diff --check` passed before the fix commit.
