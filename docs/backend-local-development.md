# Phase A backend local development

Use Java 17 and Maven 3.9+. Spring Boot 3.3.4 manages Flyway 10.10.0 and its required `flyway-database-postgresql` module together; there is no standalone Flyway version override. The prior Boot 3.0.7/Flyway 9.5.1 baseline predates PostgreSQL 16 support. PostgreSQL 16 is authoritative. Redis is disposable cache/coordination, and MinIO is used only for avatars. Do not run a second middleware stack, H2, or Testcontainers for this project.

## Existing middleware

The authorized compose file is `E:\zahuo\实习上简历\procurement-实习的项目\deploy\docker-compose.yml`.
An authorized operator can start or reuse only the required services:

```powershell
docker compose -f 'E:\zahuo\实习上简历\procurement-实习的项目\deploy\docker-compose.yml' up -d postgres redis minio
docker compose -f 'E:\zahuo\实习上简历\procurement-实习的项目\deploy\docker-compose.yml' ps postgres redis minio
```

Compose consumes the existing deployment's configuration itself. Do not copy its `.env` files or credentials into this repository, terminal output, or documentation. Published ports may differ from compose defaults; the existing PostgreSQL instance currently publishes port 5433. Redis's default published port is 6381, and MinIO's is 9000. Use the actual deployment values.

## Database provisioning

Use distinct login roles `study_copilot_app` and `study_copilot_test_runner`, with separately provisioned passwords. The environment names below describe credentials; their values must come from your secret manager or a private process environment. Never put values into version control or a command transcript.

An authorized operator opens the existing PostgreSQL container's local administrative session:

```powershell
docker compose -f 'E:\zahuo\实习上简历\procurement-实习的项目\deploy\docker-compose.yml' exec postgres sh -c 'psql -U "$POSTGRES_USER" -d postgres'
```

Run the following SQL only for roles/databases that do not already exist. Set each role's password using interactive `\password`, which avoids placing plaintext into shell history:

```sql
CREATE ROLE study_copilot_app LOGIN;
\password study_copilot_app
CREATE ROLE study_copilot_test_runner LOGIN;
\password study_copilot_test_runner
CREATE DATABASE study_copilot OWNER study_copilot_app;
CREATE DATABASE study_copilot_test OWNER study_copilot_test_runner;
```

Keep these database owners separate and grant neither role access to unrelated project databases. Flyway creates schema `study_copilot` inside each database. PostgreSQL's trusted `btree_gist` extension is created there by V2; a database owner can install it without superuser privileges. If it was already installed in another schema, have an operator review extension placement before migrating. Do not move a shared extension blindly.

## Environment and profiles

The default runtime has no embedded credentials. Supply the following variables to the application process:

| Variable | Purpose |
| --- | --- |
| `SC_DB_URL` | `jdbc:postgresql://127.0.0.1:5433/study_copilot?currentSchema=study_copilot` (adjust actual host/port) |
| `SC_DB_USER`, `SC_DB_PASSWORD` | Application database login |
| `SC_REDIS_HOST`, `SC_REDIS_PORT`, `SC_REDIS_PASSWORD` | Existing Redis connection; host/port default to `127.0.0.1:6381` |
| `SC_REDIS_PREFIX` | Runtime namespace; default `study-copilot:` |
| `SC_JWT_SECRET` | Random JWT signing secret with at least 32 UTF-8 bytes |
| `SC_IDEMPOTENCY_HMAC_SECRET` | Separate random secret of at least 32 UTF-8 bytes for registration payload fingerprints; keep stable independently of JWT signing-key/issuer rotation |
| `SC_RECOVERY_SUPPORT_CHANNEL` | Human support channel for invited-beta account recovery; required outside tests |
| `SC_MINIO_ENDPOINT` | Existing MinIO API endpoint; default `http://127.0.0.1:9000` |
| `SC_MINIO_BUCKET` | Separate avatar bucket; default `study-copilot` |
| `SC_MINIO_ACCESS_KEY`, `SC_MINIO_SECRET_KEY` | Credentials scoped to that avatar bucket |

Create the avatar bucket using an authorized MinIO operator and isolate object paths by user. Storage services are implemented in the later avatar task; adding the SDK and configuration here does not establish any dependency on a live MinIO connection at application startup.

```powershell
mvn -f backend/pom.xml spring-boot:run
```

The `test` profile uses only `SC_TEST_DB_URL`, `SC_TEST_DB_USER`, and `SC_TEST_DB_PASSWORD` for its database connection. Set `SC_TEST_DB_URL` to `jdbc:postgresql://127.0.0.1:5433/study_copilot_test?currentSchema=study_copilot` with the actual host/port. Before datasource/Flyway instantiation it rejects every database name except exactly `study_copilot_test`; putting that string in a query parameter is not sufficient. It also validates any Hikari/Flyway URL override and rejects JDBC `PGDBNAME` query overrides, including percent-encoded names. Flyway clean is disabled. Tests must not clean shared Redis or unrelated schemas.

The test Redis namespace and Spring Cache prefix are fixed to `study-copilot:test:` and `study-copilot:test:cache:`. Tests that exercise Redis must receive `SC_REDIS_PASSWORD`; pure migration tests do not require a Redis or MinIO connection. `SC_TEST_JWT_SECRET` optionally replaces a clearly test-only signing key. It is never used in the default runtime.

After approval to run tests, execute:

```powershell
mvn -f backend/pom.xml -Dspring.profiles.active=test -Dtest=MigrationSmokeTest test
mvn -f backend/pom.xml -Dspring.profiles.active=test test
```

## Schema and boundaries

V1 stays unchanged. V2 evolves users, settings, subjects, tasks, and pet status, and creates refresh sessions, versioned learning-day rules, onboarding state, atomic import batches, repeat templates, schedule history, focus sessions/slices, idempotency records, rewards, result claims, and account deletion jobs.

Usernames are globally unique after trimming and lowercasing. V2 deliberately fails if existing users collide under that normalization; audit and resolve collisions before migration. Existing settings provide each user's first learning-day rule. Foreign keys protect ownership of subjects, tasks, repeat templates, imports, rules, slices, and rewards. List indexes start with `user_id`; due-work indexes support settlement and deletion scans. Idempotency records cannot expire earlier than 24 hours after creation.

Focus `mode` uses `FOCUS`, `SHORT`, or `LONG`; `source` uses `TIMER` or `MANUAL`; terminal states are `COMPLETED`, `ENDED_EARLY`, or `CANCELLED`. Every user has at most one nondeleted `RUNNING` focus or break. A stored half-open effective range reserves a running focus session through `scheduled_end_at`; settled focus intervals with at least 60 valid seconds use `end_at`. Breaks, discarded short intervals, and soft-deleted manual records have no effective range. Adjacent intervals are allowed; overlapping effective intervals are rejected by PostgreSQL, including concurrent inserts.

Repeated instances are unique per owner/template/learning day. Slices and rewards are unique per session/learning day. The reward ledger supports transactional quota enforcement in the settlement service; it does not independently enforce a sum across multiple rows. Legacy `t_pet_status.level` is retained for V1 compatibility and is explicitly nonauthoritative: Phase A must derive level from `total_exp`. Weekly targets are nullable and no longer receive an invented default.

All B/C controllers (`chat`, `journal`, `knowledge`, `milestone`, `mistake`, `notification`, `reminder`, and `stat`) are behind `future-features`; the pre-existing missing `JournalController` import is repaired. The later operational-hardening task will audit the exhaustive route boundary. This foundation does not implement B/C behavior. Do not activate `future-features` in Phase A acceptance or deployment.

Registration request IDs are global within the REGISTER operation before a user exists. Every application instance takes PostgreSQL advisory locks in the same order: request ID, normalized username, then user and refresh-session rows. The global rule is application-enforced over V2's user-scoped idempotency table; all registration writers must follow it. Changing any payload under a used request ID returns IDEMPOTENCY_CONFLICT. Valid replay metadata includes a version, original issuer and a non-secret signing-key fingerprint. Replay returns the identical original pair only while its configuration, account and durable session remain valid; configuration rotation, expired/revoked credentials or unsupported legacy metadata returns TOKEN_REVOKED and requires login. Never rotate the independent idempotency HMAC secret as part of ordinary JWT rotation.
