# Package 04 — Preview vs Capture Zoom Semantics

## Package ID
`04-preview-capture-zoom-semantics`

## Problem Statement

Problem 12 is not solved by shrinking the overlay alone. The user requirement is: preview should continue showing the full current lens picture, while the frame window indicates the actual saved capture area; when zoom changes, the frame window shrinks. Current `CameraXCaptureAdapter.updateZoomRatio()` calls `camera.cameraControl.setZoomRatio()`, so the live preview itself is cropped.

## Acceptance Criteria

- [ ] Define an explicit distinction between preview display zoom and capture/composition zoom.
- [ ] Live preview no longer uses CameraX zoom for the normal still preview path when the product mode is full-lens preview with smaller capture window.
- [ ] Saved still output reflects the selected composition zoom/capture window, either through a tested postprocessor crop or a clearly supported device path.
- [ ] Overlay active frame and saved crop use the same center and scale semantics.
- [ ] Frame ratio crop and zoom crop compose deterministically.
- [ ] Existing pinch/slider inputs still update session zoom state and UI feedback.
- [ ] No feature is reported as complete if saved output cannot match the visible frame.
- [ ] Unit tests cover 1x identity, 2x centered crop, frame-ratio plus zoom crop, and unsupported/degraded behavior.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt`
- `core/media/` narrowly scoped zoom/crop contract files if needed
- `core/device/` only for explicit supported/degraded contract fields if truly required

Forbidden paths:
- `core/effect/`
- focal slider files
- mode plugin behavior unrelated to zoom/crop
- broad session kernel refactors

## Dependencies

Must wait for package 03.

## Parallel Safety

Not safe to run in parallel with packages 02 or 03.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence Pack

- Clear statement of final zoom semantics.
- Proof that preview is not CameraX-cropped in the still preview path.
- Proof that saved output is cropped or honestly degraded.
- Test output and residual true-device risks.
