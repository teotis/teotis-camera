package com.opencamera.core.settings

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StyleColorPipelineTest {
    @Test
    fun `neutral color lab preserves inherited style base and records order`() {
        val base = textureBaseSpec()

        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-texture",
                baseRenderSpec = base,
                colorLabSpec = ColorLabSpec(),
                colorScience = StyleColorScience.TEXTURE
            )
        )

        assertEquals(base, result.finalRenderSpec)
        assertEquals(
            listOf(StyleColorStage.STYLE_BASE, StyleColorStage.COLOR_LAB_SECONDARY),
            result.stages
        )
        assertEquals("style-texture-v1", result.baseLutId)
    }

    @Test
    fun `style strength blends base before color lab is applied`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-texture",
                baseRenderSpec = textureBaseSpec(),
                colorLabSpec = ColorLabSpec(),
                colorScience = StyleColorScience.TEXTURE,
                styleStrength = 0f
            )
        )

        assertEquals(FilterRenderSpec(), result.inheritedBaseSpec)
        assertEquals(FilterRenderSpec(), result.finalRenderSpec)
    }

    @Test
    fun `texture tone lift fades shadows instead of simply lifting exposure`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-texture",
                baseRenderSpec = textureBaseSpec(),
                colorLabSpec = ColorLabSpec(toneAxis = 1f),
                colorScience = StyleColorScience.TEXTURE
            )
        )

        assertTrue(result.finalRenderSpec.brightnessShift <= textureBaseSpec().brightnessShift + 4)
        assertTrue(result.finalRenderSpec.shadowLift > textureBaseSpec().shadowLift + 0.12f)
        assertTrue(result.finalRenderSpec.contrast < textureBaseSpec().contrast)
    }

    @Test
    fun `texture warm color creates split grade rather than pure warming`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-texture",
                baseRenderSpec = textureBaseSpec(),
                colorLabSpec = ColorLabSpec(colorAxis = 1f),
                colorScience = StyleColorScience.TEXTURE
            )
        )

        assertTrue(result.finalRenderSpec.warmBoost > 0f)
        assertTrue(result.finalRenderSpec.coolBoost > 0f)
        assertTrue(result.notes.any { it.contains("split-grade") })
    }

    @Test
    fun `vivid tone lift prioritizes highlight recovery`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-vivid",
                baseRenderSpec = vividBaseSpec(),
                colorLabSpec = ColorLabSpec(toneAxis = 1f),
                colorScience = StyleColorScience.VIVID
            )
        )

        assertTrue(result.finalRenderSpec.highlightCompression > 0.18f)
        assertTrue(result.finalRenderSpec.shadowLift > 0.08f)
        assertTrue(result.finalRenderSpec.contrast >= 1f)
    }

    @Test
    fun `vivid color boost is capped to avoid saturation overflow`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-vivid",
                baseRenderSpec = vividBaseSpec().copy(saturation = 1.28f),
                colorLabSpec = ColorLabSpec(colorAxis = 1f),
                colorScience = StyleColorScience.VIVID
            )
        )

        assertTrue(result.finalRenderSpec.saturation <= 1.32f)
        assertTrue(result.notes.any { it.contains("saturation-guard") })
    }

    @Test
    fun `monochrome style ignores color axis but keeps tone response`() {
        val result = StyleColorPipeline.render(
            StyleColorPipelineRequest(
                styleProfileId = "photo-bw",
                baseRenderSpec = FilterRenderSpec(monochromeMix = 1f, saturation = 0f),
                colorLabSpec = ColorLabSpec(colorAxis = 1f, toneAxis = -1f),
                colorScience = StyleColorScience.MONOCHROME
            )
        )

        assertEquals(0, result.finalRenderSpec.warmthShift)
        assertEquals(0f, result.finalRenderSpec.warmBoost)
        assertEquals(0f, result.finalRenderSpec.coolBoost)
        assertEquals(1f, result.finalRenderSpec.monochromeMix)
        assertTrue(result.finalRenderSpec.contrast > 1f)
    }

    @Test
    fun `color science can be inferred from style id and base spec`() {
        assertEquals(
            StyleColorScience.VIVID,
            StyleColorPipeline.resolveColorScience("photo-vivid", vividBaseSpec())
        )
        assertEquals(
            StyleColorScience.TEXTURE,
            StyleColorPipeline.resolveColorScience("humanistic-street", textureBaseSpec())
        )
        assertEquals(
            StyleColorScience.MONOCHROME,
            StyleColorPipeline.resolveColorScience(
                "photo-original",
                FilterRenderSpec(monochromeMix = 0.8f)
            )
        )
    }

    private fun textureBaseSpec(): FilterRenderSpec {
        return FilterRenderSpec(
            brightnessShift = -3,
            contrast = 1.18f,
            saturation = 0.82f,
            warmthShift = 2,
            shadowLift = 0.02f,
            highlightCompression = 0.04f
        )
    }

    private fun vividBaseSpec(): FilterRenderSpec {
        return FilterRenderSpec(
            brightnessShift = 2,
            contrast = 1.12f,
            saturation = 1.18f,
            warmthShift = 1
        )
    }
}
