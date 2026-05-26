# Package 03 — Preview Content Bounds for Frame Overlay

## Package ID
`03-preview-content-bounds`

## Problem Statement

Problem 9 remains unresolved in main: `previewContentGeometry()` still uses the full overlay view as the content rect. When `PreviewView` uses `fitCenter`, actual camera content may be narrower or shorter than the view. A 16:9 frame overlay can therefore exceed the actual preview content.

## Acceptance Criteria

- [ ] `PreviewOverlayRenderModel` or an equivalent render model carries actual preview content bounds or an explicit preview content aspect ratio.
- [ ] `previewContentGeometry()` computes `contentRect` from actual preview content under fitCenter, not always the full view.
- [ ] 16:9, 4:3, and 1:1 overlays never exceed `contentRect` in portrait and landscape tests.
- [ ] Existing zoom scaling composes with content bounds: frame first fits inside content, then zoom/capture-window scale is applied around the same center.
- [ ] Grid lines, dim scrim, frame guideline, watermark hint frame, and tap-focus clamp use the same active frame rect.
- [ ] If actual sensor/preview aspect is not available, the fallback must be explicit, tested, and documented in the status file as a residual risk.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/PreviewContentGeometryTest.kt`
- narrowly scoped render model tests if needed

Forbidden paths:
- `core/effect/`
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessor.kt`
- focal slider files

## Dependencies

Must wait for package 01. Coordinate with package 02 if `PreviewOverlayView.kt` has changed.

## Parallel Safety

Caution with package 02. Not safe with package 04.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence Pack

- Content-bounds formula.
- Exact fallback behavior.
- Tests showing 16:9 frame no longer exceeds actual preview content.
- Verification output.
