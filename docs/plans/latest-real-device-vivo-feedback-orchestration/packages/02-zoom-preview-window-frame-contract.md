# Package 02 - Zoom Preview Window Frame Contract

## Goal

Repair the zoom regression: preview must not visibly and laggily follow every continuous zoom value. Separate `captureZoomRatio` from the preview-window baseline and use the frame overlay to show the changing capture area.

The user-requested target behavior is:

| User zoom range | Preview baseline |
|---|---|
| `0.7x - 1.1x` | `0.7x` |
| `1.1x - 3.3x` | `1.0x` |
| `3.3x - 5.5x` | `3.0x` |
| `5.5x - 10x` | `5.0x` or degraded synthetic baseline when no real `5x` node exists |

Do not hard-code this exact table as a vivo-only rule. Derive it from detected lens/zoom nodes where possible, and fall back to truthful `supported/degraded/unsupported` semantics.

## Allowed Paths

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/SessionDiagnostics.kt`
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
- focused tests for the files above
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/02-zoom-preview-window-frame-contract.md`
- package-local scratch path

## Forbidden Paths

- UI directly selecting camera IDs or driving CameraX.
- Mode plugins calling CameraX/Camera2/HAL.
- Hard-coded vendor-only camera IDs or assumptions without capability fallback.
- Unrelated Quick/Style/Watermark/Dev copy changes.

## Tasks

1. Inspect current `previewZoomRatio`, active device graph, lens-node switching, and `CameraXCaptureAdapter.updateZoomRatio()` behavior.
2. Identify why the latest APK still lets the preview stream continuously zoom and lag despite the discrete frame-basis work from 2026-06-01.
3. Define a tested mapping from detected lens nodes to preview-window baselines, including the user example ranges and degraded single-lens behavior.
4. Ensure CameraX preview stream receives only the preview baseline where the architecture allows it, while the capture/frame overlay represents continuous desired capture zoom.
5. Add diagnostics/log text for `captureZoomRatio`, `previewZoomRatio`, selected lens node, and degraded reason.
6. Update focused tests for session/device/app render behavior.

## Acceptance Criteria

- Continuous slider movement changes the frame overlay/capture-area indication without forcing continuous preview stream zoom.
- Cross-node thresholds choose stable preview baselines with hysteresis and no fast-drag rollback.
- Diagnostics expose enough data to debug real-device lag.
- Devices without multiple real nodes report degraded behavior instead of fake physical switching.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Zoom mapping table used by implementation.
- Changed files and commit hash.
- Tests/build result.
- Residual real-device QA notes: drag across `0.7/1/3/5/10x`, record preview lag, and export Dev link logs after package 03 lands.

## Unlock Condition

Mark `completed` only after tests/build pass and the package status explains whether final device smoothness remains external.
