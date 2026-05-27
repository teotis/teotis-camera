# Package 01 - Shutter Latency Reference Diagnosis

## Package ID

`01-shutter-latency-reference-diagnosis`

## Goal

Explain why the shutter button still feels slow after the V1 data boundary landed. Produce a concrete diagnosis and design target before any new runtime or UI changes stack on top.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/01-shutter-latency-reference-diagnosis`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-01-shutter-latency-reference-diagnosis`
- Base: latest `main` unless `99-finalize` or the user says otherwise.

## Allowed Paths

- Read-only: `core/session/src/main/kotlin/com/opencamera/core/session/**`
- Read-only: `app/src/main/java/com/opencamera/app/**`
- Read-only: `app/src/main/java/com/opencamera/app/camera/**`
- Read-only: `core/effect/**`
- Read-only: `/Users/dingren/Applications/MiuiCamera/codex/intro_capture_data_flow.md`
- Read-only: `/Users/dingren/Applications/MiuiCamera/codex/intro_watermark.md`
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/01-shutter-latency-reference-diagnosis.md`
- Coordinator state file row for `01-shutter-latency-reference-diagnosis`

## Forbidden Paths

- Runtime source edits
- Test edits
- Other package status files
- `INDEX.md`
- Network calls or vendor-private API claims

## Work

1. Re-check current mainline shutter flow:
   - `CameraXCaptureAdapter.captureStillImage(...)`
   - `CaptureRecordingSessionProcessor.handleDataReceived(...)`
   - `SessionCockpitRenderModel.shutterDisabledReason(...)`
   - `MainActivityActionBinder`
   - focused tests around `DataReceived`, `activeShot`, `shutterDisabledReason`, and event ordering.
2. Identify the remaining slow-feel phase:
   - tap-to-visual-press,
   - tap-to-`ShotStarted`,
   - tap-to-`DataReceived`,
   - `DataReceived`-to-clickable,
   - second tap accepted or rejected by session,
   - postprocess/save indicator persistence.
3. Synthesize a design target:
   - Apple-like immediate press confirmation,
   - vivo-like cadence/readiness,
   - MiuiCamera staged data task flow from local docs,
   - OpenCamera's Session Kernel ownership rules.
4. Decide what package 02 and 03 should implement, including what they must not touch.

## Acceptance Criteria

- [ ] Diagnosis explains why V1 did not fully satisfy the real-device feel.
- [ ] Diagnosis names the exact code surfaces and timing events to change or instrument.
- [ ] Design target separates true capture safety from perceived visual recovery.
- [ ] Special capture kinds remain conservative.
- [ ] Output includes thresholds for real-device QA, even if they are provisional.

## Verification Commands

Run from the package worktree if useful:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

This is primarily a diagnosis package; if commands are skipped, explain why and cite source evidence instead.

## Evidence Required

- Worktree path and branch if used.
- Base commit.
- Source evidence with file/function names.
- Timing phase table.
- Recommended implementation scope for packages 02 and 03.
- Explicit residual uncertainties requiring real-device measurement.
