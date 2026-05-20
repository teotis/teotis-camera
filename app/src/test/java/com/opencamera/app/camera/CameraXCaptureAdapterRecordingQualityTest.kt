package com.opencamera.app.camera

import androidx.camera.video.Quality
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.RecordingQualityPreset
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CameraXCaptureAdapterRecordingQualityTest {
    @Test
    fun `uhd preset prefers uhd first`() {
        assertEquals(
            listOf(Quality.UHD, Quality.FHD, Quality.HD, Quality.SD),
            orderedRecordingQualities(RecordingQualityPreset.UHD)
        )
    }

    @Test
    fun `fhd preset prefers full hd first`() {
        assertEquals(
            listOf(Quality.FHD, Quality.HD, Quality.SD),
            orderedRecordingQualities(RecordingQualityPreset.FHD)
        )
    }

    @Test
    fun `hd preset prefers hd first with full hd fallback`() {
        assertEquals(
            listOf(Quality.HD, Quality.FHD, Quality.SD),
            orderedRecordingQualities(RecordingQualityPreset.HD)
        )
    }

    @Test
    fun `sd preset prefers sd first with higher fallbacks`() {
        assertEquals(
            listOf(Quality.SD, Quality.HD, Quality.FHD),
            orderedRecordingQualities(RecordingQualityPreset.SD)
        )
    }

    @Test
    fun `build video spec constraints merges standard and high speed frame rates`() {
        val constraints = buildVideoSpecConstraints(
            standardProfileFrameRates = linkedMapOf(
                VideoResolution.UHD_4K to 30,
                VideoResolution.FHD_1080P to 60
            ),
            highSpeedProfileFrameRates = linkedMapOf(
                VideoResolution.FHD_1080P to 120,
                VideoResolution.HD_720P to 240
            )
        )

        assertEquals(
            setOf(VideoFrameRate.FPS_25, VideoFrameRate.FPS_30),
            constraints.frameRatesFor(VideoResolution.UHD_4K)
        )
        assertEquals(
            setOf(VideoFrameRate.FPS_30, VideoFrameRate.FPS_60, VideoFrameRate.FPS_100, VideoFrameRate.FPS_120),
            constraints.frameRatesFor(VideoResolution.FHD_1080P)
        )
        assertEquals(
            setOf(VideoFrameRate.FPS_100, VideoFrameRate.FPS_120),
            constraints.frameRatesFor(VideoResolution.HD_720P)
        )
        assertTrue(DynamicVideoFpsPolicy.LOCKED in constraints.dynamicPolicies)
    }

    @Test
    fun `resolve device capabilities prefers matching lens video constraints`() {
        val backConstraints = buildVideoSpecConstraints(
            standardProfileFrameRates = linkedMapOf(
                VideoResolution.UHD_8K to 30,
                VideoResolution.UHD_4K to 60
            ),
            highSpeedProfileFrameRates = linkedMapOf(
                VideoResolution.UHD_4K to 120
            )
        )
        val frontConstraints = buildVideoSpecConstraints(
            standardProfileFrameRates = linkedMapOf(
                VideoResolution.FHD_1080P to 30
            ),
            highSpeedProfileFrameRates = emptyMap()
        )

        val backCapabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    videoSpecConstraints = backConstraints
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false,
                    videoSpecConstraints = frontConstraints
                )
            ),
            preferredLensFacing = LensFacing.BACK
        )
        val frontCapabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    videoSpecConstraints = backConstraints
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false,
                    videoSpecConstraints = frontConstraints
                )
            ),
            preferredLensFacing = LensFacing.FRONT
        )

        assertTrue(VideoResolution.UHD_8K in backCapabilities.videoSpecConstraints.resolutions)
        assertTrue(VideoFrameRate.FPS_120 in backCapabilities.videoSpecConstraints.frameRatesFor(VideoResolution.UHD_4K))
        assertEquals(setOf(VideoResolution.FHD_1080P), frontCapabilities.videoSpecConstraints.resolutions)
        assertEquals(
            setOf(VideoFrameRate.FPS_25, VideoFrameRate.FPS_30),
            frontCapabilities.videoSpecConstraints.frameRatesFor(VideoResolution.FHD_1080P)
        )
    }

    @Test
    fun `target frame rate range uses resolved video spec fps`() {
        assertEquals(
            24..24,
            targetFrameRateBounds(
                VideoSpec(
                    resolution = VideoResolution.UHD_4K,
                    frameRate = VideoFrameRate.FPS_24
                )
            )
        )
    }

    @Test
    fun `runtime low light frame rate range differs from default graph fps`() {
        val defaultRange = targetFrameRateBounds(
            VideoSpec(
                resolution = VideoResolution.UHD_4K,
                frameRate = VideoFrameRate.FPS_25
            )
        )
        val runtimeRange = targetFrameRateBounds(
            VideoSpec(
                resolution = VideoResolution.UHD_4K,
                frameRate = VideoFrameRate.FPS_24,
                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS
            )
        )

        assertNotEquals(defaultRange, runtimeRange)
        assertEquals(25, defaultRange.first)
        assertEquals(24, runtimeRange.first)
    }
}
