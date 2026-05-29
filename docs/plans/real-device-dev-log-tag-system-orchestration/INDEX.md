# Real Device Dev Log Tag System - Orchestration Index

## Goal

真机实测发现的三个问题统一修复：

1. **色彩实验室页面滑动**：ColorLab 模式下内容较短，不应支持滑动。
2. **删除"耗时"、链路打印耗时**：Dev Console summary 不再展示基于 `.timing` 后缀的"最后耗时"；LINK 链路 Tab 本身具备耗时分析支持——记录每个关键环节的时间点与耗时，链路事件管线从 `PerformanceLinkRecorder` 贯通到渲染层。
3. **开发日志标签系统**：用 `DevLogTag` 枚举替代硬编码事件名集合分类，支持一个事件同时属于多个标签（分类）。未显式标注的事件通过名称规则推断标签实现平滑迁移。

## Related Orchestration

本编排是 [real-device-performance-link-logs-orchestration](../real-device-performance-link-logs-orchestration/INDEX.md) (已 finalized) 的后续。前者建立了 `PerformanceLinkEvent` 契约、核心流程插桩和 Dev LINK Tab 骨架；本次在其基础上修复真机暴露的三个具体问题。

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: /Volumes/Extreme_SSD/project/open_camera
- Coordinator plan root: /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-dev-log-tag-system-orchestration
- Mainline branch: main
- Integration branch: agent/dev-log-tag-system/integration
- Functional package branches: `agent/dev-log-tag-system/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file.
- Update the state ledger only through `bash <plan-root>/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes or intermediate artifacts only under their assigned scratch path from `bash <plan-root>/launchers/orchestrate.sh scratch-path <package-id>`.
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

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-dev-log-tag-system | none | status | completed | 1 |
| 99-finalize | 01-dev-log-tag-system | status+code | 01 completed | final |

## Merge Strategy

- Functional merge order: 01-dev-log-tag-system
- Code dependency policy: status dependency only (single package)
- Conflict owner: `99-finalize`
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-dev-log-tag-system | autonomous | Claude Code | n/a | tests, build | none | normal graph |
| real-device-qa | external-assist | user/Codex | requires physical device for visual scrolling check and LINK timing log review | APK path, install command, logs | screenshot of Color Lab page, exported link logs | release only |
