# Package 02 - Switch Latency Timing (G2, G3)

## Objective

Instrument the Mode Switch Latency (G2) and Lens Switch Latency (G3) gaps: measure the end-to-end time from when a switch intent is dispatched to when the new preview first frame becomes available.

## Gap Details

### G2: Mode Switch Latency
- **Current state**: Only records `mode.switched` event after completion, no start time.
- **Target**: Record full latency from `SessionIntent.SwitchMode` dispatch to first frame of new mode.

### G3: Lens Switch Latency
- **Current state**: Only records `lens.switched` event, no timing.
- **Target**: Record full latency from `SessionIntent.LensFacingToggled` to first frame of new lens.

## Implementation Approach

1. In `DefaultCameraSession`, when `handleSwitchMode()` is called, start a `linkRecorder.startSpan()` with flow=`mode-switch`.
2. When `PreviewFirstFrameAvailable` fires after a mode switch, `linkRecorder.completeSpan()` with the total duration.
3. Similarly for `handleLensFacingToggled()` with flow=`lens-switch`.
4. Add sub-step trace events: `mode.switch.unbind`, `mode.switch.bind`, `mode.switch.first.frame`.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/` (focused tests)

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/MainActivity.kt` (UI layer)
- `core/media/` (media pipeline)
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` (device adapter)

## Acceptance Criteria

1. `PerformanceLinkEvent` with flow=`mode-switch`, stage=`total` is recorded after mode switch completes.
2. `PerformanceLinkEvent` with flow=`lens-switch`, stage=`total` is recorded after lens switch completes.
3. Both events contain correct duration in milliseconds (monotonic elapsed time).
4. Sub-step trace events are recorded for mode switch (unbind, bind, first.frame).
5. Existing `mode.switched` and `lens.switched` trace events are preserved.
6. Focused unit tests pass.

## Verification Commands

```bash
# Focused session tests (main workspace)
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest

# Full session diagnostics test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest

# Stage 7 verification
rtk ./scripts/verify_stage_7_observability.sh

# Build verification
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash with timing instrumentation
- Test output showing `mode-switch` and `lens-switch` link events
- Link event snapshot showing correct latency values
- All existing tests still passing

## Branch/Worktree Policy

- Branch: `agent/perf-monitoring-gap/02-switch-latency-timing`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/02-switch-latency-timing`
- Base: `main` (current HEAD)

## Unlock Condition

- This package has no dependencies and can start immediately (Wave 1).
