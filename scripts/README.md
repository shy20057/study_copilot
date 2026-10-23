# 脚本使用说明

[返回文档导航](../docs/README.md)

| 脚本 | 用途 |
| --- | --- |
| `run-tests.ps1` | 以 `test` 配置运行测试，连接独立的 `study_copilot_test` 库 |
| `backup-backend.ps1` | 数据库自定义格式备份与默认 7 天的归档保留；操作要求见 [运维手册](../docs/05-运维部署/后端运维手册.md) |
| `restore-backend.ps1` | 仅恢复到名称符合要求的独立空库；操作要求见 [运维手册](../docs/05-运维部署/后端运维手册.md) |

以下为测试脚本使用说明，执行测试前需获得用户明确批准。

测试角色 `study_copilot_test_runner` 不保存密码。脚本每次生成一次性密码、
经环境变量注入、用完即清，不落盘也不进入命令行历史。

```powershell
pwsh scripts/run-tests.ps1
pwsh scripts/run-tests.ps1 -Selection "AuthApiTest"
```

容器名和端口可用 `-Container` / `-DbPort` 或环境变量 `SC_PG_CONTAINER` 覆盖。
前置条件见 [后端本地开发](../docs/02-开发指南/后端本地开发.md)。
