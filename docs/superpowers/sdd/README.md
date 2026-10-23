# SDD 执行记录

本目录保存 superpowers SDD（Spec-Driven Development）流程的执行产物，
原本存放在 `.worktrees/phase-a-backend/.superpowers/sdd/`（已被 git 忽略）。
该 worktree 完全合入 main 后删除，产物在此保留。

| 文件 | 内容 |
| --- | --- |
| `task-N-brief.md` | 从主计划切片出的单任务执行说明 |
| `task-N-report.md` | 实施结论、契约推导、review 结论与 carry-forward 义务 |
| `../development-ledger.md` | 总体进度账本（含未完成任务的清单） |

**主计划**：`docs/superpowers/plans/2026-10-22-study-copilot-phase-a-backend.md`
（904 行，覆盖 Task 1–13 的步骤级细节，是权威来源；本目录是其执行侧记录）

**Task 6–11 的说明**：任务状态在账本中未勾选，因为它们是在账本最后一次更新后，
按"直接实现、不再走 TDD/独立评审"的指令一次性补上的（commit `1427692`）。
这些任务的 report 因此缺失，实现契约以代码和 `docs/demo-backend-delivery.md` 为准。

原目录中的 `.diff`（review 快照）可由 `git diff <a>..<b>` 重新生成；
`.log` 为一次性测试输出，未保留。
