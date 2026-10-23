# Task 4 — Settings, Subjects, Learning-Day Rules

Status: IMPLEMENTED; self-reviewed and ready for scoped review. Base: `92da68988eeddaa5133986eb3ed444b6c83fe74d`. Commit: `11f94fecbb114e3149cfb090c95f3e70ff34fbd5` (`feat: add settings subjects and learning day rules`). Tracked worktree is clean after commit. Backend tests were explicitly authorized by the parent/user. No frontend implementation or UI approval gate was changed.

## Implementation

- Settings now read PostgreSQL defaults and update only the authenticated owner's row. A mandatory optimistic `version` returns Task 2 `VERSION_CONFLICT` with authorized `currentVersion`. Persisted fields include SYSTEM/LIGHT/DARK theme, independent motion/transparency preferences, sound/notification preferences, goal/date, nullable weekly goal, focus duration, available time and owned active default subject. The existing PUT success envelope stays unchanged; GET returns setting/rule versions. Omitted/null fields preserve the current value, except an explicitly null weekly goal clears it.
- Timezone and day-start edits validate `ZoneId` and append a new immutable learning-day rule with increasing version and `effective_at`. Non-rule edits do not create rule versions. Any nondeleted RUNNING focus, short break or long break blocks rule edits. Historical lookup chooses the latest rule effective at or before the supplied instant; existing rows are never rewritten.
- `LearningDayContext resolve(long userId, Instant instant)`, deterministic `resolve(Instant, ZoneId, int, long)` and `List<LearningSlice> split(Instant, Instant, ZoneId, int, long)` are available for settlement/dashboard consumers. Context and slices expose exact start/end instants, learning date and rule version. Context also exposes zone/day-start. Half-open intervals exclude the end boundary. DST gaps resolve forward, overlaps use the earlier offset, and comparison uses instants rather than clock hours.
- Subject list/create/edit/archive/restore are persisted and owner-scoped. Writes require current versions after creation, and responses return the committed new subject version/state. Archive preserves rows, completed/cancelled tasks and historical snapshots. It blocks the last active subject and any subject used by a running focus/break. Unfinished TODO tasks and ACTIVE/PAUSED repeat templates require explicit `MIGRATE` to an owned active subject or `CANCEL`; affected mutable rows increment versions. Archiving a default subject updates settings/default-subject version within the same transaction.
- Per the approved schema ruling, V3 drops V1's all-history `uk_user_name` and creates `uk_subject_active_normalized_name` on `(user_id,lower(btrim(name))) where archived=0`. This permits archived historical duplicates and rejects active normalized duplicates in PostgreSQL. Service prechecks and duplicate-key handling return `SUBJECT_DUPLICATE`. V1 and V2 are unchanged. No subject soft-delete column exists, so no deleted predicate was invented.
- Subject persistence consistently uses JDBC: an initial mixed JDBC/MyBatis implementation exposed transaction-local cached stale responses and was corrected. Settings use their scoped mapper once per transaction and explicit JDBC writes; there is no cached read after write in that path.

## Shared locking contract — required for Tasks 5/6/7/8

Every settings/rule/subject/task/template/import/focus/break mutation must acquire `select id from study_copilot.t_user where id=? for update` at transaction start, before other resource rows. Keep the owner lock through commit. Background writers for a known user follow the same order. Registration retains global request-ID and username advisory locks before the user lock. This protects last-active-subject checks, archive versus new tasks/sessions, rule edits versus session starts, and cross-resource default changes. Row locks only on individual settings/subjects/sessions do not protect these invariants.

The contract is documented prominently in `docs/backend-local-development.md` under “Cross-resource mutation locking contract (Tasks 4–8)”. All HTTP services call `UserContext.requireUserId()` and scope SQL reads/writes to that user. The internal persisted time resolver deliberately accepts a trusted user ID so background settlement/dashboard work need not fabricate an HTTP security context; it never queries rules without `user_id`.

## RED/GREEN commands and actual output

The bounded `task-4-run-tests.ps1` runner in this same ignored orchestration directory invokes:

```powershell
mvn -B -ntp -f backend/pom.xml -Dtest=LearningDayServiceTest test
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=SettingsSubjectApiTest test
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test -Dtest=LearningDayServiceTest,SettingsSubjectApiTest test
mvn -B -ntp -f backend/pom.xml -Dspring.profiles.active=test test
```

- Domain RED (`task-4-time-red.log`, completed 2026-10-23 15:07:42 +08:00): after the initial expected missing-type compilation result, signature stubs permitted execution; **6 tests, 1 failure, 5 errors, 0 skipped**, BUILD FAILURE. Unsupported operations exposed missing resolve/split behavior; the invalid-rule assertion also failed against the stub.
- API RED (`task-4-api-red.log`, 15:08:10): **6 tests, 3 failures, 3 errors, 0 skipped**, BUILD FAILURE. Settings/subject stub responses were empty, archive API behavior was absent, and the database did not reject normalized duplicates.
- First implemented focus run (`task-4-focused-green.log`, 15:13:11): **12 tests, 3 failures, 0 errors**. All 6 domain tests passed. API failures were isolated with `task-4-diagnostic.log` (one test, 15:14:25) to an explicit path-variable-name requirement: compiler parameter metadata is unavailable. Task 4 subject route annotations now specify `id`.
- Expanded focus run (`task-4-focused-2.log`, 15:15:58): **16 tests, 2 failures, 0 errors**. The failures proved update/archive responses returned cached old subject state/version after JDBC writes. The retained regressions assert the actual response `version` and `archived` values.
- Final focused GREEN (`task-4-focused-final.log`, 15:17:48): **16 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 57.151 seconds. SettingsSubjectApiTest 10, LearningDayServiceTest 6.
- Full current suite run **once after the final implementation** (`task-4-full.log`, 15:19:38): **167 tests, 0 failures, 0 errors, 0 skipped**, BUILD SUCCESS, 47.030 seconds. Existing suite 151 plus Task 4's 16 tests. Flyway validates the unchanged V1/V2 and new V3; current schema version 3. No production/test code changed after this run.

Coverage includes Shanghai 04:00 attribution, exact/empty/reversed intervals, New York DST gap and repeated hour, persisted effective-time history, no-op rule changes, independent preferences, invalid settings, nullable weekly goal, stale settings/subject versions, owner isolation, active-name uniqueness in API and direct PostgreSQL writes, archive/restore conflict, last-subject protection, completed-row/snapshot retention, repeat-template migration, explicit task migration/cancellation, running focus/short/long blocks, simultaneous normalized duplicate creates, simultaneous final-subject archives and simultaneous settings versions. Tests use real JWT authentication, services and PostgreSQL rather than mocked security/persistence.

Only existing `procurement-stack-postgres-1`, database `study_copilot_test` and role `study_copilot_test_runner` were used. The runner generates a transient password, sends ALTER ROLE through Docker stdin and supplies Maven only through process environment, then clears the environment. No password is printed or persisted, no deployment `.env` is read, and no middleware/database is created. Fixture cleanup tracks each exact generated normalized username and deletes only that owner's dependencies, in FK-safe order. No wildcard or broad cleanup is used. Expected full-suite diagnostics are the pre-existing injected rollback/unsafe-datasource failures and JVM class-sharing warning; no unexpected Task 4 request failure remains.

## Files

Modified:

- `backend/src/main/java/com/example/studycopilot/controller/subject/SubjectController.java`
- `backend/src/main/java/com/example/studycopilot/dao/entity/setting/UserSettingDO.java`
- `backend/src/main/java/com/example/studycopilot/dao/entity/subject/SubjectDO.java`
- `backend/src/main/java/com/example/studycopilot/dto/req/setting/UpdateSettingRequest.java`
- `backend/src/main/java/com/example/studycopilot/dto/req/subject/CreateSubjectRequest.java`
- `backend/src/main/java/com/example/studycopilot/dto/resp/setting/SettingResponse.java`
- `backend/src/main/java/com/example/studycopilot/dto/resp/subject/SubjectResponse.java`
- `backend/src/main/java/com/example/studycopilot/service/setting/impl/SettingServiceImpl.java`
- `backend/src/main/java/com/example/studycopilot/service/subject/SubjectService.java`
- `backend/src/main/java/com/example/studycopilot/service/subject/impl/SubjectServiceImpl.java`
- `docs/backend-local-development.md`

Created:

- `backend/src/main/java/com/example/studycopilot/dao/entity/setting/LearningDayRuleDO.java`
- `backend/src/main/java/com/example/studycopilot/dao/mapper/setting/LearningDayRuleMapper.java`
- `backend/src/main/java/com/example/studycopilot/domain/time/LearningDayContext.java`
- `backend/src/main/java/com/example/studycopilot/domain/time/LearningDayService.java`
- `backend/src/main/java/com/example/studycopilot/domain/time/LearningSlice.java`
- `backend/src/main/java/com/example/studycopilot/domain/time/TimeSliceService.java`
- `backend/src/main/java/com/example/studycopilot/dto/req/subject/SubjectStateRequest.java`
- `backend/src/main/resources/db/migration/V3__subject_active_name.sql`
- `backend/src/test/java/com/example/studycopilot/api/SettingsSubjectApiTest.java`
- `backend/src/test/java/com/example/studycopilot/domain/time/LearningDayServiceTest.java`

The existing SettingController already delegates its GET/PUT through validation and the common Result envelope; it needed no wrapper-only change. Reports, runner and logs remain in the established ignored `.superpowers` orchestration area.

## Self-review and carry-forward

- Reviewed SQL predicates, authenticated ownership, lock ordering, optimistic versions, name-constraint migration, historical references, archive rollback paths and rule version/effective boundary behavior. `git diff --check` passes. Frontend, V1/V2, authentication and B/C profile boundaries have no diff.
- Mutation review: removing normalization, owner predicates, user locks, version checks, archived checks, task strategies, session checks, rule append, default replacement or response rereads is covered by behavioral assertions. Tests explicitly retain the stale version/archive response regression.
- No architectural blocker remains. V3 intentionally fails rather than modifying legacy duplicate active names; a production migration must explicitly resolve such existing collisions.
- Later writers must implement the shared owner-lock protocol. Time slices preserve exact instants; `seconds()` truncates each duration to whole elapsed seconds. Later settlement should apply its authoritative integer-second allocation consistently when intervals have fractional seconds, rather than discard exact instants or infer fixed 24-hour days.
- Learning-day rules cannot be found before the user's first persisted rule; this correctly produces SETTING_NOT_FOUND rather than inventing historical rules. Deterministic splitting adopts one explicit immutable rule/version for the whole interval; settlement must pass the session's adopted rule.
