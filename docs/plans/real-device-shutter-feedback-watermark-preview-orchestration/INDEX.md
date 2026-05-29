# Real Device Shutter Feedback And Watermark Preview - Orchestration Index

## Goal

Turn the latest real-device findings into a verified implementation loop:

1. Shutter issue 2: the capture button still feels slow to recover after taking a photo. Current mainline already has the V1 data boundary, so this package must diagnose the remaining latency and design a faster perceived feedback model inspired by 行业标准 immediate press feedback, vivo-style continuous shooting readiness, and the local 参考相机应用 capture data flow notes.
2. Watermark issue 10: when the user chooses a watermark template, the live preview should show the template at the corresponding position so the user can see the expected result before shooting.

The outcome should be mergeable code branches plus a final device-oriented QA protocol. Do not declare real-device PASS unless a real-device run is recorded.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/shutter-feedback-watermark/integration`
- Functional package branches: `agent/shutter-feedback-watermark/<package-id>`
- Implementation isolation: one worktree per functional package under `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-<package-id>`.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Current Evidence

- The older research package `vivo-x300-pixel-shutter-lifecycle-orchestration` identified the original slow shutter root as UI gating tied to media postprocess/save.
- The newer implementation package `shutter-data-boundary-v1-orchestration` has already landed its core idea in current code:
  - `CameraXCaptureAdapter.captureStillImage(...)` emits `DeviceEvent.DataReceived` after `PhotoCaptureOutcome.Success`.
  - Ordinary still `emitShotCompleted(...)` is now launched from `adapterScope`, so postprocess is off the critical path.
  - `CaptureRecordingSessionProcessor.handleDataReceived(...)` clears `activeShot` for ordinary `ShotKind.STILL_CAPTURE`.
  - `shutterDisabledReason(...)` allows the shutter when `captureStatus == DATA_RECEIVED` and `activeShot == null`.
- Therefore this round must not repeat V1. It must measure and improve the remaining perceived delay:
  - press-to-visual-feedback,
  - press-to-shot-started,
  - press-to-data-received,
  - data-received-to-clickable,
  - clickable-to-actual-second-shot acceptance,
  - and postprocess/save overlap behavior.
- Local 参考相机应用 notes in `<HOME>/Applications/参考相机应用/codex/intro_capture_data_flow.md` emphasize separating fast user feedback from longer algorithm/save work.
- Local 参考相机应用 notes in `<HOME>/Applications/参考相机应用/codex/intro_watermark.md` emphasize that watermark templates should be previewed before shooting and that final capture should consume a snapshot of the chosen template configuration.
- Current OpenCamera preview already has `WatermarkHintSpec`, `WatermarkPreviewShape`, `PreviewEffectAdapter`, and `PreviewOverlayView`, but the selected watermark template in settings is not guaranteed to appear as an accurate preview template at the corresponding position when the user is choosing it.

## Product Direction

### Shutter

Target experience:

- Tap response is immediate, even if actual capture is still progressing.
- The button returns to a ready-looking state as soon as the next ordinary capture is truly accepted.
- Background save/postprocess is represented as a low-weight indicator, not as a disabled shutter.
- Special modes remain conservative: Live, multi-frame, night-like captures, recording transitions, high-pixel paths, and unstable preview/recovery states must not allow unsafe overlap.
- Real-device metrics should make the distinction visible instead of relying on subjective feel only.

Reference synthesis:

- 行业领先厂商-like direction: immediate tactile/visual press confirmation, then quietly manage processing without making the main capture control look stuck.
- vivo-like direction: keep capture cadence and clear readiness as the dominant experience, while slower computational work is backgrounded or represented elsewhere.
- 参考相机应用 direction from local docs: use staged capture data flow and task objects so quick feedback and final save quality are both protected.

### Watermark Preview

Target experience:

- Opening the watermark selector or detail page shows the selected or highlighted template on the preview surface.
- Placement, text scale, opacity, frame/background shape, and template family should be represented closely enough that the user can predict the saved result.
- Templates that expand the frame or four-border treatment should be visually differentiated from simple text overlay.
- Preview remains approximate and must not promise exact saved-photo rendering if the saved postprocessor has richer pixels.
- Capture metadata and saved output remain driven by settings/session/media pipeline, not by UI-local preview state.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths.
- Run listed verification commands through `rtk`; in a worktree use `rtk ./scripts/run_isolated_gradle.sh`.
- Commit local package changes.
- Write only their assigned coordinator status file and state row in the main checkout.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- claim 行业领先厂商/vivo parity or real-device PASS without evidence

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-shutter-latency-reference-diagnosis | none | status | completed | 1 |
| 04-watermark-template-preview | none | code | completed | 1 |
| 02-shutter-fast-feedback-runtime | 01-shutter-latency-reference-diagnosis | code | diagnosis completed | 2 |
| 03-shutter-visual-state-and-qa | 01-shutter-latency-reference-diagnosis, 02-shutter-fast-feedback-runtime | code | runtime semantics mergeable | 3 |
| 05-real-device-acceptance | 02-shutter-fast-feedback-runtime, 03-shutter-visual-state-and-qa, 04-watermark-template-preview | status | implementation packages completed | 4 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `04-watermark-template-preview -> 02-shutter-fast-feedback-runtime -> 03-shutter-visual-state-and-qa -> 05-real-device-acceptance`.
- Code dependency policy: `02` and `03` consume the diagnosis from `01`; `03` consumes runtime semantics from `02`; `05` is status/QA only and should not merge runtime code.
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
- Shutter work enables unsafe overlapping captures for special/risky capture kinds.
- UI work creates a second hidden capture/session owner.
- Watermark preview becomes the source of saved-output truth.
- Desktop/unit tests are presented as final real-device acceptance.

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-shutter-latency-reference-diagnosis.md](packages/01-shutter-latency-reference-diagnosis.md) | research/design agent | none | safe with 04 | Measure current remaining shutter latency and synthesize 行业领先厂商/vivo/Miui-inspired design targets |
| [02-shutter-fast-feedback-runtime.md](packages/02-shutter-fast-feedback-runtime.md) | implementation agent | 01 | no | Repair runtime/capture acceptance semantics only where diagnosis proves a remaining blocker |
| [03-shutter-visual-state-and-qa.md](packages/03-shutter-visual-state-and-qa.md) | UI implementation agent | 01, 02 | no | Make the button feel responsive without lying about capture safety |
| [04-watermark-template-preview.md](packages/04-watermark-template-preview.md) | implementation agent | none | safe with 01 | Show selected watermark template on preview at the corresponding position |
| [05-real-device-acceptance.md](packages/05-real-device-acceptance.md) | QA planning agent | 02, 03, 04 | no | Produce real-device timing and visual QA protocol/evidence |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all | no | Merge, verify, report, and clean up on success |

## Agent Budget

- Recommended Claude Code agents: 5 functional agents + finalize.
- Max parallel agents: 3.
- First wave: `01-shutter-latency-reference-diagnosis`, `04-watermark-template-preview`.
- Final package: `99-finalize`.
- Downstream dispatch is triggered by package tail calls to `advance`.
