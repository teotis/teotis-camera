# Perf Monitoring Gap Orchestration — Final Report

## Outcome: All 10 gaps (G1-G10) instrumented and merged to mainline

- **Mainline merge commit**: `d5323e26`
- **Integration branch**: `agent/perf-monitoring-gap/integration` (merged to main)
- **Finalized at**: 2026-05-31

## Package Summary

| Package | Gaps | Priority | Branch | Commit | Status |
|---|---|---|---|---|---|
| 01-capture-latency-timing | G1 | High | `agent/perf-monitoring-gap/01-capture-latency-timing` | `69c7d49d` | completed |
| 02-switch-latency-timing | G2, G3 | High | `agent/perf-monitoring-gap/02-switch-latency-timing` | `15cd3096` | completed |
| 03-pipeline-timing | G4, G5, G8 | Medium | `agent/perf-monitoring-gap/03-pipeline-timing` | `6ba4da48` | completed |
| 04-runtime-metrics | G6, G7, G9, G10 | Medium/Low | `agent/perf-monitoring-gap/04-runtime-metrics` | `2e968170` | completed |

## Gap Implementation Details

### G1: Shutter-to-Capture Latency (`CaptureRecordingSessionProcessor`)
Records delta from `shutterPressedAtElapsedMillis` to `deviceCaptureStartedAtElapsedMillis` as `PerformanceLinkEvent` with flow=`capture`, stage=`shutter-to-device`.

### G2: Mode Switch Latency (`DefaultCameraSession`)
Records full latency from `SessionIntent.SwitchMode` dispatch to first frame of new mode via `linkRecorder.startSpan()/completeSpan()` with flow=`mode-switch`.

### G3: Lens Switch Latency (`DefaultCameraSession`)
Records full latency from `SessionIntent.LensFacingToggled` to first frame of new lens via `linkRecorder` with flow=`lens-switch`.

### G4: Post-Processor Individual Timing (`MediaPostProcessors`)
Each processor in `CompositeMediaPostProcessor` emits per-processor timing via `onProcessorTimed` callback bridged to `linkRecorder` with flow=`postprocess`.

### G5: File I/O Save Latency (`MediaSaveContracts`)
`withSaveIoTiming` extension on `ShotResult` records file write latency as `PerformanceLinkEvent` with flow=`save`, stage=`file-io`.

### G8: Video Recording Start (`CameraXCaptureAdapter`)
Records delta from video intent to `VideoRecordEvent.Start` as `PerformanceLinkEvent` with flow=`recording`, stage=`start`.

### G6: Preview FPS / Frame Drop Rate (`RuntimeMetricsTracker`)
`PreviewFpsTracker` with sliding window tracks frame rate and drop rate.

### G7: Algorithm Job Queue Depth (`AlgorithmJobScheduler`)
Queue depth query functions expose pending/active job counts.

### G9: Video Recording Quality (`RuntimeMetricsTracker`)
`VideoRecordingQualityTracker` tracks actual FPS during recording, detects quality drops.

### G10: Memory Pressure (`RuntimeMetricsTracker`)
`MemoryPressureSnapshot` samples `Runtime` memory usage vs `CameraResourceBudget`.

## Infrastructure

All gaps use the existing `PerformanceLinkRecorder` infrastructure (`InMemoryPerformanceLinkRecorder` via `System.nanoTime()`), wired through `AppContainer` → `DefaultCameraSession` → processors. No parallel timing systems introduced.

## Merge History

```
main:        d5323e26 ← merge all packages
integration: d25c6031 ← merge 04-runtime-metrics
             203768c0 ← merge 03-pipeline-timing
             148f71e2 ← merge 02-switch-latency-timing
             38a4fb28 ← merge 01-capture-latency-timing
base:        82e1c642 (main HEAD)
```

One conflict occurred in `CameraXCaptureAdapter.kt` (03 and 04 both modified `VideoRecordEvent.Start` block). Resolved by keeping both G8 recording start latency and G9 video quality tracker start calls.

## Verification Results

| Command | Result |
|---|---|
| `verify_stage_7_observability.sh` | core:media:test PASS, core:session:test 213 tests / 41 pre-existing failures (no regressions) |
| `:app:assembleDebug` | BUILD SUCCESSFUL |

## Changed Files (15 files, +992/-12)

- `app/src/main/java/com/opencamera/app/AppContainer.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/AlgorithmJobScheduler.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaSaveContracts.kt`
- `core/media/src/main/kotlin/com/opencamera/core/media/RuntimeMetricsTracker.kt` (new)
- `core/media/src/test/kotlin/com/opencamera/core/media/CompositeMediaPostProcessorTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/MediaSaveTransactionTest.kt`
- `core/media/src/test/kotlin/com/opencamera/core/media/RuntimeMetricsTrackerTest.kt` (new)
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/SessionDiagnosticsTest.kt`

## Landing Strategy

Primary landing path executed: all 4 functional packages completed, all 10 gaps instrumented, integration verification passed, merged to mainline.

## Cleanup

Local package branches and worktrees preserved for reference. See INDEX for branch/worktree policy.
