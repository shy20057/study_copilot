# scripts

| 脚本 | 用途 |
| --- | --- |
| `run-tests.ps1` | 以 `test` profile 运行测试，连接独立的 `study_copilot_test` 库 |

测试角色 `study_copilot_test_runner` 不保存密码。脚本每次生成一次性密码、
经环境变量注入、用完即清，不落盘也不进 shell 历史。

```powershell
pwsh scripts/run-tests.ps1
pwsh scripts/run-tests.ps1 -Selection "AuthApiTest"
```

容器名和端口可用 `-Container` / `-DbPort` 或环境变量 `SC_PG_CONTAINER` 覆盖。
前置条件见 `docs/backend-local-development.md`。
