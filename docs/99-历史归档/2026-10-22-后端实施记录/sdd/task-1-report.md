# Task 1 implementation report

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

Status: DONE

Commit: `8a1cf66 build: establish phase A backend foundation`

Worktree: `E:\a-ProjectNew\my_codingrepo\study_copilot\.worktrees\phase-a-backend`

Branch: `feature/phase-a-backend`

## Implementation summary

- Preserved the existing `.gitignore`, including `.worktrees/`; it already contains every ignore required by Step 1.
- Established Java 17 / Spring Boot 3.3.4 with BOM-managed Flyway core and PostgreSQL module 10.10.0. Kept MyBatis-Plus 3.5.5 and the existing PostgreSQL runtime dependency. Added Spring Security test, MinIO 8.5.17, JUnit-platform-capable Surefire 3.2.5, and UTF-8 resource encoding.
- Removed runtime credential fallbacks. Database, Redis, JWT signing, and avatar-storage credentials use environment variables. Added separate normal/test Redis and Spring Cache namespaces.
- Added a test-profile guard before datasource/Flyway instantiation. It requires exactly the PostgreSQL database `study_copilot_test`, checks Hikari/Flyway URL overrides, and rejects query-parameter tricks, including percent-encoded `PGDBNAME`. Diagnostics do not print URLs or credentials.
- Added immutable-history-preserving V2: users/settings/subjects/tasks/pets are extended, and all 12 Phase A tables are created: refresh sessions, learning-day rules, onboarding progress, import batches, repeat templates, schedule logs, focus sessions, focus slices, idempotency records, reward ledger, result presentation, and account deletion jobs.
- Installed `btree_gist` in the application schema. Added the exact required `uk_focus_user_running` partial unique index, stored half-open effective ranges with a GiST exclusion constraint, ownership foreign keys, user-first list indexes, due-work indexes, and the required request/template/day uniqueness constraints. Idempotency retention is at least 24 hours.
- Added a real PostgreSQL migration smoke test, 10 schema behavior tests, and 12 startup-safety cases. Constraint tests run in rolled-back transactions and use literal/hand-checked expected outcomes.
- Added local-development documentation with existing-compose-only service commands, interactive role/database provisioning, environment names, isolated test commands, and schema/boundary contracts. No procurement `.env` was read or copied.
- Repaired the pre-existing `JournalController` missing `PathVariable` import. Per the parent agent's explicit ruling, mechanically gated all eight named B/C controllers behind `future-features`. Implemented no B/C behavior.
- Per the parent agent's explicit ruling, converted SecurityConfig to the equivalent lambda DSL, retaining disabled CSRF, stateless sessions, the same permitted routes, and the same JWT filter ordering.

## Plan reconciliations

1. The brief's `(user_id, normalized_username)` uniqueness wording is inconsistent with global login-name uniqueness because `t_user` has an already-unique `id` and no owner `user_id`. The parent agent ruled that `t_user.normalized_username` must instead be globally unique. V2 enforces that, plus normalization consistency with the trimmed/lowercase username.
2. The initial Boot 3.0.7/Flyway 9.5.1 baseline can migrate PostgreSQL 16 but warns that only PostgreSQL 15 is supported. Overriding only Flyway to 9.22.3 breaks Boot 3.0.7's direct `oracleSqlplus` API call. Boot 3.2.6 aligns with Flyway 9.22.3 but still produces the PostgreSQL 16 support warning. After local BOM/bytecode inspection and the parent's ruling, the final runtime is Boot 3.3.4 with its managed Flyway 10.10.0 plus `flyway-database-postgresql`. No standalone Flyway version override or custom migration bootstrap remains.
3. All eight B/C controller profile gates were brought forward from Task 12 to satisfy the safe-default ruling now; Task 12 retains its exhaustive route audit and acceptance tests.

## Infrastructure used

- Reused `procurement-stack-postgres-1`, the PostgreSQL 16.15 service from the authorized existing compose deployment, published on localhost port 5433.
- Created only database `study_copilot_test` and its dedicated non-superuser login role `study_copilot_test_runner`. Each database-test invocation generated a fresh transient password in its process environment and supplied it to that role without echoing it or writing it to repository files.
- `SC_TEST_DB_URL`, `SC_TEST_DB_USER`, and `SC_TEST_DB_PASSWORD` were set only for each invocation. Connection URLs included the exact isolated database and `currentSchema=study_copilot`.
- No unrelated database, role, container, or compose configuration was changed. Redis was already running and was not mutated; neither Redis nor MinIO is needed by these migration/schema tests, so no MinIO service or bucket was started/created.
- Docker inspection and the authorized test/provisioning commands required sandbox escalation, which succeeded. No automatic-approval rejection remains.

## RED evidence

The migration smoke test was written before V2. After repairing the known baseline compile import and correcting a test-YAML quoting mistake, ran:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=MigrationSmokeTest test
```

At 2026-10-22 21:53:11 +08:00, PostgreSQL was connected and V1 had migrated successfully. The genuine feature RED was:

```text
MigrationSmokeTest.phaseASchemaExists
expected: 3
 but was: 0
Tests run: 1, Failures: 1, Errors: 0, Skipped: 0
BUILD FAILURE
```

Reason: the required V2 focus session, slice, and reward tables did not yet exist. This was an assertion failure against the real database, not a mocked result or a startup error.

The safety test was also written before its guard implementation:

```powershell
mvn -B -ntp -f backend/pom.xml -Dtest=TestDatabaseSafetyTest test
```

At 21:56:51, it reported 8 tests, 6 failures, 0 errors: unsafe database contexts started successfully instead of being refused. A later test-first refinement at 22:03:58 reported 12 tests, 4 failures, 0 errors, exposing Hikari/Flyway override and encoded query-name bypasses. The final guard fixes those cases.

## GREEN evidence

Final-runtime focused command:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=MigrationSmokeTest test
```

Completed at 2026-10-22 22:06:49 +08:00:

```text
Database: jdbc:postgresql://127.0.0.1:5433/study_copilot_test (PostgreSQL 16.15)
Successfully validated 3 migrations
Current version of schema "study_copilot": 2
Schema "study_copilot" is up to date. No migration necessary.
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

After the mechanical SecurityConfig lambda change, ran the complete task suite once:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test test
```

Completed at 2026-10-22 22:09:25 +08:00 in 45.209 seconds:

```text
TestDatabaseSafetyTest: 12 tests, 0 failures, 0 errors, 0 skipped
MigrationSmokeTest: 1 test, 0 failures, 0 errors, 0 skipped
SchemaConstraintsIntegrationTest: 10 tests, 0 failures, 0 errors, 0 skipped
Tests run: 23, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

No unsupported-PostgreSQL, Flyway API, or compiler-deprecation warnings remain in the final suite. Unsafe-context test cases intentionally emit rejection WARN logs, and the test JVM emits the standard class-data-sharing warning after Mockito instrumentation; neither is a test failure.

## All test attempts

| Attempt | Result | Explanation |
| --- | --- | --- |
| Initial migration setup run, 21:52:20 | 1 error | Unquoted trailing-colon test Redis prefix was invalid YAML; corrected before recording genuine RED. |
| Migration smoke RED, 21:53:11 | 1 assertion failure | Real V1 database contained zero of the three required V2 tables. |
| Initial safety RED, 21:56:51 | 8 tests / 6 failures | No guard existed, so unsafe URLs were accepted. |
| First GREEN attempt, 22:00:12 | 1 startup error | Boot 3.0.7 and Flyway 9.22.3 API mismatch. Resolved through a managed runtime alignment. |
| Safety bypass refinement RED, 22:03:58 | 12 tests / 4 failures | URL overrides and encoded PGDBNAME were not yet rejected. |
| Intermediate smoke, 22:04:14 | 1 passed | V2 applied successfully; remaining PostgreSQL-support warning led to final runtime alignment. |
| Final-runtime focused smoke, 22:06:49 | 1 passed | Boot 3.3.4/Flyway 10.10.0 PostgreSQL module, no unsupported database warning. |
| Final task suite, 22:09:25 | 23 passed | All safety, smoke, and schema behavior tests passed. |

## Schema behavior verified

- All 12 new Phase A tables exist.
- Trimmed/case-normalized usernames cannot be duplicated across accounts.
- Focus and break sessions share a single RUNNING slot even for disjoint intervals.
- Adjacent half-open intervals are accepted, while overlapping TIMER/MANUAL intervals are rejected.
- Running focus sessions reserve their planned interval against manual inserts.
- Soft-deleted manual intervals can be reused.
- Idempotency keys are unique per owner and operation, while other owners/operations can reuse a request ID.
- Repeat instances cannot duplicate an owner/template/learning-day tuple.
- A focus session cannot reference another user's subject.
- A session/day reward cannot be awarded twice.

## Files changed

1. `backend/pom.xml`
2. `backend/src/main/java/com/example/studycopilot/config/SecurityConfig.java`
3. `backend/src/main/java/com/example/studycopilot/config/TestDatabaseSafetyConfiguration.java`
4. `backend/src/main/java/com/example/studycopilot/controller/chat/ChatController.java`
5. `backend/src/main/java/com/example/studycopilot/controller/journal/JournalController.java`
6. `backend/src/main/java/com/example/studycopilot/controller/knowledge/KnowledgePointController.java`
7. `backend/src/main/java/com/example/studycopilot/controller/milestone/MilestoneController.java`
8. `backend/src/main/java/com/example/studycopilot/controller/mistake/MistakeController.java`
9. `backend/src/main/java/com/example/studycopilot/controller/notification/NotificationController.java`
10. `backend/src/main/java/com/example/studycopilot/controller/reminder/ReminderController.java`
11. `backend/src/main/java/com/example/studycopilot/controller/stat/StatController.java`
12. `backend/src/main/resources/application.yml`
13. `backend/src/main/resources/db/migration/V2__phase_a_core.sql`
14. `backend/src/test/java/com/example/studycopilot/config/TestDatabaseSafetyTest.java`
15. `backend/src/test/java/com/example/studycopilot/integration/MigrationSmokeTest.java`
16. `backend/src/test/java/com/example/studycopilot/integration/SchemaConstraintsIntegrationTest.java`
17. `backend/src/test/resources/application-test.yml`
18. `docs/backend-local-development.md`

This report is a local orchestration artifact under the repository's already-ignored `.superpowers` area. `.gitignore` and V1 were inspected but did not need edits.

## Self-review

- Reviewed the staged SQL, configuration, dependency alignment, tests, docs, and mechanical controller/security changes against every Task 1 step.
- `git diff --cached --check` passed before commit. `git status --short` was clean after commit.
- Confirmed V1 has no diff, no frontend file changed, and no new middleware definition was added.
- Verified existing ignores cover worktrees, `.env`, backend build output, frontend dependencies/build output, and logs. The tracked-file artifact/secret-name scan returned no matches.
- The real application context compiles and initializes MyBatis-Plus mapper infrastructure, Jakarta application components, Spring Security, and Flyway together under the final managed stack.
- Test expectations use independent table names/counts, literal timestamps, and observable accepted/rejected writes. They do not compare production helpers with themselves or assert mock calls.
- No blocking self-review findings remain.

## Concerns and carry-forward

- No blocker remains for Task 1.
- V2 intentionally fails on pre-existing username normalization collisions or orphaned ownership references. Audit any populated deployment before migration; tests covered the repository's clean V1 baseline.
- Legacy V1 `t_pet_status.level` remains for compatibility but is explicitly nonauthoritative; Task 9 must derive levels from `total_exp`. `total_exp` is now PostgreSQL bigint, so the eventual growth entity should use a Java Long.
- Daily reward sum locking, settlement/slicing logic, account cleanup, avatar operations, and the exhaustive HTTP route boundary audit belong to their later approved tasks. The schema enables them but this report does not claim those workflows are implemented.
- Subsequent schema changes should use new Flyway migrations; V1 and committed V2 must stay immutable.

## Fix round 1

Status: DONE. The original Task 1 implementation and V2 are preserved; this is a separate startup-safety correction.

Commit: `c1ab420b194262410163401847eb168b118576dd fix: fail closed on test datasource selection overrides`. Staged whitespace check passed and tracked worktree status was clean after commit.

### Review finding

> [P1] Guard validated JDBC URLs but allowed alternative datasource/JNDI selection, including a safe main URL plus blank Hikari jdbc-url and data-source-jndi. Must reject unsupported datasource/JNDI/data-source-class/property families and explicitly blank URL overrides in test profile, without leaking supplied values.

### Inherited RED evidence

The previous implementer wrote the regression cases before its partial implementation. At 2026-10-22 22:20:57 +08:00, `mvn -B -ntp -f backend/pom.xml -Dtest=TestDatabaseSafetyTest test` ran 28 tests with 14 assertion failures and 0 errors. Real `DataSourceAutoConfiguration` accepted a safe main URL plus whitespace `spring.datasource.hikari.jdbc-url` and `spring.datasource.hikari.data-source-jndi`, without creating a connection or performing a JNDI lookup. The failures exposed Spring datasource JNDI/type/driver selection, Hikari DataSource/JNDI/class/driver/data-source-properties, XA class/properties, and blank URL overrides.

I resumed the two uncommitted files rather than discarding that work. Its inherited implementation first passed all 28 cases at 22:25:05. Additional self-review identified two related binding gaps: non-enumerable sources expose XA JavaBean leaves even though descendant discovery returns UNKNOWN, and Flyway has its own driver and JDBC-property overrides. Added regression cases first; the focused command at 22:26:32 ran 44 tests with 6 assertion failures and 0 errors, covering those remaining gaps.

### Changes

- Resolve URLs and supported scalar options through Spring Boot `Binder`, matching relaxed aliases rather than only exact environment keys. Explicit empty and whitespace Hikari/Flyway URLs are rejected instead of treated as absent.
- Reject the presence of unsupported Spring JNDI/XA and Hikari DataSource/JNDI/class/property families, including direct values, dotted/bracketed descendants, camel-case names, and environment variable aliases. Query XA's known class/properties leaves directly so non-enumerable sources cannot bypass the family check.
- Restrict explicit datasource type to `com.zaxxer.hikari.HikariDataSource` and all explicit Spring/Hikari/Flyway drivers to `org.postgresql.Driver`. Reject Flyway JDBC-property families because those can override connection behavior independently of the checked URL.
- Replace binding/validation failures with one fixed diagnostic, retaining neither untrusted supplied values nor their exception causes.
- Keep safe explicit URLs, supported driver/type values, ordinary pool tuning, and non-test profiles working. Real datasource autoconfiguration is exercised for both the original bypass rejection and accepted safe pool settings.

### GREEN commands and results

Focused command:

```powershell
mvn -B -ntp -f backend/pom.xml -Dtest=TestDatabaseSafetyTest test
```

Completed at 2026-10-22 22:27:20 +08:00: 44 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.

Complete Task 1 suite:

```powershell
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test test
```

Completed at 2026-10-22 22:28:12 +08:00: TestDatabaseSafetyTest 44 passed, MigrationSmokeTest 1 passed, SchemaConstraintsIntegrationTest 10 passed; total 55 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS in 25.471 seconds. Flyway connected to PostgreSQL 16.15 and validated the existing schema at V2 without modifying migrations.

The suite reused only `procurement-stack-postgres-1`, `study_copilot_test`, and its dedicated `study_copilot_test_runner` role. A cryptographically random transient password was supplied through stdin for that role and then through the Maven process environment; it was neither printed nor written to files. No procurement `.env` was read, no unrelated role/database/container was altered, and no migration clean was performed. Docker access required sandbox escalation, which succeeded. Expected unsafe-context rejection warnings and the existing Mockito/JVM class-data-sharing warning remain harmless.

### Files changed

1. `backend/src/main/java/com/example/studycopilot/config/TestDatabaseSafetyConfiguration.java`
2. `backend/src/test/java/com/example/studycopilot/config/TestDatabaseSafetyTest.java`
3. This existing ignored orchestration report, `.superpowers/sdd/2026-10-22-study-copilot-phase-a-backend/task-1-report.md`.

### Self-review

- Reviewed Spring Boot 3.3.4's local binding/source bytecode, XA properties, Flyway properties, and Hikari 5.1.0 connection-selection API against the guard; no external documentation or secret configuration was needed.
- Verified relaxed aliases and dotted/bracketed descendants, direct family values, canonical environment-variable aliases, empty overrides, non-enumerable XA leaves, and complete diagnostic stack traces. The additional regression RED demonstrated the implementation gaps before they were fixed.
- Unknown descendant state is not blindly treated as present: normal non-enumerable sources may legitimately return UNKNOWN. All known bindable XA leaves are queried explicitly; arbitrary map entries require discoverable keys or a direct map value, both handled by the family checks.
- Family presence is deliberately rejected even in a lower-priority source. Supported scalar values follow the same effective property precedence as downstream binding. Standard pool settings remain accepted, so the guard does not reject the entire Hikari namespace.
- All tests exercise context startup and real binding; no mocked connection or JNDI behavior substitutes for the safety boundary. Unsafe selections fail before datasource/Flyway instantiation, and fixed diagnostics omit supplied markers and binding causes.
- `git diff --check` passed. Only the two safety Java files are tracked changes; V1, V2, frontend, and unrelated backend files have no diff. No blocking concern remains.
