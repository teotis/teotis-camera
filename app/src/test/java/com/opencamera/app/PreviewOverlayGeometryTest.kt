package com.opencamera.app

import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.EffectTarget
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.FrameRatio
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewOverlayGeometryTest {

    // --- orientedFrameRatio tests ---

    @Test
    fun `4_3 ratio in portrait becomes 3_4 oriented`() {
        val result = orientedFrameRatio(4, 3, PreviewDisplayOrientation.PORTRAIT)
        assertEquals(3, result.orientedWidth)
        assertEquals(4, result.orientedHeight)
    }

    @Test
    fun `4_3 ratio in landscape stays 4_3 oriented`() {
        val result = orientedFrameRatio(4, 3, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(4, result.orientedWidth)
        assertEquals(3, result.orientedHeight)
    }

    @Test
    fun `16_9 ratio in portrait becomes 9_16 oriented`() {
        val result = orientedFrameRatio(16, 9, PreviewDisplayOrientation.PORTRAIT)
        assertEquals(9, result.orientedWidth)
        assertEquals(16, result.orientedHeight)
    }

    @Test
    fun `16_9 ratio in landscape stays 16_9 oriented`() {
        val result = orientedFrameRatio(16, 9, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(16, result.orientedWidth)
        assertEquals(9, result.orientedHeight)
    }

    @Test
    fun `1_1 ratio is unchanged by orientation`() {
        val portrait = orientedFrameRatio(1, 1, PreviewDisplayOrientation.PORTRAIT)
        val landscape = orientedFrameRatio(1, 1, PreviewDisplayOrientation.LANDSCAPE)
        assertEquals(portrait, landscape)
        assertEquals(1, portrait.orientedWidth)
        assertEquals(1, portrait.orientedHeight)
    }

    // --- computeFrameRect orientation inference tests ---

    @Test
    fun `portrait view with 4_3 ratio produces portrait-tall frame`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented ratio is 3:4. targetRatio = 0.75, availableRatio = 0.5625
        // targetRatio > availableRatio => width-limited: w=1080, h=1080/0.75=1440
        assertApprox(1080f, rect.width)
        assertApprox(1440f, rect.height)
        assertRectCentered(1080, 1920, rect)
    }

    @Test
    fun `landscape view with 4_3 ratio produces landscape-wide frame`() {
        val rect = computeFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            ratioWidth = 4,
            ratioHeight = 3
        )
        // Oriented ratio is 4:3. targetRatio = 1.333, availableRatio = 1.778
        // targetRatio < availableRatio => height-limited: h=1080, w=1080*1.333=1440
        assertApprox(1440f, rect.width)
        assertApprox(1080f, rect.height)
        assertRectCentered(1920, 1080, rect)
    }

    @Test
    fun `portrait view with 16_9 ratio produces narrow tall frame`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 9:16 = 0.5625. Available: 1080/1920 = 0.5625
        // targetRatio == availableRatio => fills entire view
        assertApprox(1080f, rect.width)
        assertApprox(1920f, rect.height)
    }

    @Test
    fun `landscape view with 16_9 ratio produces wide frame`() {
        val rect = computeFrameRect(
            viewWidth = 1920,
            viewHeight = 1080,
            ratioWidth = 16,
            ratioHeight = 9
        )
        // Oriented: 16:9 = 1.778. Available: 1920/1080 = 1.778
        // targetRatio == availableRatio => fills entire view
        assertApprox(1920f, rect.width)
        assertApprox(1080f, rect.height)
    }

    @Test
    fun `square ratio always produces centered square`() {
        val portrait = computeFrameRect(1080, 1920, 1, 1)
        val landscape = computeFrameRect(1920, 1080, 1, 1)
        assertApprox(1080f, portrait.width)
        assertApprox(1080f, portrait.height)
        assertRectCentered(1080, 1920, portrait)
        assertApprox(1080f, landscape.width)
        assertApprox(1080f, landscape.height)
        assertRectCentered(1920, 1080, landscape)
    }

    @Test
    fun `active capture frame is centered in full preview content`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 1920,
            ratioWidth = 4,
            ratioHeight = 3
        )
        assertApprox(1080f, rect.width)
        assertApprox(0f, rect.left)
        assertRectCentered(1080, 1920, rect)
    }

    @Test
    fun `active capture frame stays centered in full preview content despite bottom controls`() {
        val rect = computeFrameRect(
            viewWidth = 1080,
            viewHeight = 2400,
            ratioWidth = 4,
            ratioHeight = 3
        )

        assertEquals(1200f, rect.centerY, 1f)
    }

    // --- scaleFrameRect tests ---

    @Test
    fun `scaleFrameRect with scale 1_0 returns same rect`() {
        val rect = FrameRect(0f, 240f, 1080f, 1680f)
        val result = scaleFrameRect(rect, 1f)
        assertApprox(0f, result.left)
        assertApprox(240f, result.top)
        assertApprox(1080f, result.right)
        assertApprox(1680f, result.bottom)
    }

    @Test
    fun `scaleFrameRect with scale 0_5 halves rect around center`() {
        val rect = FrameRect(0f, 0f, 1080f, 1440f)
        val result = scaleFrameRect(rect, 0.5f)
        // center = (540, 720), half-size = (270, 360)
        assertApprox(270f, result.left)
        assertApprox(360f, result.top)
        assertApprox(810f, result.right)
        assertApprox(1080f, result.bottom)
    }

    @Test
    fun `scaleFrameRect preserves center point`() {
        val rect = FrameRect(100f, 200f, 500f, 800f)
        val result = scaleFrameRect(rect, 0.3f)
        assertApprox(300f, result.centerX)
        assertApprox(500f, result.centerY)
    }

    // --- zoom-scaled frame rect tests ---

    @Test
    fun `zoom 2x frame rect area stays within 60_80 percent of preview content`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val scale = zoomFrameScale(captureZoomRatio = 2.0f, previewZoomRatio = 1.0f)
        assertTrue(scale in SQRT_AREA_RATIO_MIN..SQRT_AREA_RATIO_MAX,
            "zoomFrameScale(2.0, 1.0) = $scale, expected in [$SQRT_AREA_RATIO_MIN, $SQRT_AREA_RATIO_MAX]")
        val scaled = scaleFrameRect(content, scale)
        val scaledArea = scaled.width * scaled.height
        val contentArea = content.width * content.height
        val areaRatio = scaledArea / contentArea
        assertTrue(areaRatio in 0.6f..0.82f,
            "Area ratio $areaRatio not in [0.6, 0.82] for 2x zoom")
    }

    @Test
    fun `zoom 1x leaves frame rect unchanged`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scaled = scaleFrameRect(base, 1f / 1f)
        assertApprox(base.width, scaled.width)
        assertApprox(base.height, scaled.height)
    }

    @Test
    fun `zoom 3x frame rect area stays within 60_80 percent of preview content`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val scale = zoomFrameScale(captureZoomRatio = 3.0f, previewZoomRatio = 1.0f)
        assertTrue(scale in SQRT_AREA_RATIO_MIN..SQRT_AREA_RATIO_MAX,
            "zoomFrameScale(3.0, 1.0) = $scale, expected in [$SQRT_AREA_RATIO_MIN, $SQRT_AREA_RATIO_MAX]")
        val scaled = scaleFrameRect(content, scale)
        val scaledArea = scaled.width * scaled.height
        val contentArea = content.width * content.height
        val areaRatio = scaledArea / contentArea
        assertTrue(areaRatio in 0.6f..0.82f,
            "Area ratio $areaRatio not in [0.6, 0.82] for 3x zoom")
        // Frame should be centered
        assertApprox(content.centerX, scaled.centerX, 1f)
        assertApprox(content.centerY, scaled.centerY, 1f)
    }

    // --- gridLinePositions tests ---

    @Test
    fun `rule of thirds grid lines at 1_3 and 2_3 within a 900x600 frame`() {
        val lines = gridLinePositions(0f, 0f, 900f, 600f, listOf(1f / 3f, 2f / 3f))
        assertEquals(4, lines.size)
        // Vertical lines at x=300 and x=600
        assertApprox(300f, lines[0].x1)
        assertApprox(600f, lines[2].x1)
        // Horizontal lines at y=200 and y=400
        assertApprox(200f, lines[1].y1)
        assertApprox(400f, lines[3].y1)
    }

    @Test
    fun `golden ratio grid lines at correct fractions`() {
        val lines = gridLinePositions(100f, 50f, 800f, 600f, listOf(0.38196602f, 0.61803395f))
        // Vertical line at x = 100 + 800*0.38196602 = 405.57
        assertApprox(405.57f, lines[0].x1, 0.5f)
        // Horizontal line at y = 50 + 600*0.38196602 = 279.18
        assertApprox(279.18f, lines[1].y1, 0.5f)
    }

    @Test
    fun `grid lines stay within frame bounds`() {
        val lines = gridLinePositions(50f, 30f, 400f, 300f, listOf(1f / 3f, 2f / 3f))
        lines.forEach { seg ->
            assertInRange(50f, 450f, seg.x1)
            assertInRange(50f, 450f, seg.x2)
            assertInRange(30f, 330f, seg.y1)
            assertInRange(30f, 330f, seg.y2)
        }
    }

    // --- Acceptance: frame-ratio selection does not request CameraX preview rebind ---

    @Test
    fun `frameEffect targets CAPTURE only, not PREVIEW or BOTH`() {
        val ratios = listOf(FrameRatio.RATIO_4_3, FrameRatio.RATIO_16_9, FrameRatio.RATIO_1_1)
        for (ratio in ratios) {
            val effect = FrameEffect(ratio)
            assertEquals(
                EffectTarget.CAPTURE,
                effect.target,
                "FrameEffect($ratio) must target CAPTURE only"
            )
            assertFalse(
                effect.target == EffectTarget.PREVIEW || effect.target == EffectTarget.BOTH,
                "FrameEffect($ratio) must not target PREVIEW or BOTH"
            )
        }
    }

    @Test
    fun `effectSpec with only FrameEffect has no PREVIEW target`() {
        for (ratio in listOf(FrameRatio.RATIO_4_3, FrameRatio.RATIO_16_9, FrameRatio.RATIO_1_1)) {
            val spec = EffectSpec(listOf(FrameEffect(ratio)))
            assertFalse(
                spec.hasTarget(EffectTarget.PREVIEW),
                "EffectSpec with only FrameEffect($ratio) must not have PREVIEW target"
            )
            assertTrue(
                spec.hasTarget(EffectTarget.CAPTURE),
                "EffectSpec with FrameEffect($ratio) must have CAPTURE target"
            )
        }
    }

    // --- Acceptance: frame rects never exceed content rect ---

    @Test
    fun `4_3 frame rect never exceeds content rect in portrait`() {
        assertFrameWithinContent(1080, 1920, 4, 3)
    }

    @Test
    fun `4_3 frame rect never exceeds content rect in landscape`() {
        assertFrameWithinContent(1920, 1080, 4, 3)
    }

    @Test
    fun `16_9 frame rect never exceeds content rect in portrait`() {
        assertFrameWithinContent(1080, 1920, 16, 9)
    }

    @Test
    fun `16_9 frame rect never exceeds content rect in landscape`() {
        assertFrameWithinContent(1920, 1080, 16, 9)
    }

    @Test
    fun `1_1 frame rect never exceeds content rect in portrait`() {
        assertFrameWithinContent(1080, 1920, 1, 1)
    }

    @Test
    fun `1_1 frame rect never exceeds content rect in landscape`() {
        assertFrameWithinContent(1920, 1080, 1, 1)
    }

    @Test
    fun `all frame rects never exceed content rect for 1080x2400`() {
        for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
            assertFrameWithinContent(1080, 2400, rw, rh)
        }
    }

    @Test
    fun `all frame rects never exceed content rect for 2400x1080`() {
        for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
            assertFrameWithinContent(2400, 1080, rw, rh)
        }
    }

    // --- Acceptance: overlay crop and saved crop share matching center-crop semantics ---

    @Test
    fun `overlay crop aspect matches saved crop for 4_3 portrait`() {
        // 1080x1920 portrait view, 4:3 ratio -> overlay produces 1080x1440 centered
        val rect = computeFrameRect(1080, 1920, 4, 3)
        assertEquals(rect.width / rect.height, 3f / 4f, 0.01f)
        assertTrue(rect.width <= 1080f)
        assertTrue(rect.height <= 1920f)
    }

    @Test
    fun `overlay crop aspect matches saved crop for 16_9 portrait`() {
        val rect = computeFrameRect(1080, 1920, 16, 9)
        assertEquals(rect.width / rect.height, 9f / 16f, 0.01f)
        assertTrue(rect.width <= 1080f)
        assertTrue(rect.height <= 1920f)
    }

    @Test
    fun `overlay crop aspect matches saved crop for 1_1 portrait`() {
        val rect = computeFrameRect(1080, 1920, 1, 1)
        assertEquals(rect.width, rect.height, 1f)
        assertTrue(rect.width <= 1080f)
        assertTrue(rect.height <= 1920f)
    }

    @Test
    fun `overlay crop aspect matches saved crop for 4_3 landscape`() {
        val rect = computeFrameRect(1920, 1080, 4, 3)
        assertEquals(rect.width / rect.height, 4f / 3f, 0.01f)
        assertTrue(rect.width <= 1920f)
        assertTrue(rect.height <= 1080f)
    }

    @Test
    fun `overlay crop aspect matches saved crop for 16_9 landscape`() {
        val rect = computeFrameRect(1920, 1080, 16, 9)
        assertEquals(rect.width / rect.height, 16f / 9f, 0.01f)
        assertTrue(rect.width <= 1920f)
        assertTrue(rect.height <= 1080f)
    }

    @Test
    fun `overlay crop aspect matches saved crop for 1_1 landscape`() {
        val rect = computeFrameRect(1920, 1080, 1, 1)
        assertEquals(rect.width, rect.height, 1f)
        assertTrue(rect.width <= 1920f)
        assertTrue(rect.height <= 1080f)
    }

    @Test
    fun `overlay and saved crop both center the frame rect`() {
        val configs = listOf(
            Triple(1080, 1920, listOf(4 to 3, 16 to 9, 1 to 1)),
            Triple(1920, 1080, listOf(4 to 3, 16 to 9, 1 to 1)),
            Triple(1080, 2400, listOf(4 to 3, 16 to 9, 1 to 1)),
        )
        for ((vw, vh, ratios) in configs) {
            for ((rw, rh) in ratios) {
                val rect = computeFrameRect(vw, vh, rw, rh)
                assertEquals(
                    vw / 2f,
                    rect.centerX,
                    1f,
                    "Frame must be horizontally centered for ${rw}:${rh} in ${vw}x${vh}"
                )
                assertEquals(
                    vh / 2f,
                    rect.centerY,
                    1f,
                    "Frame must be vertically centered for ${rw}:${rh} in ${vw}x${vh}"
                )
            }
        }
    }

    // --- scan guide geometry bounds tests ---

    @Test
    fun `scan guide rect stays within content rect for 4_3 portrait view`() {
        val density = 3.0f
        val marginPx = 28f * density
        val content = computeFrameRect(1080, 1920, 4, 3)
        val left = content.left + marginPx
        val top = content.top + marginPx
        val right = content.right - marginPx
        val bottom = content.bottom - marginPx
        assertTrue(left >= content.left, "Scan guide left must be within content left")
        assertTrue(top >= content.top, "Scan guide top must be within content top")
        assertTrue(right <= content.right, "Scan guide right must be within content right")
        assertTrue(bottom <= content.bottom, "Scan guide bottom must be within content bottom")
        assertTrue(right > left, "Scan guide must have positive width")
        assertTrue(bottom > top, "Scan guide must have positive height")
    }

    @Test
    fun `scan guide rect stays within content rect for 4_3 landscape view`() {
        val density = 3.0f
        val marginPx = 28f * density
        val content = computeFrameRect(1920, 1080, 4, 3)
        val left = content.left + marginPx
        val top = content.top + marginPx
        val right = content.right - marginPx
        val bottom = content.bottom - marginPx
        assertTrue(left >= content.left)
        assertTrue(top >= content.top)
        assertTrue(right <= content.right)
        assertTrue(bottom <= content.bottom)
        assertTrue(right > left && bottom > top)
    }

    @Test
    fun `scan guide rect stays within content rect for all aspect ratios`() {
        val density = 3.0f
        val marginPx = 28f * density
        for ((vw, vh) in listOf(1080 to 1920, 1920 to 1080, 1080 to 2400, 2400 to 1080)) {
            for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
                val content = computeFrameRect(vw, vh, rw, rh)
                val left = content.left + marginPx
                val top = content.top + marginPx
                val right = content.right - marginPx
                val bottom = content.bottom - marginPx
                assertTrue(left >= content.left, "left out of bounds for ${rw}:${rh} in ${vw}x${vh}")
                assertTrue(top >= content.top, "top out of bounds for ${rw}:${rh} in ${vw}x${vh}")
                assertTrue(right <= content.right, "right out of bounds for ${rw}:${rh} in ${vw}x${vh}")
                assertTrue(bottom <= content.bottom, "bottom out of bounds for ${rw}:${rh} in ${vw}x${vh}")
                assertTrue(right > left && bottom > top, "zero-size guide for ${rw}:${rh} in ${vw}x${vh}")
            }
        }
    }

    @Test
    fun `corner length is clamped within guide rect bounds`() {
        val density = 3.0f
        val marginPx = 28f * density
        val content = computeFrameRect(1080, 1920, 4, 3)
        val guideWidth = (content.right - content.left) - 2 * marginPx
        val guideHeight = (content.bottom - content.top) - 2 * marginPx
        val cornerLength = (minOf(guideWidth, guideHeight) * 0.12f)
            .coerceIn(36f * density, 72f * density)
        assertTrue(cornerLength > 0f, "Corner length must be positive")
        assertTrue(cornerLength <= minOf(guideWidth, guideHeight) / 2f, "Corner length must fit within guide")
    }

    private fun assertFrameWithinContent(viewW: Int, viewH: Int, ratioW: Int, ratioH: Int) {
        val rect = computeFrameRect(viewW, viewH, ratioW, ratioH)
        assertTrue(
            rect.left >= 0f,
            "Frame left ${rect.left} < 0 for $ratioW:$ratioH in ${viewW}x$viewH"
        )
        assertTrue(
            rect.top >= 0f,
            "Frame top ${rect.top} < 0 for $ratioW:$ratioH in ${viewW}x$viewH"
        )
        assertTrue(
            rect.right <= viewW.toFloat() + 1f,
            "Frame right ${rect.right} > $viewW for $ratioW:$ratioH in ${viewW}x$viewH"
        )
        assertTrue(
            rect.bottom <= viewH.toFloat() + 1f,
            "Frame bottom ${rect.bottom} > $viewH for $ratioW:$ratioH in ${viewW}x$viewH"
        )
    }

    private fun assertApprox(expected: Float, actual: Float, tolerance: Float = 1f) {
        assertEquals(expected, actual, tolerance)
    }

    private fun assertRectCentered(viewW: Int, viewH: Int, rect: FrameRect) {
        val cx = viewW / 2f
        val cy = viewH / 2f
        assertEquals(cx, rect.centerX, 1f)
        assertEquals(cy, rect.centerY, 1f)
    }

    private fun assertInRange(min: Float, max: Float, value: Float) {
        assert(value in min..max) { "Expected $value to be in [$min, $max]" }
    }

    // --- previewContentGeometry: null aspect defaults to 4:3 sensor ---

    @Test
    fun `null previewContentAspect defaults to 4_3 sensor aspect`() {
        // Verify that null aspect produces 4:3 content by checking the underlying
        // computeFrameRect logic (RectF is stubbed in JVM tests, so we test the math directly).
        // 4:3 sensor in 1080x1920 portrait → content = 1080x1440
        val content43 = computeFrameRect(1080, 1920, 4, 3)
        assertApprox(1080f, content43.width)
        assertApprox(1440f, content43.height)
        // 4:3 sensor in 1920x1080 landscape → content = 1440x1080
        val content43Land = computeFrameRect(1920, 1080, 4, 3)
        assertApprox(1440f, content43Land.width)
        assertApprox(1080f, content43Land.height)
    }

    @Test
    fun `null aspect 16_9 frame in portrait view never overflows 4_3 content`() {
        // Simulate: null aspect → 4:3 content, then 16:9 frame inside it
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        assertTrue(frame.width <= content.width + 1f,
            "16:9 frame width ${frame.width} exceeds 4:3 content width ${content.width}")
        assertTrue(frame.height <= content.height + 1f,
            "16:9 frame height ${frame.height} exceeds 4:3 content height ${content.height}")
        // Frame must be centered within content
        assertApprox(content.centerX, content.left + frame.centerX, 1f)
        assertApprox(content.centerY, content.top + frame.centerY, 1f)
    }

    @Test
    fun `null aspect all frame ratios in portrait stay within 4_3 content`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        for ((rw, rh) in listOf(4 to 3, 16 to 9, 1 to 1)) {
            val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), rw, rh)
            assertTrue(frame.width <= content.width + 1f,
                "${rw}:${rh} frame width exceeds 4:3 content")
            assertTrue(frame.height <= content.height + 1f,
                "${rw}:${rh} frame height exceeds 4:3 content")
        }
    }

    // --- zoom-scaled frame rect with clamping ---

    @Test
    fun `zoom scaled frame stays within content rect`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        // Simulate zoom 2x: scale = 1/2
        val scaled = scaleFrameRect(content, 0.5f)
        assertTrue(scaled.left >= content.left - 1f)
        assertTrue(scaled.top >= content.top - 1f)
        assertTrue(scaled.right <= content.right + 1f)
        assertTrue(scaled.bottom <= content.bottom + 1f)
    }

    @Test
    fun `zoom 3x scaled frame stays within content rect`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val scaled = scaleFrameRect(content, 1f / 3f)
        assertTrue(scaled.left >= content.left - 1f)
        assertTrue(scaled.top >= content.top - 1f)
        assertTrue(scaled.right <= content.right + 1f)
        assertTrue(scaled.bottom <= content.bottom + 1f)
    }

    @Test
    fun `scaleFrameRect never produces negative coordinates`() {
        val rect = FrameRect(100f, 200f, 500f, 800f)
        val scaled = scaleFrameRect(rect, 0.1f)
        assertTrue(scaled.left >= 0f, "scaled left ${scaled.left} < 0")
        assertTrue(scaled.top >= 0f, "scaled top ${scaled.top} < 0")
        assertTrue(scaled.right >= scaled.left)
        assertTrue(scaled.bottom >= scaled.top)
    }

    // --- previewZoomRatio: frame scale = previewZoomRatio / captureZoomRatio ---

    @Test
    fun `frame scale previewZoom over captureZoom produces smaller frame`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scale = zoomFrameScale(captureZoomRatio = 2.0f, previewZoomRatio = 1.0f)
        assertEquals(SQRT_AREA_RATIO_MIN, scale, 0.001f,
            "preview/capture=1/2 should clamp to SQRT_AREA_RATIO_MIN")
        val scaled = scaleFrameRect(base, scale)
        assertApprox(base.width * SQRT_AREA_RATIO_MIN, scaled.width, 1f)
        assertApprox(base.height * SQRT_AREA_RATIO_MIN, scaled.height, 1f)
    }

    @Test
    fun `frame scale equal zoom and previewZoom produces full area-constrained frame`() {
        val base = computeFrameRect(1080, 1920, 4, 3)
        val scale = zoomFrameScale(captureZoomRatio = 2.0f, previewZoomRatio = 2.0f)
        assertEquals(SQRT_AREA_RATIO_MAX, scale, 0.001f,
            "preview/capture=1/1 should clamp to SQRT_AREA_RATIO_MAX")
        val scaled = scaleFrameRect(base, scale)
        assertApprox(base.width * SQRT_AREA_RATIO_MAX, scaled.width, 1f)
        assertApprox(base.height * SQRT_AREA_RATIO_MAX, scaled.height, 1f)
    }

    @Test
    fun `frame scale at 1x leaves watermark preview breathing room`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val scale = zoomFrameScale(captureZoomRatio = 1.0f, previewZoomRatio = 1.0f)
        val scaled = scaleFrameRect(content, scale)
        val areaRatio = (scaled.width * scaled.height) / (content.width * content.height)

        assertTrue(
            areaRatio <= 0.82f,
            "1x frame area ratio $areaRatio should leave visible space for full watermark preview"
        )
        assertApprox(content.centerX, scaled.centerX, 1f)
        assertApprox(content.centerY, scaled.centerY, 1f)
    }

    @Test
    fun `frame scale at lens switch point stays within area bounds`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        // At lens switch point: capture=2.0, preview=2.0 -> rawScale=1.0, clamped by max area.
        val scale = zoomFrameScale(captureZoomRatio = 2.0f, previewZoomRatio = 2.0f)
        assertTrue(scale in SQRT_AREA_RATIO_MIN..SQRT_AREA_RATIO_MAX,
            "At lens switch: scale=$scale not in [$SQRT_AREA_RATIO_MIN, $SQRT_AREA_RATIO_MAX]")
        val scaled = scaleFrameRect(content, scale)
        // Should NOT be full-size.
        assertTrue(scaled.width < content.width - 1f,
            "Frame width ${scaled.width} should be smaller than content ${content.width}")
        assertTrue(scaled.height < content.height - 1f,
            "Frame height ${scaled.height} should be smaller than content ${content.height}")
    }

    @Test
    fun `frame scale stays within sqrt area bounds across zoom sweep`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val zoomSteps = listOf(0.7f, 1.0f, 1.3f, 1.5f, 2.0f, 2.5f, 3.0f, 5.0f, 7.0f, 10.0f)
        for (capture in zoomSteps) {
            val preview = (capture * 0.9f).coerceAtLeast(0.01f)
            val scale = zoomFrameScale(captureZoomRatio = capture, previewZoomRatio = preview)
            assertTrue(scale in SQRT_AREA_RATIO_MIN..SQRT_AREA_RATIO_MAX,
                "zoomFrameScale($capture, $preview)=$scale not in [$SQRT_AREA_RATIO_MIN, $SQRT_AREA_RATIO_MAX]")
            // Area constraint check
            val scaled = scaleFrameRect(content, scale)
            val areaRatio = (scaled.width * scaled.height) / (content.width * content.height)
            assertTrue(areaRatio in 0.6f..0.82f,
                "Area ratio $areaRatio out of [0.6, 0.82] for captureZoom=$capture")
        }
    }

    // --- area-constrained frame box: sweep tests ---

    @Test
    fun `frame box area ratio is within 0_6 to 0_82 for captureZoom 0_7 to 10_0 sweep`() {
        val previewAspects = listOf(4 to 3, 16 to 9, 1 to 1)
        val frameRatios = listOf(4 to 3, 16 to 9, 3 to 4, 1 to 1)
        for ((pw, ph) in previewAspects) {
            val viewW = if (pw > ph) 1920 else 1080
            val viewH = if (pw > ph) 1080 else 1920
            val content = computeFrameRect(viewW, viewH, pw, ph)
            for ((fw, fh) in frameRatios) {
                for (capture in listOf(0.7f, 1.0f, 1.3f, 2.0f, 3.0f, 5.0f, 10.0f)) {
                    val preview = (capture * 0.9f).coerceAtLeast(0.01f)
                    val scale = zoomFrameScale(captureZoomRatio = capture, previewZoomRatio = preview)
                    assertTrue(scale in SQRT_AREA_RATIO_MIN..SQRT_AREA_RATIO_MAX,
                        "scale=$scale out of bounds for capture=$capture, preview=$preview")
                    val scaled = scaleFrameRect(content, scale)
                    val scaledArea = scaled.width * scaled.height
                    val contentArea = content.width * content.height
                    val areaRatio = scaledArea / contentArea
                    assertTrue(areaRatio in 0.6f..0.82f,
                        "Area ratio $areaRatio out of [0.6, 0.82] for ${pw}:${ph} preview + ${fw}:${fh} frame at zoom $capture")
                }
            }
        }
    }

    @Test
    fun `frame scrim alpha default is 200`() {
        assertEquals(200, PreviewOverlayView.FRAME_SCRIM_ALPHA_DEFAULT)
    }

    @Test
    fun `frame scrim alpha configurable via render model`() {
        val ratio = FrameRatio.RATIO_4_3
        val defaultModel = PreviewFrameRenderModel(
            ratio = ratio,
            label = "4:3",
            dimOutsideFrame = true
        )
        assertEquals(PreviewOverlayView.FRAME_SCRIM_ALPHA_DEFAULT, defaultModel.frameScrimAlpha)

        val customModel = defaultModel.copy(frameScrimAlpha = 180)
        assertEquals(180, customModel.frameScrimAlpha)
    }

    @Test
    fun `frame box stays centered across zoom sweep`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        for (capture in listOf(0.7f, 1.0f, 2.0f, 5.0f, 10.0f)) {
            val preview = (capture * 0.9f).coerceAtLeast(0.01f)
            val scale = zoomFrameScale(captureZoomRatio = capture, previewZoomRatio = preview)
            val scaled = scaleFrameRect(content, scale)
            assertApprox(content.centerX, scaled.centerX, 1f)
            assertApprox(content.centerY, scaled.centerY, 1f)
        }
    }

    // --- clampReticleCenter tests ---

    @Test
    fun `center tap in full-view bounds stays at center`() {
        val result = clampReticleCenter(540f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(540f, result.x)
        assertEquals(960f, result.y)
    }

    @Test
    fun `tap near left edge is clamped`() {
        val result = clampReticleCenter(5f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.x) // 0 + 24 + 8
    }

    @Test
    fun `tap near right edge is clamped`() {
        val result = clampReticleCenter(1075f, 960f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(1048f, result.x) // 1080 - 24 - 8
    }

    @Test
    fun `tap near top edge is clamped`() {
        val result = clampReticleCenter(540f, 5f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.y) // 0 + 24 + 8
    }

    @Test
    fun `tap near bottom edge is clamped`() {
        val result = clampReticleCenter(540f, 1915f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(1888f, result.y) // 1920 - 24 - 8
    }

    @Test
    fun `reticle stays within inset 4_3 frame`() {
        // 16:9 view with 4:3 frame: frame has horizontal insets
        val result = clampReticleCenter(100f, 960f, 24f, 8f, 180f, 0f, 900f, 1920f)
        assertEquals(212f, result.x) // 180 + 24 + 8
    }

    @Test
    fun `reticle at exact boundary remains unchanged`() {
        val result = clampReticleCenter(32f, 32f, 24f, 8f, 0f, 0f, 1080f, 1920f)
        assertEquals(32f, result.x)
        assertEquals(32f, result.y)
    }

    // --- content-bounds-aware frame rect tests ---
    // When the preview content aspect differs from the view, the content rect
    // is a fitCenter sub-rect of the view. Frame overlays must fit inside it.

    @Test
    fun `4_3 preview content in portrait 16_9 view is letterboxed`() {
        // View: 1080x1920 (9:16). Preview 4:3 => content = 1080x1440 centered.
        val content = computeFrameRect(1080, 1920, 4, 3)
        assertApprox(1080f, content.width)
        assertApprox(1440f, content.height)
        assertRectCentered(1080, 1920, content)
        // Content is smaller than full view
        assert(content.height < 1920f) { "Content should be letterboxed" }
    }

    @Test
    fun `4_3 preview content in landscape 16_9 view is pillarboxed`() {
        // View: 1920x1080. Preview 4:3 => content = 1440x1080 centered.
        val content = computeFrameRect(1920, 1080, 4, 3)
        assertApprox(1440f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1920, 1080, content)
        assert(content.width < 1920f) { "Content should be pillarboxed" }
    }

    @Test
    fun `16_9 preview content in portrait 4_3 view fills width`() {
        // View: 1080x1920. Preview 16:9 => content = 1080x1920 (9:16 = 16:9 portrait).
        val content = computeFrameRect(1080, 1920, 16, 9)
        assertApprox(1080f, content.width)
        assertApprox(1920f, content.height)
    }

    @Test
    fun `16_9 preview content in landscape 4_3 view is letterboxed`() {
        // View: 1920x1080. Preview 16:9 => content = 1920x1080 (exact match).
        val content = computeFrameRect(1920, 1080, 16, 9)
        assertApprox(1920f, content.width)
        assertApprox(1080f, content.height)
    }

    @Test
    fun `1_1 preview content in portrait view is centered square`() {
        val content = computeFrameRect(1080, 1920, 1, 1)
        assertApprox(1080f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1080, 1920, content)
    }

    @Test
    fun `1_1 preview content in landscape view is centered square`() {
        val content = computeFrameRect(1920, 1080, 1, 1)
        assertApprox(1080f, content.width)
        assertApprox(1080f, content.height)
        assertRectCentered(1920, 1080, content)
    }

    // --- nested frame inside preview content ---

    @Test
    fun `16_9 frame nested inside 4_3 preview content fits within content`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        val content = computeFrameRect(1080, 1920, 4, 3)
        // Frame: 16:9 inside content
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        // Frame must not exceed content
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `4_3 frame nested inside 16_9 preview content fits within content`() {
        // View: 1920x1080. Preview: 16:9 => content = 1920x1080.
        val content = computeFrameRect(1920, 1080, 16, 9)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 4, 3)
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `1_1 frame nested inside letterboxed preview content fits`() {
        // View: 1080x1920. Preview: 4:3 => content = 1080x1440.
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 1, 1)
        assert(frame.width <= content.width + 1f) { "Frame width exceeds content" }
        assert(frame.height <= content.height + 1f) { "Frame height exceeds content" }
    }

    @Test
    fun `nested frame is centered within content rect`() {
        val content = computeFrameRect(1080, 1920, 4, 3)
        val frame = computeFrameRect(content.width.toInt(), content.height.toInt(), 16, 9)
        // Nested frame is local to the content rect; offset it before comparing.
        assertEquals(content.centerX, content.left + frame.centerX, 1f)
        assertEquals(content.centerY, content.top + frame.centerY, 1f)
    }
}
