# Package 02 - Shutter Fast Feedback Runtime

## Package ID

`02-shutter-fast-feedback-runtime`

## Goal

Repair remaining runtime or capture-acceptance blockers identified by package 01. Keep Session Kernel as the only runtime owner and preserve the V1 data boundary. Do not make purely cosmetic UI changes here.

## Dependencies

- Wait for `01-shutter-latency-reference-diagnosis` to complete.

## Branch And Worktree

- Branch: `agent/shutter-feedback-watermark/02-shutter-fast-feedback-runtime`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/shutter-feedback-watermark-02-shutter-fast-feedback-runtime`
- Base: latest `main` plus diagnosis from package 01.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt` only if a small new timing/feedback state is required
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` only if package 01 proves a device event/ordering issue remains
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapter*Test.kt`
- Coordinator status file: `/Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-shutter-feedback-watermark-preview-orchestration/status/02-shutter-fast-feedback-runtime.md`
- Coordinator state file row for `02-shutter-fast-feedback-runtime`

## Forbidden Paths

- UI visual styling files unless a compile fix is required
- Watermark implementation files
- Other package status files
- `INDEX.md`
- Broad session rewrites
- Any overlapping-capture behavior for Live, multi-frame, night-like, high-pixel, video, or recovering preview states unless explicitly proven and tested

## Implementation Guidance

1. Start from package 01's timing table. If the remaining delay is UI-only, mark this package `completed` with no runtime code changes and hand off to package 03.
2. If runtime still blocks ordinary second capture after `DataReceived`, repair the smallest session/device contract.
3. Prefer explicit timing/diagnostic fields or trace events over hidden booleans.
4. Keep capture safety semantic:
   - ordinary still may re-arm at data received,
   - special captures keep `activeShot` until final completion,
   - recording shutter remains stop-recording,
   - preview recovering remains blocked.
5. Ensure a second ordinary still tap after re-arm is accepted by session tests, while unsafe modes are rejected.

## Acceptance Criteria

- [ ] Runtime code either stays unchanged with evidence, or changes only the proven remaining blocker.
- [ ] Tests prove ordinary still second capture can be accepted at the intended boundary.
- [ ] Tests prove special capture kinds are still blocked.
- [ ] Tests prove recording stop semantics are unchanged.
- [ ] Trace/diagnostic evidence can support package 05 timing QA.

## Verification Commands

Run from the package worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

If `CameraXCaptureAdapter.kt` is changed, add the focused adapter test command used.

## Evidence Required

- Worktree path and branch.
- Base commit and final commit hash.
- Changed files and `git diff --stat`.
- Explanation of true capture-safety boundary.
- Test output summary.
- Residual real-device risks.
