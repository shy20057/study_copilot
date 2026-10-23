### Task 1: Repository, Runtime Profiles, and Phase A Schema

**Files:**
- Create: `.gitignore`
- Modify: `backend/pom.xml`
- Modify: `backend/src/main/resources/application.yml`
- Create: `backend/src/test/resources/application-test.yml`
- Create: `backend/src/main/resources/db/migration/V2__phase_a_core.sql`
- Create: `backend/src/test/java/com/example/studycopilot/integration/MigrationSmokeTest.java`
- Create: `docs/backend-local-development.md`

**Interfaces:**
- Consumes: PostgreSQL and Redis addresses supplied through environment variables.
- Produces: the complete Phase A relational schema and repeatable local/test configuration.

- [ ] **Step 1: Add repository ignores**

```gitignore
.idea/
.vscode/
*.iml
*.log
.env
backend/target/
web/node_modules/
web/dist/
logs/
```

- [ ] **Step 2: Add required dependencies and test configuration**

Add PostgreSQL runtime, Flyway PostgreSQL support if required by the selected Spring/Flyway version, Spring Security test, and MinIO SDK. Keep dependency versions centralized in `pom.xml`; do not add H2 or Testcontainers because the approved test environment is the shared PostgreSQL container with an isolated database.

```xml
<dependency>
  <groupId>org.springframework.security</groupId>
  <artifactId>spring-security-test</artifactId>
  <scope>test</scope>
</dependency>
<dependency>
  <groupId>io.minio</groupId>
  <artifactId>minio</artifactId>
  <version>8.5.17</version>
</dependency>
```

- [ ] **Step 3: Define environment-only profiles**

```yaml
spring:
  datasource:
    url: ${SC_DB_URL}
    username: ${SC_DB_USER}
    password: ${SC_DB_PASSWORD}
  data:
    redis:
      host: ${SC_REDIS_HOST:127.0.0.1}
      port: ${SC_REDIS_PORT:6381}
      password: ${SC_REDIS_PASSWORD}
study-copilot:
  redis-key-prefix: ${SC_REDIS_PREFIX:study-copilot:}
  storage:
    endpoint: ${SC_MINIO_ENDPOINT:http://127.0.0.1:9000}
    bucket: ${SC_MINIO_BUCKET:study-copilot}
    access-key: ${SC_MINIO_ACCESS_KEY}
    secret-key: ${SC_MINIO_SECRET_KEY}
```

The test profile uses `SC_TEST_DB_URL`, `SC_TEST_DB_USER`, `SC_TEST_DB_PASSWORD`, and prefix `study-copilot:test:`. It must refuse to start if the JDBC URL does not contain `study_copilot_test`.

- [ ] **Step 4: Write the migration smoke test first**

```java
@SpringBootTest
@ActiveProfiles("test")
class MigrationSmokeTest {
    @Autowired JdbcTemplate jdbc;

    @Test
    void phaseASchemaExists() {
        Integer count = jdbc.queryForObject("""
            select count(*) from information_schema.tables
            where table_schema = 'study_copilot'
              and table_name in ('t_focus_session','t_focus_slice','t_reward_ledger')
            """, Integer.class);
        assertThat(count).isEqualTo(3);
    }
}
```

- [ ] **Step 5: Run the migration test and confirm the expected failure**

Run:

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=MigrationSmokeTest test
```

Expected: failure because V2 and the required tables do not exist.

- [ ] **Step 6: Implement V2 schema evolution**

The migration must:

- enable `btree_gist` in schema `study_copilot`;
- extend users/settings/subjects/tasks/pets with the fields from the spec;
- create refresh sessions, learning-day rules, onboarding progress, import batches, repeat templates, schedule logs, focus sessions, focus slices, idempotency records, reward ledger, result presentation, and account-deletion jobs;
- add a partial unique index on `t_focus_session(user_id)` where `status = 'RUNNING'` and `deleted = 0`;
- add unique keys for `(user_id, operation_type, request_id)`, `(user_id, repeat_template_id, learning_date)`, `(session_id, learning_date)`, and `(user_id, normalized_username)`;
- use a stored `tstzrange` or equivalent exclusion constraint for overlapping effective learning ranges;
- add ownership/query indexes for every user-scoped list.

The running-session index is exactly:

```sql
create unique index uk_focus_user_running
on study_copilot.t_focus_session (user_id)
where status = 'RUNNING' and deleted = 0;
```

- [ ] **Step 7: Run the migration test**

Run the command from Step 5.

Expected: PASS with all three tables detected.

- [ ] **Step 8: Document database creation without secrets**

Document the commands that an authorized operator runs inside the existing PostgreSQL container to create `study_copilot` and `study_copilot_test`; reference environment-variable names instead of copying values from the procurement project `.env` files.

- [ ] **Step 9: Commit**

```powershell
git add .gitignore backend/pom.xml backend/src/main/resources/application.yml backend/src/main/resources/db/migration/V2__phase_a_core.sql backend/src/test docs/backend-local-development.md
git commit -m "build: establish phase A backend foundation"
```

---

