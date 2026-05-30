# Package 01 - Capture Latency Timing (G1)

## Objective

Instrument the Shutter-to-Capture latency gap (G1): measure the time from when the user presses the shutter button to when the camera device actually begins capture execution.

## Gap Details

- **Gap**: G1 — Shutter-to-Capture latency
- **Current state**: `DefaultCameraSession` records `capture.shutter.pressed` and sets `shutterPressedAtElapsedMillis` on `ShotTiming`, but this value is only used for UI display, not tracked as an independent performance metric.
- **Target**: Record the delta from `shutterPressedAtElapsedMillis` to `deviceCaptureStartedAtElapsedMillis` as a `PerformanceLinkEvent` with flow=`capture`, stage=`shutter-to-device`.

## Implementation Approach

1. In `CaptureRecordingSessionProcessor`, when a shot completes, compute the delta between `shutterPressedAtElapsedMillis` and `deviceCaptureStartedAtElapsedMillis`.
2. Record this as a `PerformanceLinkEvent` via `linkRecorder.recordEvent()`.
3. Add trace event `capture.shutter.to.device` with the latency in ms.

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/` (focused tests)

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/MainActivity.kt` (UI layer)
- `core/media/` (media pipeline, handled by package 03)
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` (device adapter, handled by package 03)

## Acceptance Criteria

1. `PerformanceLinkEvent` with flow=`capture`, stage=`shutter-to-device` is recorded when a still photo capture completes.
2. The event contains the correct duration in milliseconds (monotonic elapsed time).
3. The event appears in `linkRecorder.snapshot()` after capture.
4. Existing `capture.timing` trace event is preserved (no regression).
5. Focused unit tests pass.

## Verification Commands

```bash
# Focused session tests (main workspace)
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest

# Full session diagnostics test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest

# Stage 7 verification
rtk ./scripts/verify_stage_7_observability.sh

# Build verification
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash with timing instrumentation
- Test output showing `capture.shutter.to.device` link event
- `pipelineNotes` or link event snapshot showing correct latency value
- All existing tests still passing

## Branch/Worktree Policy

- Branch: `agent/perf-monitoring-gap/01-capture-latency-timing`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/01-capture-latency-timing`
- Base: `main` (current HEAD)

## Unlock Condition

- This package has no dependencies and can start immediately (Wave 1).
