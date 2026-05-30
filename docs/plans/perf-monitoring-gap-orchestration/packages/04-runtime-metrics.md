# Package 04 - Runtime Metrics (G6, G7, G9, G10)

## Objective

Instrument four runtime quality/health metrics:
- **G6**: Preview FPS / frame drop rate
- **G7**: Algorithm job queue depth
- **G9**: Video recording frame rate / quality indicators
- **G10**: Memory pressure metrics (actual usage vs budget)

## Gap Details

### G6: Preview FPS / Frame Drop Rate
- **Current state**: `PreviewSceneBrightnessMonitor` samples brightness every 800ms but doesn't track FPS. MlKit scene mask source has `framesDropped` counter but only for Log.d.
- **Target**: Add preview frame rate sampling and frame drop tracking to `SessionDiagnostics`.

### G7: Algorithm Job Queue Depth
- **Current state**: `AlgorithmJobScheduler` has timeout per job class but no queue depth metric.
- **Target**: Expose current queue depth and pending job count as a diagnostic metric.

### G9: Video Recording Frame Rate / Quality
- **Current state**: No recording quality metrics.
- **Target**: Record actual frame rate during video recording, detect quality drops.

### G10: Memory Pressure
- **Current state**: `CameraResourceBudget` has `memoryBytes` budget but no actual usage tracking.
- **Target**: Sample actual memory usage and compare against budget.

## Implementation Approach

### G6: Preview FPS
1. Add frame timestamp tracking to the preview rendering path.
2. Calculate FPS over a sliding window (e.g., last 30 frames).
3. Expose via `SessionDiagnostics` or a new `PreviewFrameMetrics` data class.

### G7: Algorithm Queue Depth
1. Add queue depth query to `AlgorithmJobScheduler` interface.
2. Expose via `SessionDiagnostics` or `ResourceDiagnosticsSnapshot`.

### G9: Video Recording Quality
1. Track frame timestamps during recording.
2. Calculate actual FPS and detect drops.
3. Record as trace events or link events.

### G10: Memory Pressure
1. Sample `Runtime.getRuntime().totalMemory() - freeMemory()` periodically.
2. Compare against `CameraResourceBudget.memoryBytes`.
3. Record pressure ratio as a diagnostic metric.

## Allowed Paths

- `core/media/src/main/kotlin/com/opencamera/core/media/` (resource contracts, algorithm scheduler)
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` (recording metrics)
- `app/src/main/java/com/opencamera/app/camera/PreviewSceneBrightnessMonitor.kt` (preview metrics)
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt` (diagnostics export)
- `core/media/src/test/kotlin/com/opencamera/core/media/` (focused tests)
- `app/src/test/java/com/opencamera/app/camera/` (focused tests)

## Forbidden Paths

- `core/session/DefaultCameraSession.kt` (session kernel, handled by packages 01/02)
- `app/src/main/java/com/opencamera/app/MainActivity.kt` (UI layer)

## Acceptance Criteria

1. Preview FPS metric is available in `SessionDiagnostics` or a new diagnostics data class.
2. Algorithm job queue depth is exposed as a queryable metric.
3. Video recording frame rate is tracked and recorded.
4. Memory usage vs budget ratio is available as a diagnostic metric.
5. All new metrics are accessible via `linkRecorder.snapshot()` or `SessionDiagnostics`.
6. Focused unit tests pass.

## Verification Commands

```bash
# Media pipeline tests (main workspace)
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test

# App camera tests
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterTest

# Session diagnostics test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionDiagnosticsTest

# Stage 7 verification
rtk ./scripts/verify_stage_7_observability.sh

# Build verification
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash with runtime metrics implementation
- Test output showing new diagnostic metrics
- All existing tests still passing

## Branch/Worktree Policy

- Branch: `agent/perf-monitoring-gap/04-runtime-metrics`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/04-runtime-metrics`
- Base: `main` (current HEAD)

## Unlock Condition

- This package has no dependencies and can start immediately (Wave 1).
