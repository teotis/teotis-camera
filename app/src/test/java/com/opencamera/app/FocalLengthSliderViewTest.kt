package com.opencamera.app

import android.view.View.MeasureSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
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

    @Test
    fun `resting slider measures compactly without reserving floating label space`() {
        val view = FocalLengthSliderView(RuntimeEnvironment.getApplication())
        view.setPresetRatios(listOf(0.7f, 1.0f, 2.0f, 5.0f))
        view.setCurrentRatio(1.0f)

        view.measure(
            MeasureSpec.makeMeasureSpec(360, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        )

        val heightDp = view.measuredHeight / view.resources.displayMetrics.density
        assertTrue(heightDp in 36f..44f, "heightDp=$heightDp")
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
    fun `shouldSnap does not create huge snap zones from large zoom gaps`() {
        val presets = listOf(0.7f, 1.0f, 2.0f, 5.0f, 10.0f)

        assertFalse(FocalLengthSliderView.shouldSnap(5.3f, presets))
        assertFalse(FocalLengthSliderView.shouldSnap(1.86f, presets))
        assertTrue(FocalLengthSliderView.shouldSnap(9.94f, presets))
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

    @Test
    fun `snap threshold has absolute zoom delta cap`() {
        assertEquals(0.08f, FocalLengthSliderView.SNAP_THRESHOLD_MAX_RATIO_DELTA)
    }

    // -- formatCompactNodeLabel --

    @Test
    fun `formatCompactNodeLabel shows integer ratios without decimal`() {
        assertEquals("1x", FocalLengthSliderView.formatCompactNodeLabel(1.0f))
        assertEquals("2x", FocalLengthSliderView.formatCompactNodeLabel(2.0f))
        assertEquals("5x", FocalLengthSliderView.formatCompactNodeLabel(5.0f))
        assertEquals("10x", FocalLengthSliderView.formatCompactNodeLabel(10.0f))
    }

    @Test
    fun `formatCompactNodeLabel shows fractional ratios with one decimal`() {
        assertEquals("0.6", FocalLengthSliderView.formatCompactNodeLabel(0.6f))
        assertEquals("1.5", FocalLengthSliderView.formatCompactNodeLabel(1.5f))
        assertEquals("2.5", FocalLengthSliderView.formatCompactNodeLabel(2.5f))
        assertEquals("3.3", FocalLengthSliderView.formatCompactNodeLabel(3.3f))
    }

    @Test
    fun `formatCompactNodeLabel rounds to one decimal`() {
        assertEquals("1.3", FocalLengthSliderView.formatCompactNodeLabel(1.25f))
        assertEquals("2.8", FocalLengthSliderView.formatCompactNodeLabel(2.76f))
    }

    // -- shouldSuppressExternalUpdate (drag latch) --

    @Test
    fun `shouldSuppressExternalUpdate returns true during active drag`() {
        assertTrue(FocalLengthSliderView.shouldSuppressExternalUpdate(true))
    }

    @Test
    fun `shouldSuppressExternalUpdate returns false when not dragging`() {
        assertFalse(FocalLengthSliderView.shouldSuppressExternalUpdate(false))
    }

    @Test
    fun `nearestPresetNearTap chooses closest dot when 07 and 10 preset hit zones overlap`() {
        val presets = listOf(0.7f, 1.0f, 2.0f, 5.0f, 10.0f)
        val targetX = 10f
        val dotTapRadius = 32f
        val resolved = FocalLengthSliderView.nearestPresetNearTap(
            tapX = targetX,
            presets = presets,
            trackLeft = 0f,
            trackWidth = 100f,
            minRatio = 0.7f,
            maxRatio = 10.0f,
            dotTapRadiusPx = dotTapRadius
        )

        assertEquals(1.0f, resolved)
    }
}
