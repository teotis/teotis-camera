# Capture Readiness Sound Timing - Orchestration Index

## Goal

Optimize the moment after the user presses the photo shutter:

- Keep the shutter button in a loading/capture state until the frame has reached the earliest reliable "user may relax / next shot may be accepted" boundary.
- Move shutter sound away from the current "activeShot appears" timing and toward that capture-readiness boundary.
- Research whether CameraX/Camera2 can expose a boundary earlier than the current `ImageCapture.OnImageSavedCallback`-driven `DataReceived` event, close to vendor camera behavior where the lower layer signals as soon as frame acquisition/readout is done.
- Preserve the existing Stage 7 session ownership rules: UI renders state and dispatches intents only, Session Kernel owns runtime capture state, Device Adapter translates platform callbacks into device events, and special/risky capture modes remain conservative.

The outcome is mergeable implementation branches plus a real-device timing protocol. Do not claim vendor system-camera parity or real-device PASS without device evidence.

## User Entry Points

- Manual: copy prompts from `launchers/agent-prompts.md` into any agent platform.
- Script: run `bash launchers/orchestrate.sh start`; view Claude Code agents with `claude agents`.
- Status: run `bash launchers/orchestrate.sh status`.
- Retry: run `bash launchers/orchestrate.sh retry <package-id>`.
- Manual advancement fallback: run `bash launchers/orchestrate.sh advance`.

## Repository And Branch Policy

- Main checkout: `/Volumes/Extreme_SSD/project/open_camera`
- Coordinator plan root: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration`
- Mainline branch: `main`
- Integration branch: `agent/capture-readiness-sound/integration`
- Functional package branches: `agent/capture-readiness-sound/<package-id>`
- Implementation isolation: one worktree per functional package under `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/capture-readiness-sound-<package-id>`.
- Coordinator status/state files are not implementation artifacts and must not be committed on package branches unless explicitly requested.

## Current Evidence

- `MainActivity.maybePlayShutterSound(...)` currently plays `MediaActionSound.SHUTTER_CLICK` when `state.activeShot` first appears for a photo. That is close to request/start feedback, not "frame acquired / user can drop the phone" feedback.
- `CameraXCaptureAdapter.captureStillImage(...)` currently emits `DeviceEvent.ShotStarted` before `ImageCapture.takePicture(...)`, then emits `DeviceEvent.DataReceived` after `PhotoCaptureOutcome.Success`.
- For ordinary still capture, `emitShotCompleted(...)` now runs off the critical path after `DataReceived`, so postprocess/save completion does not have to block shutter re-arm.
- `CaptureRecordingSessionProcessor.handleDataReceived(...)` clears `activeShot` for ordinary `ShotKind.STILL_CAPTURE`, but keeps special paths conservative.
- `SessionCockpitRenderModel.shutterDisabledReason(...)` allows the shutter when `activeShot == null` and `captureStatus` is `DATA_RECEIVED` or `SAVING`; `shutterVisualState(...)` can show `BACKGROUND_SAVING` while the shutter remains enabled.
- The remaining question is whether `DataReceived` is still too late because it is tied to `OnImageSavedCallback`, and whether CameraX `OnImageCapturedCallback`, CameraX/camera2 interop callbacks, or Camera2 capture callbacks can safely provide an earlier milestone without taking over image saving or creating a second capture pipeline.

## Authorization

Package agents are authorized to:

- Create or reuse only their assigned worktree and branch.
- Edit only allowed paths in their package document.
- Run listed verification commands through `rtk`; in a worktree use `rtk ./scripts/run_isolated_gradle.sh`.
- Commit local package changes.
- Write only their assigned coordinator status file in the main checkout.
- Update the state ledger only through `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state ...`; do not edit `state.tsv` manually.
- Write temporary, non-sensitive shared working notes only under their assigned scratch path from `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh scratch-path <package-id>`.
- Call `bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from <package-id>` after recording final status.

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
- claim CameraX/Camera2 exposes a hardware frame-end signal without code/API evidence
- play shutter sound at press time while describing it as frame-end/readiness feedback
- allow unsafe overlap for Live, multi-frame, night-like, high-pixel, recording transition, recovery, or unsupported capture paths

## Dependency Graph

| Package | Depends On | Dependency Type | Unlock Condition | Wave |
|---|---|---|---|---|
| 01-camerax-camera2-signal-feasibility | none | status | completed | 1 |
| 02-current-shutter-timing-tests | none | code | completed | 1 |
| 03-capture-readiness-contract | 01-camerax-camera2-signal-feasibility, 02-current-shutter-timing-tests | code | feasibility and baseline tests completed | 2 |
| 04-adapter-earliest-ready-signal | 01-camerax-camera2-signal-feasibility, 03-capture-readiness-contract | code | contract exists and feasibility result is known | 3 |
| 05-shutter-sound-and-visible-rearm | 03-capture-readiness-contract, 04-adapter-earliest-ready-signal | code | adapter emits explicit readiness milestone or documented fallback | 4 |
| 06-real-device-timing-protocol | 04-adapter-earliest-ready-signal, 05-shutter-sound-and-visible-rearm | status | implementation packages completed | 5 |
| 99-finalize | all functional packages | status+code | all functional packages completed | final |

## Merge Strategy

- Functional merge order: `01-camerax-camera2-signal-feasibility -> 02-current-shutter-timing-tests -> 03-capture-readiness-contract -> 04-adapter-earliest-ready-signal -> 05-shutter-sound-and-visible-rearm -> 06-real-device-timing-protocol`.
- Code dependency policy: `03` consumes both first-wave evidence streams; `04` must implement the earliest safe adapter milestone identified by `01` or explicitly retain `DataReceived` as the fallback; `05` consumes the milestone from `03/04`.
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
- A package depends on physical-device validation but treats desktop/unit evidence as final PASS.
- Implementation moves sound earlier than the readiness boundary, unless the status explicitly marks it as a separate press-feedback sound and not the shutter/readiness sound.
- Implementation introduces UI-owned camera runtime behavior or a second hidden session kernel.

## Capability Preflight

| Package Or Gate | Class | Owner | Why Not Fully Autonomous | Autonomous Substitute | External Evidence Required | Blocks |
|---|---|---|---|---|---|---|
| 01-camerax-camera2-signal-feasibility | autonomous | Claude Code | n/a | local source/API inspection, repo evidence, research output | none | normal graph |
| 02-current-shutter-timing-tests | autonomous | Claude Code | n/a | focused unit tests and assemble as needed | none | normal graph |
| 03-capture-readiness-contract | autonomous | Claude Code | n/a | contract tests in core/session/device | none | normal graph |
| 04-adapter-earliest-ready-signal | agent-verifiable substitute | Claude Code | final timing requires a real camera device | local adapter tests, compile, timing diagnostics, honest fallback if API cannot provide earlier signal | device log only for final confidence | normal graph |
| 05-shutter-sound-and-visible-rearm | agent-verifiable substitute | Claude Code | final audio/feel requires real-device observation | app/unit tests proving sound trigger state/effect and shutter enabled state | device video/audio/log for final confidence | normal graph |
| 06-real-device-timing-protocol | agent-verifiable substitute | Claude Code | cannot perform physical vivo/system-camera timing comparison autonomously | APK path, install commands, log markers, checklist, expected evidence format | user/Codex device run with logs/video if available | release confidence only |
| real-device-audio-and-rearm-qa | external-assist | user/Codex device owner | requires physical camera, microphone/audio perception, and human timing judgment | debug APK, focused tests, Dev LINK log export protocol | press-to-loading, readiness-sound time, first moment button accepts second shot, saved-media completion | release confidence only unless user declares it blocking |

## Package Documents

| Work Package | Target Agent | Dependency | Parallel Safety | Purpose |
|---|---|---|---|---|
| [01-camerax-camera2-signal-feasibility.md](packages/01-camerax-camera2-signal-feasibility.md) | research/design agent | none | safe with 02 | Determine the earliest CameraX/Camera2 milestone this project can trust |
| [02-current-shutter-timing-tests.md](packages/02-current-shutter-timing-tests.md) | test/characterization agent | none | safe with 01 | Lock down current sound/re-arm semantics and expose regressions |
| [03-capture-readiness-contract.md](packages/03-capture-readiness-contract.md) | contract implementation agent | 01, 02 | no | Add explicit readiness/sound boundary through device/session contracts |
| [04-adapter-earliest-ready-signal.md](packages/04-adapter-earliest-ready-signal.md) | CameraX adapter implementation agent | 01, 03 | no | Emit the earliest safe readiness milestone or a documented fallback |
| [05-shutter-sound-and-visible-rearm.md](packages/05-shutter-sound-and-visible-rearm.md) | app/UI implementation agent | 03, 04 | no | Play sound at readiness and keep visual loading honest until then |
| [06-real-device-timing-protocol.md](packages/06-real-device-timing-protocol.md) | QA/evidence agent | 04, 05 | no | Produce install/log/checklist evidence protocol for vivo/system-camera comparison |
| [99-finalize.md](packages/99-finalize.md) | orchestrator | all | no | Merge, verify, report, and clean up on success |

## Agent Budget

- Recommended Claude Code agents: 6 functional agents + finalize.
- Max parallel agents: 2.
- First wave: `01-camerax-camera2-signal-feasibility`, `02-current-shutter-timing-tests`.
- Final package: `99-finalize`.
- Downstream dispatch is triggered by package tail calls to `advance`.
