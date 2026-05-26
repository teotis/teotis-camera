# Deep Audit & Optimization Analysis - Orchestration Index

## Goal

对 OpenCamera 项目进行全面、彻底、深度的项目检查、优化分析和架构分析。利用 abstraction-architect、renewal-architect、systematic-debugging 等专业分析技能，产出高质量的分析报告和优化建议。

**这是一个纯分析任务，不修改任何源代码，只产出分析报告到 status 目录。**

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/deep-audit-optimization-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/deep-audit/integration`
- Functional package branches: `agent/deep-audit/<package-id>`
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
| 01-architecture-structural-analysis | none | status | completed | 1 |
| 02-technical-debt-audit | none | status | completed | 1 |
| 03-module-dependency-analysis | none | status | completed | 1 |
| 04-code-quality-metrics | none | status | completed | 1 |
| 05-session-kernel-deep-dive | 01-architecture-structural-analysis | status | completed | 2 |
| 06-performance-optimization-analysis | 03-module-dependency-analysis | status | completed | 2 |
| 07-test-coverage-gap-analysis | 04-code-quality-metrics | status | completed | 2 |
| 08-architecture-boundary-violations | 01-architecture-structural-analysis, 02-technical-debt-audit | status | completed | 2 |
| 09-optimization-roadmap | 05-session-kernel-deep-dive, 06-performance-optimization-analysis, 07-test-coverage-gap-analysis, 08-architecture-boundary-violations | status | completed | 3 |
| 10-executable-action-plan | 09-optimization-roadmap | status | completed | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01, 02, 03, 04, 05, 06, 07, 08, 09, 10
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
