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

