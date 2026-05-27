# Real Device UX Regression 20260527 - Orchestration Index

## Goal

Turn the 2026-05-27 real-device screenshots and notes into a tail-driven multi-agent execution plan for OpenCamera. The plan covers:

- zoom slider threshold behavior: crossing `2x` and `5x` must switch the preview/runtime to the corresponding lens node instead of only dragging a digital zoom value;
- Dev console bottom `关闭` action becomes same-style `清理` for the current log category;
- Quick reset button style matches the other Quick panel buttons;
- Style / Color Lab no longer shows the bottom `关闭色调` action;
- bottom mode track exposes Portrait;
- right-side `开发` rail button matches the other rail buttons;
- cockpit preview and bottom deck density is corrected so the bottom area is compact and the top bar sits on a black/scrimmed background;
- Document mode organizer panel becomes compact and usable, and one tap on close actually closes it.

Dynamic progress belongs in `status/`; this index is the static execution contract.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-regression-20260527-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/real-device-ux-regression-20260527/integration`
- Functional package branches: `agent/real-device-ux-regression-20260527/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Worktree root convention: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-regression-20260527/<package-id>`
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.
- Package agents must use `rtk` for shell commands and `rtk ./scripts/run_isolated_gradle.sh ...` inside their assigned worktrees.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package doc.
- Run listed verification commands through `rtk`.
- Commit local package changes.
- Write only their assigned coordinator status file and the matching row in `status/state.tsv`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-ux-regression-20260527-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- claim real-device visual acceptance from desktop tests alone

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-zoom-threshold-lens-switch | none | status | initial ready package | 1 |
| 02-cockpit-layout-mode-entry-density | none | status | initial ready package | 1 |
| 03-panel-control-polish | none | status | initial ready package | 1 |
| 04-document-organizer-compact-close | 02-cockpit-layout-mode-entry-density | code | upstream branch merged to integration branch or explicit branch base | 2 |
| 05-integration-visual-smoke-protocol | 01-zoom-threshold-lens-switch, 02-cockpit-layout-mode-entry-density, 03-panel-control-polish, 04-document-organizer-compact-close | status+code | upstream package evidence complete or branches merged to integration | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-zoom-threshold-lens-switch -> 02-cockpit-layout-mode-entry-density -> 03-panel-control-polish -> 04-document-organizer-compact-close -> 05-integration-visual-smoke-protocol`
- Code dependency policy: packages 01, 02, and 03 can run in parallel. Package 04 must account for package 02 layout decisions before final verification. Package 05 should run after all implementation branches are available or merged to the integration branch.
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
- A package moves camera runtime ownership into UI or creates a hidden second session kernel.
- A package changes zoom or document behavior without focused tests and explicit device-smoke residual risk.

## Package Summary

| Package | Purpose | Key Allowed Area |
|---|---|---|
| [01-zoom-threshold-lens-switch.md](packages/01-zoom-threshold-lens-switch.md) | Make slider crossing `2x` / `5x` request the corresponding lens/runtime node, with hysteresis and tests | zoom UI, session/device zoom contract, CameraX adapter |
| [02-cockpit-layout-mode-entry-density.md](packages/02-cockpit-layout-mode-entry-density.md) | Expose Portrait in the mode track and tighten preview/top/bottom cockpit density | `activity_main.xml`, mode track renderer, UI resources |
| [03-panel-control-polish.md](packages/03-panel-control-polish.md) | Convert Dev close to current-log cleanup, normalize Quick reset, remove Style close action, unify Dev rail style | panel layout/render/action files |
| [04-document-organizer-compact-close.md](packages/04-document-organizer-compact-close.md) | Redesign Document organizer as compact usable UI and fix one-tap close | document organizer layout/render/router |
| [05-integration-visual-smoke-protocol.md](packages/05-integration-visual-smoke-protocol.md) | Run local integration checks and write the real-device smoke protocol for the screenshots | verification only plus coordinator status |
| [99-finalize.md](packages/99-finalize.md) | Merge, verify, report, and clean up recorded resources after success | integration branch and coordinator files |

## Current Evidence Snapshot

- `FocalLengthSliderView` and `FocalLengthSliderViewTest` exist, including preset labels and snap behavior; the new requirement is not just label/snap but runtime lens/node handoff when crossing `2x` and `5x`.
- Existing zoom/brightness rollback orchestration is adjacent. Package 01 must inspect it first and avoid duplicating already-landed drag-latch fixes.
- `CockpitSurfaceRenderer.renderModeTrack(...)` currently maps Photo, Humanistic, Video, and Document, while Portrait is explicitly hidden.
- `activity_main.xml` currently constrains preview to `modeTrackScroll`, with bottom cockpit below it; screenshots show the bottom deck is visually too tall and the top area lacks the desired black/scrimmed framing.
- Quick reset currently uses `Widget.OpenCamera.PillButton` while other Quick rows use `Widget.OpenCamera.QuickBubbleButton`.
- The right rail Dev button currently uses `Widget.OpenCamera.RightRailDevButton`, separate from the normal rail style.
- Dev log cleanup/export plumbing exists (`DevLogExporter`, `cleanupByType`, `cleanupAll`, `DevLogRenderModelTest`, `DevLogExporterTest`), but the visible bottom action is still `关闭`.
- Style/Color Lab bottom close is `buttonCloseFilter` with `button_filter_close` / `关闭色调`.
- Document organizer UI exists, but it is large (`NestedScrollView` constrained from top panel to the secondary guide) and the close action currently toggles the route rather than issuing an explicit close/dismiss command.
