package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PreviewEffectAdapterTest {

    private val adapter = PreviewEffectAdapter()

    @Test
    fun `empty spec returns empty model`() {
        val model = adapter.adapt(EffectSpec.EMPTY)

        assertNull(model.filterOverlay)
        assertNull(model.watermarkHint)
        assertNull(model.frameGuideline)
        assertNull(model.compositionGrid)
    }

    @Test
    fun `filter effect produces overlay spec`() {
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
}
