package com.opencamera.core.effect

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.WatermarkStyleSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class EffectSpecTest {

    @Test
    fun `EMPTY has no entries`() {
        assertTrue(EffectSpec.EMPTY.entries.isEmpty())
    }

    @Test
    fun `find returns matching entry`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = FilterRenderSpec(contrast = 1.2f)
        )
        val spec = EffectSpec(listOf(filter))

        val found = spec.find<FilterEffect>()
        assertNotNull(found)
        assertEquals("vivid", found.profileId)
    }

    @Test
    fun `find returns null when no match`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = null
        )
        val spec = EffectSpec(listOf(filter))

        val found = spec.find<WatermarkEffect>()
        assertNull(found)
    }

    @Test
    fun `hasTarget returns true when entry matches`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = null,
            target = EffectTarget.PREVIEW
        )
        val spec = EffectSpec(listOf(filter))

        assertTrue(spec.hasTarget(EffectTarget.PREVIEW))
    }

    @Test
    fun `hasTarget returns true for BOTH target`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = null,
            target = EffectTarget.BOTH
        )
        val spec = EffectSpec(listOf(filter))

        assertTrue(spec.hasTarget(EffectTarget.PREVIEW))
        assertTrue(spec.hasTarget(EffectTarget.CAPTURE))
    }

    @Test
    fun `hasTarget returns false when no entry matches`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = null,
            target = EffectTarget.PREVIEW
        )
        val spec = EffectSpec(listOf(filter))

        assertFalse(spec.hasTarget(EffectTarget.CAPTURE))
    }

    @Test
    fun `multiple entries of different types`() {
        val filter = FilterEffect(
            profileId = "vivid",
            renderSpec = FilterRenderSpec(contrast = 1.1f)
        )
        val watermark = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("title" to "Hello"),
            style = WatermarkStyleSettings()
        )
        val frame = FrameEffect(ratio = FrameRatio.RATIO_4_3)
        val spec = EffectSpec(listOf(filter, watermark, frame))

        assertEquals(3, spec.entries.size)
        assertNotNull(spec.find<FilterEffect>())
        assertNotNull(spec.find<WatermarkEffect>())
        assertNotNull(spec.find<FrameEffect>())
        assertNull(spec.find<DocumentEffect>())
    }

    @Test
    fun `document mode effect spec with watermark`() {
        val document = DocumentEffect(autoCrop = true, contrastProfile = "high")
        val watermark = WatermarkEffect(
            templateId = "classic-overlay",
            tokens = mapOf("watermarkModel" to "OpenCamera"),
            style = WatermarkStyleSettings(
                textPlacement = WatermarkTextPlacement.BOTTOM_LEFT
            )
        )
        val spec = EffectSpec(listOf(document, watermark))

        assertEquals(2, spec.entries.size)
        assertNotNull(spec.find<DocumentEffect>())
        assertNotNull(spec.find<WatermarkEffect>())
        assertNull(spec.find<FilterEffect>())
        assertNull(spec.find<FrameEffect>())
        assertEquals("classic-overlay", spec.find<WatermarkEffect>()!!.templateId)
        assertEquals(WatermarkTextPlacement.BOTTOM_LEFT, spec.find<WatermarkEffect>()!!.style.textPlacement)
    }

    @Test
    fun `watermark effect target is BOTH by default`() {
        val watermark = WatermarkEffect(
            templateId = "classic",
            tokens = mapOf("watermarkModel" to "Test"),
            style = WatermarkStyleSettings()
        )
        assertEquals(EffectTarget.BOTH, watermark.target)
    }
}
