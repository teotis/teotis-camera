# Shutter Data Boundary V1 - Orchestration Index

## Goal

Implement the current shutter lifecycle understanding as a verified Stage 7-local closed loop: normal still capture should re-arm the shutter when CameraX has acquired/written the frame (`DataReceived`), while postprocess, watermarking, media result assembly, diagnostics, and thumbnail updates continue without blocking the next valid capture. Special or risky capture kinds remain conservative until real-device evidence proves otherwise. This package must preserve recording stop semantics, avoid a hidden second session kernel, and produce mergeable implementation branches plus a final integration merge.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/shutter-data-boundary-v1/integration`
- Functional package branches: `agent/shutter-data-boundary-v1/<package-id>`
- Implementation isolation: one worktree per functional package under `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-data-boundary-v1-<package-id>`.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Current Evidence

- `CameraXCaptureAdapter.captureStillImage(...)` already emits `DeviceEvent.DataReceived` before `emitShotCompleted(...)`.
- `CameraXCaptureAdapter.emitShotCompleted(...)` still runs `guardedPostProcess(mediaPostProcessor, rawResult)` before `DeviceEvent.ShotCompleted`, so the effect collector can remain blocked by postprocess work.
- `CaptureRecordingSessionProcessor.handleDataReceived(...)` currently only sets `CaptureStatus.DATA_RECEIVED`; it keeps `activeShot` intact.
- `SessionCockpitRenderModel.shutterDisabledReason(...)` still blocks when photo `activeShot != null` and when status is `SAVING` or `DATA_RECEIVED`.
- Therefore, the right implementation is not a pure UI change. It requires both the device adapter boundary and the session/UI gate to agree that ordinary still capture is safe to re-arm at data received.

## Authorization

Package agents are authorized to:
- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands through `rtk`; in a worktree use `rtk ./scripts/run_isolated_gradle.sh`.
- Commit local package changes.
- Write only their assigned coordinator status file and state row in the main checkout.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/shutter-data-boundary-v1-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

`99-finalize` is authorized by default to perform incremental orchestration operations for this plan:
- Inspect package docs, status files, state, branches, commits, and diffs.
- Create/update the integration branch.
- Merge package branches into the integration branch according to Merge Strategy.
- Run integration verification.
- Merge the verified integration branch back to `main`.
- Write `FINAL_REPORT.md` and `status/99-finalize.md`.
- Delete only local branches/worktrees created and recorded by this orchestration after every finalize step succeeds.

Forbidden without explicit user approval:
- force-push
- hard reset
- delete branches/worktrees not recorded as created by this orchestration
- delete remote branches
- add secrets or credentials
- edit outside allowed paths
- cross Stage boundaries or declare Stage 7 complete

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-adapter-data-boundary | none | code | completed | 1 |
| 02-session-rearm-policy | none | code | completed | 1 |
| 03-ui-shutter-gate-and-qa | 02-session-rearm-policy | code | upstream completed and mergeable | 2 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-adapter-data-boundary -> 02-session-rearm-policy -> 03-ui-shutter-gate-and-qa`.
- Code dependency policy: `03` depends on `02`; `99-finalize` merges to the integration branch in the order above and resolves only trivial documentation/status conflicts. Code conflicts stop finalize.
- Conflict owner: `99-finalize`.
- Mainline merge: local non-force merge after integration verification passes.
- Cleanup: delete only recorded local package worktrees/branches after all finalize steps succeed.

## Stop Conditions

- Any functional package is `blocked`, `stale`, or `invalid`.
- Graph has duplicate package IDs, missing dependencies, or cycles.
- Package evidence is incomplete.
- Package changed forbidden paths.
- Merge conflict or verification failure occurs.
- Status/state mismatch cannot be reconciled.
- Implementation makes night/multi-frame/high-pixel/special capture re-arm early without an explicit tested policy.
- Implementation breaks active recording shutter stop semantics.
