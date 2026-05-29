package com.opencamera.app

import com.opencamera.app.camera.smartFilterResolutionOptions
import com.opencamera.core.device.StillCaptureOutputSize
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * 覆盖行为:
 * - 空输入返回空列表
 * - ≤3 个选项（去重后）原样返回
 * - >3 个选项返回 highest/medium/lowest 三档
 * - 去重逻辑（相同 width+height 只保留一个）
 * - 结果按像素数降序排列
 * - medium 选择最接近中间值的选项
 * - 单元素输入返回单元素列表
 *
 * 不适合单测的行为: 无
 */
class ResolutionFilterUtilsTest {

    @Test
    fun `empty input returns empty list`() {
        assertEquals(emptyList(), smartFilterResolutionOptions(emptyList()))
    }

    @Test
    fun `single element returns single element list`() {
        val input = listOf(StillCaptureOutputSize(1920, 1080))
        val result = smartFilterResolutionOptions(input)
        assertEquals(1, result.size)
        assertEquals(1920, result[0].targetWidth)
        assertEquals(1080, result[0].targetHeight)
    }

    @Test
    fun `two options return both sorted by pixel count descending`() {
        val input = listOf(
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(1920, 1080)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(2, result.size)
        assertEquals(1920, result[0].targetWidth)
        assertEquals(1280, result[1].targetWidth)
    }

    @Test
    fun `three options return all three sorted descending`() {
        val input = listOf(
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(3840, 2160),
            StillCaptureOutputSize(1920, 1080)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(3, result.size)
        assertEquals(3840, result[0].targetWidth)
        assertEquals(1920, result[1].targetWidth)
        assertEquals(1280, result[2].targetWidth)
    }

    @Test
    fun `more than three options return highest medium lowest`() {
        val input = listOf(
            StillCaptureOutputSize(640, 480),
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(2560, 1440),
            StillCaptureOutputSize(3840, 2160)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(3, result.size)
        // highest
        assertEquals(3840, result[0].targetWidth)
        assertEquals(2160, result[0].targetHeight)
        // lowest
        assertEquals(640, result[2].targetWidth)
        assertEquals(480, result[2].targetHeight)
        // medium is closest to mid pixel count
        val midPixelCount = (3840L * 2160 + 640L * 480) / 2
        val expectedMedium = listOf(
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(2560, 1440)
        ).minByOrNull { kotlin.math.abs(it.pixelCount - midPixelCount) }!!
        assertEquals(expectedMedium.width, result[1].targetWidth)
        assertEquals(expectedMedium.height, result[1].targetHeight)
    }

    @Test
    fun `deduplicates by width and height`() {
        val input = listOf(
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(1280, 720)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(2, result.size)
        assertEquals(1920, result[0].targetWidth)
        assertEquals(1280, result[1].targetWidth)
    }

    @Test
    fun `dedup reduces count to three or fewer returns all`() {
        // 5 inputs but only 3 unique
        val input = listOf(
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(1920, 1080),
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(1280, 720),
            StillCaptureOutputSize(640, 480)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(3, result.size)
        assertEquals(1920, result[0].targetWidth)
        assertEquals(1280, result[1].targetWidth)
        assertEquals(640, result[2].targetWidth)
    }

    @Test
    fun `medium selects closest to mid pixel count from candidates`() {
        // highest=8000x6000, lowest=320x240
        // mid = (48000000 + 76800) / 2 = 24038400
        // candidates: 4000x3000=12000000, 6400x4800=30720000
        // |12000000-24038400|=12038400, |30720000-24038400|=6681600 → 6400x4800 wins
        val input = listOf(
            StillCaptureOutputSize(320, 240),
            StillCaptureOutputSize(4000, 3000),
            StillCaptureOutputSize(6400, 4800),
            StillCaptureOutputSize(8000, 6000)
        )
        val result = smartFilterResolutionOptions(input)
        assertEquals(3, result.size)
        assertEquals(8000, result[0].targetWidth)
        assertEquals(6400, result[1].targetWidth)
        assertEquals(320, result[2].targetWidth)
    }

    @Test
    fun `result mapped via fromOutputSize has correct tag and label`() {
        val input = listOf(StillCaptureOutputSize(4000, 3000))
        val result = smartFilterResolutionOptions(input)
        assertEquals(1, result.size)
        assertEquals("12mp", result[0].tagValue)
        assertEquals("12MP", result[0].label)
    }
}
