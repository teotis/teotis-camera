package com.opencamera.app.camera

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.StillCaptureConfig
import com.opencamera.core.device.StillCaptureOutputSize
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
}
