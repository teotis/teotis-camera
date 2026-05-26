package com.opencamera.app.gesture

import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ZoomScaleMapperTest {

    @Test
    fun `discrete preset snaps to nearest ratio at start`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)

        val result = mapper.mapPositionToRatio(0f)
        assertEquals(1f, result.targetRatio)
        assertTrue(result.snappedToPreset)
    }

    @Test
    fun `discrete preset snaps to nearest ratio at end`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)

        val result = mapper.mapPositionToRatio(100f)
        assertEquals(5f, result.targetRatio)
        assertTrue(result.snappedToPreset)
    }

    @Test
    fun `discrete preset snaps to middle ratio at center`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)

        val result = mapper.mapPositionToRatio(50f)
        assertEquals(2f, result.targetRatio)
        assertTrue(result.snappedToPreset)
    }

    @Test
    fun `continuous maps linearly between min and max`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.CONTINUOUS,
            supportedRatios = listOf(1f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 0f, 100f)

        val start = mapper.mapPositionToRatio(0f)
        assertEquals(1f, start.targetRatio)
        assertFalse(start.snappedToPreset)

        val end = mapper.mapPositionToRatio(100f)
        assertEquals(5f, end.targetRatio)
    }

    @Test
    fun `position outside strip is clamped`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 10f, 90f)

        val beforeStart = mapper.mapPositionToRatio(-10f)
        assertEquals(1f, beforeStart.targetRatio)

        val afterEnd = mapper.mapPositionToRatio(200f)
        assertEquals(5f, afterEnd.targetRatio)
    }

    @Test
    fun `zero-width strip returns first preset`() {
        val capability = ZoomRatioCapability(
            support = ZoomControlSupport.DISCRETE_PRESET,
            supportedRatios = listOf(1f, 2f, 5f),
            defaultRatio = 1f
        )
        val mapper = ZoomScaleMapper(capability, 50f, 50f)

        val result = mapper.mapPositionToRatio(50f)
        assertEquals(1f, result.targetRatio)
    }
}
