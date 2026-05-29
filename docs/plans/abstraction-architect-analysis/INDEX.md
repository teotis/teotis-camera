# Abstraction Architect Analysis - Orchestration Index

## Goal

对 OpenCamera 项目进行深度结构抽象分析，找出缺失不变量、重复领域表示、不稳定边界、转换胶水、平台/配置分支和中心编排瓶颈。**纯分析任务，不修改任何源代码，只产出分析报告（HTML）。**

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/abstraction-architect-analysis`
- Mainline branch: `main`
- Integration branch: `agent/abstraction-analysis/integration`
- Functional package branches: `agent/abstraction-analysis/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths (status files only).
- Run listed verification commands.
- Commit local package changes (status files only).
- Write only their assigned coordinator status file and state row.
- Call `bash <plan-root>/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to mainline.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths
- **修改任何源代码文件**（这是纯分析任务）

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-architecture-boundary-integrity | none | status | completed | 1 |
| 02-domain-model-unification | none | status | completed | 1 |
| 03-session-kernel-invariants | none | status | completed | 1 |
| 04-cross-cutting-concerns | none | status | completed | 1 |
| 05-conversion-glue-detection | 01, 02, 03, 04 | status | completed | 2 |
| 06-platform-configuration-branches | 01, 02, 03, 04 | status | completed | 2 |
| 07-synthesis-html-report | 01, 02, 03, 04, 05, 06 | status | completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 02, 03, 04, 05, 06, 07
- Code dependency policy: status dependency (each package writes to its own status file only)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths (any source code modification).
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-architecture-boundary-integrity | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 02-domain-model-unification | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 03-session-kernel-invariants | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 04-cross-cutting-concerns | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 05-conversion-glue-detection | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 06-platform-configuration-branches | autonomous | Claude Code | n/a | static analysis report | none | normal graph |
| 07-synthesis-html-report | autonomous | Claude Code | n/a | HTML report | none | normal graph |

## Analysis Dimensions

每个分析包聚焦一个抽象维度：

1. **架构边界完整性** — 层间依赖方向、Session Kernel 边界、Mode Plugin 隔离
2. **领域模型统一** — 重复表示、缺失不变量、可合并的类型
3. **Session Kernel 不变量** — 状态机完整性、恢复缺口、隐式状态
4. **横切关注点** — 日志/配置/可观测性的散落耦合
5. **转换胶水检测** — 不必要的映射层、接口转换开销
6. **平台配置分支** — 设备特定代码路径、分支模式
7. **综合报告** — 整合所有发现到交互式 HTML
