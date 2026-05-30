# Package 03 - Pipeline Timing (G4, G5, G8)

## Objective

Instrument three medium-priority gaps in the media pipeline:
- **G4**: Individual post-processor timing in `CompositeMediaPostProcessor`
- **G5**: File I/O save latency in `MediaSaveTransaction`
- **G8**: Video recording start latency

## Gap Details

### G4: Post-Processor Individual Timing
- **Current state**: `CompositeMediaPostProcessor` chains 7+ processors but only records overall `postProcessCompletedAt`.
- **Target**: Record start/end time for each processor in the chain.

### G5: File I/O Save Latency
- **Current state**: No file write timing at all.
- **Target**: Record file write start/end in `MediaSaveTransaction`.

### G8: Video Recording Start Latency
- **Current state**: Has `startedAt` but no latency calculation.
- **Target**: Record delta from video intent to `MediaRecorder` started.

## Implementation Approach

### G4: Post-Processor Timing
1. In `CompositeMediaPostProcessor.process()`, wrap each processor call with `System.nanoTime()` timing.
2. Record per-processor timing as `pipelineNotes` entries: `timing:processor:<name>=<ms>ms`.
3. Also record as `PerformanceLinkEvent` with flow=`postprocess`, stage=`<processor-name>`.

### G5: File I/O Timing
1. In the save transaction implementation, record file write start/end times.
2. Add `pipelineNotes` entry: `timing:save-io=<ms>ms`.
3. Record as `PerformanceLinkEvent` with flow=`save`, stage=`file-io`.

### G8: Video Recording Start
1. In `CameraXCaptureAdapter.startVideoRecording()`, record the delta from `requestedAt` to when `MediaRecorder.start()` completes.
2. Record as `PerformanceLinkEvent` with flow=`recording`, stage=`start`.

## Allowed Paths

- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaSaveContracts.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/` (focused tests)
- `app/src/test/java/com/opencamera/app/camera/` (focused tests)

## Forbidden Paths

- `core/session/` (session kernel, handled by packages 01/02)
- `app/src/main/java/com/opencamera/app/MainActivity.kt` (UI layer)

## Acceptance Criteria

1. Each post-processor in `CompositeMediaPostProcessor` has individual timing recorded in `pipelineNotes`.
2. File I/O save latency is recorded as `timing:save-io=<ms>ms` in `pipelineNotes`.
3. Video recording start latency is recorded as a `PerformanceLinkEvent`.
4. Per-processor timing events appear in `linkRecorder.snapshot()`.
5. Existing `postProcessCompletedAt` timing is preserved (no regression).
6. Focused unit tests pass.

## Verification Commands

```bash
# Media pipeline tests (main workspace)
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test

# App capture adapter tests
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterTest

# Stage 7 verification
rtk ./scripts/verify_stage_7_observability.sh

# Build verification
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash with timing instrumentation
- Test output showing per-processor timing in `pipelineNotes`
- Link event snapshot showing save-io and recording-start events
- All existing tests still passing

## Branch/Worktree Policy

- Branch: `agent/perf-monitoring-gap/03-pipeline-timing`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/03-pipeline-timing`
- Base: `main` (current HEAD)

## Unlock Condition

- This package has no dependencies and can start immediately (Wave 1).
