### Task 5: Onboarding and Idempotent Template Import

> 历史归档：本文记录早期方案或实施过程，其中的流程要求、授权和验证结论仅适用于当时。当前状态见 [后端交付说明](../../../04-交付验收/一期A后端交付说明.md)，旧路径的新位置见 [归档索引](../../README.md)。

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

