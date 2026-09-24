package com.opencamera.app

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.RectF
import androidx.test.core.app.ApplicationProvider
import com.opencamera.core.effect.PreviewEffectRenderModel
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.effect.WatermarkPreviewDecoration
import com.opencamera.core.effect.WatermarkPreviewShape
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.WatermarkTextPlacement
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PreviewOverlayWatermarkRenderTest {

    @Test
    fun `portrait preview content is aligned to bottom cockpit edge`() {
        val geometry = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        )

        assertEquals(0f, geometry.contentRect.left)
        assertEquals(480f, geometry.contentRect.top)
        assertEquals(1080f, geometry.contentRect.right)
        assertEquals(1920f, geometry.contentRect.bottom)
    }

    @Test
    fun `expanded frame watermark reserves bottom band outside active capture frame`() {
        val rect = activeSquareFrame()

        val band = expandedFrameBottomBandRect(rect, viewHeight = 1920, density = 1f)

        requireNotNull(band)
        assertEquals(rect.bottom, band.top)
        assertTrue(band.bottom > rect.bottom, "rect=$rect band=$band")
        assertTrue(band.height() >= 56f, "rect=$rect band=$band")
    }

    @Test
    fun `high design preview uses saved render frame proportions around photo slot`() {
        val photoSlot = RectF(100f, 200f, 900f, 1200f)

        val destination = highDesignWatermarkFrameMetrics(
            photoWidth = photoSlot.width(),
            photoHeight = photoSlot.height()
        ).destinationAround(photoSlot)

        assertEquals(78.4f, destination.left, 0.01f)
        assertEquals(117f, destination.top, 0.01f)
        assertEquals(921.6f, destination.right, 0.01f)
        assertEquals(1328f, destination.bottom, 0.01f)
    }

    @Test
    fun `high design preview selects asset variant from expanded output aspect`() {
        val landscapePhotoSlot = RectF(0f, 0f, 1600f, 1200f)
        val destination = highDesignWatermarkFrameMetrics(
            photoWidth = landscapePhotoSlot.width(),
            photoHeight = landscapePhotoSlot.height()
        ).destinationAround(landscapePhotoSlot)

        assertEquals("square", highDesignWatermarkAssetSuffix(destination.width() / destination.height()))
    }

    @Test
    fun `high design preview fits the complete composition around the eighty percent photo slot`() {
        val content = RectF(0f, 480f, 1080f, 1920f)

        val layout = highDesignWatermarkPreviewLayout(
            basePhotoSlot = content,
            availableBounds = content
        )

        assertEquals(0.8f, layout.photoSlot.width() / content.width(), 0.01f)
        assertEquals(0.8f, layout.photoSlot.height() / content.height(), 0.01f)
        assertTrue(layout.destination.left >= content.left)
        assertTrue(layout.destination.top >= content.top)
        assertTrue(layout.destination.right <= content.right)
        assertTrue(layout.destination.bottom <= content.bottom)
        assertTrue(
            layout.photoSlot.centerY() < content.centerY(),
            "photo slot should move upward to reserve the larger final metadata band"
        )
    }

    @Test
    fun `high design watermark keeps an eighty percent composition window at equal zoom`() {
        val view = renderHighDesignPreview(captureZoomRatio = 1f, previewZoomRatio = 1f)

        val rect = requireNotNull(view.currentActiveFrameRectOrNull())
        val content = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        ).contentRect

        assertEquals(0.8f, rect.width() / content.width(), 0.01f)
        assertEquals(0.8f, rect.height() / content.height(), 0.01f)

        val transform = requireNotNull(view.currentPreviewSurfaceTransformOrNull())
        assertEquals(0.8f, transform.scale, 0.01f)
        assertEquals(content.left, transform.sourceClipRect.left, 0.01f)
        assertEquals(content.top, transform.sourceClipRect.top, 0.01f)
        assertEquals(content.right, transform.sourceClipRect.right, 0.01f)
        assertEquals(content.bottom, transform.sourceClipRect.bottom, 0.01f)
        assertEquals(0f, transform.translationX, 0.01f)
        assertEquals(-25.92f, transform.translationY, 0.1f)
    }

    @Test
    fun `high design watermark maps tighter capture crop into the same composition window`() {
        val view = renderHighDesignPreview(captureZoomRatio = 2f, previewZoomRatio = 1f)
        val content = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        ).contentRect
        val frame = requireNotNull(view.currentActiveFrameRectOrNull())
        val transform = requireNotNull(view.currentPreviewSurfaceTransformOrNull())
        val interactionBounds = requireNotNull(view.currentPreviewInteractionBoundsOrNull())

        assertEquals(0.8f, frame.width() / content.width(), 0.01f)
        assertEquals(0.5f, transform.sourceClipRect.width() / content.width(), 0.01f)
        assertEquals(0.5f, transform.sourceClipRect.height() / content.height(), 0.01f)
        assertEquals(1.6f, transform.scale, 0.01f)
        assertEquals(transform.sourceClipRect.left, interactionBounds.left, 0.01f)
        assertEquals(transform.sourceClipRect.top, interactionBounds.top, 0.01f)
        assertEquals(transform.sourceClipRect.right, interactionBounds.right, 0.01f)
        assertEquals(transform.sourceClipRect.bottom, interactionBounds.bottom, 0.01f)
    }

    @Test
    fun `professional bottom bar previews as expanded band when there is space below frame`() {
        val rect = activeSquareFrame()

        val bar = bottomBarPreviewRect(rect, viewHeight = 1920, density = 1f)

        assertEquals(rect.bottom, bar.top)
        assertTrue(bar.bottom > rect.bottom, "rect=$rect bar=$bar")
        assertTrue(bar.height() >= 48f, "rect=$rect bar=$bar")
    }

    @Test
    fun `professional bottom bar falls back inside frame when no outside space exists`() {
        val rect = RectF(0f, 0f, 1080f, 1920f)

        val bar = bottomBarPreviewRect(rect, viewHeight = 1920, density = 1f)

        assertTrue(bar.top < rect.bottom, "rect=$rect bar=$bar")
        assertEquals(rect.bottom, bar.bottom)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `pure text storage key previews as translucent bottom bar with ivory accent`() {
        val preview = drawBottomBarPreview(
            templateId = "pure-text",
            barBackground = 0xCC071321.toInt()
        )
        val bitmap = preview.bitmap
        val frame = preview.frame
        val barHeight = maxOf(52f * preview.density, frame.height() * 0.078f)
        val barTop = (frame.bottom - barHeight).toInt()
        val barBottom = frame.bottom.toInt()

        val blueInkCount = countCoolBlueGlassInk(
            bitmap = bitmap,
            left = frame.left.toInt() + 40,
            top = barTop + 8,
            right = frame.right.toInt() - 40,
            bottom = barBottom - 8
        )
        val ivoryAccentCount = countIvoryAccentInk(
            bitmap = bitmap,
            left = frame.left.toInt() + 8,
            top = barTop + 8,
            right = frame.left.toInt() + 60,
            bottom = barBottom - 8
        )
        val barSample = bitmap.getPixel(frame.left.toInt() + 120, barTop + 16)

        assertTrue(
            blueInkCount > 1000,
            "pure-text preview should read as a blue translucent bar, count=$blueInkCount " +
                "sample=${Color.alpha(barSample)}/${Color.red(barSample)}/${Color.green(barSample)}/${Color.blue(barSample)} " +
                "frame=$frame barTop=$barTop"
        )
        assertTrue(
            ivoryAccentCount > 20,
            "pure-text preview should include a small ivory accent, count=$ivoryAccentCount frame=$frame barTop=$barTop"
        )
        bitmap.recycle()
    }

    @Test
    fun `four border watermark uses saved-output-like edge band width`() {
        val rect = activeSquareFrame()

        val band = fourBorderPreviewBandWidth(rect, density = 1f)

        assertTrue(band >= 48f, "four border preview should read as a brand-paper frame, band=$band")
        assertTrue(band <= 64f)
    }

    @Test
    fun `four border preview compacts supporting labels into one metadata row`() {
        assertEquals(
            "2026.06.23 10:18   35mm  1/2100s  ISO50",
            fourBorderPreviewMetadata(
                listOf("OpenCamera", "2026.06.23 10:18", "35mm  1/2100s  ISO50")
            )
        )
    }

    @Test
    fun `expanded frame paper alpha remains subordinate to preview text opacity`() {
        val alpha = expandedFramePaperAlpha(
            templateId = "travel-polaroid",
            previewOpacity = 0.3f
        )

        assertTrue(alpha in 1..64, "preview paper should be subtle, alpha=$alpha")
    }

    @Test
    fun `retro frame preview is no stronger than travel polaroid`() {
        val travel = expandedFramePaperAlpha("travel-polaroid", previewOpacity = 0.3f)
        val retro = expandedFramePaperAlpha("retro-frame", previewOpacity = 0.3f)

        assertTrue(retro <= travel, "retro=$retro travel=$travel")
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `travel ticket preview draws cobalt body coral stamp and lime stub`() {
        val preview = drawExpandedFramePreviewWithFrame(
            templateId = "travel-polaroid",
            decoration = WatermarkPreviewDecoration.TRAVEL_TICKET,
            placement = WatermarkTextPlacement.BOTTOM_LEFT
        )
        val bitmap = preview.bitmap
        val band = requireNotNull(
            expandedFrameBottomBandRect(
                rect = preview.frame,
                viewHeight = bitmap.height,
                density = preview.density,
                templateId = "travel-polaroid"
            )
        )

        val cobaltInkCount = countPixelsNearColor(
            bitmap = bitmap,
            left = (band.left + band.width() * 0.54f).toInt(),
            top = band.top.toInt(),
            right = band.right.toInt(),
            bottom = band.bottom.toInt(),
            target = Color.rgb(18, 87, 214),
            tolerance = 40
        )
        val coralInkCount = countPixelsNearColor(
            bitmap = bitmap,
            left = (band.left + band.width() * 0.54f).toInt(),
            top = band.top.toInt(),
            right = band.right.toInt(),
            bottom = band.bottom.toInt(),
            target = Color.rgb(255, 101, 82),
            tolerance = 44
        )
        val limeInkCount = countPixelsNearColor(
            bitmap = bitmap,
            left = (band.left + band.width() * 0.78f).toInt(),
            top = band.top.toInt(),
            right = band.right.toInt(),
            bottom = band.bottom.toInt(),
            target = Color.rgb(184, 235, 21),
            tolerance = 44
        )

        assertTrue(cobaltInkCount > 500, "preview ticket body should be cobalt, count=$cobaltInkCount")
        assertTrue(coralInkCount > 20, "preview ticket stamp should be coral, count=$coralInkCount")
        assertTrue(limeInkCount > 80, "preview ticket stub should be lime, count=$limeInkCount")
        bitmap.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `retro preview avoids a heavy center marker`() {
        val bitmap = drawExpandedFramePreview(
            templateId = "retro-frame",
            decoration = WatermarkPreviewDecoration.ARCHIVAL_PAPER,
            placement = WatermarkTextPlacement.BOTTOM_CENTER
        )

        val centerMarkerPixels = countPixelsDifferentFromReference(
            bitmap = bitmap,
            left = 500,
            top = 1810,
            right = 580,
            bottom = 1875
        )
        assertTrue(
            centerMarkerPixels < 8,
            "retro preview should not rely on a prominent center marker, pixels=$centerMarkerPixels"
        )
        bitmap.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `night street preview uses warm memory accent instead of neon frame`() {
        val bitmap = drawExpandedFramePreview(
            templateId = "night-street",
            decoration = WatermarkPreviewDecoration.NIGHT_MEMORY,
            placement = WatermarkTextPlacement.BOTTOM_LEFT
        )

        val neonInkCount = countNeonInk(
            bitmap = bitmap,
            left = 40,
            top = 1600,
            right = 1040,
            bottom = 1780
        )
        val warmInkCount = countWarmMemoryInk(
            bitmap = bitmap,
            left = 40,
            top = 1600,
            right = 1040,
            bottom = 1780
        )

        assertTrue(neonInkCount < 16, "night-street preview should avoid cyan/magenta neon, count=$neonInkCount")
        assertTrue(warmInkCount > 10, "night-street preview should retain a low-light warm memory accent")
        bitmap.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `van gogh starry preview uses material-like painted texture around frame`() {
        val preview = drawExpandedFramePreviewWithFrame(
            templateId = "van-gogh-starry",
            decoration = WatermarkPreviewDecoration.STARRY_MOON,
            placement = WatermarkTextPlacement.BOTTOM_CENTER
        )
        val bitmap = preview.bitmap
        val frame = preview.frame
        val bottomBand = expandedFrameBottomBandRect(frame, viewHeight = 1920, density = preview.density, templateId = "van-gogh-starry")

        val topTexture = countPixelsDifferentFromReference(
            bitmap = bitmap,
            left = frame.left.toInt() + 12,
            top = frame.top.toInt(),
            right = frame.right.toInt() - 12,
            bottom = (frame.top + 96f * preview.density).toInt()
        )
        val bottomTexture = countPixelsDifferentFromReference(
            bitmap = bitmap,
            left = frame.left.toInt() + 12,
            top = (bottomBand?.top ?: frame.bottom).toInt(),
            right = frame.right.toInt() - 12,
            bottom = (bottomBand?.bottom ?: frame.bottom).toInt()
        )

        assertTrue(
            topTexture > 3_200,
            "starry preview should show dense painted top-edge texture like the saved output, count=$topTexture"
        )
        assertTrue(
            bottomTexture > 5_200,
            "starry preview should show dense painted lower-band texture like the saved output, count=$bottomTexture"
        )
        bitmap.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `blue hour preview uses material-like cool paint at photo edge and lower band`() {
        val preview = drawExpandedFramePreviewWithFrame(
            templateId = "blue-hour",
            decoration = WatermarkPreviewDecoration.BLUE_HOUR,
            placement = WatermarkTextPlacement.BOTTOM_LEFT
        )
        val bitmap = preview.bitmap
        val frame = preview.frame
        val bottomBand = expandedFrameBottomBandRect(frame, viewHeight = 1920, density = preview.density, templateId = "blue-hour")

        val topTexture = countPixelsDifferentFromReference(
            bitmap = bitmap,
            left = frame.left.toInt() + 12,
            top = frame.top.toInt(),
            right = frame.right.toInt() - 12,
            bottom = (frame.top + 96f * preview.density).toInt()
        )
        val bottomTexture = countPixelsDifferentFromReference(
            bitmap = bitmap,
            left = frame.left.toInt() + 12,
            top = (bottomBand?.top ?: frame.bottom).toInt(),
            right = frame.right.toInt() - 12,
            bottom = (bottomBand?.bottom ?: frame.bottom).toInt()
        )

        assertTrue(
            topTexture > 2_400,
            "blue-hour preview should show cool painted strokes into the photo edge, count=$topTexture"
        )
        assertTrue(
            bottomTexture > 4_800,
            "blue-hour preview should show dense lower-band material texture like the saved output, count=$bottomTexture"
        )
        bitmap.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `impression preview uses balanced chroma edge instead of orange cast`() {
        val bitmap = drawFourBorderPreview(
            templateId = "blur-four-border",
            decoration = WatermarkPreviewDecoration.IMPRESSION_CHROMA,
            placement = WatermarkTextPlacement.BOTTOM_CENTER
        )

        val chromaInkCount = countImpressionChromaInk(
            bitmap = bitmap,
            left = 60,
            top = 1480,
            right = 1020,
            bottom = 1565
        )
        val orangeInkCount = countOrangeCastInk(
            bitmap = bitmap,
            left = 60,
            top = 1480,
            right = 1020,
            bottom = 1565
        )

        assertTrue(chromaInkCount > 18, "impression preview should include a soft balanced color edge")
        assertTrue(orangeInkCount < 8, "impression preview should not read as a warm orange stripe")
        bitmap.recycle()
    }

    private fun drawExpandedFramePreview(
        templateId: String,
        decoration: WatermarkPreviewDecoration,
        placement: WatermarkTextPlacement
    ): Bitmap = drawExpandedFramePreviewWithFrame(templateId, decoration, placement).bitmap

    private data class ExpandedFramePreview(
        val bitmap: Bitmap,
        val frame: RectF,
        val density: Float
    )

    private fun drawExpandedFramePreviewWithFrame(
        templateId: String,
        decoration: WatermarkPreviewDecoration,
        placement: WatermarkTextPlacement
    ): ExpandedFramePreview {
        val view = PreviewOverlayView(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, 1080, 1920)
        view.render(
            PreviewOverlayRenderModel(
                gridMode = CompositionGridMode.OFF,
                isGridVisible = false,
                countdownLabel = null,
                isCountdownVisible = false,
                effectModel = PreviewEffectRenderModel(
                    filterOverlay = null,
                    watermarkHint = WatermarkHintSpec(
                        templateId = templateId,
                        placement = placement,
                        previewText = "OpenCamera",
                        opacity = 0.8f,
                        shape = WatermarkPreviewShape.EXPANDED_FRAME,
                        decoration = decoration
                    ),
                    frameGuideline = null,
                    compositionGrid = null
                ),
                frame = PreviewFrameRenderModel(
                    ratio = FrameRatio.RATIO_1_1,
                    label = "1:1",
                    dimOutsideFrame = false
                )
            )
        )
        val frame = requireNotNull(view.currentActiveFrameRectOrNull())
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return ExpandedFramePreview(
            bitmap = bitmap,
            frame = frame,
            density = view.resources.displayMetrics.density
        )
    }

    private fun drawFourBorderPreview(
        templateId: String,
        decoration: WatermarkPreviewDecoration,
        placement: WatermarkTextPlacement
    ): Bitmap {
        val view = PreviewOverlayView(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, 1080, 1920)
        view.render(
            PreviewOverlayRenderModel(
                gridMode = CompositionGridMode.OFF,
                isGridVisible = false,
                countdownLabel = null,
                isCountdownVisible = false,
                effectModel = PreviewEffectRenderModel(
                    filterOverlay = null,
                    watermarkHint = WatermarkHintSpec(
                        templateId = templateId,
                        placement = placement,
                        previewText = "OpenCamera",
                        opacity = 0.8f,
                        shape = WatermarkPreviewShape.FOUR_BORDER,
                        decoration = decoration
                    ),
                    frameGuideline = null,
                    compositionGrid = null
                ),
                frame = PreviewFrameRenderModel(
                    ratio = FrameRatio.RATIO_1_1,
                    label = "1:1",
                    dimOutsideFrame = false
                )
            )
        )
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
    }

    private fun renderHighDesignPreview(
        captureZoomRatio: Float,
        previewZoomRatio: Float
    ): PreviewOverlayView {
        return PreviewOverlayView(ApplicationProvider.getApplicationContext()).apply {
            layout(0, 0, 1080, 1920)
            render(
                PreviewOverlayRenderModel(
                    gridMode = CompositionGridMode.OFF,
                    isGridVisible = false,
                    countdownLabel = null,
                    isCountdownVisible = false,
                    effectModel = PreviewEffectRenderModel(
                        filterOverlay = null,
                        watermarkHint = WatermarkHintSpec(
                            templateId = "blue-hour",
                            placement = WatermarkTextPlacement.BOTTOM_LEFT,
                            previewText = "OpenCamera",
                            opacity = 0.8f,
                            shape = WatermarkPreviewShape.EXPANDED_FRAME
                        ),
                        frameGuideline = null,
                        compositionGrid = null
                    ),
                    frame = PreviewFrameRenderModel(
                        ratio = FrameRatio.RATIO_4_3,
                        label = "4:3",
                        dimOutsideFrame = true,
                        zoomRatio = captureZoomRatio,
                        previewZoomRatio = previewZoomRatio
                    ),
                    previewContentAspect = PreviewContentAspect(4, 3)
                )
            )
        }
    }

    private data class BottomBarPreview(
        val bitmap: Bitmap,
        val frame: RectF,
        val density: Float
    )

    private fun drawBottomBarPreview(
        templateId: String,
        barBackground: Int
    ): BottomBarPreview {
        val view = PreviewOverlayView(ApplicationProvider.getApplicationContext())
        view.layout(0, 0, 1080, 1920)
        view.render(
            PreviewOverlayRenderModel(
                gridMode = CompositionGridMode.OFF,
                isGridVisible = false,
                countdownLabel = null,
                isCountdownVisible = false,
                effectModel = PreviewEffectRenderModel(
                    filterOverlay = null,
                    watermarkHint = WatermarkHintSpec(
                        templateId = templateId,
                        placement = WatermarkTextPlacement.BOTTOM_LEFT,
                        previewText = "BLUE HOUR",
                        opacity = 0.8f,
                        shape = WatermarkPreviewShape.BOTTOM_BAR,
                        previewLabels = listOf("BLUE HOUR", "2026.06.22 19:41", "TEOTIS CAMERA"),
                        barBackground = barBackground
                    ),
                    frameGuideline = null,
                    compositionGrid = null
                ),
                frame = PreviewFrameRenderModel(
                    ratio = FrameRatio.RATIO_1_1,
                    label = "1:1",
                    dimOutsideFrame = false
                )
            )
        )
        val frame = requireNotNull(view.currentActiveFrameRectOrNull())
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        view.drawOverlayForTest(Canvas(bitmap))
        return BottomBarPreview(
            bitmap = bitmap,
            frame = frame,
            density = view.resources.displayMetrics.density
        )
    }

    private fun countGreenInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 20 &&
                    Color.green(pixel) > Color.red(pixel) + 5 &&
                    Color.green(pixel) > Color.blue(pixel) + 3
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countPixelsNearColor(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        target: Int,
        tolerance: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    kotlin.math.abs(Color.red(pixel) - Color.red(target)) <= tolerance &&
                    kotlin.math.abs(Color.green(pixel) - Color.green(target)) <= tolerance &&
                    kotlin.math.abs(Color.blue(pixel) - Color.blue(target)) <= tolerance
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countCoolBlueGlassInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 80 &&
                    Color.blue(pixel) > Color.red(pixel) + 8 &&
                    Color.blue(pixel) >= Color.green(pixel)
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countIvoryAccentInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 90 &&
                    Color.red(pixel) > Color.blue(pixel) + 30 &&
                    Color.green(pixel) > Color.blue(pixel) + 16 &&
                    Color.red(pixel) > 150
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countNeonInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val cyan = Color.green(pixel) > Color.red(pixel) + 28 &&
                    Color.blue(pixel) > Color.red(pixel) + 24
                val magenta = Color.red(pixel) > Color.green(pixel) + 18 &&
                    Color.blue(pixel) > Color.green(pixel) + 8
                if (Color.alpha(pixel) > 24 && (cyan || magenta)) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countWarmMemoryInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 24 &&
                    Color.red(pixel) > Color.blue(pixel) + 26 &&
                    Color.green(pixel) > Color.blue(pixel) + 8 &&
                    Color.red(pixel) in 130..255
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countImpressionChromaInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val coolRose = blue > green + 8 && red > green + 2
                val paleCyan = blue > red + 8 && green > red + 2
                if (Color.alpha(pixel) > 20 && (coolRose || paleCyan)) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countOrangeCastInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 24 &&
                    Color.red(pixel) > Color.blue(pixel) + 36 &&
                    Color.green(pixel) > Color.blue(pixel) + 18
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countPixelsDifferentFromReference(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        val reference = bitmap.getPixel(left, top)
        var count = 0
        for (y in top until bottom) {
            for (x in left until right) {
                val pixel = bitmap.getPixel(x, y)
                val distance =
                    kotlin.math.abs(Color.red(pixel) - Color.red(reference)) +
                        kotlin.math.abs(Color.green(pixel) - Color.green(reference)) +
                        kotlin.math.abs(Color.blue(pixel) - Color.blue(reference)) +
                        kotlin.math.abs(Color.alpha(pixel) - Color.alpha(reference))
                if (distance > 12) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun activeSquareFrame(): RectF {
        val geometry = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 1,
            ratioHeight = 1
        )
        return geometry.activeFrameRect
    }
}
