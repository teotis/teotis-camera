package com.opencamera.app.camera

import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals

class CameraXCaptureAdapterStillCaptureQualityTest {
    @Test
    fun `still graph quality drives adapter binding preference`() {
        val graph = DeviceGraphSpec.stillCapture(
            qualityPreference = StillCaptureQualityPreference.QUALITY
        )

        val quality = resolvedStillCaptureQuality(graph)

        assertEquals(StillCaptureQualityPreference.QUALITY, quality)
    }

    @Test
    fun `non still graph falls back to device request quality`() {
        val graph = DeviceGraphSpec.videoRecording(
            stillQualityPreference = StillCaptureQualityPreference.LATENCY
        )
        val request = DeviceShotRequest(
            shotId = "shot-1",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.STILL_CAPTURE,
            stillCaptureQuality = StillCaptureQualityPreference.QUALITY
        )

        val quality = resolvedStillCaptureQuality(
            deviceGraph = graph,
            deviceRequest = request
        )

        assertEquals(StillCaptureQualityPreference.QUALITY, quality)
    }

    @Test
    fun `still graph resolution drives adapter binding preference`() {
        val graph = DeviceGraphSpec.stillCapture(
            resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP
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
}
