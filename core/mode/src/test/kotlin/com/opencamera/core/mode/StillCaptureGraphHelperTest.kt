package com.opencamera.core.mode

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class StillCaptureGraphHelperTest {

    @Test
    fun `builds still capture graph from runtime state`() {
        val runtimeState = ModeRuntimeState(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                supportsPreviewSnapshots = false
            ),
            lensFacing = LensFacing.FRONT,
            stillCaptureQuality = StillCaptureQualityPreference.QUALITY,
            stillCaptureResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP
        )

        val graph = stillCaptureDeviceGraph(runtimeState)

        assertEquals(CaptureTemplate.STILL_CAPTURE, graph.template)
        assertEquals(LensFacing.FRONT, graph.preferredLensFacing)
        assertFalse(graph.preview.snapshotsEnabled)
        assertEquals(
            StillCaptureQualityPreference.QUALITY,
            graph.stillCapture.qualityPreference
        )
        assertEquals(
            StillCaptureResolutionPreset.SMALL_2MP,
            graph.stillCapture.resolutionPreset
        )
    }
}
