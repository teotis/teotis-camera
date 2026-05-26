# Package 03 — Shutter Lifecycle Contract

## Package ID

`03-shutter-lifecycle-contract`

## Purpose

Design a capture lifecycle contract that lets the shutter button recover as soon as normal still capture has acquired the frame / camera callback has completed, while postprocessing, watermarking, gallery writeback, diagnostics, and thumbnail updates continue independently. The design must preserve stricter blocking for night/multi-frame/high-pixel/special modes where another trigger would corrupt capture or overload the device.

## Current Evidence To Re-read

- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `captureDisabledReason(...)`
  - `shutterDisabledReason(...)`
  - `shutterVisualState(...)`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - `isShutterEnabled`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `renderShutter(...)`
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
  - `handleShotStarted(...)`
  - `handleDataReceived(...)`
  - `handleShotCompleted(...)`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
  - `SessionIntent.ShotStarted`
  - `SessionIntent.DataReceived`
  - `SessionIntent.ShotCompleted`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `captureStillImage(...)`
  - `captureSinglePhoto(...)`
  - `emitShotCompleted(...)`
- `core/media/src/main/kotlin/com/opencamera/core/media/ShotLifecycleContracts.kt`
  - `ShotTiming`
  - `ShotResult`
- `core/session/src/test/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessorTest.kt`

## Allowed Paths

- Read-only: session/app/camera/media lifecycle code and tests.
- Writable: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/03-shutter-lifecycle-contract.md` only.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit `INDEX.md`.
- Do not edit another package's status file.

## Required Design Questions

1. Which milestones should be distinct?
   - shutter pressed / request accepted,
   - device capture started,
   - frame or JPEG received,
   - postprocess completed,
   - media saved / gallery visible,
   - thumbnail rendered / UI loading complete.
2. Which milestones should block the next shutter press?
   - normal single-frame still,
   - live photo,
   - night multi-frame,
   - high-pixel still,
   - document capture with postprocess,
   - portrait/rendering/watermark heavy postprocess,
   - video start/stop.
3. Where should each responsibility live?
   - device adapter emits frame/device completion truth,
   - session kernel owns capture re-arm policy,
   - media pipeline reports postprocess/save status,
   - UI renders shutter enabled/visual state only.
4. What names/events should future implementation use?
   - reuse `DataReceived` if semantics are correct, or introduce a more explicit event such as `DeviceCaptureCompleted` / `FrameAcquired`,
   - add a separate pending media transaction state if needed,
   - avoid adding a second hidden session kernel in UI/coordinators/adapters.

## Verification Commands

Use read-only inspection first:

```bash
rtk rg -n "shutterDisabledReason|captureDisabledReason|DataReceived|ShotStarted|ShotCompleted|emitShotCompleted|postProcessCompletedAtElapsedMillis|activeShot" app/src/main/java core/session/src/main core/media/src/main -g '*.kt'
```

If running focused tests, use:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
```

In a worktree, use `rtk ./scripts/run_isolated_gradle.sh` for the same Gradle args.

## Acceptance Criteria

- [ ] Status file provides a state/milestone table separating frame acquisition, processing, saving, and UI feedback.
- [ ] Status file defines a conservative re-arm policy by capture kind: normal still should re-arm early; night/high-pixel/multi-frame may stay blocked until safe.
- [ ] Status file identifies exact future code touchpoints and tests without implementing them.
- [ ] Status file preserves recording stop semantics and permission-tap semantics.
- [ ] Status file includes timing/diagnostic evidence to collect on real device.

## Expected Evidence Pack

Write to `status/03-shutter-lifecycle-contract.md`:
- current lifecycle trace,
- proposed event/state model,
- capture-kind re-arm table,
- future tests and metrics,
- unresolved risks.
