# Package 02 - Core Flow Instrumentation

## Package ID

`02-core-flow-instrumentation`

## Goal

Use the package 01 timing contract to record the core real-device analysis paths: preview startup/recovery, capture/recording lifecycle, media postprocess/save, zoom/lens switching, focus/metering, and preview brightness application.

## Branch And Worktree

- Branch: `agent/performance-link-logs/02-core-flow-instrumentation`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/performance-link-logs/02-core-flow-instrumentation`
- Depends on: `01-performance-timing-contract`

## Allowed Paths

- `core/session/src/main/kotlin/com/opencamera/core/session/**`
- `core/session/src/test/kotlin/com/opencamera/core/session/**`
- `core/device/src/main/kotlin/com/opencamera/core/device/**`
- `core/device/src/test/kotlin/com/opencamera/core/device/**`
- `app/src/main/java/com/opencamera/app/camera/**`
- `app/src/test/java/com/opencamera/app/camera/**`

## Forbidden Paths

- UI render/export files such as `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`, `DevLog*.kt`, or Dev XML; package 03 owns those.
- Mode plugin behavior unrelated to emitting existing session/device events.
- Raw media/frame logging.
- Broad refactors of `CameraXCaptureAdapter.kt` unrelated to timing instrumentation.

## Required Design Constraints

- Do not introduce a hidden session kernel in `CameraSessionCoordinator`, `CameraXCaptureAdapter`, or UI.
- Prefer existing events and contracts: `DeviceEvent.PreviewFirstFrameAvailable`, `DeviceEvent.DataReceived`, `DeviceEvent.ShotCompleted`, `DeviceEvent.PreviewMeteringCompleted`, `DeviceEvent.PreviewBrightnessApplied`, and runtime issue forwarding.
- Preserve existing `capture.timing` / `recording.timing` event names, but enrich or supplement them through the new link timing contract.
- Treat missing platform timestamps as `unavailable` or `degraded`, not as zero-duration success.

## Candidate Flows

- `preview`: bind requested -> first frame; category must align with `PreviewStartCategory`.
- `recovery`: recovery requested -> recovery started -> first frame or failed.
- `capture`: shutter/requested -> device capture started -> data received -> postprocess completed/saved.
- `recording`: recording requested -> started -> saved/stopped.
- `lens`: zoom ratio/lens node requested -> device command dispatched -> applied/fallback.
- `metering`: focus/AE request -> device result.
- `brightness`: preview brightness request -> device result.

## Acceptance Criteria

- Focused tests prove at least preview and capture timing records reach session trace/diagnostics.
- Focused tests prove unavailable device timestamps are rendered honestly.
- App camera tests prove coordinator forwarding remains ordered, especially `DataReceived` before `ShotCompleted`.
- No Dev log UI/export changes are made in this package.

## Verification

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.CaptureRecordingSessionProcessorTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.camera.CameraXCaptureAdapterRuntimeIssueTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Evidence To Record

- Changed files.
- Flow-to-event mapping table.
- Example link records for preview and capture.
- Verification commands and results.
- Any flow intentionally left for later and why.

## Tail Step

```bash
bash /Volumes/Extreme_SSD/project/open_camera/docs/plans/real-device-performance-link-logs-orchestration/launchers/orchestrate.sh advance --from 02-core-flow-instrumentation
```
