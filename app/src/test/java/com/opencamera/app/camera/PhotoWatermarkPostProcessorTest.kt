package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.Color
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
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
import org.robolectric.annotation.GraphicsMode
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PhotoWatermarkPostProcessorTest {
    @Test
    fun `template registry owns every supported storage key`() {
        assertEquals(
            listOf(
                "classic-overlay",
                "travel-polaroid",
                "retro-frame",
                "pure-text",
                "blur-four-border",
                "professional-bottom-bar",
                "night-street",
                "van-gogh-starry",
                "blue-hour"
            ),
            PhotoWatermarkTemplateType.entries.map(PhotoWatermarkTemplateType::storageKey)
        )
    }

    @Test
    fun `template registry resolves unknown keys to classic with typed defaults`() {
        val templateType = resolvePhotoWatermarkTemplateType(" future-template ")

        assertEquals(PhotoWatermarkTemplateType.CLASSIC_OVERLAY, templateType)
        assertEquals(WatermarkTextPlacement.BOTTOM_LEFT, templateType.defaultPlacement)
        assertEquals(WatermarkFrameBackground.DARK, templateType.defaultFrameBackground)
        assertFalse(templateType.usesExpandedFrame)
    }

    @Test
    fun `complex overlay path is limited to new complex night templates`() {
        assertTrue(usesComplexWatermarkOverlay("van-gogh-starry"))
        assertTrue(usesComplexWatermarkOverlay("blue-hour"))
        assertFalse(usesComplexWatermarkOverlay("travel-polaroid"))
        assertFalse(usesComplexWatermarkOverlay("retro-frame"))
        assertFalse(usesComplexWatermarkOverlay("pure-text"))
        assertFalse(usesComplexWatermarkOverlay("night-street"))
        assertFalse(usesComplexWatermarkOverlay("professional-bottom-bar"))
    }

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
        val input = photoResult(
            watermarkText = null,
            watermarkTemplate = "classic-overlay"
        )
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
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `pure text storage key renders translucent bottom bar in source image`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(112, 160, 196))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "pure-text",
            title = "BLUE HOUR",
            supportingLines = listOf("2026.06.22 19:41", "TEOTIS CAMERA"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = false,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val centerBottom = bmp.getPixel(220, 280)
        val warmAccentPixels = countIvoryAccentInk(
            bitmap = bmp,
            left = 10,
            top = 238,
            right = 26,
            bottom = 294
        )

        assertEquals(source.width, bmp.width)
        assertEquals(source.height, bmp.height)
        assertTrue(
            Color.blue(centerBottom) > Color.red(centerBottom) + 6 &&
                Color.blue(centerBottom) >= Color.green(centerBottom),
            "bottom bar should read as cool translucent blue, pixel=$centerBottom"
        )
        assertTrue(warmAccentPixels > 24, "bottom bar should keep a warm ivory left accent")
        bmp.recycle(); source.recycle()
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
    fun `night street template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                watermarkTemplate = "night-street",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night.jpg",
                    filePath = "/tmp/night.jpg"
                )
            )
        )

        assertEquals("night-street", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:night-street"))
    }

    @Test
    fun `van gogh starry template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Night Street",
                watermarkTemplate = "van-gogh-starry",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/starry.jpg",
                    filePath = "/tmp/starry.jpg"
                )
            )
        )

        assertEquals("van-gogh-starry", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:van-gogh-starry"))
    }

    @Test
    fun `blue hour template passes through to pipeline notes`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied()
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Ignored",
                watermarkTemplate = "blue-hour",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/blue-hour.jpg",
                    filePath = "/tmp/blue-hour.jpg"
                )
            )
        )

        assertEquals("blue-hour", editor.invocations.single().templateId)
        assertTrue(result.pipelineNotes.contains("watermark:rendered:blue-hour"))
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
    fun `travel polaroid output uses a warmer wider paper frame`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(80, 130, 180))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "travel-polaroid",
            title = "Go see the sky",
            supportingLines = listOf("2026-06-05"),
            frameBackground = WatermarkFrameBackground.WHITE,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        assertTrue(bmp.width >= 432, "travel polaroid should have a more visible side paper frame")
        assertTrue(bmp.height >= 410, "travel polaroid should have a more generous bottom paper band")
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `travel polaroid draws green map details on right side of bottom band`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(80, 130, 180))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "travel-polaroid",
            title = "Go see the sky",
            supportingLines = emptyList(),
            frameBackground = WatermarkFrameBackground.WHITE,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val greenInkCount = countGreenInk(
            bitmap = bmp,
            left = (bmp.width * 0.58f).toInt(),
            top = 330,
            right = bmp.width - 18,
            bottom = bmp.height - 18
        )

        assertTrue(
            greenInkCount > 30,
            "travel bottom band should contain green map strokes, count=$greenInkCount, " +
                "maxGreenAdvantage=${maxGreenAdvantage(bmp, (bmp.width * 0.58f).toInt(), 330, bmp.width - 18, bmp.height - 18)}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `retro frame uses narrow grand tour scholar band`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(176, 160, 136))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "retro-frame",
            title = "OpenCamera",
            supportingLines = emptyList(),
            frameBackground = WatermarkFrameBackground.SOURCE_VIVID_BLUR,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val archivalCornerPixels = countSepiaInk(
            bitmap = bmp,
            left = 10,
            top = 10,
            right = 92,
            bottom = 92
        )
        val grandTourGreenPixels = countGreenInk(
            bitmap = bmp,
            left = 0,
            top = source.height + 18,
            right = bmp.width,
            bottom = bmp.height
        )

        assertTrue(
            bmp.height <= source.height + 88,
            "retro frame bottom band should stay narrow enough for a real watermark, height=${bmp.height}"
        )
        assertTrue(
            archivalCornerPixels > 18,
            "retro frame should keep a brass scholar corner treatment, count=$archivalCornerPixels"
        )
        assertTrue(
            grandTourGreenPixels > 220,
            "retro frame should use the deep green grand-tour field, count=$grandTourGreenPixels"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `night street output uses expanded frame with dark band below source`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(30, 20, 15))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "night-street",
            title = "Night Street",
            supportingLines = listOf("2026-06-15 22:30"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        assertTrue(bmp.width > 400, "night-street should add side borders")
        assertTrue(bmp.height > 300, "night-street should expand height for bottom band")
        assertTrue(bmp.height >= 340, "night-street bottom band should be at least ~12% of source")
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `night street default background is dark blue frame`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "night-street",
            watermarkText = "OpenCamera",
            metadata = MediaMetadata(),
            preservedExif = emptyMap()
        )
        assertEquals(WatermarkFrameBackground.DARK, resolved.frameBackground)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `night street uses restrained warm memory accents instead of neon colors`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(16, 24, 48))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "night-street",
            title = "Night Street",
            supportingLines = listOf("2026-06-15 22:30"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val neonPixels = countNightStreetNeonPixels(
            bitmap = bmp,
            top = source.height + 2,
            bottom = bmp.height
        )
        val warmPixels = countWarmMemoryInk(
            bitmap = bmp,
            top = source.height + 2,
            bottom = bmp.height
        )

        assertTrue(neonPixels < 18, "night-street should not render cyan/magenta neon accents, count=$neonPixels")
        assertTrue(warmPixels > 12, "night-street should keep a quiet warm low-light accent, count=$warmPixels")
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `van gogh starry default background is dark blue frame`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "van-gogh-starry",
            watermarkText = "Night Street",
            metadata = MediaMetadata(),
            preservedExif = emptyMap()
        )

        assertEquals(WatermarkFrameBackground.DARK, resolved.frameBackground)
        assertEquals(WatermarkTextPlacement.BOTTOM_CENTER, resolved.placement)
    }

    @Test
    fun `blue hour default title uses blue-hour identity and common params`() {
        val resolved = resolvePhotoWatermarkTemplate(
            templateId = "blue-hour",
            watermarkText = "Ignored text",
            metadata = MediaMetadata(
                customTags = mapOf(
                    "watermarkDatetime" to "2026.06.22 19:41",
                    "watermarkLocation" to "CITY NIGHT",
                    "watermarkCameraParams" to "24mm"
                )
            ),
            preservedExif = emptyMap()
        )

        assertEquals("蓝调时刻", resolved.title)
        assertEquals(listOf("2026.06.22 19:41 • CITY NIGHT", "24mm"), resolved.supportingLines)
        assertEquals(WatermarkFrameBackground.DARK, resolved.frameBackground)
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `van gogh starry renders starry frame and avoids a large bottom title`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(16, 42, 86))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "van-gogh-starry",
            title = "Night Street",
            supportingLines = listOf("2026.06.22 19:41", "CITY NIGHT • 24mm"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val starryPixels = countWarmMemoryInk(
            bitmap = bmp,
            top = 0,
            bottom = (bmp.height * 0.22f).toInt()
        )
        val lowerBandDarkPixels = countVeryLightPixels(
            bitmap = bmp,
            left = 0,
            top = source.height + 45,
            right = bmp.width,
            bottom = bmp.height - 18
        )

        assertTrue(bmp.width > source.width, "starry frame should add side borders")
        assertTrue(bmp.height > source.height, "starry frame should add a bottom band")
        assertTrue(starryPixels > 24, "starry frame should draw warm moon/star accents, count=$starryPixels")
        assertTrue(
            lowerBandDarkPixels < 520,
            "starry frame should avoid a large bright bottom title, brightPixels=$lowerBandDarkPixels"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `van gogh starry complex overlay adds dense edge texture outside the photo`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(16, 42, 86))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "van-gogh-starry",
            title = "Ignored",
            supportingLines = listOf("2026.06.22 19:41", "CITY NIGHT", "24mm"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val topTexture = countPixelsDifferentFromReference(
            bitmap = bmp,
            left = 0,
            top = 0,
            right = bmp.width,
            bottom = 42
        )
        val bottomTexture = countPixelsDifferentFromReference(
            bitmap = bmp,
            left = 0,
            top = 318,
            right = bmp.width,
            bottom = bmp.height
        )

        assertTrue(topTexture > 180, "starry overlay should texture the top frame, count=$topTexture")
        assertTrue(bottomTexture > 360, "starry overlay should texture the bottom band, count=$bottomTexture")
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `blue hour renders localized title band and lower right warm icon`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(18, 54, 102))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "blue-hour",
            title = "蓝调时刻",
            supportingLines = listOf("2026.06.22 19:41 • CITY NIGHT", "24mm"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val titleInk = countCoolLightInk(
            bitmap = bmp,
            left = 0,
            top = source.height + 18,
            right = (bmp.width * 0.62f).toInt(),
            bottom = bmp.height - 18
        )
        val warmIconInk = countWarmMemoryInk(
            bitmap = bmp,
            top = source.height + 18,
            bottom = bmp.height
        )

        assertTrue(bmp.width > source.width, "blue-hour should add side borders")
        assertTrue(bmp.height > source.height, "blue-hour should add a bottom band")
        assertTrue(titleInk > 90, "blue-hour should draw a pale localized title, count=$titleInk")
        assertTrue(warmIconInk > 18, "blue-hour should draw a warm lower-right icon, count=$warmIconInk")
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `blue hour complex overlay keeps visible cool border texture separate from title text`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(18, 54, 102))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "blue-hour",
            title = "蓝调时刻",
            supportingLines = listOf("2026.06.22 19:41 • CITY NIGHT", "24mm"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_LEFT,
            textScale = 1f,
            textOpacity = 1f
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val topTexture = countPixelsDifferentFromReference(
            bitmap = bmp,
            left = 0,
            top = 0,
            right = bmp.width,
            bottom = 14
        )
        val lowerRightTexture = countPixelsDifferentFromReference(
            bitmap = bmp,
            left = (bmp.width * 0.68f).toInt(),
            top = 318,
            right = bmp.width,
            bottom = bmp.height
        )

        assertTrue(topTexture > 260, "blue-hour overlay should richly texture the top border, count=$topTexture")
        assertTrue(lowerRightTexture > 220, "blue-hour overlay should decorate the lower-right band, count=$lowerRightTexture")
        bmp.recycle(); source.recycle()
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `blur four border vivid style uses balanced impression tint instead of orange wash`() {
        val source = Bitmap.createBitmap(160, 160, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(118, 118, 118))
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_VIVID_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val bottomY = bmp.height - 12
        val bottomPixel = bmp.getPixel(bmp.width / 2, bottomY)

        assertTrue(
            Color.blue(bottomPixel) >= Color.red(bottomPixel) - 20,
            "impression tint should stay balanced instead of orange-heavy: " +
                "R=${Color.red(bottomPixel)} G=${Color.green(bottomPixel)} B=${Color.blue(bottomPixel)}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border signature keeps metadata on one compact line`() {
        assertEquals(
            "FL 35mm   Shutter 1/2100s   ISO 50   Pixel 12MP",
            blurFourBorderSignatureMetadata(
                listOf("FL 35mm", "Shutter 1/2100s", "ISO 50", "Pixel 12MP")
            )
        )
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `blur four border signature stays centered when placement is bottom left`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(196, 196, 196))
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR).copy(
            title = "TEOTIS CAMERA",
            supportingLines = listOf("FL 35mm", "ISO 50"),
            placement = WatermarkTextPlacement.BOTTOM_LEFT
        )

        val bmp = renderPhotoWatermarkBitmap(source, template).bitmap
        val bandTop = 300 + blurFourBorderFrameMetrics(300f, 24f).topBorder.toInt()
        var minInkX = bmp.width
        var maxInkX = -1
        for (y in bandTop + 20 until bmp.height - 8) {
            for (x in 8 until bmp.width - 8) {
                val pixel = bmp.getPixel(x, y)
                if (Color.red(pixel) < 145 && Color.green(pixel) < 145 && Color.blue(pixel) < 145) {
                    minInkX = minOf(minInkX, x)
                    maxInkX = maxOf(maxInkX, x)
                }
            }
        }

        assertTrue(maxInkX >= minInkX, "signature ink should be visible")
        val inkCenter = (minInkX + maxInkX) / 2f
        assertTrue(
            kotlin.math.abs(inkCenter - bmp.width / 2f) < 24f,
            "signature should stay centered, inkCenter=$inkCenter bitmapCenter=${bmp.width / 2f}"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `professional bottom bar output gives metadata more breathing room`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(70, 72, 78))
        }
        val template = ResolvedPhotoWatermarkTemplate(
            templateId = "professional-bottom-bar",
            title = "OpenCamera Pro",
            supportingLines = listOf("24mm 1/120 ISO100"),
            frameBackground = WatermarkFrameBackground.DARK,
            usesExpandedFrame = true,
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            textScale = 1f,
            textOpacity = 1f
        )

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        assertEquals(400, bmp.width)
        assertTrue(bmp.height >= 391, "professional bottom bar should be taller than the old compact strip")
        bmp.recycle(); source.recycle()
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
    fun `blur four border uses brand paper proportions`() {
        val source = Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(92, 140, 188))
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap

        assertTrue(bmp.width >= 448, "four-border card should have a more visible side paper frame")
        assertTrue(bmp.height >= 396, "four-border card should reserve a generous brand/info band")
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
    fun `blur four border light blur keeps solid source tone without white tint`() {
        val sourceColor = Color.rgb(24, 132, 42)
        val source = Bitmap.createBitmap(96, 96, Bitmap.Config.ARGB_8888).apply {
            eraseColor(sourceColor)
        }
        val template = blurFourBorderTemplate(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)

        val result = renderPhotoWatermarkBitmap(source, template)
        val bmp = result.bitmap
        val topBorder = maxOf(20f, 96f * 0.045f).toInt()
        val topPixel = bmp.getPixel(bmp.width / 2, topBorder / 2)
        val toneDrift = colorDistance(topPixel, sourceColor)

        assertTrue(
            toneDrift < 36,
            "light blur should keep the source tone instead of adding a pale tint, drift=$toneDrift " +
                "pixel=(${Color.red(topPixel)}, ${Color.green(topPixel)}, ${Color.blue(topPixel)})"
        )
        bmp.recycle(); source.recycle()
    }

    @Test
    fun `blur four border light blur does not add white tint overlay`() {
        assertEquals(
            Color.TRANSPARENT,
            contentAwareEdgeTintOverlay(WatermarkFrameBackground.SOURCE_LIGHT_BLUR)
        )
    }

    @Test
    fun `blur four border uses a frosted edge blur radius on medium captures`() {
        assertTrue(
            blurFourBorderBlurRadiusForLength(560) >= 23,
            "medium captures should get enough radius to read as frosted glass"
        )
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

    // ── Structured failure mapping ──────────────────────────────────────────

    @Test
    fun `watermarkFailureMapping maps decode-failed correctly`() {
        val (cause, integrity) = watermarkFailureMapping("decode-failed")
        assertEquals(PostProcessFailureCause.DECODE_FAILED, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `watermarkFailureMapping maps decode-oom correctly`() {
        val (cause, integrity) = watermarkFailureMapping("decode-oom")
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `watermarkFailureMapping maps encode-failed correctly`() {
        val (cause, integrity) = watermarkFailureMapping("encode-failed")
        assertEquals(PostProcessFailureCause.ENCODE, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `watermarkFailureMapping maps output-unavailable with possibly-modified`() {
        val (cause, integrity) = watermarkFailureMapping("output-unavailable")
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, integrity)
    }

    @Test
    fun `watermarkFailureMapping maps unknown reason to EXCEPTION`() {
        val (cause, integrity) = watermarkFailureMapping("something-else")
        assertEquals(PostProcessFailureCause.EXCEPTION, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `editor Failed decode-failed produces structured failure`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("watermark:failed:decode-failed") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size, "Should have one structured failure")
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.WATERMARK, failure.stage)
        assertEquals(PostProcessFailureCause.DECODE_FAILED, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `editor Failed output-unavailable produces structured failure with possibly-modified`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("output-unavailable")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("watermark:failed:output-unavailable") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.WATERMARK, failure.stage)
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, failure.cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, failure.integrity)
    }

    @Test
    fun `editor Failed encode-failed produces structured failure`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("encode-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("watermark:failed:encode-failed") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.WATERMARK, failure.stage)
        assertEquals(PostProcessFailureCause.ENCODE, failure.cause)
    }

    @Test
    fun `editor Failed decode-oom produces structured failure with out-of-memory cause`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("decode-oom")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("watermark:failed:decode-oom") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.WATERMARK, failure.stage)
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
    }

    @Test
    fun `editor Skipped result does not produce structured failure`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Skipped("input-unavailable")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("watermark:skipped:input-unavailable") },
            "Should contain skip note: ${result.pipelineNotes}")
        assertTrue(result.structuredPostProcessFailures.isEmpty(),
            "Skipped should not produce structured failures")
    }

    @Test
    fun `editor Applied with warning does not produce structured failure`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = PhotoWatermarkApplied(warning = "archive-embed-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        assertTrue(result.structuredPostProcessFailures.isEmpty(),
            "Success with warning should not produce structured failures")
    }

    @Test
    fun `structured failure legacy projection matches legacy pipeline note`() = runTest {
        val editor = FakePhotoWatermarkEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoWatermarkPostProcessor(editor)
        val result = processor.process(
            photoResult(
                watermarkText = "Test",
                watermarkTemplate = "classic-overlay"
            )
        )

        val legacyNote = result.structuredPostProcessFailures.single().toLegacyNote()
        assertTrue(result.pipelineNotes.contains(legacyNote),
            "Legacy projection '$legacyNote' should be among pipeline notes: ${result.pipelineNotes}")
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

    private fun countGreenInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 40 &&
                    Color.green(pixel) > Color.red(pixel) + 6 &&
                    Color.green(pixel) > Color.blue(pixel) + 4
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun maxGreenAdvantage(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var result = Int.MIN_VALUE
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                result = maxOf(result, Color.green(pixel) - maxOf(Color.red(pixel), Color.blue(pixel)))
            }
        }
        return result
    }

    private fun countPixelsDifferentFromReference(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        val safeLeft = left.coerceIn(0, bitmap.width - 1)
        val safeTop = top.coerceIn(0, bitmap.height - 1)
        val reference = bitmap.getPixel(safeLeft, safeTop)
        var count = 0
        for (y in safeTop until bottom.coerceAtMost(bitmap.height)) {
            for (x in safeLeft until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                val distance =
                    kotlin.math.abs(Color.red(pixel) - Color.red(reference)) +
                        kotlin.math.abs(Color.green(pixel) - Color.green(reference)) +
                        kotlin.math.abs(Color.blue(pixel) - Color.blue(reference))
                if (distance > 12) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countNightStreetNeonPixels(
        bitmap: Bitmap,
        top: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (Color.alpha(pixel) < 80) continue
                val red = Color.red(pixel)
                val green = Color.green(pixel)
                val blue = Color.blue(pixel)
                val cyan = green > 150 && blue > 110 && red < 90
                val magenta = red > 170 && blue > 120 && green < 110
                if (cyan || magenta) count += 1
            }
        }
        return count
    }

    private fun countWarmMemoryInk(
        bitmap: Bitmap,
        top: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 60 &&
                    Color.red(pixel) > Color.blue(pixel) + 30 &&
                    Color.green(pixel) > Color.blue(pixel) + 8 &&
                    Color.red(pixel) > 150
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countVeryLightPixels(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 80 &&
                    Color.red(pixel) > 210 &&
                    Color.green(pixel) > 210 &&
                    Color.blue(pixel) > 210
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countCoolLightInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 80 &&
                    Color.blue(pixel) >= Color.red(pixel) &&
                    Color.red(pixel) > 145 &&
                    Color.green(pixel) > 160 &&
                    Color.blue(pixel) > 180
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
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 120 &&
                    Color.red(pixel) > Color.blue(pixel) + 36 &&
                    Color.green(pixel) > Color.blue(pixel) + 18 &&
                    Color.red(pixel) > 180
                ) {
                    count += 1
                }
            }
        }
        return count
    }

    private fun countSepiaInk(
        bitmap: Bitmap,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int
    ): Int {
        var count = 0
        for (y in top.coerceAtLeast(0) until bottom.coerceAtMost(bitmap.height)) {
            for (x in left.coerceAtLeast(0) until right.coerceAtMost(bitmap.width)) {
                val pixel = bitmap.getPixel(x, y)
                if (
                    Color.alpha(pixel) > 45 &&
                    Color.red(pixel) > Color.blue(pixel) + 18 &&
                    Color.green(pixel) > Color.blue(pixel) + 4 &&
                    Color.red(pixel) in 80..215
                ) {
                    count += 1
                }
            }
        }
        return count
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
