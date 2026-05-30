# Package Status: 01-capture-latency-timing

## State: completed

## Evidence

`completed`
- Branch: `agent/perf-monitoring-gap/01-capture-latency-timing`
- Base commit: `e6e84af2`
- Commit hash: `69c7d49d1e8421bdbec34f92ce2b9d54dcce53c4`
- Changed files:
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`
- Verification commands:
  - `CaptureRecordingSessionProcessorTest`: PASS (all tests including new G1 test)
  - `SessionDiagnosticsTest`: PASS
  - `DefaultCameraSessionTest`: 41 failures (pre-existing on base commit, not caused by this change)
  - `assembleDebug`: BUILD SUCCESSFUL
- Verification results:
  - G1 `PerformanceLinkEvent` with flow=`capture`, stage=`shutter-to-device` recorded on photo capture completion
  - `capture.shutter.to.device` trace event emitted with latency in ms
  - Duration computed from `requestedAtElapsedMillis` to `deviceCaptureStartedAtElapsedMillis`
  - Existing `capture.timing` trace event preserved (no regression)

## Risks

- None identified

## Notes

- Instruments G1: Shutter-to-Capture latency
- Used `requestedAtElapsedMillis` as shutter-press proxy (closest monotonic timestamp in ShotTiming)
- Photo-only instrumentation (video excluded as per gap definition)
