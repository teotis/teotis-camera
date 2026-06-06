package com.opencamera.app.camera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MaskAwarePortraitRenderMathTest {

    private fun smoothstep(edge0: Float, edge1: Float, value: Float): Float {
        if (edge0 == edge1) {
            return if (value >= edge1) 1f else 0f
        }
        val t = ((value - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    @Test
    fun `center subject mask produces high alpha in center`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val centerAlpha = mask.sampleAlpha(50, 40)
        assertTrue(centerAlpha > 0.8f, "Center alpha should be high, was $centerAlpha")
    }

    @Test
    fun `center subject mask produces low alpha at corners`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val cornerAlpha = mask.sampleAlpha(0, 0)
        assertTrue(cornerAlpha < 0.2f, "Corner alpha should be low, was $cornerAlpha")
    }

    @Test
    fun `center subject has high subject weight after smoothstep mapping`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val centerAlpha = mask.sampleAlpha(50, 40)
        val subjectWeight = smoothstep(0.15f, 0.85f, centerAlpha)
        assertTrue(subjectWeight > 0.8f, "Center subject weight should be high, was $subjectWeight")
    }

    @Test
    fun `corner has low subject weight after smoothstep mapping`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val cornerAlpha = mask.sampleAlpha(0, 0)
        val subjectWeight = smoothstep(0.15f, 0.85f, cornerAlpha)
        assertTrue(subjectWeight < 0.2f, "Corner subject weight should be low, was $subjectWeight")
    }

    @Test
    fun `center subject means less blur applied`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val centerAlpha = mask.sampleAlpha(50, 40)
        val subjectWeight = smoothstep(0.15f, 0.85f, centerAlpha)
        val blurMix = 1f - subjectWeight
        assertTrue(blurMix < 0.2f, "Center should have minimal blur, blurMix=$blurMix")
    }

    @Test
    fun `corner means more blur applied`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(100, 100)
        val cornerAlpha = mask.sampleAlpha(0, 0)
        val subjectWeight = smoothstep(0.15f, 0.85f, cornerAlpha)
        val blurMix = 1f - subjectWeight
        assertTrue(blurMix > 0.8f, "Corner should have heavy blur, blurMix=$blurMix")
    }

    @Test
    fun `mask alpha decreases from center to edge to corner`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(
            100, 100,
            subjectFractionX = 0.4f,
            subjectFractionY = 0.5f,
            edgeSoftness = 0.5f
        )
        val centerAlpha = mask.sampleAlpha(50, 40)
        val midAlpha = mask.sampleAlpha(98, 40)
        val cornerAlpha = mask.sampleAlpha(0, 0)

        assertTrue(centerAlpha > midAlpha, "Center alpha ($centerAlpha) should exceed mid ($midAlpha)")
        assertTrue(midAlpha > cornerAlpha, "Mid alpha ($midAlpha) should exceed corner ($cornerAlpha)")
    }

    @Test
    fun `left right split mask creates sharp subject boundary`() {
        val mask = SceneMaskTestUtils.createLeftRightSplitMask(100, 50, 0.5f)
        val leftAlpha = mask.sampleAlpha(25, 25)
        val rightAlpha = mask.sampleAlpha(75, 25)
        val leftWeight = smoothstep(0.15f, 0.85f, leftAlpha)
        val rightWeight = smoothstep(0.15f, 0.85f, rightAlpha)
        assertTrue(leftWeight > 0.9f, "Left side should be subject, weight=$leftWeight")
        assertTrue(rightWeight < 0.1f, "Right side should be background, weight=$rightWeight")
    }

    @Test
    fun `coordinate mapper correctly scales mask to target dimensions`() {
        val mapper = SceneMaskCoordinateMapper(
            maskWidth = 50,
            maskHeight = 50,
            targetWidth = 200,
            targetHeight = 200
        )
        assertEquals(25, mapper.maskX(100))
        assertEquals(25, mapper.maskY(100))
        assertEquals(0, mapper.maskX(0))
        assertEquals(0, mapper.maskY(0))
        assertEquals(49, mapper.maskX(200))
    }

    @Test
    fun `uniform mask produces consistent alpha across all pixels`() {
        val mask = SceneMaskTestUtils.createUniformMask(20, 20, 0.6f)
        for (y in 0 until 20) {
            for (x in 0 until 20) {
                val alpha = mask.sampleAlpha(x, y)
                assertEquals(0.6f, alpha, 0.01f, "Alpha at ($x,$y) should be 0.6")
            }
        }
    }
}
