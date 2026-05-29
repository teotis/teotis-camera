# Package 03 - Capture Readiness Contract

## Objective

Introduce an explicit device/session contract for "ordinary still capture is ready for user release and shutter sound" so the app no longer infers sound timing from `activeShot`.

## Dependencies

- `01-camerax-camera2-signal-feasibility`
- `02-current-shutter-timing-tests`

Read their status files and outputs before editing.

## Allowed Paths

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionIntentOwnership.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraSessionCoordinatorTest.kt`
- `docs/plans/capture-readiness-sound-timing-orchestration/status/03-capture-readiness-contract.md`
- Package scratch path printed by `scratch-path 03-capture-readiness-contract`

## Forbidden Paths

- `MainActivity.kt` sound playback implementation
- `CameraXCaptureAdapter.kt` platform callback implementation
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Work

- Add a named event/intent for capture readiness. Prefer a semantic name that does not overclaim hardware details if Package 01 did not prove a true frame-end signal.
- Carry enough data for app sound de-duplication and diagnostics:
  - `shotId`,
  - `mediaType`,
  - readiness milestone/source,
  - elapsed timestamp if available.
- Ordinary still capture should be eligible for readiness sound and re-arm.
- Special capture kinds remain conservative unless explicitly proven safe.
- Session state or presentation should expose a one-shot or monotonic readiness marker that app code can consume without polling `activeShot`.
- Tests must prove readiness is handled once per shot and does not break recording stop semantics.

## Acceptance Criteria

- Contracts compile.
- Tests cover ordinary still readiness and special-mode conservatism.
- The contract does not force the UI to know CameraX/Camera2 callback details.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
```

## Completion

Commit local changes, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 03-capture-readiness-contract completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 03-capture-readiness-contract
```
