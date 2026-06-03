package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.Color
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PhotoWatermarkPostProcessorTest {
    @Test
    fun `photo result with watermark and content uri is rendered`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "PHOTO Auto",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_1.jpg",
                    contentUri = "content://media/external/images/media/101"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/101"),
            editor.invocations.single().target
        )
        assertEquals("travel-polaroid", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:travel-polaroid"))
    }

    @Test
    fun `missing watermark text leaves result untouched`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val input = photoResult(watermarkText = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `watermark without editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "DOC Receipt",
                watermarkTemplate = "retro-frame",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Documents/OpenCamera_DOC_1.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("watermark:template:retro-frame"))
        assertTrue(result.pipelineNotes.contains("watermark:skipped:missing-output-handle"))
    }

    @Test
    fun `editor failure is captured as pipeline diagnostic`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                watermarkTemplate = "classic-overlay",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night.jpg",
                    filePath = "/tmp/night.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("watermark:template:classic-overlay"))
        assertTrue(result.pipelineNotes.contains("watermark:failed:decode-failed"))
    }

    @Test
    fun `editor warning is included in pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied(warning = "archive-embed-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night.jpg",
                    filePath = "/tmp/night.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("watermark:rendered:travel-polaroid"))
        assertTrue(result.pipelineNotes.contains("watermark:warning:archive-embed-failed"))
    }

    @Test
    fun `pure text template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "OpenCamera",
                watermarkTemplate = "pure-text",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/pure.jpg",
                    filePath = "/tmp/pure.jpg"
                )
            )
        )

        assertEquals("pure-text", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:pure-text"))
    }

    @Test
    fun `blur four border template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "OpenCamera",
                watermarkTemplate = "blur-four-border",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/blur.jpg",
                    filePath = "/tmp/blur.jpg"
                )
            )
        )

        assertEquals("blur-four-border", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:blur-four-border"))
    }

    @Test
    fun `professional bottom bar template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Scenery Handheld",
                watermarkTemplate = "professional-bottom-bar",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/pro.jpg",
                    filePath = "/tmp/pro.jpg"
                )
            )
        )

        assertEquals("professional-bottom-bar", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:professional-bottom-bar"))
    }

    @Test
    fun `non photo result is ignored`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val input = photoResult(
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(
                metadata = MediaMetadata(watermarkText = "VIDEO Torch On")
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `blur four border top and left border pixels are derived from source edge content`() {
        val source = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.RED)
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val minEdge = 100f
        val sideBorder = maxOf(20f, minEdge * 0.045f).toInt()
        val topBorder = maxOf(20f, minEdge * 0.045f).toInt()

        val topPixel = bmp.getPixel(bmp.width / 2, topBorder / 2)
        val leftPixel = bmp.getPixel(sideBorder / 2, bmp.height / 2)

        assertTrue(Color.red(topPixel) > 150, "top border should be reddish from source edge")
        assertTrue(Color.red(leftPixel) > 150, "left border should be reddish from source edge")
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border dark edge input does not produce pale washed-out border`() {
        val darkColor = Color.argb(255, 20, 15, 10)
        val source = Bitmap.createBitmap(80, 80, Bitmap.Config.ARGB_8888).apply {
            eraseColor(darkColor)
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val topBorder = maxOf(20f, 80f * 0.045f).toInt()

        val topPixel = bmp.getPixel(bmp.width / 2, topBorder / 2)

        assertFalse(Color.red(topPixel) > 150, "dark source should not produce white/pale border")
        assertFalse(Color.green(topPixel) > 150, "dark source should not produce white/pale border")
        assertFalse(Color.blue(topPixel) > 150, "dark source should not produce white/pale border")
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border top border is greenish when top edge is green`() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                source.setPixel(x, y, if (y < 40) Color.GREEN else Color.GRAY)
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        val topPixel = bmp.getPixel(bmp.width / 2, 2)
        assertTrue(
            Color.green(topPixel) > Color.red(topPixel),
            "top border should be greenish from green top edge, got R=${Color.red(topPixel)} G=${Color.green(topPixel)}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border bottom border is bluish when bottom edge is blue`() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                source.setPixel(x, y, if (y >= 160) Color.BLUE else Color.GRAY)
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        val bottomPixel = bmp.getPixel(bmp.width / 2, bmp.height - 6)
        assertTrue(
            Color.blue(bottomPixel) > Color.red(bottomPixel),
            "bottom border should be bluish from blue bottom edge, got R=${Color.red(bottomPixel)} B=${Color.blue(bottomPixel)}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border top edge has smoother color gradient than raw scaled strip`() {
        val source = Bitmap.createBitmap(200, 200, Bitmap.Config.ARGB_8888)
        for (x in 0 until 200) {
            for (y in 0 until 200) {
                source.setPixel(x, y, if (y < 50) Color.RED else Color.rgb(40, 40, 40))
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)
        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        val minEdge = 200f
        val topBorder = maxOf(20f, minEdge * 0.045f).toInt()

        val colorDiffs = mutableListOf<Int>()
        var prevPixel = bmp.getPixel(bmp.width / 2, 1)
        for (y in 2 until topBorder) {
            val cur = bmp.getPixel(bmp.width / 2, y)
            val diff = kotlin.math.abs(Color.red(cur) - Color.red(prevPixel)) +
                kotlin.math.abs(Color.green(cur) - Color.green(prevPixel)) +
                kotlin.math.abs(Color.blue(cur) - Color.blue(prevPixel))
            colorDiffs.add(diff)
            prevPixel = cur
        }
        val avgDiff = if (colorDiffs.isNotEmpty()) colorDiffs.average() else 0.0
        assertTrue(avgDiff < 80.0, "blurred border should have smooth gradient, avg adjacent diff=$avgDiff")
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border uses zoomed source background when capture crop zoom is active`() {
        val source = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888)
        for (x in 0 until 160) {
            for (y in 0 until 160) {
                source.setPixel(x, y, if (y < 18) Color.GREEN else Color.MAGENTA)
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)
            .copy(captureCropZoom = 5f)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val topPixel = bmp.getPixel(bmp.width / 2, 6)

        assertTrue(
            Color.red(topPixel) > Color.green(topPixel),
            "zoomed border should be dominated by the magnified source body, got R=${Color.red(topPixel)} G=${Color.green(topPixel)}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border suppresses high frequency edge detail`() {
        val source = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        for (x in 0 until 240) {
            val stripe = if ((x / 12) % 2 == 0) Color.WHITE else Color.BLACK
            for (y in 0 until 240) {
                source.setPixel(x, y, if (y < 60) stripe else Color.rgb(96, 96, 96))
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val topBorder = maxOf(20f, 240f * 0.045f).toInt()
        val borderLumaStdDev = horizontalLumaStdDev(
            bitmap = bmp,
            y = topBorder / 2,
            startX = 12,
            endXExclusive = bmp.width - 12
        )

        assertTrue(
            borderLumaStdDev < 18.0,
            "blurred border should strongly suppress stripe detail, stdDev=$borderLumaStdDev"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border corners transition smoothly into side borders`() {
        val source = Bitmap.createBitmap(240, 240, Bitmap.Config.ARGB_8888)
        for (x in 0 until 240) {
            for (y in 0 until 240) {
                source.setPixel(
                    x,
                    y,
                    if (x < 24) {
                        val channel = (255 * y / 239f).toInt()
                        Color.rgb(channel, 24, 255 - channel)
                    } else {
                        Color.rgb(96, 96, 96)
                    }
                )
            }
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val sideBorder = maxOf(20f, 240f * 0.045f).toInt()
        val topBorder = maxOf(20f, 240f * 0.045f).toInt()
        val seamDiff = colorDistance(
            bmp.getPixel(sideBorder / 2, topBorder - 1),
            bmp.getPixel(sideBorder / 2, topBorder + 1)
        )

        assertTrue(
            seamDiff < 70,
            "corner blur should flow into side border without a hard seam, diff=$seamDiff"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border solid source produces blurred not sharp border`() {
        val source = Bitmap.createBitmap(120, 120, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.argb(255, 80, 120, 60))
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_BLUR)
        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        val minEdge = 120f
        val sideBorder = maxOf(20f, minEdge * 0.045f).toInt()
        val leftPixel = bmp.getPixel(sideBorder / 2, bmp.height / 2)

        assertTrue(Color.green(leftPixel) > 30, "blurred green source border should retain green tint")
        assertTrue(Color.blue(leftPixel) < Color.green(leftPixel), "blurred green source border should be greenish")
        bmp.recycle(); source.recycle()
    }

    private fun blurFourBorderTemplate(
        background: WatermarkFrameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
    ) = ResolvedPhotoWatermarkTemplate(
        templateId = "blur-four-border",
        title = "Test Title",
        supportingLines = listOf("Line 1"),
        frameBackground = background,
        usesExpandedFrame = true,
        placement = WatermarkTextPlacement.BOTTOM_LEFT,
        textScale = 1f,
        textOpacity = 1f
    )

    private fun horizontalLumaStdDev(
        bitmap: Bitmap,
        y: Int,
        startX: Int,
        endXExclusive: Int
    ): Double {
        val values = (startX until endXExclusive).map { x ->
            val pixel = bitmap.getPixel(x, y)
            0.299 * Color.red(pixel) + 0.587 * Color.green(pixel) + 0.114 * Color.blue(pixel)
        }
        val mean = values.average()
        return sqrt(values.sumOf { value -> (value - mean) * (value - mean) } / values.size)
    }

    private fun colorDistance(first: Int, second: Int): Int {
        return kotlin.math.abs(Color.red(first) - Color.red(second)) +
            kotlin.math.abs(Color.green(first) - Color.green(second)) +
            kotlin.math.abs(Color.blue(first) - Color.blue(second))
    }

    private fun photoResult(
        mediaType: MediaType = MediaType.PHOTO,
        watermarkText: String? = "PHOTO Auto",
        watermarkTemplate: String = "travel-polaroid",
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/photo.jpg",
            filePath = "/tmp/photo.jpg"
        ),
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                watermarkText = watermarkText,
                customTags = mapOf("watermarkTemplate" to watermarkTemplate)
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-photo",
            mediaType = mediaType,
            outputPath = outputHandle.displayPath,
            outputHandle = outputHandle,
            saveRequest = saveRequest,
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = outputHandle.displayPath,
                renderUri = outputHandle.contentUri
            ),
            metadata = saveRequest.metadata
        )
    }

    private class FakePhotoWatermarkEditor(
        private val result: ProcessorEditorResult
    ) : PhotoWatermarkEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            metadata: MediaMetadata,
            watermarkText: String,
            templateId: String
        ): ProcessorEditorResult {
            invocations += Invocation(target, watermarkText, templateId)
            return result
        }
    }

    private data class Invocation(
        val target: ProcessorTarget,
        val watermarkText: String,
        val templateId: String
    )
}
