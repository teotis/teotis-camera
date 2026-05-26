# Zoom Cockpit V2 Productization - Orchestration Index

## Goal

Turn the existing `ApplyZoomRatio`, `FocalLengthSliderView`, zoom capability, and render-model foundation into a product-level Zoom Cockpit V2: preset dots plus continuous focal slider, one-decimal zoom/focal label, explicit supported/degraded/unsupported capability boundaries, and recording-state restrictions that do not create a second hidden camera runtime owner in UI.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/zoom-cockpit-v2-productization/integration`
- Functional package branches: `agent/zoom-cockpit-v2-productization/<package-id>`
- Implementation isolation: one worktree per functional package unless explicitly excepted.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands.
- Commit local package changes.
- Write only their assigned coordinator status file and state row.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/zoom-cockpit-v2-productization-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
| 01-product-contract-capability-boundary | none | status | initial ready wave | 1 |
| 02-slider-widget-productization | none | status | initial ready wave | 1 |
| 03-session-recording-zoom-policy | 01-product-contract-capability-boundary | status | dependency `completed` in `status/state.tsv` and status file | 2 |
| 04-cockpit-wiring-and-ux-integration | 01-product-contract-capability-boundary, 02-slider-widget-productization, 03-session-recording-zoom-policy | code | upstream package branches completed and ready for integration base | 3 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-product-contract-capability-boundary` -> `02-slider-widget-productization` -> `03-session-recording-zoom-policy` -> `04-cockpit-wiring-and-ux-integration`
- Code dependency policy: status dependencies unlock implementation; `99-finalize` merges all completed package branches into the integration branch before integration verification.
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

## Package Documents

| Work Package | Purpose |
| --- | --- |
| [01-product-contract-capability-boundary.md](./packages/01-product-contract-capability-boundary.md) | Define product/capability/render semantics for dots, slider, label, unsupported/degraded states, and recording availability. |
| [02-slider-widget-productization.md](./packages/02-slider-widget-productization.md) | Productize `FocalLengthSliderView` math, continuous release, snap threshold, preset dot taps, and one-decimal label. |
| [03-session-recording-zoom-policy.md](./packages/03-session-recording-zoom-policy.md) | Enforce zoom clamp/snap and recording policy in Session Kernel while keeping UI as dispatcher. |
| [04-cockpit-wiring-and-ux-integration.md](./packages/04-cockpit-wiring-and-ux-integration.md) | Wire render model and widget callbacks into the cockpit without using `ZoomRatioToggled` for exact controls. |
| [99-finalize.md](./packages/99-finalize.md) | Merge completed package branches, run integration verification, merge back to mainline, report, and clean up only after success. |

## Local CLI Check

- Local `claude --version`: `2.1.142 (Claude Code)`.
- Local `claude agents --help` confirms the `agents` command and options for `--cwd`, `--model`, `--effort`, `--permission-mode`, and `--setting-sources`.
