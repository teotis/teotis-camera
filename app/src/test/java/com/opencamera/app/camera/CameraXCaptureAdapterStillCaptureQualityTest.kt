package com.opencamera.app.camera

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.PreviewStreamAspect
import com.opencamera.core.device.StillCaptureConfig
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.StillCaptureResolutionSource
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraXCaptureAdapterStillCaptureQualityTest {

    @Test
    fun `still graph resolution drives adapter binding preference`() {
        val graph = DeviceGraphSpec(
            template = CaptureTemplate.STILL_CAPTURE,
            stillCapture = StillCaptureConfig(
                resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
            )
        )

        val preset = resolvedStillCaptureResolutionPreset(graph)

        assertEquals(StillCaptureResolutionPreset.MEDIUM_8MP, preset)
    }

    @Test
    fun `resolution preset maps to expected target dimensions`() {
        val targetDimensions = targetDimensionsForStillCaptureResolutionPreset(
            StillCaptureResolutionPreset.SMALL_2MP
        )

        assertEquals(1600, targetDimensions.width)
        assertEquals(1200, targetDimensions.height)
    }

    @Test
    fun `resolution preset resolves to closest available native output size`() {
        val outputSize = resolveStillCaptureOutputSize(
            preset = StillCaptureResolutionPreset.MEDIUM_8MP,
            availableOutputSizes = listOf(
                StillCaptureOutputSize(width = 4000, height = 3000),
                StillCaptureOutputSize(width = 3264, height = 2448),
                StillCaptureOutputSize(width = 1600, height = 1200)
            )
        )

        assertEquals(3264, outputSize.width)
        assertEquals(2448, outputSize.height)
    }

    @Test
    fun `LARGE_12MP selects maximum available output size`() {
        val outputSize = resolveStillCaptureOutputSize(
            preset = StillCaptureResolutionPreset.LARGE_12MP,
            availableOutputSizes = listOf(
                StillCaptureOutputSize(width = 4000, height = 3000),
                StillCaptureOutputSize(width = 8000, height = 6000),
                StillCaptureOutputSize(width = 1600, height = 1200),
                StillCaptureOutputSize(width = 3264, height = 2448)
            )
        )

        assertEquals(8000, outputSize.width)
        assertEquals(6000, outputSize.height)
    }

    @Test
    fun `normalizeStillCaptureOutputSizes preserves resolution source metadata`() {
        val normalized = normalizeStillCaptureOutputSizes(
            availableOutputSizes = listOf(
                StillCaptureTargetResolution(
                    width = 8000, height = 6000,
                    resolutionSource = StillCaptureResolutionSource.MAXIMUM_RESOLUTION
                ),
                StillCaptureTargetResolution(
                    width = 4000, height = 3000,
                    resolutionSource = StillCaptureResolutionSource.HIGH_RESOLUTION
                ),
                StillCaptureTargetResolution(
                    width = 3264, height = 2448,
                    resolutionSource = StillCaptureResolutionSource.STANDARD
                )
            )
        )

        assertEquals(3, normalized.size)
        val maxRes = normalized.firstOrNull { it.width == 8000 }
        assertEquals(StillCaptureResolutionSource.MAXIMUM_RESOLUTION, maxRes?.resolutionSource)
        val highResSize = normalized.firstOrNull { it.width == 4000 }
        assertEquals(StillCaptureResolutionSource.HIGH_RESOLUTION, highResSize?.resolutionSource)
        val stdSize = normalized.firstOrNull { it.width == 3264 }
        assertEquals(StillCaptureResolutionSource.STANDARD, stdSize?.resolutionSource)
    }

    @Test
    fun `default still capture quality is QUALITY`() {
        val graph = DeviceGraphSpec.stillCapture()
        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            graph.stillCapture.qualityPreference
        )
    }

    @Test
    fun `preview stream aspect maps to camera x target dimensions`() {
        val sixteenNine = targetSizeForPreviewStreamAspect(PreviewStreamAspect.RATIO_16_9)
        assertEquals(1920, sixteenNine.width)
        assertEquals(1080, sixteenNine.height)

        val square = targetSizeForPreviewStreamAspect(PreviewStreamAspect.RATIO_1_1)
        assertEquals(1080, square.width)
        assertEquals(1080, square.height)
    }
}
