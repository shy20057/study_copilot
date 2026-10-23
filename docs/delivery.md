# Study Copilot 一期 A 后端交付说明

> 本文是本次后端交付的**唯一入口**，合并自原 `docs/demo-backend-delivery.md` 与 `docs/handover.md`（二者已删除）。
> 环境、配置与数据库供给细节见 `docs/backend-local-development.md`（活的环境手册，持续更新）。
> 历史流程记录见 `docs/development-ledger.md`（冻结的账本快照，不再修改正文）。

---

## 1. 状态摘要

### 1.1 一句话结论

一期 A 后端 Demo 已实现、可编译、可通过测试。但**测试通过不等于产品可用**：185 个用例只覆盖 Task 1–5 的地基，Task 6–11（任务、专注、重复模板、萌宠、统计）**没有任何测试覆盖，也从未对着运行中的应用跑过一次**。Task 12、Task 13 未开始，前端不存在。

### 1.2 验证状态更新（2026-10-23，接手 agent 补记）

- 当前 HEAD 上全量测试 **185 通过 / 0 失败 / 0 错误，BUILD SUCCESS**（`scripts/run-tests.ps1`，跑在一次性 PostgreSQL 容器上）。
- **V1–V5 已由 Flyway 在全新空库上真实迁移成功**——此前交付说明记录的「V5 未执行」已不成立。
- 此前的接口接线修改已重新编译并通过全量测试。
- 应用**从未启动**，接口**从未联调**。仍不能视为运行验收通过。

### 1.3 仓库现状

| | |
| --- | --- |
| 分支 | `main`，领先 `origin/main` 12 个提交，**未推送** |
| 工作区 | 干净 |
| Worktrees | `.worktrees/` 已清空；`feature/phase-a-backend` 分支与 `phase-a-backend` worktree 已删除，其独有产物抢救进 `docs/` |
| 主源码 | `backend/src/main/java` 下 160 个 Java 文件 |
| 测试源码 | `backend/src/test/java` 下 8 个 Java 文件 |
| 前端 | `web/` 只有空目录：`public/` 与 `src/{api,layouts,router,stores,types,utils,views}`，没有任何文件 |

最近提交（新→旧）：

| 提交 | 内容 |
| --- | --- |
| `66d5945` | docs: 更正路由边界结论——`/api/pomodoro` 本就已被隔离 |
| `666161b` | docs: 记录路由边界审计（结论已被上一条更正） |
| `c262d02` | docs: 刷新交付状态，账本快照正文保持不动 |
| `5d5db9e` | docs: 在交接单中记录可复用的测试容器 |
| `e9e2553` | docs: 一期 A 后端交接给下一个 agent |
| `34ccb77` | fix: 让测试运行脚本真正可跑 |
| `8b6fa36` | style: 把一期 A Demo 服务重排到项目代码风格 |
| `f6f797f` | chore: 加入隔离测试库的测试运行脚本 |
| `0e7799d` | docs: 保留 SDD brief 与任务报告 |
| `62985a8` | docs: 删除 worktree 前保留 superpowers SDD 账本 |
| `fe85368` | chore: 把仓库行尾固定为 LF |
| `b4dcf7a` | docs: 把产品文档移入 `docs/` |
| `1427692` | feat: 交付最小后端 Demo 工作流（Task 6–11 实现） |

### 1.4 已验证什么、怎么验证的

交出的是**没有测试的代码**，所以验证方式必须说清楚。用了三项互相独立的检查：

**重排版的字节码等价性。** Task 6–11 的代码原先有大量语句挤在一行，被重写成 Task 1–5 的风格——显式 import、`private final` 字段、一行一条语句、controller 带中文 Javadoc 头。为证明逻辑没有移动，改动前后各编译一次，用 `javap -p -c -constants` 导出字节码，剥掉行号表、局部变量表、source-file 属性和字节偏移，再 diff。**唯一差异**是 58 处从直接读 `OwnerStore.jdbc` 字段改为调用新增的 `OwnerStore.jdbc()` 访问器，加上该访问器自身的方法体和字段的可见性修饰符。import 不进入字节码，因此按构造就不可见于 diff。

**一次真实测试运行。** 自 Task 5 以来首次端到端执行：**185 通过 / 0 失败 / 0 错误**。Flyway 同时在全新空库上应用了 V1–V5。

**测试脚本是跑过的，不只是读过的。** `scripts/run-tests.ps1` 在 `f6f797f` 提交后从未被成功执行过，实际发现并修掉了四个独立缺陷（见 §5.2）。

### 1.5 做了什么 / 还没做什么

**Tasks 1–5 —— 已实现、已独立评审、有测试覆盖：**

- Task 1：仓库、运行时 profile、V1 schema、测试设施
- Task 2：统一响应封装、trace ID、领域错误码
- Task 3：注册、登录、刷新会话、登出、改密、人工恢复说明
- Task 4：设置、科目、学习日规则、V3
- Task 5：引导流程与幂等的模板导入

**Tasks 6–11 —— 已实现、**无测试**、只经过一次编译：**

- Task 6：任务内核、层级、排序、乐观版本号
- Task 7：重复模板与顺延批次
- Task 8：唯一的专注/休息会话与服务端计时恢复
- Task 9：结算、时间切片、奖励、萌宠
- Task 10：手动补录、重叠保护、记录查询
- Task 11：控制台聚合与一致指标

**Task 12 / Task 13 未开始。前端不存在。** 详见 §4。

---

## 2. 接口契约（前端照抄）

### 2.1 已有功能一览

- 注册、登录、刷新/退出、改密、人工恢复说明；设置、科目、学习日、引导和模板导入。
- 任务创建/编辑/完成/重开/取消、父子联动、5 秒软删除恢复、全量筛选计数和原子排序。
- 重复模板维护、首次访问生成当天实例、选中任务顺延及 5 秒批次撤销。
- 专注/休息唯一运行会话、服务端计时、到期补偿、提前结束、原子休息转专注。
- 固定历史规则的跨日切片、按日经验上限、伙伴类型/十级成长、成果展示权。
- 手动补录的增改删恢复与重叠保护、记录分页、控制台聚合和 dataVersion。

### 2.2 通用约定

所有请求除 auth 外带 `Authorization: Bearer <accessToken>`。关键写入带 `Idempotency-Key`；已有资源的写入传 `expectedVersion`。

本批新增的 JDBC 接口**返回数据库字段名**（例如 `subject_id`、`start_at`、`version`），**请求使用 camelCase**。前端接入时统一适配即可。

### 2.3 Task 6–11 路由与请求体

下列接口是**本批（Task 6–11）新增**的部分，不是全部路由。Task 1–5 的接口（`/api/auth`、`/api/settings`、`/api/subjects`、`/api/onboarding`、`/api/templates`、`/api/account`）见各自 controller。旧番茄钟 `/api/pomodoro` 不在其中，且默认 profile 下不注册。

- `GET/POST /api/tasks`；`PUT/DELETE /api/tasks/{id}`；`POST /{id}/complete`、`reopen`、`cancel`、`restore`；`PUT /api/tasks/reorder`。
- 创建任务：`{"title":"复习一章","subjectId":1,"targetDate":"2026-10-23","plannedPomodoros":1}`。
- 排序：`{"targetDate":"2026-10-23","status":"ALL","items":[{"id":1,"expectedVersion":0}]}`，可带 `parentId`。
- `GET/POST /api/tasks/repeat-templates`；`PUT /api/tasks/repeat-templates/{id}`。
- 重复模板：`title`、`subjectId`、`repeatRule`（DAILY/WEEKDAYS/WEEKLY）、`weekdays`（1–7）、`startDate`、可选 `endDate`、`plannedPomodoros`、`priority`；更新加 `expectedVersion`。
- `POST /api/tasks/carry-over`：`{"items":[{"id":1,"expectedVersion":0}],"conflictChoice":"KEEP_BOTH"}`；`POST /api/tasks/carry-over/{batchId}/undo`。
- `POST /api/focus/start`：`{"subjectId":1,"taskId":1,"minutes":25}`；`GET /api/focus/current`。
- `POST /api/focus/{id}/complete`；`POST /api/focus/{id}/end-early`：`{"claimPartialReward":true}`。
- `POST /api/focus/break/start`：`{"mode":"SHORT","minutes":5}`；`POST /api/focus/break/end-and-start`（与 start 同请求）。
- `POST /api/focus/{id}/presentation/claim`、`ack`：`{"deviceId":"browser-demo"}`。
- `GET /api/focus/records?learningDate=2026-10-23&page=1&pageSize=20`。
- `POST /api/focus/records/manual`：`{"subjectId":1,"startAt":"2026-10-23T01:00:00Z","endAt":"2026-10-23T01:25:00Z"}`。
- `PUT/DELETE /api/focus/records/{id}`；`POST /api/focus/records/{id}/restore`；删除用查询参数 `expectedVersion`，修改/恢复放请求体。
- `GET /api/dashboard`；`GET /api/pet/status`、`/options`；`POST /api/pet/switch`（请求体直接为数字 1–5）。

---

## 3. 怎么跑

### 3.1 跑测试（推荐入口）

```powershell
powershell -NoProfile -File scripts/run-tests.ps1
powershell -NoProfile -File scripts/run-tests.ps1 -Selection "AuthApiTest"
```

脚本给 `study_copilot_test_runner` 角色设一个一次性密码，通过环境变量注入，跑完再清掉，**不落盘、不进 shell 历史**。默认连 `procurement-stack-postgres-1` 的 5433 端口。

改共享容器上的角色密码，是对其他项目也在用的工作负载的写操作。**想完全避开它，就跑自己的临时容器**：

```powershell
docker run -d --name sc-test-pg -e POSTGRES_HOST_AUTH_METHOD=trust -e POSTGRES_USER=postgres -p 127.0.0.1:5434:5432 postgres:16-alpine
docker exec sc-test-pg psql -U postgres -d postgres -c "create role study_copilot_test_runner login"
docker exec sc-test-pg psql -U postgres -d postgres -c "create database study_copilot_test owner study_copilot_test_runner"
$env:SC_PG_CONTAINER = 'sc-test-pg'
$env:SC_TEST_DB_URL = 'jdbc:postgresql://127.0.0.1:5434/study_copilot_test?currentSchema=study_copilot'
powershell -NoProfile -File scripts/run-tests.ps1
```

`POSTGRES_HOST_AUTH_METHOD=trust` 只因为容器绑定在回环地址且用完即弃才可接受——它让凭据管理这一步整个消失。任何活得比这次运行久的用途都用真密码。

数据库必须由测试所用登录角色**拥有**，不能是超级用户。如果 Flyway 的历史表归别人所有，每个 Spring 上下文都会以 `permission denied for table flyway_schema_history` 失败。

> 本机上留着一个按上面配方创建、名为 `sc-test-pg` 的容器，测试库已备好且归测试角色所有。重建前先 `docker ps` 看一眼，在就直接复用。

### 3.2 跑应用

应用**从未启动过**。它需要 `study_copilot` 数据库、`study_copilot_app` 角色、Redis、MinIO，以及 `docs/backend-local-development.md` 里列的环境变量。开发库和 `study_copilot_app` 角色目前都还不存在——创建它们超出了本次交接范围。

```powershell
mvn -f backend/pom.xml spring-boot:run
```

必填环境变量：`SC_DB_URL`、`SC_DB_USER`、`SC_DB_PASSWORD`、`SC_JWT_SECRET`、`SC_IDEMPOTENCY_HMAC_SECRET`、`SC_RECOVERY_SUPPORT_CHANNEL`，以及现有 Redis/MinIO 的凭据变量。V1–V4 保持原样，新增的 `V5__data_version.sql` 由 Flyway 在应用启动时执行。

### 3.3 测试安全边界

`test` profile 拒绝任何名字**不等于** `study_copilot_test` 的数据库，并拒绝 JDBC `PGDBNAME` 覆盖（包括百分号编码的）。Flyway clean 已禁用。测试不得清理共享 Redis 或无关 schema。这些防护在 `backend/src/main/java/com/example/studycopilot/config/TestDatabaseSafetyConfiguration.java`，本身也被 `TestDatabaseSafetyTest` 覆盖。

---

## 4. 未完成与缺口

### 4.1 计划中尚未开始的任务

**Task 12 —— 头像存储、账户注销、运维加固。** 未开始。计划见 `docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md` 第 798–854 行。头像上传仍是占位服务；账户注销和数据清理不存在。

**Task 13 —— 整体验收与交付。** 未开始。计划第 855–904 行。Task 13 第 1 步是全量测试——现在跑过一次并通过了，但**尚未作为验收证据记录**。

**前端。** 什么都没有。`web/` 下 `src/` 和 `public/` 都是空目录。Task 13 第 5 步是硬门禁：**先用 UI skills 并获得用户对设计的明确批准，再动任何前端文件**。

### 4.2 已知功能缺口

- 重复模板的编辑目前只作用于后续实例；取消父任务后「恢复」不恢复子任务原状态；删除撤销的元数据是简化实现。
- 登录没有渐进限流。运维指标、备份恢复演练均缺失。
- `dto/resp/notification` 包是一个空目录。
- `POST /api/upload` 是未实现的占位：`AttachmentServiceImpl.uploadImage` 返回 null。它就是头像桩，归 Task 12。
- 计时、任务、统计模块**未做运行/并发验证**；早期测试不代表它们已被验证。

### 4.3 路由边界审计（2026-10-23）

边界是**干净的**，默认 profile 只暴露一期 A 路由。十个 controller 被停放，必须保持停放：

- 八个 B/C controller 挂在 `future-features` 下：`chat`、`journal`、`knowledge`、`milestone`、`mistake`、`notification`、`reminder`、`stat`。
- 两个被取代的一期 A controller 挂在 `legacy-api` 下：**旧的 task controller** 和**旧的番茄钟 controller**。它们已被 Task 6–11 的替代实现取代。

两个 profile **默认都不激活**，仓库里没有任何 `spring.profiles.default` / `include` / `group` 配置，所以默认 profile 下只有一期 A 路由注册。

> **审计时的陷阱：** 这些注解有时写成完全限定名 `@org.springframework.context.annotation.Profile("legacy-api")`，用 `grep "@Profile"` 匹配不到。审计请用：
> ```
> grep -roE '@(org\.springframework\.context\.annotation\.)?Profile\("[^"]*"\)' backend/src/main/java
> ```
> （本仓库此前就因此被误判过一次，结论已更正。）

`/api/pomodoro` 是 Task 8 之前的旧番茄钟骨架，`PomodoroServiceImpl` 六个方法全是 TODO 占位、恒返回 null。它已被 `/api/focus` 取代，并挂在 `legacy-api` profile 下，**默认 profile 不注册，前端不会碰到它**。

Task 12 仍然拥有最终的穷尽式边界验证。

---

## 5. 接手须知

### 5.1 容易被违反的约束

- **owner 锁必须最先拿。** 每一处 settings、学习规则、科目、任务、模板/导入、专注/休息会话的写操作，都必须**在事务开头**用 `select id from study_copilot.t_user where id=? for update` 锁住 owner 行，然后才碰任何依赖行，并持有到提交。只锁 settings/科目/任务/会话行**保护不了**跨表不变量。详见 `docs/backend-local-development.md` 的 Cross-resource mutation locking contract。
- **绝不把幂等 HMAC 密钥和 JWT 轮换绑在一起。** 两者独立，幂等密钥必须保持稳定。
- **凭据值不进版本库、不进终端输出、不进文档。** 设角色密码用交互式 `\password`。
- **库里存的 `t_pet_status.level` 不可信。** 等级由 `total_exp` 推导。
- **B/C controller 留在 `future-features` profile 后。** 不要在一期 A 的验收或部署里激活它。
- **不要从 HTTP 请求体接收 owner ID。** 所有 HTTP service 从 `UserContext.requireUserId()` 取 owner。
- **不复制部署目录的 `.env` 或凭据**到本仓库、终端输出或文档。

### 5.2 值得知道的坑（已修，但别改回去）

`scripts/run-tests.ps1` 在 `f6f797f` 提交后从未被执行过，四个缺陷每一个单独都足以让它跑不起来，已在 `34ccb77` 修掉：

1. 用 `[Convert]::ToHexString` 生成一次性密码——该 API 只存在于 .NET 5+。本机只有 Windows PowerShell 5.1，脚本还没做任何事就死于 `MethodNotFound`。现在手工做 hex 编码。
2. 日志写到 `target/test-output.log`，而全新 clone 上 `target/` 不存在，重定向直接 `DirectoryNotFoundException`。现在默认落到 `backend/target/`，父目录缺失时创建。
3. 在 `$ErrorActionPreference = 'Stop'` 下，Maven 往 stderr 写进度会变成终止性 `NativeCommandError`。现在在调用 Maven 前后放宽该偏好，改用退出码判定成败。
4. 注释声称跑完会丢掉一次性密码，实际从来没丢——角色在两次运行之间一直留着凭据。现在 `finally` 里清掉，且写成不会抛异常、不会掩盖测试退出码的形式。

另外，`OwnerStore.jdbc` 原本是 public 字段，现在是 private + `jdbc()` 访问器，与包内其他组件一致。所有调用点机械更新，覆盖在上面那次字节码 diff 里。

`.gitattributes` 把仓库固定为 `* text=auto eol=lf`。没有它，`core.autocrlf` 会让同一 commit 的两个 clone 在磁盘上不一致，`diff -r` 报告每个源文件都被改过——此前把 `main` 和 worktree 比对时「170+ 文件不同」就是这么来的，实际内容完全一样。

### 5.3 推荐的后续顺序

1. **启动应用，手工跑一遍 Task 6–11 的接口。** 这是价值最高的一步，也是从来没做过的那一步。§2.3 列全了每个路由和请求体。**编译通过不是证据，跑通一次请求才是。**
2. **补上 Task 6–11 从来没有的测试。** 这些测试就是整个 Demo 的价值，也是「已实现」和「已验证」之间的差别。工作量大，但这是对「还剩什么」的诚实回答。
3. **Task 12** —— 头像隔离、账户注销、B/C profile 审计、运维指标、`docs/backend-operations.md`。
4. **Task 13** —— 对照 A.16 用例的验收证据、`docs/phase-a-backend-acceptance.md`。
5. **前端** —— 只在用户批准设计之后开始。

### 5.4 文件位置索引

| 路径 | 内容 |
| --- | --- |
| `docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md` | 权威主计划，904 行，Task 1–13 |
| `docs/superpowers/specs/2026-10-22-study-copilot-phase-a-backend-design.md` | 计划所依据的设计 |
| `docs/superpowers/sdd/` | 各任务的 brief 与报告。**Task 6–11 的报告缺失**——那批任务是绕开 SDD 流程实现的 |
| `docs/development-ledger.md` | 从已删除的 worktree 抢救回来的 SDD 进度账本快照（冻结） |
| `docs/backend-local-development.md` | 供给、环境变量、加锁契约、schema 说明（活文档） |
| `docs/Study_Copilot_产品总纲_v0.2.md`、`docs/Study_Copilot_一期A_详细需求文档_v0.2.md` | 产品与一期 A 需求，中文 |
| `docs/delivery.md` | 本文——交付入口 |
| `scripts/run-tests.ps1` | 上面 §3.1 的测试运行脚本 |

### 5.5 代码约定

- 4 空格缩进，一行一条语句，显式 import，不用通配符。
- `@Service @RequiredArgsConstructor` 写在同一行，`private final` 字段，构造器注入。
- Task 1–5 的文件带中文 Javadoc 头，保持这个风格。
- 代码与技术文档用英文，产品文档用中文。
- 约定式提交前缀，与既有历史一致：`feat:`、`fix:`、`chore:`、`docs:`、`style:`。
