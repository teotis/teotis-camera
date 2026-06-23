package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PerceptualColorRecipe
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
        assertEquals(WatermarkTextPlacement.BOTTOM_RIGHT.storageKey, tags["watermarkPosition"])
        assertEquals(WatermarkFrameBackground.WHITE.storageKey, tags["watermarkFrameBackground"])
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
            beautyStrength = "balanced",
            bokehEffect = "smooth",
            depthStrength = 75
        )
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("portrait", tags["mode"])
        assertEquals("gpu-accelerated", tags["renderPath"])
        assertEquals("luminous", tags["portraitProfile"])
        assertEquals("clear", tags["portraitBeautyPreset"])
        assertEquals("balanced", tags["portraitBeautyStrength"])
        assertEquals("smooth", tags["portraitBokehEffect"])
        assertEquals("75", tags["portraitDepthStrength"])
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
        assertEquals("Photo Title", result.watermarkText)
    }

    @Test
    fun `toPostProcessSpec with empty spec`() {
        val result = EffectBridge.toPostProcessSpec(EffectSpec.EMPTY)

        assertNull(result.algorithmProfile)
        assertNull(result.watermarkText)
    }

    @Test
    fun `toMetadataTags with non-neutral recipe emits recipe tags`() {
        val recipe = PerceptualColorRecipe(
            toneLift = 0.5f,
            toneDepth = 0.3f,
            chromaBoost = 0.2f,
            warmthBias = 0.4f,
            tintBias = -0.1f,
            neutralProtection = 0.75f,
            skinProtection = 0.70f
        )
        val effect = FilterEffect(profileId = "color-lab", renderSpec = null, recipe = recipe)
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("color-lab", tags["filterProfile"])
        assertEquals("0.5", tags["recipe.toneLift"])
        assertEquals("0.3", tags["recipe.toneDepth"])
        assertEquals("0.2", tags["recipe.chromaBoost"])
        assertEquals("0.4", tags["recipe.warmthBias"])
        assertEquals("-0.1", tags["recipe.tintBias"])
        assertEquals("0.75", tags["recipe.neutralProtection"])
        assertEquals("0.7", tags["recipe.skinProtection"])
    }

    @Test
    fun `toMetadataTags with neutral recipe does not emit recipe tags`() {
        val effect = FilterEffect(profileId = "vivid", renderSpec = null, recipe = PerceptualColorRecipe.NEUTRAL)
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("vivid", tags["filterProfile"])
        assertNull(tags["recipe.toneLift"])
        assertNull(tags["recipe.chromaBoost"])
    }

    @Test
    fun `toMetadataTags with DocumentEffect and WatermarkEffect`() {
        val document = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val watermark = WatermarkEffect(
            templateId = "classic-overlay",
            tokens = mapOf(
                "watermarkModel" to "OpenCamera",
                "watermarkDatetime" to "2026-05-26 10:00",
                "watermarkCameraParams" to "12MP"
            ),
            style = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
                textScale = WatermarkTextScale.NORMAL,
                textOpacity = WatermarkTextOpacity.SOFT,
                frameBackground = WatermarkFrameBackground.DARK
            )
        )
        val spec = EffectSpec(listOf(document, watermark))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("document", tags["mode"])
        assertEquals("true", tags["autoCrop"])
        assertEquals("classic-overlay", tags["watermarkTemplate"])
        assertEquals("OpenCamera", tags["watermarkModel"])
        assertEquals("2026-05-26 10:00", tags["watermarkDatetime"])
        assertEquals("12MP", tags["watermarkCameraParams"])
        assertEquals(WatermarkTextPlacement.BOTTOM_LEFT.storageKey, tags["watermarkPosition"])
    }

    @Test
    fun `toPostProcessSpec with DocumentEffect and WatermarkEffect joins tokens`() {
        val document = DocumentEffect(autoCrop = false, contrastProfile = null)
        val watermark = WatermarkEffect(
            templateId = "travel-polaroid",
            tokens = mapOf(
                "watermarkModel" to "OpenCamera",
                "watermarkDatetime" to "2026-05-26 10:00",
                "watermarkCameraParams" to "12MP"
            ),
            style = WatermarkStyleSettings()
        )
        val spec = EffectSpec(listOf(document, watermark))

        val result = EffectBridge.toPostProcessSpec(spec)

        assertEquals("OpenCamera | 2026-05-26 10:00 | 12MP", result.watermarkText)
    }

    @Test
    fun `toMetadataTags with PortraitEffect and WatermarkEffect`() {
        val portrait = PortraitEffect(
            profileId = "luminous",
            renderPath = "depth",
            beautyPreset = "radiant",
            beautyStrength = "elevated",
            bokehEffect = "dreamy"
        )
        val watermark = WatermarkEffect(
            templateId = "retro-frame",
            tokens = mapOf(
                "watermarkModel" to "OpenCamera",
                "watermarkDatetime" to "2026-05-26 10:00",
                "watermarkCameraParams" to "12MP • 4:3"
            ),
            style = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_CENTER,
                textScale = WatermarkTextScale.LARGE,
                textOpacity = WatermarkTextOpacity.SOLID,
                frameBackground = WatermarkFrameBackground.SOURCE_VIVID_BLUR
            )
        )
        val frame = FrameEffect(ratio = FrameRatio.RATIO_4_3)
        val spec = EffectSpec(listOf(portrait, watermark, frame))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("portrait", tags["mode"])
        assertEquals("luminous", tags["portraitProfile"])
        assertEquals("retro-frame", tags["watermarkTemplate"])
        assertEquals(WatermarkTextScale.LARGE.multiplier.toString(), tags["watermarkTextScale"])
        assertEquals(WatermarkFrameBackground.SOURCE_VIVID_BLUR.storageKey, tags["watermarkFrameBackground"])
        assertEquals("4:3", tags["frameRatio"])
    }

    // ── Characterization: collision semantics ────────────────────────────

    @Test
    fun `char portrait effect sets mode to portrait - collision with mode plugin downstream`() {
        val portrait = PortraitEffect(
            profileId = "luminous", renderPath = "depth",
            beautyPreset = "clear", beautyStrength = "balanced",
            bokehEffect = "smooth"
        )
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(portrait)))
        assertEquals("portrait", tags["mode"])
    }

    @Test
    fun `char document effect sets mode to document - collision with mode plugin downstream`() {
        val doc = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(doc)))
        assertEquals("document", tags["mode"])
    }

    @Test
    fun `char checkin mode plugin overrides portrait effect mode tag`() {
        val portrait = PortraitEffect(
            profileId = "luminous", renderPath = "depth",
            beautyPreset = "clear", beautyStrength = "balanced",
            bokehEffect = "smooth"
        )
        val bridgeTags = EffectBridge.toMetadataTags(EffectSpec(listOf(portrait)))
        val finalMetadata = buildMap {
            putAll(bridgeTags)
            put("mode", "check-in")
        }
        assertEquals("check-in", finalMetadata["mode"])
        assertEquals("luminous", finalMetadata["portraitProfile"])
    }

    @Test
    fun `char photo mode does not use portrait effect so mode key comes only from buildSaveRequest`() {
        val filter = FilterEffect(profileId = "photo-vivid", renderSpec = null)
        val bridgeTags = EffectBridge.toMetadataTags(EffectSpec(listOf(filter)))
        assertFalse(bridgeTags.containsKey("mode"))
        val finalMetadata = buildMap {
            putAll(bridgeTags)
            put("mode", "photo")
        }
        assertEquals("photo", finalMetadata["mode"])
    }

    @Test
    fun `char watermark tokens - datetime format and model semantics`() {
        val watermark = WatermarkEffect(
            templateId = "default",
            tokens = mapOf(
                "watermarkModel" to "OpenCamera",
                "watermarkDatetime" to "2026-06-10 14:30",
                "watermarkCameraParams" to "12MP • 4:3"
            ),
            style = WatermarkStyleSettings()
        )
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(watermark)))
        assertEquals("default", tags["watermarkTemplate"])
        assertEquals("OpenCamera", tags["watermarkModel"])
        assertEquals("2026-06-10 14:30", tags["watermarkDatetime"])
        assertEquals("12MP • 4:3", tags["watermarkCameraParams"])
    }

    @Test
    fun `char selfie mirror effect sets selfieMirrorApply to true`() {
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(SelfieMirrorEffect())))
        assertEquals("true", tags["selfieMirrorApply"])
    }

    @Test
    fun `char selfie mirror absent from non-mirror effect`() {
        val filter = FilterEffect(profileId = "vivid", renderSpec = null)
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(filter)))
        assertFalse(tags.containsKey("selfieMirrorApply"))
    }

    @Test
    fun `char stillResolution not from effect bridge - only from mode plugins`() {
        val filter = FilterEffect(profileId = "photo-vivid", renderSpec = null)
        val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(filter)))
        assertFalse(tags.containsKey("stillResolution"))
    }

    @Test
    fun `toMetadataTags maps DocumentEffect with colorMode and scanGuide`() {
        val effect = DocumentEffect(
            autoCrop = true,
            contrastProfile = "high",
            colorMode = DocumentColorMode.GRAYSCALE,
            scanGuide = true
        )
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertEquals("document", tags["mode"])
        assertEquals("true", tags["autoCrop"])
        assertEquals("high", tags["contrastProfile"])
        assertEquals("grayscale", tags["documentColorMode"])
        assertEquals("true", tags["documentScanGuide"])
    }

    @Test
    fun `toMetadataTags omits documentColorMode when null`() {
        val effect = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val spec = EffectSpec(listOf(effect))

        val tags = EffectBridge.toMetadataTags(spec)

        assertNull(tags["documentColorMode"])
        assertEquals("false", tags["documentScanGuide"])
    }

    @Test
    fun `toMetadataTags emits all color modes`() {
        for (mode in DocumentColorMode.entries) {
            val effect = DocumentEffect(
                autoCrop = false,
                contrastProfile = null,
                colorMode = mode
            )
            val tags = EffectBridge.toMetadataTags(EffectSpec(listOf(effect)))
            assertEquals(mode.tagValue, tags["documentColorMode"])
        }
    }
}
