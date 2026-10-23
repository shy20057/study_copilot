# 开发进度账本（快照）

> 来源：`.worktrees/phase-a-backend/.superpowers/sdd/2026-10-22-study-copilot-phase-a-backend/progress.md`
> 快照于 commit `1427692`（2026-10-23），该 worktree 随后已删除。
> 这是 superpowers SDD 流程的**运行账本**快照，其中 Task 6–11 的状态未勾选，
> 因为它们是在账本最后一次更新之后、按用户"直接实现、不再走 TDD/评审"的指令
> 一次性补上的（同样是 `1427692`）。以 `docs/demo-backend-delivery.md` 为准。

---

# SDD ledger — plan: docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md

## Baseline

- USER FINAL SCOPE 2026-10-23: no subagents/tests/reviews; smallest delivery now. Task6–11 demo production code added directly, main-source compilation passed. New V5 not executed. Task12 avatar/deletion/hardening deferred. Final details docs/demo-backend-delivery.md; no full phase-complete claim.

- USER OVERRIDE 2026-10-23: Demo delivery; directly implement remaining production code. No additional test writing/execution or independent reviews. Prior TDD/review plan steps superseded by explicit user instruction. Preserve existing tests and completed code; report remaining work as untested. Frontend UI skills consultation gate still applies.

- Branch: `feature/phase-a-backend`
- Worktree: `E:\a-ProjectNew\my_codingrepo\study_copilot\.worktrees\phase-a-backend`
- Baseline command: `mvn -f backend/pom.xml test`
- Baseline result: failed during compilation because `JournalController` uses `@PathVariable` without importing it.
- Ruling: treat the missing import as a pre-existing compile blocker and repair it in Task 1 while placing B/C controllers behind the planned non-default profile. This is required to make the Phase A backend buildable and does not expose or implement B/C functionality.

## Pre-flight dependency and overlap scan

| Tasks | Shared files/contracts | Sequencing ruling |
|---|---|---|
| 1 → all | `pom.xml`, runtime profiles, Flyway schema, test infrastructure | Task 1 establishes the executable schema and test harness; later tasks may only evolve it through new migrations or explicitly planned config changes. |
| 1 ↔ 12 | B/C controller profiles, operational configuration | Task 1 fixes the compile blocker and establishes safe defaults; Task 12 completes the exhaustive boundary audit and operations hardening. |
| 3 ↔ 12 | account lifecycle, sessions, authentication filters | Task 3 owns active-account authentication; Task 12 extends it with deletion-pending revocation and cleanup, preserving Task 3 contracts. |
| 4 ↔ 8 | learning-day settings and running-session guard | Task 4 owns rule calculation and versioning; Task 8 consumes it and blocks rule changes while a session is running. |
| 5 ↔ 6 | task creation, idempotency, hierarchy | Task 5 imports templates through a narrow application service; Task 6 establishes the canonical task write rules that later callers reuse. If Task 5 needs an interim seam, keep it interface-focused and reconcile in Task 6. |
| 6 ↔ 7 | task entities/services, scheduled creation, carry-over | Task 6 owns core task invariants; Task 7 adds batch/template scheduling without duplicating core writes. |
| 8 ↔ 9 ↔ 10 | focus session entity/service/controller, time slices | Task 8 owns session lifecycle and uniqueness; Task 9 owns settlement/rewards; Task 10 adds manual sessions and record queries using the same overlap model. |
| 9 ↔ 11 | reward ledger, pets, time-slice aggregates | Task 9 owns durable facts; Task 11 is read-only aggregation over those facts. |
| 10 ↔ 11 | record queries and dashboard summaries | Task 10 owns record retrieval semantics; Task 11 composes them without redefining calculations. |
| 12 → 13 | production profiles, boundary controls, operational docs | Task 12 finishes deployability; Task 13 validates the entire contract without introducing new product behavior. |

## Plan consistency rulings

- Ruling: Task5 V4 adds template identity/provenance to tasks, unique per owner/template/item/immutable import learning date across batches, versions and soft deletion — stable item IDs identify same imported task even after rescheduling — incorrect interpretation would require explicit reimport policy change/migration.

- Ruling: Task 4 adds V3 for user-scoped trimmed case-insensitive active subject name uniqueness, excluding archived/deleted rows — V2 lacks this invariant and is immutable — incorrect predicate would reject historical duplicates and needs migration rework.

- Ruling: PostgreSQL 16 is the sole source of truth; Redis may be cleared or unavailable without losing authoritative state; MinIO is used only for avatar objects.
- Ruling: integration tests use the isolated `study_copilot_test` database and `study-copilot:test:` Redis prefix; H2 and Testcontainers are out of scope.
- Ruling: all default/test profiles must hide B/C endpoints; compile-only repairs to those controllers are permitted, feature implementation is not.
- Ruling: `dataVersion`, idempotency keys, optimistic versions, row locking, and PostgreSQL exclusion/partial-unique constraints are shared contracts and must not be reinterpreted per task.
- Ruling: the plan's conditional Flyway PostgreSQL module means Task 1 must resolve the actual dependency graph and add the module only if this Spring Boot/Flyway combination requires it.
- Ruling: `t_user.normalized_username` is globally unique. The brief's `(user_id, normalized_username)` phrase is inconsistent because `t_user.id` is already unique and such a composite key would not prevent duplicate login names; no redundant `user_id` column/key will be added.
- Ruling: align Spring Boot to 3.3.4 so its BOM-managed Flyway 10.10.0 plus `flyway-database-postgresql` explicitly supports PostgreSQL 16 without binary-incompatible overrides or a custom bootstrap; this remains within the approved Spring Boot 3.x/Java 17 stack. Boot 3.2.6/Flyway 9.22.3 was rejected because it still warned that PostgreSQL 16 was unsupported.
- Ruling: apply `@Profile("future-features")` to every named B/C controller in Task 1 as a mechanical safety boundary; Task 12 still owns exhaustive route verification and hardening.
- Ruling: because the approved brief/spec names Task 2 error semantics but not numeric assignments, preserve existing category ranges and assign: version/idempotency/rate-limit `SC_001005/6/7`, revoked token `SC_002008`, learning-rule change blocked `SC_003002`, subject-in-use/last-active-subject `SC_004003/4`, and session-exists/not-due/time-overlap `SC_007004/5/6`.
- Ruling: the frontend remains untouched until the separately required UI-skills consultation and user approval.

## Task status

- [x] Task 1 — Repository, Runtime Profiles, and Phase A Schema (`8a1cf66`, fix `c1ab420`; review approved)
- [x] Task 2 — Result Envelope, Trace IDs, and Domain Errors (`d0b4727`, fix `ba3aee6`; review approved)
- [x] Task 3 — Authentication, Refresh Sessions, and Account Initialization (`a1a0da2`, fix `92da689`; review approved)
- [x] Task 4 — Settings, Subjects, and Learning-Day Rules (`11f94fe`; review approved with minors)
- [x] Task 5 — Onboarding and Idempotent Template Import (`b4d2550`, fix `c0e0f40`; review approved)
- [ ] Task 6 — Task Core, Hierarchy, Ordering, and Optimistic Versions
- [ ] Task 7 — Repeat Templates and Carry-Over Batches
- [ ] Task 8 — Unique Focus/Break Sessions and Server-Time Recovery
- [ ] Task 9 — Settlement, Time Slices, Rewards, and Pets
- [ ] Task 10 — Manual Records, Overlap Protection, and Record Queries
- [ ] Task 11 — Dashboard Aggregate and Consistent Metrics
- [ ] Task 12 — Avatar Storage, Account Deletion, Profiles, and Operational Hardening
- [ ] Task 13 — Full Backend Acceptance and Handoff

## Review carry-forward

- Task5: fix round1/5 (2 addressed, 0 open; b4d2550..c0e0f40). Task5 complete, review clean; focused18/full185.
- Task5 minor (deferred): catalog startup validation should check complete identities/stages/upper bounds when catalog evolves.
- Task5 minor (deferred): idempotency currently replays retained records indefinitely (at least24h, not exact expiry); future cleanup must reconcile import-batch uniqueness and key reuse.

- Task 4: complete (commits 92da689..11f94fe, review approved; focused16/full167).
- Task 4: minor (deferred to Task9): LearningSlice.seconds floors individual fractional slices; settlement must allocate integer seconds globally preserving total elapsed time, exact endpoints remain authoritative.
- Task 4: minor (deferred): strengthen identical-rule no-op, fractional interval, archived/self migration and direct cross-owner archive/restore cases when those paths are next changed.
- Task 11 obligation: establish dataVersion invalidation across all resource writes; current resource optimistic versions are implemented.
- Tasks 5–8 obligation: every resource mutation/background writer locks t_user before dependent rows; detailed contract is in backend-local-development.md.

- Task 3: fix round 1/5 (3 addressed, 0 open; commits a1a0da2..92da689).
- Task 3: complete (commits ba3aee6..92da689, review clean). Focused 40/full 151 passed.
- Task 4 BASE: 92da68988eeddaa5133986eb3ed444b6c83fe74d.

- Task 1 review round 1: Important/P1 — test database safety guard alternate datasource/JNDI bypass. Addressed by `c1ab420`; scoped re-review approved with no new Critical/Important breakage.
- Task 2 review round 1: Important/P1 safe correlated fallback diagnostics plus dispatch/alias minors. Addressed by `ba3aee6`; scoped re-review approved with no new Critical/Important breakage.
- Carry-forward to Task 3: Spring Security `AuthenticationEntryPoint` and `AccessDeniedHandler` must serialize the Task 2 `Result` envelope because filter-chain rejections do not pass through MVC advice.
- Task 3 review round 1: Important/P1 — registration request IDs must be globally coordinated for REGISTER before a user exists, including cross-username sequential/concurrent conflicts. Important/P2 — idempotency HMAC must be independent from JWT signing configuration and replay metadata must detect issuer/key/session drift before reconstruction. Minor — test cleanup must not use unescaped `_` wildcards. Fix assigned to original implementer; no V2 edit authorized.
- Carry-forward to Task 9: legacy `t_pet_status.level` is non-authoritative; derive level from `total_exp`, whose Java entity field must be `Long` for PostgreSQL `bigint`.
