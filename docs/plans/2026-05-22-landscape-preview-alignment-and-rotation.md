# Landscape Preview Alignment And Rotation Plan

> **For agentic workers:** This is a non-multimodal implementation handoff. Do not judge screenshot aesthetics yourself. Implement deterministic geometry/orientation behavior, then hand visual verification to the multimodal QA plan. Use `rtk` for shell commands.

**Goal:** Make landscape interaction coherent while keeping the cockpit layout recognizable, and remove the class of bugs where the actual imaging area appears vertically offset from the preview/overlay area.

## User-Facing Requirements

- Landscape should not become a new rearranged camera UI. Keep the same top/bottom/side topology.
- In landscape, controls that contain text or direction-sensitive glyphs should rotate so they remain readable/upright relative to the user.
- Preview frame, grid, tap area, and saved-frame visual mask must describe the same active imaging rect.
- The reported "real imaging area is lower than the preview area" must be addressed by code-level geometry alignment, not by hand-tuned visual offsets.

## Current Code Facts

- `app/src/main/res/layout/activity_main.xml` makes `cameraPreview` and `previewOverlay` full-screen.
- `cameraPreview` uses `app:scaleType="fillCenter"`.
- `PreviewOverlayView` owns frame and grid drawing, but earlier grid/frame work can still diverge from CameraX `PreviewView` if it assumes the full view is the active content rect.
- `MainActivity` has no explicit orientation render model for rotating controls.
- There is no `layout-land/activity_main.xml`.

## Architecture Boundary

- UI may rotate views and draw overlays.
- Session Kernel remains the owner of camera/session state.
- Device Adapter remains the owner of CameraX binding.
- Do not add hidden camera state to `MainActivity`.
- Geometry helpers should be pure and unit-testable.

## Implementation Plan

### Step 1: Add a display orientation render model

Add a small UI-only orientation model, either in `CameraCockpitRenderModel.kt` or a dedicated UI geometry file:

```kotlin
internal enum class CockpitDisplayOrientation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    LANDSCAPE_RIGHT
}

internal data class CockpitOrientationRenderModel(
    val orientation: CockpitDisplayOrientation,
    val controlRotationDegrees: Float
)
```

The render model should derive from display rotation/configuration. Keep it independent from camera sensor orientation.

Expected behavior:

- Portrait: `0f`
- Landscape left/right: rotate text-bearing controls by `90f` or `-90f` so labels read upright.

Apply rotation to:

- right/top utility buttons,
- quick-panel buttons,
- mode labels if the mode track remains visually vertical relative to user,
- settings/panel headers only if panel content is shown in landscape.

Do not rotate `PreviewView` itself.

### Step 2: Define one active preview content rect

Introduce a pure helper that returns the rect all overlay UI should use:

```kotlin
internal data class PreviewContentGeometry(
    val viewWidth: Int,
    val viewHeight: Int,
    val contentRect: RectF,
    val activeFrameRect: RectF
)
```

The first implementation can conservatively treat `PreviewView.fillCenter` as covering the full view when the app has no source aspect-ratio metadata. But the helper must be the only place that later accounts for CameraX crop/letterbox behavior.

Acceptance for this step:

- Grid, frame outline, dim region, and tap-to-focus normalized coordinates all read from this helper or an equivalent single source.
- No overlay component hardcodes an independent top/bottom offset.

### Step 3: Remove downward bias from frame calculation

If `computePreviewFrameRect()` or equivalent code applies bottom cockpit insets, make the inset policy explicit:

- `contentRect` describes the actual preview content.
- `safeOverlayRect` describes where non-preview UI may draw.
- `activeFrameRect` describes the captured frame inside `contentRect`.

Do not calculate the imaging frame from a UI-safe rect that excludes the bottom cockpit unless the saved output is also cropped that way. This is the likely source of "actual imaging area appears lower/offset" bugs.

### Step 4: Keep frame-ratio orientation semantics

Reuse the third-round requirement:

- Portrait screen: `16:9` is shown as `9:16`; `4:3` is shown as `3:4`.
- Landscape screen: `16:9` is shown as `16:9`; `4:3` is shown as `4:3`.
- `1:1` remains square.

The active frame rect must be centered inside `contentRect`, not inside a panel-safe or full-screen rect with asymmetric UI offsets.

### Step 5: Add tests

Add pure JVM tests, for example:

- `PreviewContentGeometryTest`
- `CameraCockpitRenderModelTest`
- Existing `SessionUiRenderModelTest` if orientation is exposed there.

Required cases:

- Portrait `1080 x 2400`, `16:9` produces a centered `9:16` rect.
- Landscape `2400 x 1080`, `16:9` produces a centered `16:9` rect.
- The frame rect center equals the content rect center within 1 px for `1:1`, `4:3`, and `16:9`.
- Grid line positions are inside `activeFrameRect`, never outside.
- Orientation render model returns non-zero control rotation in landscape.

### Step 6: Real-device visual verification handoff

After implementation, hand to `2026-05-22-fourth-feedback-multimodal-visual-qa.md` for screenshots:

- portrait photo mode,
- landscape left,
- landscape right,
- quick panel open,
- frame ratio `4:3 / 16:9 / 1:1`.

## Files To Inspect Or Modify

- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/res/layout/activity_main.xml`
- Optional: `app/src/main/res/layout-land/activity_main.xml`
- `app/src/test/java/com/opencamera/app/*Geometry*Test.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

## Non-Goals

- Do not alter CameraX binding solely to fix a visual overlay offset.
- Do not create a second preview/capture state owner.
- Do not tune offsets by screenshots without a pure geometry explanation.

## Verification

Focused:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

If new geometry tests are added, run them explicitly:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewContentGeometryTest
```

Then:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```
