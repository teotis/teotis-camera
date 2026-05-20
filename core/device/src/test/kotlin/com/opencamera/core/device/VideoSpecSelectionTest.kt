package com.opencamera.core.device

import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoSpecConstraints
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VideoSpecSelectionTest {
    @Test
    fun `video spec resolves to nearest supported matrix entry`() {
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            videoSpecConstraints = VideoSpecConstraints(
                supportedFrameRatesByResolution = linkedMapOf(
                    VideoResolution.UHD_4K to setOf(
                        VideoFrameRate.FPS_25,
                        VideoFrameRate.FPS_30
                    ),
                    VideoResolution.FHD_1080P to setOf(
                        VideoFrameRate.FPS_25,
                        VideoFrameRate.FPS_30,
                        VideoFrameRate.FPS_60
                    )
                ),
                dynamicPolicies = setOf(DynamicVideoFpsPolicy.LOCKED),
                audioProfiles = setOf(AudioProfile.STANDARD)
            )
        )

        val resolved = capabilities.resolveVideoSpec(
            VideoSpec(
                resolution = VideoResolution.UHD_8K,
                frameRate = VideoFrameRate.FPS_120,
                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                audioProfile = AudioProfile.CONCERT
            )
        )

        assertTrue(resolved.isDegraded)
        assertTrue(resolved.resolutionDegraded)
        assertTrue(resolved.frameRateDegraded)
        assertTrue(resolved.dynamicPolicyDegraded)
        assertTrue(resolved.audioProfileDegraded)
        assertEquals(VideoResolution.UHD_4K, resolved.applied.resolution)
        assertEquals(VideoFrameRate.FPS_30, resolved.applied.frameRate)
        assertEquals(DynamicVideoFpsPolicy.LOCKED, resolved.applied.dynamicFpsPolicy)
        assertEquals(AudioProfile.STANDARD, resolved.applied.audioProfile)
    }

    @Test
    fun `video spec keeps supported audio and fps settings intact`() {
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            supportsAudioRecording = true,
            videoSpecConstraints = VideoSpecConstraints(
                supportedFrameRatesByResolution = linkedMapOf(
                    VideoResolution.UHD_4K to setOf(
                        VideoFrameRate.FPS_25,
                        VideoFrameRate.FPS_30,
                        VideoFrameRate.FPS_60
                    )
                ),
                dynamicPolicies = setOf(
                    DynamicVideoFpsPolicy.LOCKED,
                    DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS
                ),
                audioProfiles = setOf(
                    AudioProfile.STANDARD,
                    AudioProfile.CONCERT
                )
            )
        )

        val resolved = capabilities.resolveVideoSpec(
            VideoSpec(
                resolution = VideoResolution.UHD_4K,
                frameRate = VideoFrameRate.FPS_60,
                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                audioProfile = AudioProfile.CONCERT
            )
        )

        assertFalse(resolved.isDegraded)
        assertEquals(VideoResolution.UHD_4K, resolved.applied.resolution)
        assertEquals(VideoFrameRate.FPS_60, resolved.applied.frameRate)
        assertEquals(DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS, resolved.applied.dynamicFpsPolicy)
        assertEquals(AudioProfile.CONCERT, resolved.applied.audioProfile)
    }

    @Test
    fun `runtime video spec drops to low light frame rate when scene is dark`() {
        val capabilities = DeviceCapabilities.DEFAULT.copy(
            videoSpecConstraints = VideoSpecConstraints(
                supportedFrameRatesByResolution = linkedMapOf(
                    VideoResolution.UHD_4K to setOf(
                        VideoFrameRate.FPS_24,
                        VideoFrameRate.FPS_25,
                        VideoFrameRate.FPS_30,
                        VideoFrameRate.FPS_60
                    )
                ),
                dynamicPolicies = setOf(
                    DynamicVideoFpsPolicy.LOCKED,
                    DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS
                ),
                audioProfiles = setOf(AudioProfile.STANDARD)
            )
        )

        val runtimeSpec = capabilities.resolveRuntimeVideoSpec(
            base = VideoSpec(
                resolution = VideoResolution.UHD_4K,
                frameRate = VideoFrameRate.FPS_60,
                dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS
            ),
            sceneSignal = VideoSceneSignal(
                isLowLight = true,
                brightnessScore = 0.12f
            )
        )

        assertEquals(VideoFrameRate.FPS_24, runtimeSpec.frameRate)
        assertEquals(DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS, runtimeSpec.dynamicFpsPolicy)
    }
}
