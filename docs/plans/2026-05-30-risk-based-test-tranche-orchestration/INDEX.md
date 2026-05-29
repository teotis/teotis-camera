# Risk-Based Test Tranche - Orchestration Index

## Goal
对 8-12 个最高 ROI 无测试类补全单元测试。基于风险分级，优先覆盖纯逻辑/配置/文本解析/持久化类，对高风险 Android/CameraX/UI 类仅做 testability audit 报告。

## User Entry Points
- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy
- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/2026-05-30-risk-based-test-tranche-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/test-tranche/integration`
- Functional package branches: `agent/test-tranche/<package-id>`
- Implementation isolation: one worktree per functional package.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches.

## Authorization
Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash <plan-root>/launchers/orchestrate.sh mark-state ...`.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

Forbidden without explicit user approval:
- force-push, hard reset, delete branches/worktrees not recorded by this orchestration
- delete remote branches, add secrets, edit outside allowed paths

## Dependency Graph
| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-settings-codecs-tests | none | status | completed | 1 |
| 02-device-media-pure-tests | none | status | completed | 1 |
| 03-app-logic-tests | none | status | completed | 1 |
| 04-app-mixed-tests | none | status | completed | 1 |
| 05-testability-audit | none | status | completed | 1 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy
- Functional merge order: 01, 02, 03, 04, 05
- Code dependency policy: status dependency (all independent)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions
- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## Capability Preflight
| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-settings-codecs-tests | autonomous | Claude Code | n/a | tests/build | none | normal graph |
| 02-device-media-pure-tests | autonomous | Claude Code | n/a | tests/build | none | normal graph |
| 03-app-logic-tests | autonomous | Claude Code | n/a | tests/build | none | normal graph |
| 04-app-mixed-tests | autonomous | Claude Code | n/a | tests/build | none | normal graph |
| 05-testability-audit | autonomous | Claude Code | n/a | audit report (Markdown) | none | normal graph |
| 99-finalize | autonomous | Claude Code | n/a | integration tests | none | normal graph |

## Test Quality Contract
每个新增测试必须满足：
- 能在 `rtk ./gradlew --no-daemon :app:testDebugUnitTest` 或对应模块 test task 中稳定运行
- 不依赖真实设备、真实相机、真实系统权限
- 避免过度 mock 私有实现
- 优先测试 public contract 和 observable behavior
- 每个类说明测试覆盖了哪些行为、哪些行为暂时不适合单测
- 使用 `unitTests.isReturnDefaultValues = true` 处理 Android 框架默认值
