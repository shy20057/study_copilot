# Task 5 — onboarding and idempotent template import

Commit: `b4d2550530b372c5cd64938e593fa5c097244e95` (`feat: implement onboarding and template imports`). Working tree clean after commit. This report remains local under the repository's existing ignored `.superpowers` directory.

## Requirements and implementation

- Implemented authenticated `GET/PUT /api/onboarding/progress`, `POST /api/onboarding/complete`, `GET /api/templates`, and `GET /api/templates/{id}/preview` with Task 2 Result/error envelopes and Task 3 authentication. Every HTTP entry derives its owner using `UserContext.requireUserId()`.
- Progress uses optimistic versions, persists the current step and confirmed draft data, merges later confirmed fields without dropping earlier steps, and supports skip and resume. Skip retains registration's usable default subject and pet. `confirmedData` is resumable draft data; settings/subjects keep their existing dedicated mutation APIs.
- Resource version 1 contains four example templates (foundation, reinforcement, sprint, full-course), stable item IDs, subject aliases, relative day offsets, estimated pomodoros and display copy. All descriptions identify adjustable examples; no personalized scheduling is promised.
- Preview is read-only, resolves today through `LearningDayService`, and exposes only the current seven learning days. A requested base date must be within that window. Stages are read-only summaries; their resource version and summary are retained in the import batch. Only selected preview items create executable tasks.
- Suggested selection defaults to at most three today and at most 70% of each day's optional availability budget. Explicit selection can add a fourth today item. Each item's estimate is frozen from the focus duration at import; changing settings later does not rewrite it.
- Date ranges too short for the chosen stage return safe fixed `SC_015001` guidance to choose a shorter template or arrange tasks manually. The sprint template supports a three-day range. No silent stage compression occurs.
- Preview versions use the dedicated HMAC secret and cover owner, resource content/version, requested base date, current learning date/horizon, current learning-rule version, scheduling settings and active owned subjects/versions. Completion validates the preview again under the owner's mutation lock.
- Completion creates batch, selected tasks, onboarding terminal state and durable replay response in one transaction. Every mutation locks `t_user` before resource rows. Replay runs before current-state/version validation so a response lost after commit remains recoverable.
- Shared `IdempotencyService.execute` scopes keys to authenticated owner + operation + request ID and fingerprints the normalized payload with `IdempotencyProperties`, independently of JWT signing material. DTO item IDs are sorted sets, override titles are trimmed, and map keys are canonicalized. Identical requests replay the original public response; changed payloads conflict with `SC_001006`. Failed transactions leave no replay record and the key can be retried. PostgreSQL retains responses for at least 24 hours.

## Migration and Task 6 integration contract

V1/V2/V3 remain unchanged. V4 adds nullable `t_task.template_id`, `template_version`, `template_item_id`, and `template_learning_date`, with an all-null/all-present check and partial unique index on `(user_id, template_id, template_item_id, template_learning_date)` where item identity is present. Imported metadata requires an import batch; manual tasks retain null metadata and may use the same title.

Stable template/item/original learning-date identity deduplicates across batches and catalog versions. `template_version` is provenance, not identity. `template_learning_date` is immutable provenance: Task 6 rescheduling must change only ordinary scheduling fields, and edits, cancellation and logical deletion must retain import identity. The index deliberately covers logically deleted imports too; importing again does not recreate a deleted item. Legacy imported rows lacking item metadata remain legal; V4 cannot infer stable identity from historical titles.

`TemplateTaskWriter.create` is the narrow Task 6 seam. It requires an existing transaction and caller-held owner lock, validates the active owned subject and subject version, creates root tasks only, stores a subject name snapshot and frozen estimate, and carries stable import provenance. No task update/hierarchy APIs are invented here. Task 6 can route this seam through its canonical creation logic while preserving these invariants and lock order.

## API examples

Save a step with `PUT /api/onboarding/progress`:

```json
{"version":0,"currentStep":"SUBJECTS","confirmedData":{"goalName":"Exam","petType":2}}
```

Preview with `GET /api/templates/foundation/preview`; optional `itemIds` and ISO `learningDate` query parameters control selection/base date. Response includes `templateVersion`, `previewVersion`, learning date, stages, counts, estimated minutes and item-level selection/import flags, owned subject IDs and subject versions. Reading previews never creates a batch or task.

Complete with `POST /api/onboarding/complete` and required `Idempotency-Key`:

```json
{"version":1,"templateId":"foundation","previewVersion":"<from preview>","learningDate":"2026-10-23","itemIds":["d0-read","d1-review"],"overrides":{"d0-read":{"title":"Read one section","plannedPomodoros":2}}}
```

Optional item overrides accept title, estimated pomodoros and active owned subject + subjectVersion. Overrides for unselected items are rejected. An empty item selection imports no tasks; completion never implicitly imports suggestions. `{"version":1,"skip":true}` skips, and `{"version":1,"skip":false}` completes without a template. Import response contains status, progress version, batch UUID, created task IDs and skipped already-imported item IDs. Resuming uses a versioned progress save.

## RED/GREEN evidence

Tests were authorized by the user via the parent task. Commands used the existing `procurement-stack-postgres-1`, exactly `study_copilot_test` and `study_copilot_test_runner`; runner credentials were generated transiently and passed through Docker stdin/process environment. No `.env` was read, no credentials were printed or persisted, and no new middleware, H2 or Testcontainers was introduced. Every test cleans only accounts created by that test.

- Initial RED: `mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=OnboardingApiTest test`: 10 tests, 10 expected missing-route failures, 0 errors.
- First implementation run exposed a missing-header 500 and a stale-preview test setup that prevented the intended subject-version check. Header handling was fixed to yield 400; the fixture now refreshes after activating its subject.
- Additional RED: 13-test suite showed exactly two requirement gaps: out-of-window selected items accepted and insufficient-date guidance lost behind a generic validation code. Both were fixed.
- Additional focused RED: later-step save lost the earlier pet draft field. Confirmed-data merge fixed it.
- Focused GREEN: 13 tests, 0 failures, 0 errors, Maven BUILD SUCCESS.
- Full current suite: `mvn -f backend/pom.xml -Dspring.profiles.active=test test`: 180 tests, 0 failures, 0 errors, 0 skipped, Maven BUILD SUCCESS (2026-10-23 15:41 Asia/Shanghai). This run includes the final current-learning-date preview fingerprint. Existing security/error-path tests intentionally log rejected contexts and sanitized exceptions; no unexpected onboarding errors were logged.

Coverage includes progress restore/isolation/version conflict, skip/resume/defaults, read-only/versioned/bounded previews, availability cap, explicit fourth today item, target-date guidance, selected-only atomic import, late foreign-subject rollback and same-key retry, lost-response replay, normalized set/map/title replay, changed-payload conflict, owner key isolation, stale previews, active owned subjects and versions, concurrent replay, same-title manual tasks, rescheduling/logical-deletion dedup, database uniqueness across resource versions, and frozen focus estimates.

## Files and self-review

Added onboarding controllers, explicit request/response DTOs, onboarding service/catalog/import writer, shared idempotency service, versioned JSON template resource, V4 migration and `OnboardingApiTest`. Extended `ScErrorCode` with the safe template-range error.

Self-review checked owner isolation, transaction/lock order, replay ordering, no JWT/HMAC coupling, canonical request order, historical metadata preservation, manual same-title legality, no preview writes, bounded learning dates, and no hidden task hierarchy creation. Included the current learning date in preview fingerprinting so future-anchored previews expire when the rolling horizon advances.

Remaining limits: these are adjustable built-in example schedules; longer-term stages are stored as batch summaries and do not generate future tasks automatically. The client must present the shorter-template choice after `SC_015001`. No frontend or unrelated Phase B/C behavior was added.

## Fix round 1 — preview summaries and sprint stage bounds

Fix commit: `c0e0f4068e24a2552b314c174c8af0c5bbbb7c35` (`fix: align template preview totals and sprint horizon`). Working tree clean after commit; this report remains a local ignored artifact.

Verified the review findings against the implementation: summaries counted every candidate despite selection/import flags, and sprint candidates without an exam date continued beyond the three-day stage summary. Added five API regressions before changing production code.

- RED: focused `OnboardingApiTest` ran 18 tests with exactly five expected failures and zero errors. An explicit single selection reported ten tasks; a previously imported explicit selection reported ten instead of zero; the default summary reported ten instead of nine; sprint exposed ten candidates instead of six; and completion accepted day-four sprint content instead of rejecting it.
- The public `taskCount`, `todayTaskCount` and `estimatedFocusMinutes` now count only selected, not-yet-imported items. `items` remains the candidate list, with selection/import flags. Default foundation totals are nine tasks, three today and 275 minutes; an explicit first-day single item reports one/one/25, and an imported-only selection reports zero/zero/zero.
- Stage construction and candidate filtering now share one resolved template end. Without an exam date, sprint ends on base + two learning days; it exposes only those three days. Completion validates against the same candidate list, rejects later item IDs before writing a batch or tasks, and accepts in-stage selections.
- Files changed: `OnboardingServiceImpl`, `TemplatePreview` documentation and `OnboardingApiTest`. No migrations or catalog resources changed; deferred catalog validation and retention policy were left untouched.
- Focused GREEN: `mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=OnboardingApiTest test`: 18 tests passed, zero failures/errors/skips, BUILD SUCCESS (2026-10-23 15:49 Asia/Shanghai).
- Full current suite, run once after final changes: `mvn -f backend/pom.xml -Dspring.profiles.active=test test`: 185 tests passed, zero failures/errors/skips, BUILD SUCCESS (2026-10-23 15:50 Asia/Shanghai). Existing intentional safety/error-path diagnostics remain expected.
