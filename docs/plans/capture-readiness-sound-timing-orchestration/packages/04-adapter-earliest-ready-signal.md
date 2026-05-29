# Package 04 - Adapter Earliest Ready Signal

## Objective

Implement the earliest safe CameraX/Camera2 readiness milestone identified by Package 01, or explicitly wire the current `DataReceived` boundary as the honest fallback.

## Dependencies

- `01-camerax-camera2-signal-feasibility`
- `03-capture-readiness-contract`

Read the feasibility recommendation before choosing an implementation path.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterRuntimeIssueTest.kt`
- `app/src/test/java/com/opencamera/app/camera/CameraXCaptureAdapterCapabilityDetectionTest.kt`
- New focused adapter tests under `app/src/test/java/com/opencamera/app/camera/`
- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` only if Package 03 left a small integration gap
- `docs/plans/capture-readiness-sound-timing-orchestration/status/04-adapter-earliest-ready-signal.md`
- Package scratch path printed by `scratch-path 04-adapter-earliest-ready-signal`

## Forbidden Paths

- `MainActivity.kt`
- broad media pipeline rewrites
- switching to a fully custom JPEG save path unless Package 01 explicitly proves it is the smallest safe path and tests cover cleanup/failure behavior
- Other package status files
- `docs/plans/capture-readiness-sound-timing-orchestration/INDEX.md`

## Required Work

- If CameraX/Camera2 can supply an earlier safe callback:
  - emit the new readiness device event at that callback,
  - keep `ShotCompleted` and postprocess/save behavior unchanged,
  - ensure failures do not leave the session permanently armed or silent.
- If not:
  - emit the explicit readiness event from the existing `PhotoCaptureOutcome.Success` / `DataReceived` point,
  - document why it is the honest boundary in status.
- Add timing diagnostics so Dev/LINK logs can distinguish:
  - request/start,
  - device/platform readiness,
  - `DataReceived`,
  - final saved/postprocess completion.
- Preserve conservative behavior for Live/multi-frame/special captures.

## Acceptance Criteria

- Adapter tests or compile-level tests prove the new event is emitted in the intended order.
- The implementation does not duplicate save ownership.
- Status records whether the chosen boundary is earlier than current `DataReceived` or a deliberate fallback.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:compileDebugKotlin
```

In a worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest --tests com.opencamera.app.camera.CameraXCaptureAdapterCapabilityDetectionTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:compileDebugKotlin
```

## Completion

Commit local changes, update coordinator status, then:

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh mark-state 04-adapter-earliest-ready-signal completed --commit <commit-sha> --verification "<commands/results>"
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/capture-readiness-sound-timing-orchestration/launchers/orchestrate.sh advance --from 04-adapter-earliest-ready-signal
```
