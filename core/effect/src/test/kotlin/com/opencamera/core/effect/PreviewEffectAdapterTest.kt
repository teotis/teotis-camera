package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.test.assertFalse

class PreviewEffectAdapterTest {

    private val adapter = PreviewEffectAdapter()

    @Test
    fun `empty spec returns empty model`() {
        val model = adapter.adapt(EffectSpec.EMPTY)

        assertNull(model.filterOverlay)
        assertNull(model.watermarkHint)
        assertNull(model.frameGuideline)
        assertNull(model.compositionGrid)
        assertNull(model.colorTransform)
        assertEquals(PreviewColorFidelity.NONE, model.colorFidelity)
    }

    @Test
    fun `filter effect produces overlay spec and color transform`() {
        val renderSpec = FilterRenderSpec(
            warmthShift = 3,
            tintShift = 1,
            saturation = 0.8f,
            contrast = 1.2f,
            vignetteStrength = 0.5f
        )
        val effect = FilterEffect(profileId = "warm", renderSpec = renderSpec)
        val spec = EffectSpec(listOf(effect))

        val model = adapter.adapt(spec)

        assertNotNull(model.filterOverlay)
        assertEquals(3f, model.filterOverlay!!.warmthShift)
        assertEquals(0.5f, model.filterOverlay!!.vignetteStrength)

        assertNotNull(model.colorTransform)
        assertFalse(model.colorTransform!!.isIdentity)
        assertEquals(PreviewColorFidelity.GOOD, model.colorFidelity)
    }

    @Test
    fun `filter with default spec produces identity color transform`() {
        val effect = FilterEffect(profileId = "original", renderSpec = FilterRenderSpec())
        val model = adapter.adapt(EffectSpec(listOf(effect)))

        assertNotNull(model.colorTransform)
        assertTrue(model.colorTransform!!.isIdentity)
        assertEquals(PreviewColorFidelity.NONE, model.colorFidelity)
    }

    @Test
    fun `watermark effect produces hint spec`() {
        val style = WatermarkStyleSettings(
            textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
            textOpacity = WatermarkTextOpacity.SOFT
        )
        val effect = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("watermarkModel" to "MyCamera Pro", "title" to "Hello"),
            style = style
        )
        val spec = EffectSpec(listOf(effect))

        val model = adapter.adapt(spec)

        assertNotNull(model.watermarkHint)
        assertEquals("classic", model.watermarkHint!!.templateId)
        assertEquals("MyCamera Pro", model.watermarkHint!!.previewText)
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT, model.watermarkHint!!.placement)
    }

    @Test
    fun `watermark hint uses fallback text when watermarkModel token missing`() {
        val effect = WatermarkEffect(
            templateId = "minimal",
            tokens = mapOf("title" to "Hello"),
            style = WatermarkStyleSettings()
        )
        val spec = EffectSpec(listOf(effect))

        val model = adapter.adapt(spec)

        assertNotNull(model.watermarkHint)
        assertEquals("Watermark", model.watermarkHint!!.previewText)
    }

    @Test
    fun `frame effect produces guideline spec`() {
        val effect = FrameEffect(ratio = FrameRatio.RATIO_16_9)
        val spec = EffectSpec(listOf(effect))

        val model = adapter.adapt(spec)

        assertNotNull(model.frameGuideline)
        assertEquals(FrameRatio.RATIO_16_9, model.frameGuideline!!.ratio)
    }

    @Test
    fun `multiple effects produce combined model`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = FilterRenderSpec(warmthShift = 2, contrast = 1.3f)
        )
        val watermark = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("watermarkModel" to "Pro"),
            style = WatermarkStyleSettings(textPlacement = WatermarkTextPlacement.TOP_LEFT)
        )
        val frame = FrameEffect(ratio = FrameRatio.RATIO_1_1)
        val spec = EffectSpec(listOf(filter, watermark, frame))

        val model = adapter.adapt(spec)

        assertNotNull(model.filterOverlay)
        assertNotNull(model.watermarkHint)
        assertNotNull(model.frameGuideline)
        assertNull(model.compositionGrid)

        assertEquals(2f, model.filterOverlay!!.warmthShift)
        assertEquals("classic", model.watermarkHint!!.templateId)
        assertEquals("Pro", model.watermarkHint!!.previewText)
        assertEquals(WatermarkTextPlacement.TOP_LEFT, model.watermarkHint!!.placement)
        assertEquals(FrameRatio.RATIO_1_1, model.frameGuideline!!.ratio)
    }

    @Test
    fun `tint color is proportional to warmth shift not clamped to extremes`() {
        val spec = FilterRenderSpec(warmthShift = 6, tintShift = -1, warmBoost = 0.09f)
        val effect = FilterEffect(profileId = "warm", renderSpec = spec)
        val model = adapter.adapt(EffectSpec(listOf(effect)))

        val tintColor = model.filterOverlay!!.tintColor
        val r = (tintColor ushr 16) and 0xFF
        val g = (tintColor ushr 8) and 0xFF
        val b = tintColor and 0xFF

        // With warmthShift=6, channels should be warm but not clamped to extremes
        assertTrue(r in 130..200, "R=$r should be warm but not saturated (was clamped to 255 with old ×60)")
        assertTrue(b in 70..128, "B=$b should be cool but not zero (was clamped to 0 with old ×60)")
        assertTrue(g in 120..140, "G=$g should be near neutral")
    }

    @Test
    fun `tint color distinguishes different warmth levels`() {
        val mild = FilterEffect("mild", FilterRenderSpec(warmthShift = 3))
        val strong = FilterEffect("strong", FilterRenderSpec(warmthShift = 10))

        val mildColor = adapter.adapt(EffectSpec(listOf(mild))).filterOverlay!!.tintColor
        val strongColor = adapter.adapt(EffectSpec(listOf(strong))).filterOverlay!!.tintColor

        val mildR = (mildColor ushr 16) and 0xFF
        val strongR = (strongColor ushr 16) and 0xFF
        val mildB = mildColor and 0xFF
        val strongB = strongColor and 0xFF

        // Strong warm should have higher R and lower B than mild warm
        assertTrue(strongR > mildR, "strong warm R=$strongR should be > mild warm R=$mildR")
        assertTrue(strongB < mildB, "strong warm B=$strongB should be < mild warm B=$mildB")
    }

    @Test
    fun `pure text watermark hint is text overlay`() {
        val model = adapter.adapt(
            EffectSpec(listOf(
                WatermarkEffect(
                    templateId = "pure-text",
                    tokens = mapOf("watermarkModel" to "OpenCamera"),
                    style = WatermarkStyleSettings(
                        textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
                        textOpacity = WatermarkTextOpacity.SOFT
                    )
                )
            ))
        )

        assertEquals("pure-text", model.watermarkHint?.templateId)
        assertEquals(WatermarkPreviewShape.TEXT_ONLY, model.watermarkHint?.shape)
    }

    @Test
    fun `blur four border watermark hint is frame hint`() {
        val model = adapter.adapt(
            EffectSpec(listOf(
                WatermarkEffect(
                    templateId = "blur-four-border",
                    tokens = mapOf("watermarkModel" to "OpenCamera"),
                    style = WatermarkStyleSettings(
                        textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                        textOpacity = WatermarkTextOpacity.SOLID,
                        frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
                    )
                )
            ))
        )

        assertEquals(WatermarkPreviewShape.FOUR_BORDER, model.watermarkHint?.shape)
    }

    @Test
    fun `travel polaroid watermark hint is expanded frame`() {
        val model = adapter.adapt(
            EffectSpec(listOf(
                WatermarkEffect(
                    templateId = "travel-polaroid",
                    tokens = mapOf("watermarkModel" to "Camera"),
                    style = WatermarkStyleSettings()
                )
            ))
        )

        assertEquals(WatermarkPreviewShape.EXPANDED_FRAME, model.watermarkHint?.shape)
    }

    @Test
    fun `unknown watermark hint defaults to backed text`() {
        val model = adapter.adapt(
            EffectSpec(listOf(
                WatermarkEffect(
                    templateId = "custom-template",
                    tokens = mapOf("watermarkModel" to "Test"),
                    style = WatermarkStyleSettings()
                )
            ))
        )

        assertEquals(WatermarkPreviewShape.BACKED_TEXT, model.watermarkHint?.shape)
    }
}
