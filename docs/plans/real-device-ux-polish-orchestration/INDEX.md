# Real Device UX Polish - Orchestration Index

## Goal

Turn the latest real-device findings into a tail-driven multi-agent execution plan for OpenCamera: restore Humanistic/Portrait mode entrances, clean Style/Filter copy, make Settings third-level navigation direct, dismiss Quick on outside taps, unify persisted adjustments with Reset controls, and add Stage 7-aligned Dev log storage governance. Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/real-device-ux-polish/integration`
- Functional package branches: `agent/real-device-ux-polish/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-polish/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- The current main checkout has unrelated dirty/conflict state. Package agents must use their assigned worktree and must not clean unrelated conflicts from the main checkout.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file and the matching row in `status/state.tsv`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-polish-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- resolve unrelated main-checkout conflicts

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 00-mode-entry-visibility | none | status | initial ready package | 1 |
| 03-quick-panel-outside-dismiss | 00-mode-entry-visibility | status | 00 completed | 2 |
| 05-dev-log-storage-governance | 00-mode-entry-visibility | status | 00 completed | 2 |
| 01-style-copy-noise-cleanup | 00-mode-entry-visibility, 03-quick-panel-outside-dismiss, 05-dev-log-storage-governance | status | upstream packages completed | 3 |
| 02-settings-third-level-navigation | 01-style-copy-noise-cleanup | status | 01 completed | 4 |
| 04-persistence-reset-unification | 01-style-copy-noise-cleanup, 02-settings-third-level-navigation, 03-quick-panel-outside-dismiss | code | upstream package branches merged to integration or explicit rebase/base evidence recorded | 5 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `00-mode-entry-visibility -> 03-quick-panel-outside-dismiss -> 05-dev-log-storage-governance -> 01-style-copy-noise-cleanup -> 02-settings-third-level-navigation -> 04-persistence-reset-unification`
- Code dependency policy: package agents may proceed on status dependencies, but `04-persistence-reset-unification` should base on or rebase after the package branches it integrates. `99-finalize` resolves all final code integration by merging functional branches into the integration branch in the order above.
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
- A package claims real-device UX completion without local deterministic tests or an explicit “needs real-device smoke” residual risk.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| 00-mode-entry-visibility | Restore Humanistic and Portrait visibility through catalog/bottom mode/availability chain | `core/mode`, mode track/render files |
| 03-quick-panel-outside-dismiss | Close Quick on unrelated outside taps without triggering capture/focus/mode actions | Quick route/render/layout files |
| 05-dev-log-storage-governance | Add 20MB log cap and one-tap type cleanup | Dev log/export/render files |
| 01-style-copy-noise-cleanup | Rename Lens to Style and remove meaningless selected-filter copy | Style/filter render/text files |
| 02-settings-third-level-navigation | Route Settings Portrait/Watermark rows to third-level pages | Settings router/render/action files |
| 04-persistence-reset-unification | Persist and reset Settings/Style/Color Lab/Quick adjustments coherently | `core/settings` and settings manager/render files |
| 99-finalize | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |

## Current Evidence Snapshot

- `ModeId` already includes `HUMANISTIC` and `PORTRAIT`; `ModeRegistry.supportedModes(...)` filters plugin availability through `isSupported(...)`.
- App mode visibility converges in `SessionCockpitRenderModel.visibleModeEntryOrder(...)`, `modeDirectoryRenderModel(...)`, `modeTrackRenderModel(...)`, `CockpitSurfaceRenderer`, `MainActivityViews`, `activity_main.xml`, and focused app tests.
- Settings already has `SettingsSubpage.PORTRAIT_LAB`, `WATERMARK_SELECTOR`, and `WATERMARK_DETAIL`, plus corresponding panel commands.
- Quick panel already uses `CockpitPanelRoute.QuickBubble` and `panelDismissScrim`; real-device behavior suggests scrim/hit region or z-order misses unrelated lower regions.
- Style/Color Lab already has persisted actions for filters, custom profiles, Color Lab spec, and style strength, but selected-filter copy and reset semantics need one coherent UX contract.
- Dev logs currently render in-memory trace events and export timestamped files under `debug-logs`; no current evidence of a 20MB cap or type-specific cleanup owner was found.

## Claude Background Notes

- Local Claude Code version checked while creating this kit: `2.1.142 (Claude Code)`.
- `orchestrate.sh` launches background agents with `claude --bg --name`.
- Generated scripts default to inheriting the user's configured Claude Code permission mode by omitting `--permission-mode`.
- Set `CLAUDE_PERMISSION_MODE=bypassPermissions`, `auto`, `default`, or another supported mode only when an explicit per-dispatch override is needed.
- If using `CLAUDE_PERMISSION_MODE=auto`, run `claude --permission-mode auto` interactively once before background dispatch.
- Never use `--dangerously-skip-permissions`.
