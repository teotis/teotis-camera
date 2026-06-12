package com.opencamera.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class BlueHourSceneMetricsTest {

    // Helper to create a uniform pixel with given RGB values
    private fun rgb(r: Int, g: Int, b: Int): Int =
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b

    // Helper to create a uniform pixel array
    private fun uniformPixels(r: Int, g: Int, b: Int, count: Int = 1024): IntArray =
        IntArray(count) { rgb(r, g, b) }

    @Test
    fun `analyzePixels returns null for empty array`() {
        assertNull(BlueHourSceneMetrics.analyzePixels(intArrayOf()))
    }

    @Test
    fun `uniform black pixels classify as LOW_LIGHT`() {
        val pixels = uniformPixels(0, 0, 0)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertNotNull(metrics)
        assertEquals(SceneLightState.LOW_LIGHT, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `uniform white pixels classify as NORMAL`() {
        val pixels = uniformPixels(255, 255, 255)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(SceneLightState.NORMAL, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `uniform mid-gray pixels classify as NORMAL`() {
        // RGB 128,128,128 -> luma ~0.5 -> above NORMAL threshold
        val pixels = uniformPixels(128, 128, 128)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(SceneLightState.NORMAL, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `dark blue pixels classify as LOW_LIGHT`() {
        // RGB 0,0,40 -> luma very low -> LOW_LIGHT
        val pixels = uniformPixels(0, 0, 40)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(SceneLightState.LOW_LIGHT, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `blue-hour-like blue cyan pixels with highlights classify as BLUE_HOUR`() {
        // Create a mix: 40% blue pixels (RGB 40,80,140) + 10% bright highlights (RGB 255,255,255) + 50% dark (RGB 10,10,10)
        val blueCount = 409
        val highlightCount = 102
        val darkCount = 513
        val pixels = IntArray(1024)
        repeat(blueCount) { pixels[it] = rgb(40, 80, 140) }
        repeat(highlightCount) { pixels[blueCount + it] = rgb(255, 255, 255) }
        repeat(darkCount) { pixels[blueCount + highlightCount + it] = rgb(10, 10, 10) }

        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertNotNull(metrics)
        // Luma: blue pixels ~0.30, highlights ~1.0, dark ~0.016
        // Weighted: (409*0.30 + 102*1.0 + 513*0.016) / 1024 ≈ 0.22
        // Blue/cyan ratio: blue pixels counted as blue/cyan -> ~409/1024 ≈ 0.40
        // Highlight ratio: 102/1024 ≈ 0.10
        assertEquals(SceneLightState.BLUE_HOUR, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `blue pixels without highlights do not classify as BLUE_HOUR`() {
        // All blue pixels, no highlights, moderate luma
        val pixels = uniformPixels(30, 60, 130)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        // High blueCyanRatio but highlightRatio = 0 -> not BLUE_HOUR
        val state = BlueHourSceneMetrics.classify(metrics)
        // Could be LOW_LIGHT or UNKNOWN depending on exact luma, but NOT BLUE_HOUR
        assertEquals(false, state == SceneLightState.BLUE_HOUR)
    }

    @Test
    fun `low luma with high blue ratio classifies as LOW_LIGHT not BLUE_HOUR`() {
        // Very dark blue: luma < 0.08 (below BLUE_HOUR_LUMA_MIN)
        val pixels = uniformPixels(0, 0, 20)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(SceneLightState.LOW_LIGHT, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `blue-hour metrics have non-zero confidence`() {
        val blueCount = 409
        val highlightCount = 102
        val darkCount = 513
        val pixels = IntArray(1024)
        repeat(blueCount) { pixels[it] = rgb(40, 80, 140) }
        repeat(highlightCount) { pixels[blueCount + it] = rgb(255, 255, 255) }
        repeat(darkCount) { pixels[blueCount + highlightCount + it] = rgb(10, 10, 10) }

        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(true, metrics.confidence > 0f)
    }

    @Test
    fun `analyzePixels computes correct averageLuma for uniform pixels`() {
        // RGB 100,100,100 -> luma = 0.2126*100/255 + 0.7152*100/255 + 0.0722*100/255 = 100/255 ≈ 0.392
        val pixels = uniformPixels(100, 100, 100)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(true, metrics.averageLuma > 0.38f && metrics.averageLuma < 0.40f)
    }

    @Test
    fun `analyzePixels computes correct blueCyanRatio for all-blue pixels`() {
        // Pure blue (0,0,255): isBlueDominated = true since b > r and b > g*0.8
        val pixels = uniformPixels(0, 0, 255)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(1.0f, metrics.blueCyanRatio)
    }

    @Test
    fun `analyzePixels computes zero blueCyanRatio for warm pixels`() {
        // Warm orange: R dominant, not blue or cyan
        val pixels = uniformPixels(200, 100, 30)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(0.0f, metrics.blueCyanRatio)
    }

    @Test
    fun `analyzePixels computes correct highlightRatio for bright pixels`() {
        // RGB 255,255,255 -> luma = 1.0 > 0.70 -> highlight
        val pixels = uniformPixels(255, 255, 255)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(1.0f, metrics.highlightRatio)
    }

    @Test
    fun `analyzePixels computes zero highlightRatio for dark pixels`() {
        val pixels = uniformPixels(50, 50, 50)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(0.0f, metrics.highlightRatio)
    }

    @Test
    fun `mixed warm scene without blue dominance classifies as NORMAL`() {
        // 80% warm daylight (180,180,160) + 20% sky reflection (100,140,200)
        // Sky reflection (100,140,200) has b>r so counts as blue/cyan,
        // but 20% keeps total blueCyanRatio < 0.30 threshold
        val warmCount = 819
        val coolCount = 205
        val pixels = IntArray(1024)
        repeat(warmCount) { pixels[it] = rgb(180, 180, 160) }
        repeat(coolCount) { pixels[warmCount + it] = rgb(100, 140, 200) }

        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(SceneLightState.NORMAL, BlueHourSceneMetrics.classify(metrics))
    }

    @Test
    fun `blue-hour requires all three conditions luma blue cyan highlight`() {
        // High luma + high blue ratio but no highlights -> should not be BLUE_HOUR
        val pixels = IntArray(1024) { rgb(60, 80, 140) }
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        // Luma ≈ 0.25 (in range), high blue ratio, but highlight ratio = 0
        assertEquals(false, BlueHourSceneMetrics.classify(metrics) == SceneLightState.BLUE_HOUR)
    }

    @Test
    fun `existing computeAverageLuma behavior preserved through analyzePixels`() {
        // The old implementation used the same ITU-R BT.709 formula
        // Verify: RGB 128,128,128 -> luma ≈ 0.502
        val pixels = uniformPixels(128, 128, 128, 1024)
        val metrics = BlueHourSceneMetrics.analyzePixels(pixels)!!
        assertEquals(true, metrics.averageLuma > 0.50f && metrics.averageLuma < 0.51f)
    }
}
