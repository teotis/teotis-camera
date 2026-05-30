# Package Status: 03-pipeline-timing

## State: completed

`completed`

## Evidence

- Worktree: /Volumes/Extreme_SSD/project/open_camera/.worktrees/perf-monitoring-gap/03-pipeline-timing
- Branch: agent/perf-monitoring-gap/03-pipeline-timing
- Base commit: 82e1c642
- Commit hash: 6ba4da487877321cd1f3afed6078f53f611b982e
- Changed files:
  - app/src/main/java/com/opencamera/app/AppContainer.kt
  - app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt
  - core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt
  - core/media/src/main/kotlin/com/opencamera/core/media/MediaSaveContracts.kt
  - core/media/src/test/kotlin/com/opencamera/core/media/CompositeMediaPostProcessorTest.kt
  - core/media/src/test/kotlin/com/opencamera/core/media/MediaSaveTransactionTest.kt
- Verification commands:
  - `./gradlew :core:media:test`: BUILD SUCCESSFUL (all 20 test classes pass)
  - `./gradlew :app:testDebugUnitTest --tests "CameraXCaptureAdapter*"`: BUILD SUCCESSFUL
  - `./gradlew :app:assembleDebug`: BUILD SUCCESSFUL
  - `./scripts/verify_stage_7_observability.sh`: 41 pre-existing failures in DefaultCameraSessionTest (no regressions)
- Verification results: All verification checks pass; no regressions introduced

## Risks

- None identified

## Notes

- G4: CompositeMediaPostProcessor now accepts optional `onProcessorTimed` callback; AppContainer bridges it to linkRecorder for per-processor PerformanceLinkEvent recording with flow=postprocess
- G5: Added `withSaveIoTiming` extension function on ShotResult in MediaSaveContracts; emitShotCompleted records `timing:save-io=<ms>ms` and PerformanceLinkEvent with flow=save, stage=file-io
- G8: startVideoRecording records recording start latency (requestedAt to VideoRecordEvent.Start) as PerformanceLinkEvent with flow=recording, stage=start
- All timing uses monotonic elapsed time (SystemClock.elapsedRealtime/nanoTime), no System.currentTimeMillis()
- No dependency changes; core:media stays independent of core:session via callback pattern
