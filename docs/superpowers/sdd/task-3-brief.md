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

