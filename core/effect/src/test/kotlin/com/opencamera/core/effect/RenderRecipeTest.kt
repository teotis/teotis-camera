package com.opencamera.core.effect

import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RenderRecipeTest {

    // --- from(EffectSpec) ---

    @Test
    fun `empty EffectSpec yields no-postprocess recipe`() {
        val recipe = RenderRecipe.from(EffectSpec.EMPTY)

        assertFalse(recipe.requiresFinalOutputPostprocess)
        assertNull(recipe.filterProfileId)
        assertNull(recipe.filterRenderSpec)
        assertNull(recipe.frameRatio)
        assertNull(recipe.watermarkTemplateId)
        assertNull(recipe.watermarkText)
        assertFalse(recipe.selfieMirror)
    }

    @Test
    fun `FilterEffect with renderSpec yields postprocess`() {
        val spec = EffectSpec(
            listOf(
                FilterEffect(
                    profileId = "vivid",
                    renderSpec = FilterRenderSpec(contrast = 1.1f)
                )
            )
        )
        val recipe = RenderRecipe.from(spec)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("vivid", recipe.filterProfileId)
        assertEquals(FilterRenderSpec(contrast = 1.1f), recipe.filterRenderSpec)
    }

    @Test
    fun `FilterEffect without renderSpec but with profileId does not require final postprocess`() {
        val spec = EffectSpec(
            listOf(
                FilterEffect(profileId = "neutral", renderSpec = null)
            )
        )
        val recipe = RenderRecipe.from(spec)

        assertFalse(recipe.requiresFinalOutputPostprocess)
        assertEquals("neutral", recipe.filterProfileId)
        assertNull(recipe.filterRenderSpec)
    }

    @Test
    fun `non-4_3 FrameEffect yields postprocess`() {
        val spec = EffectSpec(listOf(FrameEffect(ratio = FrameRatio.RATIO_16_9)))
        val recipe = RenderRecipe.from(spec)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals(FrameRatio.RATIO_16_9, recipe.frameRatio)
    }

    @Test
    fun `4_3 FrameEffect alone does not trigger postprocess`() {
        val spec = EffectSpec(listOf(FrameEffect(ratio = FrameRatio.RATIO_4_3)))
        val recipe = RenderRecipe.from(spec)

        assertFalse(recipe.requiresFinalOutputPostprocess)
        assertEquals(FrameRatio.RATIO_4_3, recipe.frameRatio)
    }

    @Test
    fun `WatermarkEffect yields postprocess`() {
        val style = WatermarkStyleSettings(
            textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
            textOpacity = WatermarkTextOpacity.SOLID
        )
        val spec = EffectSpec(
            listOf(
                WatermarkEffect(
                    templateId = "classic-overlay",
                    tokens = mapOf("watermarkModel" to "Pixel 9"),
                    style = style
                )
            )
        )
        val recipe = RenderRecipe.from(spec)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("classic-overlay", recipe.watermarkTemplateId)
        assertEquals("Pixel 9", recipe.watermarkText)
    }

    @Test
    fun `SelfieMirrorEffect yields postprocess`() {
        val spec = EffectSpec(listOf(SelfieMirrorEffect()))
        val recipe = RenderRecipe.from(spec)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertTrue(recipe.selfieMirror)
    }

    @Test
    fun `combined effects accumulate in recipe`() {
        val style = WatermarkStyleSettings(
            textPlacement = WatermarkTextPlacement.BOTTOM_LEFT,
            textOpacity = WatermarkTextOpacity.SOLID
        )
        val spec = EffectSpec(
            listOf(
                FilterEffect(profileId = "rich", renderSpec = FilterRenderSpec(saturation = 1.2f)),
                WatermarkEffect(templateId = "retro-frame", tokens = emptyMap(), style = style),
                FrameEffect(ratio = FrameRatio.RATIO_1_1)
            )
        )
        val recipe = RenderRecipe.from(spec)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("rich", recipe.filterProfileId)
        assertEquals(FrameRatio.RATIO_1_1, recipe.frameRatio)
        assertEquals("retro-frame", recipe.watermarkTemplateId)
    }

    // --- from(ShotRequest) ---

    @Test
    fun `clean single-frame no-effect shot yields false`() {
        val shot = buildShotRequest()
        val recipe = RenderRecipe.from(shot)

        assertFalse(recipe.requiresFinalOutputPostprocess)
    }

    @Test
    fun `ShotRequest with filterSpec version yields postprocess`() {
        val shot = buildShotRequest(
            customTags = FilterRenderSpec(contrast = 1.1f).toMetadataTags() +
                mapOf("filterProfile" to "custom-vivid")
        )
        val recipe = RenderRecipe.from(shot)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("custom-vivid", recipe.filterProfileId)
        assertEquals(FilterRenderSpec(contrast = 1.1f), recipe.filterRenderSpec)
    }

    @Test
    fun `ShotRequest with frameRatio 16_9 yields postprocess`() {
        val shot = buildShotRequest(customTags = mapOf("frameRatio" to "16:9"))
        val recipe = RenderRecipe.from(shot)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals(FrameRatio.RATIO_16_9, recipe.frameRatio)
    }

    @Test
    fun `ShotRequest with frameRatio 4_3 alone does not trigger postprocess`() {
        val shot = buildShotRequest(customTags = mapOf("frameRatio" to "4:3"))
        val recipe = RenderRecipe.from(shot)

        assertFalse(recipe.requiresFinalOutputPostprocess)
        assertEquals(FrameRatio.RATIO_4_3, recipe.frameRatio)
    }

    @Test
    fun `ShotRequest with watermark text yields postprocess`() {
        val shot = buildShotRequest(
            watermarkText = "PHOTO OpenCamera",
            customTags = mapOf("watermarkTemplate" to "travel-polaroid")
        )
        val recipe = RenderRecipe.from(shot)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("PHOTO OpenCamera", recipe.watermarkText)
        assertEquals("travel-polaroid", recipe.watermarkTemplateId)
    }

    @Test
    fun `ShotRequest with selfieMirrorApply yields postprocess`() {
        val shot = buildShotRequest(customTags = mapOf("selfieMirrorApply" to "true"))
        val recipe = RenderRecipe.from(shot)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertTrue(recipe.selfieMirror)
    }

    @Test
    fun `ShotRequest with algorithmProfile only yields postprocess`() {
        val shot = buildShotRequest(algorithmProfile = "photo-vivid")
        val recipe = RenderRecipe.from(shot)

        assertTrue(recipe.requiresFinalOutputPostprocess)
        assertEquals("photo-vivid", recipe.filterProfileId)
    }

    private fun buildShotRequest(
        algorithmProfile: String? = null,
        watermarkText: String? = null,
        customTags: Map<String, String> = emptyMap()
    ): ShotRequest {
        return ShotRequest(
            shotId = "test-shot",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(
                    algorithmProfile = algorithmProfile,
                    watermarkText = watermarkText,
                    customTags = customTags
                )
            ),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = com.opencamera.core.media.PostProcessSpec(
                algorithmProfile = algorithmProfile
            ),
            captureProfile = CaptureProfile()
        )
    }
}
