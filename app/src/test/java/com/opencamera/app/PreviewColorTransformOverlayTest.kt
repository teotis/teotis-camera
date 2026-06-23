package com.opencamera.app

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.effect.PreviewColorTransform
import com.opencamera.core.effect.PreviewEffectAdapter
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PerceptualColorRecipe
import com.opencamera.core.settings.PreviewColorFidelity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PreviewColorTransformOverlayTest {

    @Test
    fun `none transform is identity and hidden by overlay contract`() {
        val transform = PreviewColorTransform.NONE

        assertTrue(transform.isIdentity)
        assertEquals(PreviewColorFidelity.NONE, transform.fidelity)
        assertNull(previewColorTransformOverlaySpec(transform),
            "NONE transform must not produce an overlay spec")
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

    // ── ISSUE-003: colorMatrix must survive the overlay adapter even without tint ──

    @Test
    fun `black-and-white transform preserves matrix in overlay spec without tint`() {
        val transform = PreviewColorTransform.fromSpec(
            FilterRenderSpec(monochromeMix = 1.0f)
        )

        assertFalse(transform.isIdentity)
        assertNotNull(transform.matrix)
        assertEquals(0f, transform.tintAlpha, "B&W transform should not rely on tint")

        val spec = previewColorTransformOverlaySpec(transform)
        assertNotNull(spec, "B&W matrix must produce an overlay spec even without tint")
        assertNotNull(spec.colorMatrix, "Matrix must be preserved on the overlay spec")
        assertFalse(PreviewColorMatrixBuilder.isIdentity(spec.colorMatrix))
        assertEquals(0f, spec.tintAlpha, "B&W overlay should not introduce tint")
    }

    @Test
    fun `warm shift transform preserves matrix in overlay spec without tint`() {
        val transform = PreviewColorTransform.fromSpec(
            FilterRenderSpec(warmthShift = 12)
        )

        assertFalse(transform.isIdentity)
        assertNotNull(transform.matrix)
        assertEquals(0f, transform.tintAlpha)

        val spec = previewColorTransformOverlaySpec(transform)
        assertNotNull(spec)
        assertNotNull(spec.colorMatrix)
        // Warmth matrix adds to R translation and subtracts from B translation.
        assertTrue(spec.colorMatrix!![4] > 0f, "R translation should be positive for warm shift")
        assertTrue(spec.colorMatrix!![14] < 0f, "B translation should be negative for warm shift")
    }

    @Test
    fun `cool shift transform preserves matrix in overlay spec without tint`() {
        val transform = PreviewColorTransform.fromSpec(
            FilterRenderSpec(warmthShift = -12)
        )

        assertFalse(transform.isIdentity)
        assertEquals(0f, transform.tintAlpha)

        val spec = previewColorTransformOverlaySpec(transform)
        assertNotNull(spec)
        assertNotNull(spec.colorMatrix)
        assertTrue(spec.colorMatrix!![4] < 0f, "R translation should be negative for cool shift")
        assertTrue(spec.colorMatrix!![14] > 0f, "B translation should be positive for cool shift")
    }

    @Test
    fun `contrast transform preserves matrix in overlay spec without tint`() {
        val transform = PreviewColorTransform.fromSpec(
            FilterRenderSpec(contrast = 1.5f)
        )

        assertFalse(transform.isIdentity)
        assertEquals(0f, transform.tintAlpha)

        val spec = previewColorTransformOverlaySpec(transform)
        assertNotNull(spec)
        assertNotNull(spec.colorMatrix)
        // Contrast matrix scales diagonal around midpoint.
        assertEquals(1.5f, spec.colorMatrix!![0], 0.001f)
        assertEquals(1.5f, spec.colorMatrix!![6], 0.001f)
        assertEquals(1.5f, spec.colorMatrix!![12], 0.001f)
    }

    @Test
    fun `recipe tint and spec matrix coexist in overlay spec`() {
        // When the adapter resolves a spec-based matrix (non-identity), it takes priority
        // over recipe-driven tint (see PreviewEffectAdapter.resolveColorTransform). The
        // overlay spec must therefore preserve the matrix even though tintAlpha is zero
        // — this is the ISSUE-003 fix: matrix-only transforms no longer disappear.
        val adapter = PreviewEffectAdapter()
        val effect = FilterEffect(
            profileId = "color-lab",
            renderSpec = FilterRenderSpec(contrast = 1.4f),
            recipe = PerceptualColorRecipe(warmthBias = 0.4f, chromaBoost = 0.3f)
        )

        val transform = adapter.adapt(EffectSpec(listOf(effect))).colorTransform
        val spec = previewColorTransformOverlaySpec(transform)

        assertNotNull(spec)
        assertNotNull(spec.colorMatrix, "Spec matrix should be preserved on the overlay spec")
        assertFalse(PreviewColorMatrixBuilder.isIdentity(spec.colorMatrix))
    }

    @Test
    fun `identity transform with zero tint produces no overlay spec`() {
        val transform = PreviewColorTransform.IDENTITY

        assertNull(previewColorTransformOverlaySpec(transform),
            "Identity matrix with no tint must not produce an overlay spec")
    }
}
