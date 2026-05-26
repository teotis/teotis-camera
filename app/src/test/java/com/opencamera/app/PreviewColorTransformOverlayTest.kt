package com.opencamera.app

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.PreviewColorTransform
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PerceptualColorRecipe
import com.opencamera.core.settings.PreviewColorFidelity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PreviewColorTransformOverlayTest {

    @Test
    fun `none transform is identity and hidden by overlay contract`() {
        val transform = PreviewColorTransform.NONE

        assertTrue(transform.isIdentity)
        assertEquals(PreviewColorFidelity.NONE, transform.fidelity)
    }

    @Test
    fun `adapter produces approximate fidelity for active filter preview`() {
        val adapter = PreviewEffectAdapter()
        val effect = FilterEffect(
            profileId = "test",
            renderSpec = FilterRenderSpec(
                saturation = 0.7f,
                contrast = 1.3f,
                warmthShift = 4
            )
        )

        val transform = adapter.adapt(EffectSpec(listOf(effect))).colorTransform

        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
        assertNotNull(transform.matrix)
        assertFalse(transform.isIdentity)
    }

    @Test
    fun `recipe-only color lab effect produces approximate overlay transform`() {
        val recipe = PerceptualColorRecipe(warmthBias = 0.45f, chromaBoost = 0.3f)
        val adapter = PreviewEffectAdapter()

        val transform = adapter.adapt(
            EffectSpec(listOf(FilterEffect("color-lab", null, recipe = recipe)))
        ).colorTransform

        assertEquals(PreviewColorFidelity.APPROXIMATE, transform.fidelity)
        assertTrue(transform.tintAlpha > 0f)
        assertFalse(transform.isIdentity)
    }
}
