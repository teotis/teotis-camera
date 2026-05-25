# Preview Frame FitCenter Geometry Plan

> **For agentic workers:** This is a non-multimodal implementation handoff. Do not judge final visual polish from screenshots. Implement deterministic geometry, focused tests, and leave real-device visual acceptance to Codex/user. Use `rtk` for every shell command.

## Goal

Keep the live camera preview in its uncropped native displayed aspect, and make frame-ratio switching change only the overlay frame inside that displayed preview content. The frame guideline, outside dim region, grid, watermark frame hint, and tap-to-focus active area must always be equal to or smaller than the visible preview image, never larger and never calculated from UI chrome bounds.

## Context

- User request: preview must remain uncropped native aspect when switching `4:3 / 16:9 / 1:1`; only the frame box changes; the box must not overlap interface buttons/text and must never exceed the preview image.
- Verified fact: [`activity_main.xml`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/res/layout/activity_main.xml:15) sets `PreviewView` to `app:scaleType="fitCenter"`, so the displayed preview may be letterboxed inside the full-screen `PreviewView`.
- Verified fact: [`PreviewOverlayView.previewContentGeometry()`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt:443) currently builds `contentRect` as the full overlay/view size and its comment still says "full view for fillCenter".
- Verified fact: frame, grid, dim scrim, watermark four-border hint, and tap-to-focus active frame all derive from that helper via [`activeContentGeometry()`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt:178), [`drawFrameGuideline()`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt:256), [`drawPreviewFrame()`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt:343), and [`currentActiveFrameRectOrNull()`](/Volumes/Extreme_SSD/project/open_camera/app/src/main/java/com/opencamera/app/PreviewOverlayView.kt:193).
- Verified fact: existing tests in [`PreviewOverlayGeometryTest`](/Volumes/Extreme_SSD/project/open_camera/app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt:49) lock the old full-view geometry, including a test named "active capture frame is centered in full preview content".
- Non-goals: do not change `PreviewView` to `fillCenter`; do not crop or zoom the live preview when frame ratio changes; do not move session/kernel ownership; do not change saved JPEG frame-ratio postprocessing in this package; do not solve final visual QA without a screenshot or real device.

## Implementation Scope

- Replace full-view overlay geometry with a `fitCenter` content-rect model that represents the visible preview image inside the overlay.
- Keep frame ratio as an overlay/capture-guideline concept. `FrameRatioSelected` should still flow through existing mode/session/effect contracts, but must not force a preview rebind, PreviewView scale-type change, or CameraX zoom/crop change.
- Make all overlay features use the same source-of-truth `contentRect` and `activeFrameRect`.
- Add tests proving the frame is bounded by the preview content rect and that switching ratios changes only frame dimensions inside a stable content rect.

## Product Rules

- `PreviewView` remains `fitCenter` or an equivalent no-crop mode.
- The visible preview content rect is the maximum rect of the preview stream aspect that fits inside the overlay view.
- The active frame rect is the maximum rect of the selected frame ratio that fits inside the visible preview content rect.
- If the preview content aspect is unknown, use a conservative default matching the current still preview baseline, but structure the model so CameraX/native preview size can later update it.
- UI controls may overlay the screen, but the frame box itself must be computed from visible preview content and should not extend under the bottom cockpit or top bar when the fit-centered preview naturally leaves letterbox space.

## Steps

1. Add failing geometry tests in `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`.
   - Add a helper/test for `computeFitCenterContentRect(viewWidth, viewHeight, contentWidth, contentHeight)`.
   - Example portrait phone: `view=1080x2400`, native preview aspect `4:3` oriented as `3:4`; expected visible content is `1080x1440` centered vertically, not `1080x2400`.
   - Assert `1:1` frame is `1080x1080` centered inside the `1080x1440` preview content.
   - Assert portrait `16:9` frame is `810x1440` inside the same content, not `1080x1920` or full-screen.
   - Assert every frame rect edge is within content rect edges.

2. Update `PreviewOverlayView.kt` geometry contracts.
   - Introduce a small pure type such as:

```kotlin
internal data class PreviewContentAspect(
    val width: Int,
    val height: Int
)
```

   - Change `previewContentGeometry(...)` to accept preview content dimensions or aspect, for example:

```kotlin
internal fun previewContentGeometry(
    viewWidth: Int,
    viewHeight: Int,
    previewAspectWidth: Int = 3,
    previewAspectHeight: Int = 4,
    ratioWidth: Int = 0,
    ratioHeight: Int = 0
): PreviewContentGeometry
```

   - Compute `contentRect` with fit-center math first.
   - Compute `activeFrameRect` inside that `contentRect`, not inside the full view.
   - Rename comments away from `fillCenter`; the new invariant is "visible preview content for fitCenter".

3. Wire a stable preview aspect source into the render path.
   - Minimal acceptable first pass: add `previewAspect` to `PreviewOverlayRenderModel` with a conservative portrait-oriented `4:3` default, and have `PreviewOverlayView.render(...)` use it.
   - Better pass if the current CameraX dependency exposes it cleanly: update the aspect from `PreviewView.outputTransform` / actual preview output size / adapter-reported preview stream size. Keep that bridge app-layer only.
   - Do not read CameraX APIs from core modules.
   - Do not use frame ratio as the preview aspect. The preview aspect is stable when the selected frame changes.

4. Update all overlay consumers to use the new geometry.
   - `activeContentGeometry()`
   - `currentActiveFrameRectOrNull()`
   - `drawGrid()`
   - `drawFrameGuideline()`
   - `drawPreviewFrame()`
   - `drawWatermarkFourBorderHint()`
   - Tap filtering in `MainActivityActionBinder` should continue to read `overlayView.currentActiveFrameRectOrNull()`.

5. Update or remove old tests that assert full-view geometry.
   - Replace "active capture frame is centered in full preview content" with a fit-center invariant.
   - Keep tests that prove bottom controls do not shrink geometry, but rewrite the expectation: geometry is based on preview content rect, not arbitrary UI safe-area insets.

6. Run focused verification, then the current Stage 7 gate if implementation touches only app overlay.

## Acceptance Criteria

- Switching frame ratio never changes `PreviewView.scaleType`, `PreviewView.scaleX` except existing selfie mirror behavior, CameraX zoom, or preview use-case binding.
- For a tall phone view with a `4:3` native preview shown via `fitCenter`, selected `4:3`, `16:9`, and `1:1` frame rects are all inside the preview content rect.
- The frame/guideline, dim outside region, grid lines, four-border watermark hint, and tap-to-focus active area share the same `activeFrameRect`.
- Geometry tests fail against the current full-view implementation and pass after the fix.
- App tests and assemble pass.
- Final real-device visual QA confirms the frame does not exceed the live preview image and does not cover top/bottom cockpit controls in normal portrait use.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.PreviewContentGeometryTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.PreviewTapFocusGeometryTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- The exact preview stream aspect may vary by device, lens, CameraX negotiation, or future `UseCaseGroup/ViewPort` work. Keep the aspect source explicit and replaceable.
- Pure JVM tests cannot fully inspect Android `RectF` behavior in all cases, so keep most math in pure `FrameRect` helpers and test those directly.
- Saved JPEG frame-ratio crop may still need separate visual acceptance. This handoff only fixes preview overlay bounds relative to the live preview image.
- Codex/user should retain final multimodal acceptance with a portrait real-device screenshot or recording, because the defect is ultimately visual.
