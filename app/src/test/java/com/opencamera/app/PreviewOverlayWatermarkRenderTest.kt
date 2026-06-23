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
    fun `default 1x frame uses eighty percent of preview span for watermark breathing room`() {
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
                        templateId = "travel-polaroid",
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
                    zoomRatio = 1f,
                    previewZoomRatio = 1f
                ),
                previewContentAspect = PreviewContentAspect(4, 3)
            )
        )

        val rect = requireNotNull(view.currentActiveFrameRectOrNull())
        val content = previewContentGeometry(
            viewWidth = 1080,
            viewHeight = 1920,
            previewContentAspect = PreviewContentAspect(4, 3)
        ).contentRect

        assertEquals(0.8f, rect.width() / content.width(), 0.01f)
        assertEquals(0.8f, rect.height() / content.height(), 0.01f)
        assertTrue(
            content.bottom - rect.bottom >= content.height() * 0.09f,
            "frame should leave a visible bottom watermark region: content=$content frame=$rect"
        )
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
    fun `travel preview draws map strokes in right side of reserved band`() {
        val bitmap = drawExpandedFramePreview(
            templateId = "travel-polaroid",
            decoration = WatermarkPreviewDecoration.TRAVEL_MAP,
            placement = WatermarkTextPlacement.BOTTOM_LEFT
        )

        val greenInkCount = countGreenInk(
            bitmap = bitmap,
            left = 590,
            top = 1640,
            right = 970,
            bottom = 1770
        )

        assertTrue(greenInkCount > 20, "travel preview should include green map strokes")
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
        val bitmap = Bitmap.createBitmap(1080, 1920, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        return bitmap
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
