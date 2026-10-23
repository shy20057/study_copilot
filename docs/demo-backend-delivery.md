# 后端 Demo 交付（2026-10-23）

按用户最新要求收敛：直接实现代码，停止新增/执行测试和独立评审。最后一次验证仅为跳过测试的 Maven 主代码编译，BUILD SUCCESS；后续两处接口接线修改未再次编译。没有启动应用、执行 V5 迁移或做接口联调，不能视为运行验收通过。

## 已有功能
- 注册、登录、刷新/退出、改密、人工恢复说明；设置、科目、学习日、引导和模板导入。
- 任务创建/编辑/完成/重开/取消、父子联动、5 秒软删除恢复、全量筛选计数和原子排序。
- 重复模板维护、首次访问生成当天实例、选中任务顺延及 5 秒批次撤销。
- 专注/休息唯一运行会话、服务端计时、到期补偿、提前结束、原子休息转专注。
- 固定历史规则的跨日切片、按日经验上限、伙伴类型/十级成长、成果展示权。
- 手动补录的增改删恢复与重叠保护、记录分页、控制台聚合和 dataVersion。

本批新增 JDBC 接口返回数据库字段名（例如 subject_id、start_at、version）；请求使用下列 camelCase。前端接入时统一适配即可。

## 最小接入约定
所有请求除 auth 外带 Authorization: Bearer <accessToken>。关键写入带 Idempotency-Key；已有资源写入传 expectedVersion。

- GET/POST /api/tasks；PUT/DELETE /api/tasks/{id}；POST /{id}/complete、reopen、cancel、restore；PUT /api/tasks/reorder。
- 创建任务：{"title":"复习一章","subjectId":1,"targetDate":"2026-10-23","plannedPomodoros":1}。
- 排序：{"targetDate":"2026-10-23","status":"ALL","items":[{"id":1,"expectedVersion":0}]}，可带 parentId。
- GET/POST /api/tasks/repeat-templates；PUT /api/tasks/repeat-templates/{id}。
- 重复模板：title、subjectId、repeatRule（DAILY/WEEKDAYS/WEEKLY）、weekdays（1–7）、startDate、可选 endDate、plannedPomodoros、priority；更新加 expectedVersion。
- POST /api/tasks/carry-over：{"items":[{"id":1,"expectedVersion":0}],"conflictChoice":"KEEP_BOTH"}；POST /api/tasks/carry-over/{batchId}/undo。
- POST /api/focus/start：{"subjectId":1,"taskId":1,"minutes":25}；GET /api/focus/current。
- POST /api/focus/{id}/complete；POST /api/focus/{id}/end-early：{"claimPartialReward":true}。
- POST /api/focus/break/start：{"mode":"SHORT","minutes":5}；POST /api/focus/break/end-and-start（与 start 同请求）。
- POST /api/focus/{id}/presentation/claim、ack：{"deviceId":"browser-demo"}。
- GET /api/focus/records?learningDate=2026-10-23&page=1&pageSize=20。
- POST /api/focus/records/manual：{"subjectId":1,"startAt":"2026-10-23T01:00:00Z","endAt":"2026-10-23T01:25:00Z"}。
- PUT/DELETE /api/focus/records/{id}；POST /api/focus/records/{id}/restore；删除用查询参数 expectedVersion，修改/恢复放请求体。
- GET /api/dashboard；GET /api/pet/status、options；POST /api/pet/switch（请求体直接为数字 1–5）。

## 运行
依赖与环境变量见 backend-local-development.md。复用既有 PostgreSQL / Redis / MinIO；本次没有创建额外中间件。
V1–V4 保持原样，新增 V5__data_version.sql 将由 Flyway 在应用启动时执行。
必填包含 SC_DB_URL、SC_DB_USER、SC_DB_PASSWORD、SC_JWT_SECRET、SC_IDEMPOTENCY_HMAC_SECRET、SC_RECOVERY_SUPPORT_CHANNEL，以及现有 Redis/MinIO 凭据占位变量。
启动：mvn -f backend/pom.xml spring-boot:run

## 本次停止位置
这是最小后端 Demo 代码交付，不是一期全部验收。以下明确未完成：
- 头像上传仍为原始占位服务；账户注销/数据清理未实现。
- 登录渐进限流、完整运维指标、备份恢复演练未实现。
- 重复模板当前编辑仅作用后续实例；取消父任务后恢复不恢复子任务原状态；删除撤销元数据为简化实现。
- 新增计时、任务、统计模块未做运行/并发验证；早期测试不代表它们已验证。
- 前端尚未编写；开始前仍需使用 UI skills 与用户确认设计。

