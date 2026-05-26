package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FocalLengthSliderViewTest {

    // -- formatRatio --

    @Test
    fun `formatRatio shows one decimal place`() {
        assertEquals("1.0x", FocalLengthSliderView.formatRatio(1.0f))
        assertEquals("2.3x", FocalLengthSliderView.formatRatio(2.3f))
        assertEquals("0.5x", FocalLengthSliderView.formatRatio(0.5f))
        assertEquals("10.0x", FocalLengthSliderView.formatRatio(10.0f))
    }

    @Test
    fun `formatRatio rounds to one decimal`() {
        assertEquals("1.2x", FocalLengthSliderView.formatRatio(1.24f))
        assertEquals("1.3x", FocalLengthSliderView.formatRatio(1.25f))
    }

    // -- shouldSnap --

    @Test
    fun `shouldSnap returns true when exactly on preset`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        assertTrue(FocalLengthSliderView.shouldSnap(2.0f, presets))
    }

    @Test
    fun `shouldSnap returns true within threshold`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        // Neighbor distance = 1.0, threshold = 15% = 0.15
        // 2.05 is 0.05 from preset 2.0, well within 0.15
        assertTrue(FocalLengthSliderView.shouldSnap(2.05f, presets))
    }

    @Test
    fun `shouldSnap returns false beyond threshold`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        // 2.3 is 0.3 from preset 2.0, which is 30% of neighbor distance 1.0 — beyond 15%
        assertFalse(FocalLengthSliderView.shouldSnap(2.3f, presets))
    }

    @Test
    fun `shouldSnap returns true near min preset`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        assertTrue(FocalLengthSliderView.shouldSnap(1.05f, presets))
    }

    @Test
    fun `shouldSnap returns true near max preset`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        assertTrue(FocalLengthSliderView.shouldSnap(2.95f, presets))
    }

    @Test
    fun `shouldSnap returns false at midpoint between presets`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        // 1.5 is 0.5 from either preset — way beyond threshold
        assertFalse(FocalLengthSliderView.shouldSnap(1.5f, presets))
    }

    @Test
    fun `shouldSnap with single preset always snaps`() {
        val presets = listOf(2.0f)
        assertTrue(FocalLengthSliderView.shouldSnap(2.0f, presets))
        assertTrue(FocalLengthSliderView.shouldSnap(1.5f, presets))
    }

    @Test
    fun `shouldSnap with empty presets returns false`() {
        assertFalse(FocalLengthSliderView.shouldSnap(1.0f, emptyList()))
    }

    @Test
    fun `shouldSnap with uneven presets uses correct neighbor distance`() {
        // Presets: 1.0, 1.5, 4.0
        val presets = listOf(1.0f, 1.5f, 4.0f)
        // At 1.1: nearest=1.0, second=1.5, neighborDist=0.5, threshold=0.075
        // distance=0.1 > 0.075 → false
        assertFalse(FocalLengthSliderView.shouldSnap(1.1f, presets))
        // At 1.04: distance=0.04 < 0.075 → true
        assertTrue(FocalLengthSliderView.shouldSnap(1.04f, presets))
    }

    @Test
    fun `shouldSnap boundary is exclusive`() {
        val presets = listOf(1.0f, 2.0f, 3.0f)
        // Exactly at threshold: 0.15 * 1.0 = 0.15 distance
        // The check is `<`, not `<=`, so 1.15 should NOT snap
        assertFalse(FocalLengthSliderView.shouldSnap(1.15f, presets))
    }

    // -- Constants --

    @Test
    fun `snap threshold fraction is 15 percent`() {
        assertEquals(0.15f, FocalLengthSliderView.SNAP_THRESHOLD_FRACTION)
    }
}
