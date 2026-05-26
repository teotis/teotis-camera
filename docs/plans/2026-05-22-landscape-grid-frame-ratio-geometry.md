# Landscape, Grid, And Frame Ratio Geometry Plan

> **For agentic workers:** This is a self-contained implementation handoff for a non-multimodal agent. Do not rely on screenshots. Use deterministic geometry tests and render-model assertions. Run shell commands through `rtk`.

**Goal:** Make landscape orientation, composition grid, and frame ratio work as one coherent geometry system: the UI topology remains stable, labels/buttons rotate with device orientation, the preview/capture frame becomes landscape-aware, and grid lines divide the actual captured frame area.

**Architecture:** Session/mode/effect state remains the source of truth for selected frame ratio. `PreviewOverlayView` owns overlay drawing only. `MainActivity` may apply view rotation and layout variants, but it must not own camera state or frame-ratio state.

**Tech Stack:** Android Views/XML, CameraX `PreviewView`, custom `PreviewOverlayView`, Kotlin render-model/unit tests.

---

## Evidence

The user requested:

- Landscape adaptation with unchanged UI layout, rotated buttons/text, and a landscape preview frame.
- Better composition grid, based on the actual captured preview region and x/y division like vivo/apple implementations.
- Correct frame ratio handling with attention to using sensor area; in portrait use, the long edge should remain vertical.

Current code facts:

- `activity_main.xml` has no `layout-land` variant.
- `MainActivity` has no orientation/render model for rotating labels/buttons.
- `PreviewOverlayView.drawGrid()` draws against the entire view.
- `PreviewOverlayView.drawPreviewFrame()` computes a frame rect, dims outside it, and draws a frame label.
- `computePreviewFrameRect()` only receives `ratioWidth`/`ratioHeight`; it has no orientation policy.
- `PhotoFrameRatioPostProcessor.computeCenterCropBounds()` crops by target ratio but does not encode long-edge orientation semantics beyond width/height ratio.

## Required Behavior

- In portrait:
  - `4:3` means a portrait-oriented frame `3:4` on screen when the phone is vertical.
  - `16:9` means a portrait-oriented frame `9:16` on screen when the phone is vertical.
  - `1:1` is square.
  - This keeps the long edge vertical and avoids wasting preview/sensor area visually.
- In landscape:
  - `4:3` means landscape `4:3`.
  - `16:9` means landscape `16:9`.
  - `1:1` is square.
- Grid lines divide the active captured frame rect, not the full screen.
- Grid line count and positions:
  - `RULE_OF_THIRDS`: two vertical and two horizontal lines at 1/3 and 2/3 inside the frame.
  - `GOLDEN_RATIO`: two vertical and two horizontal lines at 0.381966 and 0.618034 inside the frame.
- Outside-frame dimming should not receive grid lines.
- UI topology should remain recognizable in landscape: main controls stay in their relative regions, but text/buttons rotate to remain readable.

## Files

Modify:

- `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- Add if needed: `app/src/main/res/layout-land/activity_main.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Add if useful: `app/src/test/java/com/opencamera/app/PreviewOverlayGeometryTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoFrameRatioPostProcessorTest.kt`

Do not change CameraX binding or device adapter unless a test proves the saved output ratio is wrong.

## Implementation Tasks

- [ ] **Step 1: Introduce orientation-aware frame geometry helpers**

In `PreviewOverlayView.kt`, create pure helpers near `computePreviewFrameRect()`:

```kotlin
internal enum class PreviewDisplayOrientation {
    PORTRAIT,
    LANDSCAPE
}

internal data class OrientedFrameRatio(
    val width: Int,
    val height: Int
)

internal fun orientedFrameRatio(
    ratioWidth: Int,
    ratioHeight: Int,
    orientation: PreviewDisplayOrientation
): OrientedFrameRatio {
    if (ratioWidth == ratioHeight) return OrientedFrameRatio(1, 1)
    return when (orientation) {
        PreviewDisplayOrientation.PORTRAIT -> OrientedFrameRatio(
            width = minOf(ratioWidth, ratioHeight),
            height = maxOf(ratioWidth, ratioHeight)
        )
        PreviewDisplayOrientation.LANDSCAPE -> OrientedFrameRatio(
            width = maxOf(ratioWidth, ratioHeight),
            height = minOf(ratioWidth, ratioHeight)
        )
    }
}
```

Derive orientation from `viewWidth <= viewHeight` unless a render model later supplies display rotation.

- [ ] **Step 2: Update `computePreviewFrameRect()`**

Change it so it orients the requested ratio by display orientation before calculating the rect:

```kotlin
val orientation = if (viewWidth <= viewHeight) PreviewDisplayOrientation.PORTRAIT else PreviewDisplayOrientation.LANDSCAPE
val oriented = orientedFrameRatio(ratioWidth, ratioHeight, orientation)
val targetRatio = oriented.width.toFloat() / oriented.height.toFloat()
```

Keep padding/top/bottom inset behavior.

- [ ] **Step 3: Draw grid inside the active frame rect**

Refactor `drawGrid()`:

```kotlin
private fun activeFrameRectOrFullView(): RectF {
    val frame = renderModel.frame
    return if (frame != null) {
        computePreviewFrameRect(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frame.ratio.width,
            ratioHeight = frame.ratio.height,
            horizontalPaddingPx = 12f * density
        )
    } else {
        RectF(0f, 0f, width.toFloat(), height.toFloat())
    }
}
```

Then draw:

```kotlin
val rect = activeFrameRectOrFullView()
canvas.drawLine(rect.left + rect.width() * fraction, rect.top, rect.left + rect.width() * fraction, rect.bottom, gridPaint)
canvas.drawLine(rect.left, rect.top + rect.height() * fraction, rect.right, rect.top + rect.height() * fraction, gridPaint)
```

Remove or greatly soften the current full-view safe-inset emphasis rectangle. If an outline is needed, draw only the active frame border.

- [ ] **Step 4: Add geometry tests**

Create unit tests for pure geometry helpers:

```kotlin
@Test
fun `portrait 16 9 frame uses vertical long edge`() {
    val rect = computePreviewFrameRect(
        viewWidth = 1080,
        viewHeight = 2400,
        ratioWidth = 16,
        ratioHeight = 9,
        horizontalPaddingPx = 0f
    )
    val ratio = rect.width() / rect.height()
    assertEquals(9f / 16f, ratio, 0.01f)
}

@Test
fun `landscape 16 9 frame uses horizontal long edge`() {
    val rect = computePreviewFrameRect(
        viewWidth = 2400,
        viewHeight = 1080,
        ratioWidth = 16,
        ratioHeight = 9,
        horizontalPaddingPx = 0f
    )
    val ratio = rect.width() / rect.height()
    assertEquals(16f / 9f, ratio, 0.01f)
}
```

Also test `1:1` and `4:3`.

- [ ] **Step 5: Add grid-position pure helper if needed**

If `Canvas` tests are impractical, extract:

```kotlin
internal fun gridLineSegments(rect: RectF, fractions: List<Float>): List<GridLineSegment>
```

Assert all segment endpoints are inside `rect` and match thirds/golden-ratio positions. This gives text-only verification of the core grid behavior.

- [ ] **Step 6: Add orientation-aware UI rotation without state ownership**

In `MainActivity`, add a small view helper:

```kotlin
private fun applyControlRotationForDisplay() {
    val rotationDegrees = when (windowManager.defaultDisplay.rotation) {
        Surface.ROTATION_90 -> 90f
        Surface.ROTATION_270 -> -90f
        else -> 0f
    }
    listOf(
        buttonFilterEntry,
        buttonQuickLauncher,
        buttonLensLabEntry,
        buttonDevEntry,
        shutterButton,
        lensFacingButton
    ).forEach { it.rotation = rotationDegrees }
}
```

Use modern `display?.rotation` if available in the min SDK setup. Call it from `onCreate`, `onConfigurationChanged`, and render if needed. Register `android:configChanges="orientation|screenSize|keyboardHidden"` only if the app intentionally handles configuration changes in-place.

This helper rotates controls only. It must not change session state.

- [ ] **Step 7: Add a `layout-land` variant only if necessary**

If the existing constraint layout compresses panels in landscape, add `app/src/main/res/layout-land/activity_main.xml` with the same IDs and hierarchy. Keep:

- preview full-bleed,
- right rail relative to screen side,
- bottom cockpit reachable,
- mode track and zoom strip outside the active frame when possible.

Do not redesign the full UI in this plan.

- [ ] **Step 8: Verify saved-output ratio remains aligned**

Keep existing `PhotoFrameRatioPostProcessorTest` center-crop assertions. Add tests for portrait source dimensions:

```kotlin
@Test
fun `portrait source 16 9 target crops to vertical 9 16 when metadata requests portrait frame`() {
    // If current media metadata cannot express oriented ratios, document that saved output orientation follows EXIF/source orientation.
}
```

If saved JPEG orientation cannot be tested reliably in unit tests because EXIF rotation is involved, record the limitation in the test comment and leave visual saved-output matching to multimodal QA.

## Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.PreviewOverlayGeometryTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoFrameRatioPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

If `PreviewOverlayGeometryTest` is not created, omit that command and ensure equivalent tests are added to an existing app test class.

## Manual Smoke

- Portrait `16:9` should show a tall frame.
- Landscape `16:9` should show a wide frame.
- Grid lines should appear only inside the active frame.
- Rotated buttons/text should remain readable after device rotation.

## Non-Goals

- Do not implement vendor-style exact grid aesthetics in this text-only pass.
- Do not rewrite the entire cockpit layout.
- Do not add hidden frame-ratio state outside session/mode/effect ownership.
- Do not require screenshot comparison for this implementation pass.

