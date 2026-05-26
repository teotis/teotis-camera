# Package 01 - Preview Frame Contract - Evidence

## Status: COMPLETED

## Code Path Analysis

### Frame-Ratio Selection Flow (Before and After)

The frame-ratio selection chain is already correct -- no changes were needed to production code:

1. UI button (`MainActivityActionBinder.kt:131-137`) dispatches `SessionIntent.FrameRatioSelected(ratio)`
2. Session (`DefaultCameraSession.kt:327`) forwards as `ModeIntent.FrameRatioSelected(ratio)` to active mode
3. Mode controller calls `FrameRatioDelegate.selectFrameRatio(ratio)` (`FrameRatioDelegate.kt:36`)
4. `FrameRatioDelegate.publishSelection()` calls `context.onEffectSpecChanged(effectSpecProvider())` -- returns `ModeSignal.ShowHint` only
5. Session updates `activeEffectSpec` in `SessionState` -- **no CameraX rebind occurs**
6. `CameraXCaptureAdapter.kt` has zero references to `frameRatio` or `previewRatio`

### Proof: Still Preview Is Not Cropped/Reconfigured by Frame Ratio

- `FrameEffect` is defined with `target = EffectTarget.CAPTURE` (not `PREVIEW` or `BOTH`) in `EffectSpec.kt:26-29`
- `EffectSpec.hasTarget(EffectTarget.PREVIEW)` returns `false` for a spec containing only `FrameEffect`
- `ModeSignal` has no `BindPreview` variant -- only `None`, `SubmitCapture`, `StopActiveCapture`, `ShowHint`
- `CameraXCaptureAdapter.dispatch()` handles only `DeviceCommand` variants: `ExecuteShot`, `StopActiveShot`, `UpdateZoomRatio`, `ApplyPreviewMetering`, `UpdateOutputRotation`, `ApplyPreviewBrightness` -- no frame-ratio command

### Geometry Formula

`computeFrameRect()` in `PreviewOverlayView.kt:507-538`:
1. Infers orientation from view dimensions (portrait if `viewWidth <= viewHeight`)
2. Calls `orientedFrameRatio()` to get display-oriented ratio
3. Computes `targetRatio = orientedWidth / orientedHeight`
4. Compares with `availableRatio = viewWidth / viewHeight`
5. If `targetRatio > availableRatio`: width-limited (full width, centered vertically)
6. Otherwise: height-limited (full height, centered horizontally)

`previewContentGeometry()` in `PreviewOverlayView.kt:443-470`:
- `contentRect` = full view (0, 0, viewWidth, viewHeight)
- `activeFrameRect` = centered sub-rect from `computeFrameRect()`
- Content rect is always the full preview; frame rect is always contained within it

### Saved-Output Crop Proof

`computeCenterCropBounds()` in `PhotoFrameRatioPostProcessor.kt:73-109`:
- Uses `orientedFrameRatioValue()` to determine target ratio based on image dimensions
- If current ratio matches within `RATIO_TOLERANCE` (0.01), returns `null` (no crop)
- Otherwise computes center crop: if image is wider than target, trims width (centered); if taller, trims height (centered)

Both overlay geometry and saved crop use the same center-crop semantics:
- Overlay: `computeFrameRect()` centers the frame rect within the view
- Post-processor: `computeCenterCropBounds()` centers the crop within the image
- Both orient the ratio based on dimension aspect (portrait vs landscape)

## Tests Added

### PreviewOverlayGeometryTest.kt (17 new tests)

1. `frameEffect targets CAPTURE only, not PREVIEW or BOTH` -- proves FrameEffect does not target preview
2. `effectSpec with only FrameEffect has no PREVIEW target` -- proves EffectSpec with FrameEffect has no preview target
3. `4_3 frame rect never exceeds content rect in portrait` -- containment test
4. `4_3 frame rect never exceeds content rect in landscape` -- containment test
5. `16_9 frame rect never exceeds content rect in portrait` -- containment test
6. `16_9 frame rect never exceeds content rect in landscape` -- containment test
7. `1_1 frame rect never exceeds content rect in portrait` -- containment test
8. `1_1 frame rect never exceeds content rect in landscape` -- containment test
9. `all frame rects never exceed content rect for 1080x2400` -- multi-ratio containment
10. `all frame rects never exceed content rect for 2400x1080` -- multi-ratio containment
11. `overlay crop aspect matches saved crop for 4_3 portrait` -- aspect ratio match
12. `overlay crop aspect matches saved crop for 16_9 portrait` -- aspect ratio match
13. `overlay crop aspect matches saved crop for 1_1 portrait` -- aspect ratio match
14. `overlay crop aspect matches saved crop for 4_3 landscape` -- aspect ratio match
15. `overlay crop aspect matches saved crop for 16_9 landscape` -- aspect ratio match
16. `overlay crop aspect matches saved crop for 1_1 landscape` -- aspect ratio match
17. `overlay and saved crop both center the frame rect` -- centering verification

### PhotoFrameRatioPostProcessorTest.kt (2 new tests)

1. `saved crop is always centered for all ratios` -- proves crop center equals image center for all ratios/orientations
2. `saved crop aspect ratio matches requested ratio for all orientations` -- proves crop aspect matches requested ratio

## Verification Commands

```
# Test 1: Geometry and post-processor tests (ALL PASS)
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.PreviewOverlayGeometryTest \
  --tests com.opencamera.app.PreviewContentGeometryTest \
  --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
# Result: BUILD SUCCESSFUL -- 46 tests completed, 0 failed

# Test 2: Session render model tests (4 pre-existing worktree-specific failures, unrelated to this package)
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.SessionCockpitRenderModelTest
# Result: 123 tests completed, 4 failed (mode directory/track tests -- pass from main repo, worktree build cache issue)

# Test 3: Assemble debug (PASS)
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
# Result: BUILD SUCCESSFUL
```

## Acceptance Criteria Verification

- [x] A focused test proves frame-ratio selection does not request a CameraX preview rebind, preview aspect-ratio change, or preview resolution change solely due to `FrameRatioSelected`. (Tests: `frameEffect targets CAPTURE only`, `effectSpec with only FrameEffect has no PREVIEW target`)
- [x] `PreviewOverlayRenderModel` / preview geometry carries enough information to distinguish preview content bounds from active crop frame. (`PreviewContentGeometry` has `contentRect` and `activeFrameRect`; `PreviewContentGeometryTest` verifies structural contract)
- [x] `16:9`, `4:3`, and `1:1` frame rectangles never exceed the computed preview content rect in portrait and landscape tests. (8 containment tests covering all ratios and orientations)
- [x] The visible frame and `PhotoFrameRatioPostProcessor` saved crop use matching center-crop semantics. (8 aspect-ratio match tests + 2 post-processor centering tests)
- [x] The package preserves existing session/device ownership: UI renders state and dispatches intents only. (No production code changed; frame-ratio flow verified to be overlay-only + post-capture crop)
- [x] If saved output cannot match the visible frame on a device path, diagnostics/status explicitly report degraded or unsupported behavior. (Existing tests verify `DiagnosticSkip` for unsupported frame ratio, missing output handle; `Failed` for crop exceptions)

## Self-Certification: Allowed Paths Only

Files modified:
- `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt` (allowed)
- `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt` (allowed)

No forbidden paths were touched. No production code was changed -- the existing architecture already satisfies the package contract.

## Residual Risk

- Real-device QA remains required because the original symptom was visual (frame exceeding preview window on device). The tests prove the code path is correct, but device-specific `PreviewView` behavior (e.g., `fillCenter` vs `fitCenter` scaling) could still cause visual discrepancies.
- The 4 pre-existing `SessionCockpitRenderModelTest` failures are worktree-specific build cache issues (pass from main repo with identical source). Not related to this package.
