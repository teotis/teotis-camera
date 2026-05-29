# i18n Multi-Language System — Orchestration Index

## Goal

为该 Android 相机应用构建完整的多语言（i18n）体系，实现：语言设定持久化、设置页一键切换全应用语言、翻译完整性自动审计、以及低开发成本的新语言支持流程。

## User Entry Points

- **Manual**: 从 `launchers/agent-prompts.md` 复制 prompt 到任意 agent 平台。
- **Script**: 运行 `bash launchers/orchestrate.sh start` 自动启动首波 agent；可通过 `claude agents` 查看 Claude Code 后台 agent。
- **Status**: 运行 `bash launchers/orchestrate.sh status`。
- **Retry**: 运行 `bash launchers/orchestrate.sh retry <package-id>`。
- **Manual advancement fallback**: 运行 `bash launchers/orchestrate.sh advance`。

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/i18n-multi-language-system`
- Mainline branch: `main`
- Integration branch: `feat/i18n-multi-language`
- Functional package branches: `agent/i18n/<package-id>`
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
| 01-i18n-core-infrastructure | none | status | completed | 1 |
| 02-translation-audit-completeness | none | status | completed | 1 |
| 03-i18n-developer-workflow | 01-i18n-core-infrastructure, 02-translation-audit-completeness | status | both upstream completed | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: 01-i18n-core-infrastructure, 02-translation-audit-completeness, 03-i18n-developer-workflow
- Code dependency policy: status dependency — 03 depends on 01 and 02 being completed but does not require merge-to-integration first
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
| 01-i18n-core-infrastructure | autonomous | Claude Code | n/a | tests + assembleDebug | none | normal graph |
| 02-translation-audit-completeness | autonomous | Claude Code | n/a | audit script output + assembleDebug | none | normal graph |
| 03-i18n-developer-workflow | autonomous | Claude Code | n/a | script + doc + assembleDebug | none | normal graph |
| real-device-language-switch-qa | external-assist | user/Codex/device owner | requires physical device to visually verify language switch renders correctly in all panels | APK path, install command, smoke checklist | screenshot/video of each panel in target language | release confidence only |
