# Handover — Study Copilot Phase A backend

Written 2026-10-23, at the request of the user, so that a fresh agent can pick the work up without reading the whole history. Everything below was checked against the working tree, not recalled.

## TL;DR

The Phase A backend demo is implemented and now compiles and passes its test suite. Tasks 1–5 of the plan are implemented, reviewed, and covered by tests. Tasks 6–11 are implemented but have **no test coverage at all** and have never been exercised against a running application. Tasks 12 and 13 are not started. The frontend does not exist.

The single most important fact for whoever picks this up: **the green test run does not mean the product works.** The 185 tests cover the Task 1–5 foundation only. The task, focus, repeat-template, pet, and dashboard endpoints that make up the visible demo are untested and have never been run end to end.

## Repository state

| | |
| --- | --- |
| Branch | `main`, ahead of `origin/main` and not pushed |
| Working tree | clean apart from this handover commit |
| Worktrees | `.worktrees/` is empty; the `feature/phase-a-backend` branch and the `phase-a-backend` worktree were deleted after their unique artifacts were rescued into `docs/` |
| Main sources | 160 Java files under `backend/src/main/java` |
| Test sources | 8 Java files under `backend/src/test/java` |
| Frontend | `web/` holds only empty directories — `public/` plus `src/{api,layouts,router,stores,types,utils,views}` — and no files |

Recent commits, newest first:

| Commit | What |
| --- | --- |
| `34ccb77` | fix: make the test runner script actually runnable |
| `8b6fa36` | style: reformat phase A demo services to house style |
| `f6f797f` | chore: add test runner script for the isolated test database |
| `0e7799d` | docs: preserve SDD briefs and task reports |
| `62985a8` | docs: preserve superpowers SDD ledger before removing worktree |
| `fe85368` | chore: pin repository line endings to LF |
| `b4dcf7a` | docs: move product specs under docs/ |
| `1427692` | feat: deliver minimal backend demo workflows — the Task 6–11 implementation |

## What was verified, and how

Verification matters here because the code being handed over was written without tests. Three independent checks were used.

**Bytecode equivalence for the reformat.** The Task 6–11 code had been written with several statements collapsed onto one line. It was rewritten to match the Task 1–5 style — explicit imports, private final fields, one statement per line, Chinese Javadoc headers on controllers. To prove no logic moved, the sources were compiled before and after and the bytecode was dumped with `javap -p -c -constants`, with line-number tables, local-variable tables, source-file attributes and byte offsets stripped, then diffed. The only differences are the 58 call sites that changed from reading the `OwnerStore.jdbc` field to calling the new `OwnerStore.jdbc()` accessor, plus that accessor's own body and the field's visibility modifier. Imports do not reach the bytecode, so they are invisible to the diff by construction.

**A real test run.** For the first time since Task 5, the suite was executed end to end: **185 tests, 0 failures, 0 errors, BUILD SUCCESS.** Flyway also applied V1–V5 against a brand-new empty database, so the V5 migration has now actually run — the previous delivery note recorded that it never had.

**The test runner script was executed, not just read.** It had never been run successfully before, and it turned out to have four independent defects. All are fixed in `34ccb77`; see "Fixes worth knowing about" below.

## Fixes worth knowing about

`scripts/run-tests.ps1` was committed in `f6f797f` and had never been executed. Four defects, each of which alone prevented it from working:

1. It generated the throwaway password with `[Convert]::ToHexString`, which only exists on .NET 5+. This machine has only Windows PowerShell 5.1, so the script died with `MethodNotFound` on line 29 before doing anything. The hex is now encoded by hand.
2. It wrote its log to `target/test-output.log`, a directory that does not exist on a fresh clone, so the redirect failed with `DirectoryNotFoundException`. The default is now `backend/target/`, and the parent directory is created if missing.
3. Under `$ErrorActionPreference = 'Stop'`, Maven writing progress to stderr became a terminating `NativeCommandError`. The preference is now relaxed around the Maven call and the run is judged by its exit code.
4. Its comment claimed it dropped the throwaway password afterwards, but it never did — the role kept a stored credential between runs. The `finally` block now clears it, and it is written so it cannot throw and mask the test run's exit code.

Separately, `OwnerStore.jdbc` was a public field. It is now private with a `jdbc()` accessor, matching every other component in the package. All call sites were updated mechanically and are covered by the bytecode diff above.

`.gitattributes` pins `* text=auto eol=lf`. Without it, `core.autocrlf` makes two clones of the same commit differ on disk and `diff -r` reports every source file as changed. This was the reason an earlier comparison of `main` against the worktree looked like 170+ files differed when the content was identical.

## What is done

**Tasks 1–5** — implemented, independently reviewed, covered by tests:

- Task 1: repository, runtime profiles, V1 schema, test harness
- Task 2: result envelope, trace IDs, domain errors
- Task 3: registration, login, refresh sessions, logout, password change, account recovery
- Task 4: settings, subjects, learning-day rules, V3
- Task 5: onboarding and idempotent template import

**Tasks 6–11** — implemented, **untested**, and now only exercised by a compile:

- Task 6: task core, hierarchy, ordering, optimistic versions
- Task 7: repeat templates and carry-over batches
- Task 8: unique focus/break sessions and server-time recovery
- Task 9: settlement, time slices, rewards, pets
- Task 10: manual records, overlap protection, record queries
- Task 11: dashboard aggregate and consistent metrics

The HTTP contract for Tasks 6–11 is written down in `docs/demo-backend-delivery.md` under 最小接入约定. Use that; do not reverse-engineer it from the controllers.

## What is not done

**Task 12 — avatar storage, account deletion, operational hardening.** Not started. The plan is `docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md`, Task 12, lines 798–854. Avatar upload is still a placeholder service; account deletion and data cleanup do not exist.

**Task 13 — acceptance and handoff.** Not started. Plan lines 855–904. Step 1 of Task 13 is the full test suite, which has now been run once successfully but has not been recorded as acceptance evidence.

**Frontend.** Nothing exists. `web/` has empty `src/` and `public/` directories. Task 13 Step 5 is a hard gate: invoke the UI skills and get explicit user approval of the design before scaffolding any frontend file.

Known functional gaps in the Task 6–11 code, recorded in `docs/demo-backend-delivery.md` and not yet addressed:

- Repeat template edits only affect future instances; cancelling a parent task does not restore child task states on undo; delete-undo metadata is a simplified implementation.
- Login has no progressive rate limiting. Operational metrics, backup/restore drills are absent.
- The `dto/resp/notification` package is an empty directory.

## How to run things

### The tests

```powershell
powershell -NoProfile -File scripts/run-tests.ps1
powershell -NoProfile -File scripts/run-tests.ps1 -Selection "AuthApiTest"
```

The wrapper sets a throwaway password on the `study_copilot_test_runner` role, injects it through the environment, and clears it again afterwards, so no credential reaches disk or a shell transcript. It defaults to `procurement-stack-postgres-1` on port 5433.

Changing a role password on the shared PostgreSQL container is a write to a workload other projects also use. To avoid it, run against a disposable container instead:

```powershell
docker run -d --name sc-test-pg -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=postgres -p 127.0.0.1:5434:5432 postgres:16-alpine
docker exec sc-test-pg psql -U postgres -d postgres -c "create role study_copilot_test_runner login"
docker exec sc-test-pg psql -U postgres -d postgres -c "create database study_copilot_test owner study_copilot_test_runner"
$env:SC_PG_CONTAINER = 'sc-test-pg'
$env:SC_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5434/study_copilot_test?currentSchema=study_copilot'
powershell -NoProfile -File scripts/run-tests.ps1
```

The database must be owned by the login role the tests use, not by a superuser. If Flyway's history table is owned by someone else, every Spring context fails with `permission denied for table flyway_schema_history`.

### The application

The application has never been started. It needs a `study_copilot` database, a `study_copilot_app` role, Redis, and MinIO, plus the environment variables listed in `docs/backend-local-development.md`. The dev database and the `study_copilot_app` role do not exist yet — creating them was out of scope for the handover.

```powershell
mvn -f backend/pom.xml spring-boot:run
```

### Test safety

The `test` profile refuses to start against any database whose name is not exactly `study_copilot_test`, and rejects JDBC `PGDBNAME` overrides including percent-encoded ones. Flyway clean is disabled. Tests must not clean shared Redis or unrelated schemas. These guards are in `backend/src/main/java/com/example/studycopilot/config/TestDatabaseSafetyConfiguration.java` and are themselves covered by `TestDatabaseSafetyTest`.

## Constraints that are easy to violate

- **The owner lock comes first.** Every settings, learning-rule, subject, task, template/import, and focus/break session mutation must begin its transaction by locking the owner's row with `select id from study_copilot.t_user where id=? for update`, before touching any dependent row, and hold it until commit. Locking only the settings, subject, task, or session row does not protect the cross-table invariants. See `docs/backend-local-development.md` § Cross-resource mutation locking contract.
- **Never rotate the idempotency HMAC secret with ordinary JWT rotation.** They are independent secrets and the idempotency secret must stay stable.
- **No credential values in version control, terminal output, or documentation.** Use interactive `\password` when setting a role password.
- **The stored `t_pet_status.level` column is non-authoritative.** The level is derived from `total_exp`.
- **B/C controllers stay behind the `future-features` profile.** Do not activate it in Phase A acceptance or deployment.
- **Do not accept an owner ID from an HTTP request body.** All HTTP services derive the owner from `UserContext.requireUserId()`.

## Recommended order of work

1. **Start the application and exercise the Task 6–11 endpoints by hand.** This is the highest-value next step and the one that has never been done. `docs/demo-backend-delivery.md` lists every route and request shape. The compile is not evidence; a running request is.
2. **Write the tests that Tasks 6–11 never got.** They are the demo's whole value and they are the difference between "implemented" and "verified". This is a large piece of work and is the honest answer to "what is left".
3. **Task 12** — avatar isolation, account deletion, B/C profile audit, operational metrics, `docs/backend-operations.md`.
4. **Task 13** — acceptance evidence mapped to the A.16 cases, `docs/phase-a-backend-acceptance.md`.
5. **Frontend**, only after the user approves a design.

## Where things live

| Path | What |
| --- | --- |
| `docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md` | The authoritative master plan, 904 lines, Tasks 1–13 |
| `docs/superpowers/specs/2026-10-22-study-copilot-phase-a-backend-design.md` | The design the plan came from |
| `docs/superpowers/sdd/` | Per-task briefs and reports. Task 6–11 reports are missing — those tasks were implemented outside the SDD flow |
| `docs/development-ledger.md` | Snapshot of the SDD progress ledger, rescued from the deleted worktree |
| `docs/demo-backend-delivery.md` | The Task 6–11 HTTP contract and the list of known gaps |
| `docs/backend-local-development.md` | Provisioning, environment variables, the locking contract, the schema notes |
| `docs/Study_Copilot_产品总纲_v0.2.md`, `docs/Study_Copilot_一期A_详细需求文档_v0.2.md` | Product and Phase A requirements, in Chinese |
| `scripts/run-tests.ps1` | The test runner described above |

## Conventions to follow

- 4-space indent, one statement per line, explicit imports rather than wildcards.
- `@Service @RequiredArgsConstructor` on one line, `private final` fields, constructor injection.
- Chinese Javadoc headers on the Task 1–5 files; keep that style.
- English for code and technical docs, Chinese for the product documents.
- Conventional commit prefixes, matching the existing history: `feat:`, `fix:`, `chore:`, `docs:`, `style:`.
