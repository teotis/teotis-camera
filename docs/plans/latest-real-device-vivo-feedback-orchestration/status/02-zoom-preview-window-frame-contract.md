# 02-zoom-preview-window-frame-contract Status

## State

`completed`

- State: completed
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/latest-real-device-vivo-feedback/02-zoom-preview-window-frame-contract`
- Branch: `agent/latest-real-device-vivo-feedback/02-zoom-preview-window-frame-contract`
- Base commit: pending
- Commit: cd55abf4

## Evidence

### Zoom Mapping Table (derived from lens node thresholds)

| Capture zoom range | Preview baseline (previewZoomRatio) | Lens node | Degraded? |
|---|---|---|---|
| 0.7x – 1.1x | 1.0x (or 0.7x if 0.7x node available) | WIDE | no |
| 1.1x – 3.3x | 1.0x | WIDE | no |
| 3.3x – 5.5x | 3.0x | TELEPHOTO | degraded if no real 3x node |
| 5.5x – 10x | 5.0x | PERISCOPE | degraded if no real 5x node |

- `computePreviewZoomRatio()` returns the largest available threshold ≤ captureZoom.
- Hysteresis: 0.1x delta on `evaluateLensNode()` prevents fast-drag rollback.
- Single-lens devices: `previewZoomRatio == captureZoomRatio` (degraded, logged).

### Changed Files

- `core/session/.../SessionContracts.kt`: `SessionEffect.ApplyZoomRatio` adds `previewZoomRatio: Float`
- `core/device/.../DeviceContracts.kt`: `DeviceCommand.UpdateZoomRatio` adds `previewZoomRatio: Float`
- `core/session/.../DefaultCameraSession.kt`: `requestZoomApply()` reads previewZoomRatio from device graph state
- `core/session/.../SessionDiagnostics.kt`: `ZoomDiagnosticsSnapshot` with captureZoomRatio, previewZoomRatio, requestedLensNode, degradedReason
- `app/.../CameraSessionCoordinator.kt`: forwards previewZoomRatio to adapter
- `app/.../CameraXCaptureAdapter.kt`: `setZoomRatio()` uses previewZoomRatio; bind/recovery paths use previewZoomRatio
- `app/.../CameraSessionCoordinatorTest.kt`: updated to match new effect/command signatures

### Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest --tests com.opencamera.core.session.SessionDiagnosticsTest
# BUILD SUCCESSFUL

rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.FocalLengthSliderViewTest
# BUILD SUCCESSFUL

rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
# BUILD SUCCESSFUL
```

## Acceptance Notes

- Continuous slider movement now changes the frame overlay/capture-area indication without forcing continuous preview stream zoom.
- Cross-node thresholds use `evaluateLensNode()` with 0.1x hysteresis for stable preview baselines.
- `ZoomDiagnosticsSnapshot` exposes captureZoomRatio, previewZoomRatio, requestedLensNode, lensNodeMapSize, zoomControlSupport, and degradedReason.
- Single-lens devices report degraded behavior in diagnostics instead of fake physical switching.

## Residual Device QA

- Real smoothness and device lens-node behavior require physical multi-lens device evidence.
- Recommended QA: drag across 0.7/1/3/5/10x on vivo device, record preview lag, export Dev link logs after package 03 lands.

## Risks / Blockers

- Real smoothness and device lens-node behavior require physical multi-lens device evidence.
- No blockers for code merge; device QA is external.
