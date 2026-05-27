package com.opencamera.app.camera

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraXCaptureAdapterCapabilityDetectionTest {
    @Test
    fun `back camera flash support enables flash control`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = false),
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false
                )
            )
        )

        assertTrue(capabilities.supportsFlashControl)
        assertEquals(
            setOf(LensFacing.BACK, LensFacing.FRONT),
            capabilities.availableLensFacings
        )
        assertEquals(
            StillCaptureResolutionPreset.entries.toSet(),
            capabilities.availableStillCaptureResolutionPresets
        )
        assertTrue(capabilities.availableStillCaptureOutputSizes.isEmpty())
    }

    @Test
    fun `back camera without flash disables flash control even if front metadata differs`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = false
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = true
                )
            )
        )

        assertFalse(capabilities.supportsFlashControl)
    }

    @Test
    fun `preferred front lens resolves flash support for front graph`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    availableStillCaptureOutputSizes = listOf(
                        StillCaptureOutputSize(width = 4000, height = 3000),
                        StillCaptureOutputSize(width = 3264, height = 2448)
                    ),
                    availableStillCaptureResolutionPresets = setOf(
                        StillCaptureResolutionPreset.LARGE_12MP,
                        StillCaptureResolutionPreset.MEDIUM_8MP,
                        StillCaptureResolutionPreset.SMALL_2MP
                    )
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false,
                    availableStillCaptureOutputSizes = listOf(
                        StillCaptureOutputSize(width = 1600, height = 1200)
                    ),
                    availableStillCaptureResolutionPresets = setOf(
                        StillCaptureResolutionPreset.SMALL_2MP
                    )
                )
            ),
            preferredLensFacing = LensFacing.FRONT
        )

        assertFalse(capabilities.supportsFlashControl)
        assertEquals(
            setOf(StillCaptureResolutionPreset.SMALL_2MP),
            capabilities.availableStillCaptureResolutionPresets
        )
        assertEquals(
            listOf(StillCaptureOutputSize(width = 1600, height = 1200)),
            capabilities.availableStillCaptureOutputSizes
        )
    }

    @Test
    fun `jpeg output sizes resolve to expected still resolution presets`() {
        val presets = resolveAvailableStillCaptureResolutionPresets(
            availableOutputSizes = listOf(
                StillCaptureTargetResolution(width = 4000, height = 3000),
                StillCaptureTargetResolution(width = 3264, height = 2448),
                StillCaptureTargetResolution(width = 1600, height = 1200)
            )
        )

        assertEquals(
            setOf(
                StillCaptureResolutionPreset.LARGE_12MP,
                StillCaptureResolutionPreset.MEDIUM_8MP,
                StillCaptureResolutionPreset.SMALL_2MP
            ),
            presets
        )
    }

    @Test
    fun `jpeg output ceiling limits still resolution presets conservatively`() {
        val presets = resolveAvailableStillCaptureResolutionPresets(
            availableOutputSizes = listOf(
                StillCaptureTargetResolution(width = 3264, height = 2448),
                StillCaptureTargetResolution(width = 1600, height = 1200)
            )
        )

        assertEquals(
            setOf(
                StillCaptureResolutionPreset.MEDIUM_8MP,
                StillCaptureResolutionPreset.SMALL_2MP
            ),
            presets
        )
    }

    @Test
    fun `normalization prefers four thirds sizes and sorts descending`() {
        val outputSizes = normalizeStillCaptureOutputSizes(
            availableOutputSizes = listOf(
                StillCaptureTargetResolution(width = 1920, height = 1080),
                StillCaptureTargetResolution(width = 4000, height = 3000),
                StillCaptureTargetResolution(width = 3264, height = 2448)
            )
        )

        assertEquals(
            listOf(
                StillCaptureOutputSize(width = 4000, height = 3000),
                StillCaptureOutputSize(width = 3264, height = 2448)
            ),
            outputSizes
        )
    }

    @Test
    fun `empty camera metadata keeps base capability fallback`() {
        val baseCapabilities = DeviceCapabilities.DEFAULT.copy(
            supportsFlashControl = false
        )

        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = baseCapabilities,
            cameraProfiles = emptyList()
        )

        assertEquals(baseCapabilities, capabilities)
    }

    @Test
    fun `preferred lens can override manual control matrix for future device adaptation`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    manualControlCapabilities = ManualControlCapabilityMatrix(
                        raw = ManualControlSupport.SAVED_ONLY,
                        iso = ManualControlSupport.APPLY,
                        shutter = ManualControlSupport.APPLY,
                        exposureCompensation = ManualControlSupport.APPLY,
                        focusDistance = ManualControlSupport.APPLY,
                        aperture = ManualControlSupport.UNSUPPORTED,
                        whiteBalance = ManualControlSupport.UNSUPPORTED
                    )
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false,
                    manualControlCapabilities = ManualControlCapabilityMatrix.SAVED_ONLY_DEFAULT
                )
            ),
            preferredLensFacing = LensFacing.BACK
        )

        assertEquals(ManualControlSupport.APPLY, capabilities.resolvedManualControlCapabilities.iso)
        assertEquals(
            ManualControlSupport.UNSUPPORTED,
            capabilities.resolvedManualControlCapabilities.aperture
        )
        assertEquals(
            ManualControlSupport.UNSUPPORTED,
            capabilities.resolvedManualControlCapabilities.whiteBalance
        )
    }

    @Test
    fun `preferred lens resolves zoom presets for current lens`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT,
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true,
                    zoomRatioCapability = ZoomRatioCapability(
                        support = ZoomControlSupport.DISCRETE_PRESET,
                        supportedRatios = listOf(1f, 2f, 5f),
                        defaultRatio = 1f
                    )
                ),
                CameraLensProfile(
                    lensFacing = LensFacing.FRONT,
                    hasFlashUnit = false,
                    zoomRatioCapability = ZoomRatioCapability(
                        support = ZoomControlSupport.DISCRETE_PRESET,
                        supportedRatios = listOf(1f, 1.5f, 2f),
                        defaultRatio = 1f
                    )
                )
            ),
            preferredLensFacing = LensFacing.FRONT
        )

        assertEquals(ZoomControlSupport.DISCRETE_PRESET, capabilities.zoomRatioCapability.support)
        assertEquals(listOf(1f, 1.5f, 2f), capabilities.zoomRatioCapability.normalizedSupportedRatios)
    }

    @Test
    fun `merge zoom capability falls back when profiles do not expose alternate ratios`() {
        val capabilities = resolveDeviceCapabilities(
            baseCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.UNSUPPORTED,
                    supportedRatios = listOf(1f),
                    defaultRatio = 1f
                )
            ),
            cameraProfiles = listOf(
                CameraLensProfile(
                    lensFacing = LensFacing.BACK,
                    hasFlashUnit = true
                )
            )
        )

        assertEquals(
            listOf(1f),
            capabilities.zoomRatioCapability.normalizedSupportedRatios
        )
        assertEquals(ZoomControlSupport.UNSUPPORTED, capabilities.zoomRatioCapability.support)
    }

    // --- detectLensNodeMap ---

    @Test
    fun `detectLensNodeMap returns empty when no back cameras`() {
        val profiles = listOf(
            CameraLensProfile(lensFacing = LensFacing.FRONT, hasFlashUnit = false)
        )
        val result = detectLensNodeMap(profiles)
        assertTrue(result.isEmpty())
    }

    @Test
    fun `detectLensNodeMap returns only WIDE for single back camera`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                physicalCameraId = "0",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f, 10f)
                )
            )
        )
        val result = detectLensNodeMap(profiles)
        assertEquals(1, result.size)
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.WIDE))
        assertEquals("0", result[com.opencamera.core.device.LensNode.WIDE]?.physicalCameraId)
    }

    @Test
    fun `detectLensNodeMap detects telephoto from 2x max zoom camera`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                physicalCameraId = "0",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f)
                )
            ),
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = false,
                physicalCameraId = "1",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 3f)
                )
            )
        )
        val result = detectLensNodeMap(profiles)
        assertEquals(2, result.size)
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.WIDE))
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.TELEPHOTO))
        assertEquals("1", result[com.opencamera.core.device.LensNode.TELEPHOTO]?.physicalCameraId)
        assertEquals(2.0f, result[com.opencamera.core.device.LensNode.TELEPHOTO]?.thresholdRatio)
    }

    @Test
    fun `detectLensNodeMap detects periscope from 5x max zoom camera`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                physicalCameraId = "0",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f)
                )
            ),
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = false,
                physicalCameraId = "2",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 5f, 10f)
                )
            )
        )
        val result = detectLensNodeMap(profiles)
        assertEquals(2, result.size)
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.WIDE))
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.PERISCOPE))
        assertEquals("2", result[com.opencamera.core.device.LensNode.PERISCOPE]?.physicalCameraId)
        assertEquals(5.0f, result[com.opencamera.core.device.LensNode.PERISCOPE]?.thresholdRatio)
    }

    @Test
    fun `detectLensNodeMap ignores front cameras`() {
        val profiles = listOf(
            CameraLensProfile(
                lensFacing = LensFacing.FRONT,
                hasFlashUnit = false,
                physicalCameraId = "2",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 5f, 10f)
                )
            ),
            CameraLensProfile(
                lensFacing = LensFacing.BACK,
                hasFlashUnit = true,
                physicalCameraId = "0",
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f)
                )
            )
        )
        val result = detectLensNodeMap(profiles)
        assertEquals(1, result.size)
        assertTrue(result.containsKey(com.opencamera.core.device.LensNode.WIDE))
    }
}
