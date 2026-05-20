package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectBridgeTest {

    @Test
    fun `toMetadataTags with empty spec returns empty map`() {
        val tags = EffectBridge.toMetadataTags(EffectSpec.EMPTY)
        assertTrue(tags.isEmpty())
    }

    @Test
    fun `toMetadataTags maps FilterEffect`() {
        val renderSpec = FilterRenderSpec(contrast = 1.2f, saturation = 0.9f)
        val effect = FilterEffect(profileId = "vivid", renderSpec = renderSpec)
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("vivid", tags["filterProfile"])
        // FilterRenderSpec.toMetadataTags() should be merged
        assertEquals("1.2", tags["filterSpec.contrast"])
        assertEquals("0.9", tags["filterSpec.saturation"])
        assertEquals("1", tags["filterSpec.version"])
    }

    @Test
    fun `toMetadataTags maps WatermarkEffect`() {
        val style = WatermarkStyleSettings(
            textScale = WatermarkTextScale.LARGE,
            textOpacity = WatermarkTextOpacity.SOFT,
            textPlacement = WatermarkTextPlacement.BOTTOM_RIGHT,
            frameBackground = WatermarkFrameBackground.WHITE
        )
        val effect = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("title" to "Hello", "subtitle" to "World"),
            style = style
        )
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("classic", tags["watermarkTemplate"])
        assertEquals("Hello", tags["title"])
        assertEquals("World", tags["subtitle"])
        assertEquals(WatermarkTextScale.LARGE.multiplier.toString(), tags["watermarkTextScale"])
        assertEquals(WatermarkTextOpacity.SOFT.alphaFraction.toString(), tags["watermarkTextOpacity"])
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT.name, tags["watermarkPosition"])
        assertEquals(WatermarkFrameBackground.WHITE.name, tags["watermarkFrameBackground"])
    }

    @Test
    fun `toMetadataTags maps FrameEffect`() {
        val effect = FrameEffect(ratio = FrameRatio.RATIO_16_9)
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("16:9", tags["frameRatio"])
    }

    @Test
    fun `toMetadataTags maps PortraitEffect`() {
        val effect = PortraitEffect(
            profileId = "luminous",
            renderPath = "gpu-accelerated",
            beautyPreset = "clear",
            beautyStrength = 0.7f,
            bokehEffect = "smooth"
        )
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("portrait", tags["mode"])
        assertEquals("gpu-accelerated", tags["renderPath"])
        assertEquals("luminous", tags["portraitProfile"])
        assertEquals("clear", tags["portraitBeautyPreset"])
        assertEquals("0.7", tags["portraitBeautyStrength"])
        assertEquals("smooth", tags["portraitBokehEffect"])
    }

    @Test
    fun `toMetadataTags maps DocumentEffect`() {
        val effect = DocumentEffect(autoCrop = true, contrastProfile = "high-contrast")
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("document", tags["mode"])
        assertEquals("true", tags["autoCrop"])
        assertEquals("high-contrast", tags["contrastProfile"])
    }

    @Test
    fun `toMetadataTags maps DocumentEffect with null contrastProfile`() {
        val effect = DocumentEffect(autoCrop = false, contrastProfile = null)
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("document", tags["mode"])
        assertEquals("false", tags["autoCrop"])
        assertNull(tags["contrastProfile"])
    }

    @Test
    fun `toMetadataTags maps SelfieMirrorEffect`() {
        val effect = SelfieMirrorEffect()
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("true", tags["selfieMirrorApply"])
    }

    @Test
    fun `toPostProcessSpec with filter and watermark`() {
        val filter = FilterEffect(profileId = "vivid", renderSpec = null)
        val watermark = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("title" to "Photo Title", "subtitle" to ""),
            style = WatermarkStyleSettings()
        )
        val spec = EffectSpec(listOf(filter, watermark))

        val result = EffectBridge.toPostProcessSpec(spec)

        assertEquals("vivid", result.algorithmProfile)
        // blank values are filtered out by buildWatermarkText
        assertEquals("Photo Title", result.watermarkText)
    }

    @Test
    fun `toPostProcessSpec with empty spec`() {
        val result = EffectBridge.toPostProcessSpec(EffectSpec.EMPTY)

        assertNull(result.algorithmProfile)
        assertNull(result.watermarkText)
    }
}
