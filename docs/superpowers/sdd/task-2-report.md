# Task 2 implementation report

Status: DONE

Commit: `d0b4727d455706b3bbd9fcfb9c696ca6641d9d60 feat: define phase A API error contract`

Subject: `feat: define phase A API error contract`

Base commit: `c1ab420b194262410163401847eb168b118576dd`

Worktree: `E:\a-ProjectNew\my_codingrepo\study_copilot\.worktrees\phase-a-backend`

## Contract and implementation

- `Result<T>` serializes exactly `code`, `message`, `data`, `traceId`, `retryable`, `fieldErrors`, `currentVersion`, and `currentSession`. Null data/conflict metadata remain present, and field errors default to an empty list.
- Existing success/failure factories, chainable setters, and `isSuccess()` remain usable. Deprecated `getRequestId()`/`setRequestId()` alias `traceId`; legacy `requestId` and derived `success` are excluded from JSON. Existing controllers require no mechanical changes.
- `FieldViolation` exposes only field and sanitized message. No rejected values or arbitrary exception/validation messages enter error envelopes.
- `BusinessException` retains both existing constructors and adds `(IErrorCode, Long currentVersion, Object currentSession)` for conflict context. Callers must pass an authorized public DTO as session context, never a persistence entity.
- `ScErrorCode` owns status and retryability. Validation is 400; authentication is 401; forbidden/disabled is 403; missing resources are 404; business conflicts are 409; rate limits are 429; system failures are 500. Existing unsupported-method errors remain 405. Retryability is true for 429 and 500, false for other families, including conflicts requiring an explicit recovery action.
- The global MVC handler returns `ResponseEntity<Result<Void>>`, uses catalogue messages, handles missing routes/static resources, malformed JSON, binding/type/validation errors, Spring authentication/access-denied exceptions, and fallback exceptions. Logging records unexpected exception class only, omitting messages, bodies, SQL, credentials, and stack traces that could include private values.
- Response body advice sets every `Result` trace ID from the request attribute immediately before serialization.
- `TraceIdFilter` runs before security. It accepts exactly one `X-Trace-Id` header matching `[A-Za-z0-9][A-Za-z0-9._-]{0,63}`; absent, repeated, overlong, blank, control-character, Unicode, or other unsafe input becomes a UUID. The selected value is stored in a request attribute, response header, and MDC `traceId`. MDC is removed in `finally`; redispatch can reuse the request ID and rebind MDC.
- No frontend files, profile gates, security configuration, migrations, or future-feature routes changed.

## Numeric assignments

The authoritative brief and design section 9 specify names but no numeric table. The parent explicitly approved the following category-preserving allocation. Existing codes remain unchanged.

| Name | Code | HTTP |
| --- | --- | --- |
| VERSION_CONFLICT | SC_001005 | 409 |
| IDEMPOTENCY_CONFLICT | SC_001006 | 409 |
| RATE_LIMIT | SC_001007 | 429 |
| AUTH_REQUIRED | SC_002006 | 401 |
| TOKEN_REVOKED | SC_002008 | 401 |
| RULE_CHANGE_BLOCKED | SC_003002 | 409 |
| SUBJECT_IN_USE | SC_004003 | 409 |
| LAST_ACTIVE_SUBJECT | SC_004004 | 409 |
| SESSION_EXISTS | SC_007004 | 409 |
| SESSION_NOT_DUE | SC_007005 | 409 |
| TIME_OVERLAP | SC_007006 | 409 |

`AUTH_REQUIRED` is a distinct enum constant with a compatibility code shared with `UNAUTHORIZED`. String-code normalization uses the existing `UNAUTHORIZED` catalogue message for that shared code; both mean authentication is required.

## RED evidence

Wrote real MockMvc validation/success contract tests before implementation. Ran:

```powershell
mvn -f backend/pom.xml '-Dtest=ErrorContractTest' test
```

Completed 2026-10-22 22:33:37 +08:00, exit 1:

```text
ErrorContractTest.successExposesTheRequiredMetadata: No value at JSON path "$.traceId"
ErrorContractTest.validationReturns400AndFieldErrors: No value at JSON path "$.code"
Tests run: 2, Failures: 2, Errors: 0, Skipped: 0
BUILD FAILURE
```

Success lacked trace metadata, and the existing package-scoped advice did not envelop validation failures outside the legacy controller package. These were behavioral assertion failures, not compilation/startup errors. Expanded the remaining status/trace/context/sanitization cases before applying implementation.

## GREEN evidence

Initial implementation command:

```powershell
mvn -f backend/pom.xml '-Dtest=ErrorContractTest' test
```

Completed 22:38:38 +08:00: 50 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.

Added an interpolated-validation-message sanitization case and strengthened assertions that MDC is visible inside the error probe before it throws. No behavior changes were needed. Final focused command:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dtest=ErrorContractTest' test
```

Completed 22:40:41 +08:00:

```text
Tests run: 51, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Then ran the entire current backend suite once, with approved isolated test environment variables:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dspring.profiles.active=test' test
```

Completed 22:41:36 +08:00, exit 0, 30.631 seconds:

```text
ErrorContractTest: 51 tests, 0 failures, 0 errors, 0 skipped
TestDatabaseSafetyTest: 44 tests, 0 failures, 0 errors, 0 skipped
MigrationSmokeTest: 1 test, 0 failures, 0 errors, 0 skipped
SchemaConstraintsIntegrationTest: 10 tests, 0 failures, 0 errors, 0 skipped
Database: jdbc:postgresql://127.0.0.1:5433/study_copilot_test (PostgreSQL 16.15)
Successfully validated 3 migrations
Current version of schema "study_copilot": 2
Schema "study_copilot" is up to date. No migration necessary.
Tests run: 106, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Only the existing `procurement-stack-postgres-1` container and `study_copilot_test` database were used. Per the parent's explicit authorization, generated a cryptographically random transient password, changed only the dedicated `study_copilot_test_runner` role via container stdin, and supplied `SC_TEST_DB_URL`, `SC_TEST_DB_USER`, and `SC_TEST_DB_PASSWORD` in the Maven process environment. Passwords were neither printed nor saved. Environment variables were removed in `finally`. The sandbox's initial Docker read was denied; the authorized escalation succeeded with no automatic-review rejection. No unrelated database, role, container, or configuration was modified.

The expected unsafe-context rejection warnings, one deliberate fallback-error log, and existing Mockito/JVM class-data-sharing warning do not represent test failures.

## Files changed

1. `backend/src/main/java/com/example/studycopilot/common/result/Result.java`
2. `backend/src/main/java/com/example/studycopilot/common/result/FieldViolation.java`
3. `backend/src/main/java/com/example/studycopilot/common/errorcode/ScErrorCode.java`
4. `backend/src/main/java/com/example/studycopilot/common/exception/BusinessException.java`
5. `backend/src/main/java/com/example/studycopilot/common/global/GlobalExceptionHandler.java`
6. `backend/src/main/java/com/example/studycopilot/support/trace/TraceIdFilter.java`
7. `backend/src/test/java/com/example/studycopilot/api/ErrorContractTest.java`
8. This ignored orchestration report.

## Self-review

- Reviewed the entire production diff and new files against the brief and parent contract. Seven tracked files are confined to backend response/error/trace infrastructure and its behavioral test.
- Verified exact JSON keys, including null context; all required HTTP families and every new domain code; header/body equality; absent/repeated/safe/unsafe/64-character/65-character trace cases; UUID uniqueness across requests; MDC visibility in successful and failing controllers and cleanup after both; version/session conflict context; real malformed JSON/type/missing-parameter handling; missing route and wrong method; rejected/interpolated validation-value and fallback exception sanitization.
- Tests use the real MockMvc dispatcher, filter, global handler, Bean Validation and Jackson serialization with a test-only throwing controller. No source-inspection tests or mocked behaviors are used. Literal expected codes/statuses are independent of enum status helpers.
- Existing controllers compile without changes, and the full application initializes against the existing PostgreSQL schema.
- `git diff --check` and `git diff --cached --check` passed. The seven-file commit succeeded, and post-commit `git status --short` was clean. Git staging/commit required sandbox escalation because the worktree index resides under the parent repository's protected `.git`; escalation succeeded with no automatic-review rejection. No blocking self-review finding remains.

## Concerns and carry-forward

- No blocker remains for Task 2.
- Per parent scope ruling, Task 3 owns pre-controller Spring Security behavior. Its authentication entry point and access-denied handler must serialize this `Result` contract; MDC is already available because the trace filter runs first. This task tests Spring authentication/access-denied exceptions reaching MVC and does not claim existing security filter rejection bodies have been replaced.
- New session conflict callers should use the new `BusinessException` context constructor with an authorized public DTO. Error sanitization does not grant permission to expose another user's conflict context.
- Broad MVC advice is necessary to envelope framework 404/405 errors as well as module-controller exceptions; it does not alter controller registration or profile boundaries.

## Fix round 1

Status: DONE. The original Task 2 commit is preserved; this is a separate diagnostics and coverage correction.

Subject: `fix: add safe correlated API failure diagnostics`

Commit: `ba3aee63c4321d9b0cc6c09d74ebf1a63108940a fix: add safe correlated API failure diagnostics`

### Review findings and changes

- **P1:** The original fallback log recorded only the exception type. It now includes an explicit validated `traceId`, a bounded exception type, and up to five distinct application class/method/line origins. Frames must have the exact `com.example.studycopilot.` package prefix, ASCII class-name tokens of at most 120 characters and method-name tokens of at most 60 characters. Malformed, injected, overlong and nonapplication frames are excluded. Filenames and filesystem paths are never formatted; no exception object, cause, message or raw stack trace is passed to the logger. Missing safe metadata uses a fixed placeholder. With bounded trace/type/frame components, the diagnostic stays below 1,200 characters.
- **M1:** Added real-filter behavioral cases for ASYNC and ERROR redispatch. They verify the request's original trace survives a changed header, MDC is rebound on redispatch, downstream `ServletException` still clears MDC, the response retains the original trace header, and a subsequent request receives its own binding. These cases passed with the existing filter; no production filter changes were necessary. Tests invoke the actual filter using servlet request/response fixtures and do not claim to test container dispatch registration.
- **M2:** Documented `AUTH_REQUIRED` as the intentional `SC_002006` alias beside `UNAUTHORIZED`. `fromCode` now explicitly canonicalizes that code to `UNAUTHORIZED` before iteration, so its public message no longer depends on enum declaration order. Added an HTTP regression case verifying both names produce the canonical authentication code/message. Existing external behavior is preserved.

### RED evidence

Wrote logger-capture, redispatch and alias tests before production changes. Ran:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dtest=ErrorContractTest' test
```

Completed 2026-10-22 22:50:28 +08:00, exit 1:

```text
unexpectedFailureLogContainsTraceAndApplicationOriginWithoutPrivateData:
  actual "Unhandled request failure: IllegalStateException"
  missing diagnostic-trace and ErrorContractTest$ErrorProbeController.exception
diagnosticFramesAreApplicationOnlySanitizedAndBounded:
  actual "Unhandled request failure: IllegalStateException"
  missing bounded-trace and com.example.studycopilot.service.FocusService.settle:73
Tests run: 56, Failures: 2, Errors: 0, Skipped: 0
BUILD FAILURE
```

These are genuine assertions against emitted Logback events from real MockMvc-handled failures. The newly added redispatch and alias regression cases already passed, so only P1 required a behavior correction; M2 is a behavior-preserving deterministic refactor.

### GREEN evidence

Ran the same focused command after implementation. Completed 22:52:01 +08:00:

```text
Tests run: 56, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Captured diagnostic includes `traceId=bounded-trace`, `type=IllegalStateException`, and the allowed `FocusService.settle:73` origin, followed by only four additional safe application frames. Tests exclude framework/lookalike-package frames, injected names, filenames, private values, newlines, raw throwable proxies, and excess frames. The assertions check useful components and bounds rather than an entire exact log line.

Ran the full current backend suite once:

```powershell
mvn -B -ntp -f backend/pom.xml '-Dspring.profiles.active=test' test
```

Completed 22:52:52 +08:00, exit 0, 24.047 seconds:

```text
ErrorContractTest: 56 passed
TestDatabaseSafetyTest: 44 passed
MigrationSmokeTest: 1 passed
SchemaConstraintsIntegrationTest: 10 passed
Tests run: 111, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Reused only the existing PostgreSQL container and isolated `study_copilot_test` database at schema V2. Renewed only the dedicated `study_copilot_test_runner` password through container stdin using a cryptographically random transient value, supplied process-only `SC_TEST_DB_*` variables, and removed those variables in `finally`. No credentials were printed or saved; no unrelated state changed. Authorized sandbox escalation succeeded.

### Files changed and self-review

1. `backend/src/main/java/com/example/studycopilot/common/global/GlobalExceptionHandler.java`
2. `backend/src/main/java/com/example/studycopilot/common/errorcode/ScErrorCode.java`
3. `backend/src/test/java/com/example/studycopilot/api/ErrorContractTest.java`
4. This ignored report.

- Reviewed all three tracked-file diffs. No controller, security, frontend, migration, profile-boundary or filter-registration changes occurred.
- All logging arguments are sanitized strings. No raw `Throwable` reaches Logback; captured events assert `getThrowableProxy()` is null. Package prefix and token-length checks occur before origin formatting. The five-frame cap and fixed component bounds prevent unbounded output.
- Test fixtures deliberately include filesystem filenames, framework frames, a neighboring package prefix, malicious newline names, a very long class name and more than 100 application frames; only a bounded useful subset is emitted.
- The filter tests exercise actual MDC lifecycle side effects, including failed redispatch and subsequent request rebinding, without mocks. The existing filter passed these cases before the diagnostics fix.
- Alias normalization is explicit and preserves the original numeric code/status/public message; enum declaration order is no longer consulted for the shared code.
- `git diff --check` and `git diff --cached --check` passed. The separate fix commit succeeded, and post-commit `git status --short` was clean. No blocking concerns remain. The original Task 3 security-entry-point handoff is unchanged.
