package com.opencamera.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.settings.FilterRenderSpec
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PreviewOverlayViewColorMatrixTest {

    private fun newCanvas(): Canvas = Canvas()

    private fun grayscaleSpec(): FilterOverlaySpec = FilterOverlaySpec(
        tintColor = 0,
        tintAlpha = 0f,
        vignetteStrength = 0f,
        warmthShift = 0f,
        colorMatrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(monochromeMix = 1.0f))
    )

    @Test
    fun `grayscale matrix attaches ColorMatrixColorFilter to paint during draw`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(monochromeMix = 1.0f))
        assertNotNull(matrix)
        assertFalse(PreviewColorMatrixBuilder.isIdentity(matrix))

        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        val filter = paint.colorFilter
        assertNotNull(filter, "ColorMatrixColorFilter must be applied to the overlay paint")
        assertTrue(filter is ColorMatrixColorFilter,
            "Expected ColorMatrixColorFilter, got ${filter.javaClass.name}")
    }

    @Test
    fun `warmth matrix attaches ColorMatrixColorFilter and preserves direction`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(warmthShift = 12))
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter,
            "Warmth matrix must be consumed via ColorMatrixColorFilter")
        assertTrue(matrix!![4] > 0f, "R translation should be positive for warm shift")
        assertTrue(matrix[14] < 0f, "B translation should be negative for warm shift")
    }

    @Test
    fun `cool matrix attaches ColorMatrixColorFilter and preserves direction`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(warmthShift = -12))
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter,
            "Cool matrix must be consumed via ColorMatrixColorFilter")
        assertTrue(matrix!![4] < 0f, "R translation should be negative for cool shift")
        assertTrue(matrix[14] > 0f, "B translation should be positive for cool shift")
    }

    @Test
    fun `contrast matrix attaches ColorMatrixColorFilter with scaled diagonal`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(contrast = 1.5f))
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter,
            "Contrast matrix must be consumed via ColorMatrixColorFilter")
        assertEquals(1.5f, matrix!![0], 0.001f, "Contrast should scale R diagonal")
        assertEquals(1.5f, matrix[6], 0.001f, "Contrast should scale G diagonal")
        assertEquals(1.5f, matrix[12], 0.001f, "Contrast should scale B diagonal")
    }

    @Test
    fun `matrix-only overlay with zero tint consumes matrix and uses zero alpha`() {
        val spec = grayscaleSpec()
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter,
            "Matrix must be consumed via ColorMatrixColorFilter even without tint")
        assertEquals(0, paint.alpha,
            "Matrix-only overlay must draw with alpha 0 to avoid polluting the preview")
    }

    @Test
    fun `no matrix and no tint leaves paint color filter untouched`() {
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = null
        )
        val paint = Paint()
        val initialFilter = paint.colorFilter

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertEquals(initialFilter, paint.colorFilter,
            "No-op spec must not modify the paint's color filter")
    }

    @Test
    fun `identity matrix is treated as no matrix`() {
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = PreviewColorMatrixBuilder.IDENTITY.copyOf()
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertNull(paint.colorFilter,
            "Identity matrix should not attach a ColorMatrixColorFilter")
    }

    @Test
    fun `tint-only spec draws without color filter`() {
        val spec = FilterOverlaySpec(
            tintColor = Color.RED,
            tintAlpha = 0.5f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = null
        )
        val paint = Paint()

        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertNull(paint.colorFilter,
            "Tint-only spec must not attach a color filter")
        assertEquals(Color.RED, paint.color,
            "Tint-only spec must use the tint color on the paint")
    }

    // ── Pixel-level assertion (pure matrix math, agent-verifiable) ──────────────────
    //
    // Robolectric's default Canvas shadow records draw calls but does not render
    // pixels to bitmaps. To satisfy the "at least one non-tint transform pixel-level
    // assertion" acceptance criterion in an agent-verifiable way, we apply the matrix
    // attached to the overlay paint to a known input color via the same 4x5 row-major
    // math that ColorMatrixColorFilter uses, and verify the matrix that reaches the
    // paint would transform a red input pixel into a near-gray pixel.

    @Test
    fun `grayscale matrix attached to paint produces gray pixel from red input`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(monochromeMix = 1.0f))
        assertNotNull(matrix)

        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()
        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter,
            "Matrix must be attached to the paint before we evaluate its pixel effect")

        // Apply the 4x5 matrix to Color.RED (255, 0, 0, 255).
        val rIn = 255f
        val gIn = 0f
        val bIn = 0f
        val aIn = 255f
        val rOut = matrix[0] * rIn + matrix[1] * gIn +
            matrix[2] * bIn + matrix[3] * aIn + matrix[4]
        val gOut = matrix[5] * rIn + matrix[6] * gIn +
            matrix[7] * bIn + matrix[8] * aIn + matrix[9]
        val bOut = matrix[10] * rIn + matrix[11] * gIn +
            matrix[12] * bIn + matrix[13] * aIn + matrix[14]

        // Saturation=0 collapses R/G/B to luminance: 0.213*255 ≈ 54.
        assertTrue(abs(rOut - gOut) <= 1f && abs(gOut - bOut) <= 1f,
            "Grayscale matrix should collapse channels, got r=$rOut g=$gOut b=$bOut")
        assertTrue(rOut in 40f..70f,
            "Grayscale luminance of red should be ~54, got $rOut")
    }

    @Test
    fun `warmth matrix attached to paint shifts gray pixel toward warm`() {
        val matrix = PreviewColorMatrixBuilder.buildMatrix(FilterRenderSpec(warmthShift = 30))
        val spec = FilterOverlaySpec(
            tintColor = 0,
            tintAlpha = 0f,
            vignetteStrength = 0f,
            warmthShift = 0f,
            colorMatrix = matrix
        )
        val paint = Paint()
        drawColorTransformOverlay(newCanvas(), spec, RectF(0f, 0f, 32f, 32f), paint)

        assertTrue(paint.colorFilter is ColorMatrixColorFilter)

        // Apply to mid-gray (128, 128, 128, 255).
        val rIn = 128f
        val gIn = 128f
        val bIn = 128f
        val aIn = 255f
        val rOut = matrix!![0] * rIn + matrix[1] * gIn +
            matrix[2] * bIn + matrix[3] * aIn + matrix[4]
        val bOut = matrix[10] * rIn + matrix[11] * gIn +
            matrix[12] * bIn + matrix[13] * aIn + matrix[14]

        assertTrue(rOut > 128f, "Warm matrix should raise red above 128, got $rOut")
        assertTrue(bOut < 128f, "Warm matrix should lower blue below 128, got $bOut")
    }
}
