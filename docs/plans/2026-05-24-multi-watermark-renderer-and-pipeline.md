# Multi Watermark Renderer And Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Render the new `pure-text` and `blur-four-border` templates into saved JPEGs through the existing media postprocessor chain.

**Architecture:** `PhotoWatermarkPostProcessor` remains the still-photo output owner. The renderer resolves template/style metadata from `MediaMetadata`, draws onto a bitmap, preserves EXIF, embeds OCWM, and emits pipeline notes. Mode plugins, session, UI, and coordinator must not draw or mutate final watermark pixels.

**Tech Stack:** Kotlin, Android `Bitmap`/`Canvas`/`Paint`, AndroidX `ExifInterface`, existing app unit tests and JVM-friendly renderer tests.

---

## File Structure

- Modify `app/src/main/java/com/opencamera/app/camera/PhotoWatermarkPostProcessor.kt`
  - Add template constants `pure-text` and `blur-four-border`.
  - Resolve template defaults for the two new templates.
  - Add pure text drawing.
  - Add blurred four-border drawing.
  - Keep OCWM archive helper and EXIF restore order unchanged.
- Modify `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkTemplateResolverTest.kt`
  - Cover new template resolution, defaults, fallback, and background restrictions.
- Modify `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkPostProcessorTest.kt`
  - Cover processor notes for the new template IDs at the fake-editor seam.
- Add or extend bitmap-level renderer tests if the project test runtime supports Android graphics:
  - `app/src/test/java/com/opencamera/app/camera/PhotoWatermarkBitmapRendererTest.kt`

## Rendering Semantics

### Pure Text

Behavior:

- Does not expand the bitmap.
- Draws title and up to two supporting lines directly over the image.
- Uses no rounded card and no solid background.
- Uses placement, text scale, and opacity from `WatermarkStyleSettings`.
- Uses shadow for contrast; shadow opacity follows text opacity.

Default visual:

```text
position: bottom-left
title: device/model or mode title
details: datetime and camera params when available
type: sans-serif, medium title, regular details
```

### Blurred Four-Border

Behavior:

- Expands the output canvas.
- Draws a blurred, source-derived background across the full canvas.
- Draws the original unblurred photo inset from all four edges.
- Keeps all four borders visible; the lower border may be taller to hold text, but it must still read as part of the same blurred frame, not as a separate polaroid card.
- Supports only `SOURCE_BLUR`, `SOURCE_LIGHT_BLUR`, and `SOURCE_VIVID_BLUR`.
- Places text in the lower border according to bottom-left, bottom-center, or bottom-right.

Recommended geometry:

```text
side border = max(20 px, minEdge * 0.045)
top border = max(20 px, minEdge * 0.045)
bottom border = max(titleTextSize * 2.35, minEdge * 0.09)
inner hairline = 1 px white at 28% alpha for light blur, black at 24% alpha for vivid blur
```

This keeps the center photo dominant and prevents the frame from becoming a heavy poster layout.

## Implementation Tasks

### Task 1: Resolve new templates

- [ ] **Step 1: Add failing resolver tests**

Add tests in `PhotoWatermarkTemplateResolverTest.kt`:

```kotlin
@Test
fun `pure text resolver keeps overlay template and no frame expansion`() {
    val resolved = resolvePhotoWatermarkTemplate(
        templateId = "pure-text",
        watermarkText = "OpenCamera",
        metadata = MediaMetadata(
            customTags = mapOf(
                "watermarkPosition" to "top-right",
                "watermarkTextScale" to "1.2",
                "watermarkTextOpacity" to "0.8"
            )
        ),
        preservedExif = emptyMap()
    )

    assertEquals("pure-text", resolved.templateId)
    assertFalse(resolved.usesExpandedFrame)
    assertEquals(WatermarkTextPlacement.TOP_RIGHT, resolved.placement)
    assertEquals(1.2f, resolved.textScale)
    assertEquals(0.8f, resolved.textOpacity)
}

@Test
fun `blur four border resolver clamps unsupported solid background to light blur`() {
    val resolved = resolvePhotoWatermarkTemplate(
        templateId = "blur-four-border",
        watermarkText = "OpenCamera",
        metadata = MediaMetadata(
            customTags = mapOf(
                "watermarkFrameBackground" to "white",
                "watermarkPosition" to "bottom-center"
            )
        ),
        preservedExif = emptyMap()
    )

    assertEquals("blur-four-border", resolved.templateId)
    assertTrue(resolved.usesExpandedFrame)
    assertEquals(WatermarkFrameBackground.SOURCE_LIGHT_BLUR, resolved.frameBackground)
    assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, resolved.placement)
}
```

- [ ] **Step 2: Extend resolver constants and defaults**

Add constants:

```kotlin
private const val TEMPLATE_PURE_TEXT = "pure-text"
private const val TEMPLATE_BLUR_FOUR_BORDER = "blur-four-border"
```

Include them in normalized template matching.

Set:

```kotlin
usesExpandedFrame = normalizedTemplateId == TEMPLATE_TRAVEL_POLAROID ||
    normalizedTemplateId == TEMPLATE_RETRO_FRAME ||
    normalizedTemplateId == TEMPLATE_BLUR_FOUR_BORDER
```

Default background:

```kotlin
TEMPLATE_BLUR_FOUR_BORDER -> WatermarkFrameBackground.SOURCE_LIGHT_BLUR
```

If `TEMPLATE_BLUR_FOUR_BORDER` receives `DARK` or `WHITE`, resolve to `SOURCE_LIGHT_BLUR`.

- [ ] **Step 3: Run resolver tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest
```

Expected result: resolver tests pass.

### Task 2: Draw pure text

- [ ] **Step 1: Add bitmap renderer test**

If Android graphics tests are available in the current app unit test environment, add:

```kotlin
@Test
fun `pure text renderer preserves bitmap dimensions`() {
    val bitmap = Bitmap.createBitmap(320, 240, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(32, 64, 96))

    val result = renderPhotoWatermarkBitmap(
        bitmap = bitmap,
        template = ResolvedPhotoWatermarkTemplate(
            templateId = "pure-text",
            title = "OpenCamera",
            supportingLines = listOf("2026-05-24", "ISO 100"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = false,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )
    )

    assertEquals(320, result.bitmap.width)
    assertEquals(240, result.bitmap.height)
}
```

If the graphics runtime cannot run this test locally, add the resolver/processor tests and record bitmap renderer smoke as visual/device QA in the UI verification package.

- [ ] **Step 2: Implement `drawPureTextOverlay()`**

Add a helper near `drawClassicOverlay()`:

```kotlin
private fun drawPureTextOverlay(
    canvas: Canvas,
    bitmap: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
)
```

Implementation requirements:

- Use `Paint.Align` based on placement.
- Use title color `Color.WHITE`.
- Use detail color `Color.argb(220, 235, 238, 242)`.
- Use `setShadowLayer(titleTextSize * 0.22f, 0f, titleTextSize * 0.08f, Color.argb(190, 0, 0, 0))`.
- Do not draw `drawRoundRect()` or any backing rectangle.
- Use `fitText()` for supporting lines.

- [ ] **Step 3: Route template in `renderPhotoWatermarkBitmap()`**

Add branch:

```kotlin
TEMPLATE_PURE_TEXT -> {
    val canvas = Canvas(bitmap)
    drawPureTextOverlay(
        canvas = canvas,
        bitmap = bitmap,
        template = template,
        titleTextSize = titleTextSize,
        detailTextSize = detailTextSize,
        padding = padding
    )
    PhotoWatermarkBitmapRenderResult(bitmap = bitmap, warning = template.warning)
}
```

- [ ] **Step 4: Run focused app tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
```

Expected result: focused tests pass.

### Task 3: Draw blurred four-border

- [ ] **Step 1: Add renderer dimension test**

Add a test:

```kotlin
@Test
fun `blur four border expands bitmap and keeps source centered`() {
    val bitmap = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888)
    bitmap.eraseColor(Color.rgb(80, 120, 160))

    val result = renderPhotoWatermarkBitmap(
        bitmap = bitmap,
        template = ResolvedPhotoWatermarkTemplate(
            templateId = "blur-four-border",
            title = "OpenCamera",
            supportingLines = listOf("2026-05-24 • ISO 100"),
            frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            textScale = 1f,
            textOpacity = 1f
        )
    )

    assertTrue(result.bitmap.width > 400)
    assertTrue(result.bitmap.height > 300)
}
```

- [ ] **Step 2: Implement `drawBlurFourBorderFrame()`**

Add helper:

```kotlin
private fun drawBlurFourBorderFrame(
    source: Bitmap,
    template: ResolvedPhotoWatermarkTemplate,
    titleTextSize: Float,
    detailTextSize: Float,
    padding: Float
): PhotoWatermarkBitmapRenderResult
```

Implementation requirements:

- Compute side/top/bottom borders from the geometry in this document.
- Create `framedBitmap` with expanded dimensions.
- Call existing `drawFrameBackground()` to fill full canvas with source blur.
- Draw source bitmap at `(sideBorder, topBorder)`.
- Draw an inner hairline around the source photo.
- Draw title and up to two supporting lines inside the bottom border.
- Use `Paint.Align.CENTER` only for `BOTTOM_CENTER`; use left/right for other allowed placements.
- Use warm near-white text for `SOURCE_BLUR`, ink-gray text for `SOURCE_LIGHT_BLUR`, and ivory text for `SOURCE_VIVID_BLUR`.
- Recycle only intermediate bitmaps created by this helper; do not recycle `source` here.

- [ ] **Step 3: Route template in `renderPhotoWatermarkBitmap()`**

Add branch:

```kotlin
TEMPLATE_BLUR_FOUR_BORDER -> drawBlurFourBorderFrame(
    source = bitmap,
    template = template,
    titleTextSize = titleTextSize,
    detailTextSize = detailTextSize,
    padding = padding
)
```

- [ ] **Step 4: Ensure archive and EXIF behavior remains unchanged**

Do not change this order in `AndroidPhotoWatermarkEditor.apply()`:

```text
read source bytes
decode
render template bitmap
write visible JPEG
restore EXIF
read visible JPEG after EXIF restore
embed OCWM archive
write archived JPEG
return PhotoWatermarkApplied
```

- [ ] **Step 5: Run focused watermark tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest
```

Expected result: resolver, postprocessor, and archive tests pass.

### Task 4: Verify pipeline notes and bridge behavior

- [ ] **Step 1: Add fake-editor processor-note cases**

In `PhotoWatermarkPostProcessorTest.kt`, assert:

```kotlin
assertTrue(result.pipelineNotes.contains("watermark:rendered:pure-text"))
assertTrue(result.pipelineNotes.contains("watermark:rendered:blur-four-border"))
```

Use fake editor invocations to prove `decidePhotoWatermarkWork()` passes the selected template ID through unchanged.

- [ ] **Step 2: Run bridge/media tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
```

Expected result: tests pass without changing media pipeline ownership.

## Acceptance

- `pure-text` visibly alters saved JPEG pixels without changing bitmap dimensions.
- `blur-four-border` visibly expands saved JPEG dimensions and creates source-derived blur on all four sides.
- Existing three templates keep current output semantics.
- Unknown template fallback still lands on `classic-overlay`.
- OCWM archive embedding still stores the pre-watermark JPEG bytes.
- EXIF restore still happens before OCWM embedding.
- Pipeline notes include the rendered template ID for both new templates.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoWatermarkTemplateResolverTest --tests com.opencamera.app.camera.PhotoWatermarkPostProcessorTest --tests com.opencamera.app.camera.PhotoWatermarkArchiveEditorTest --tests com.opencamera.app.camera.AlgorithmProcessorBridgesTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:media:test --tests com.opencamera.core.media.ShotExecutorTest
rtk ./scripts/verify_stage_6b3_watermark_v2.sh
```
