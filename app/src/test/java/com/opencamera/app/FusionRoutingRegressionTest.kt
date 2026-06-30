package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.PhotoLowLightStrategySupport
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.CompositeMediaPostProcessor
import com.opencamera.core.media.FrameBundle
import com.opencamera.core.media.FrameBundleFrame
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.FrameBundleStatus
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MultiFrameFusionProcessor
import com.opencamera.core.media.PixelReference
import com.opencamera.core.media.PipelineMetadataPostProcessor
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.frameBundleStatus
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.PhotoLowLightRuntimeState
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.feature.checkin.CheckInModePlugin
import com.opencamera.feature.photo.PhotoModePlugin
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Integration regression tests verifying:
 * - Photo low-light routes through multi-frame when supported (6 frames / 450ms)
 * - CheckIn clarity routes through multi-frame when supported
 * - Degraded paths remain explicit and visible
 * - Pipeline notes no longer report placeholder for successful fusion
 * - FrameBundleStatus correctly reflects bundle health
 */
class FusionRoutingRegressionTest {

    // ── Photo low-light multi-frame ──────────────────────────────────────

    @Test
    fun `photo low light requests 6 frames with 450ms when multi frame supported`() = runBlocking {
        val controller = createPhotoController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = true,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.15f,
                    source = "integration-test"
                ),
                support = PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME
            )
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        val strategy = signal.strategy
        assertIs<CaptureStrategy.MultiFrame>(strategy)
        assertEquals(6, strategy.captureProfile.frameCount,
            "Low-light multi-frame should request 6 frames")
        assertEquals(450L, strategy.captureProfile.longExposureMillis,
            "Low-light multi-frame should use 450ms exposure")
    }

    @Test
    fun `photo low light multi frame carries multi frame strategy tag in metadata`() = runBlocking {
        val controller = createPhotoController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = true,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.2f,
                    source = "test"
                ),
                support = PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME
            )
        )
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("multi-frame", metadata["photoLowLightStrategy"])
        assertEquals("on", metadata["photoLowLightNightAssist"])
    }

    // ── Photo low-light degraded / unsupported ──────────────────────────

    @Test
    fun `photo low light degraded falls back to single frame with explicit tag`() = runBlocking {
        val controller = createPhotoController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = true,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.3f,
                    source = "test"
                ),
                support = PhotoLowLightStrategySupport.DEGRADED_SINGLE_FRAME
            )
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        assertEquals("single-frame-degraded",
            signal.strategy.saveRequest.metadata.customTags["photoLowLightStrategy"])
    }

    @Test
    fun `photo low light unsupported returns single frame without low light tags`() = runBlocking {
        val controller = createPhotoController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = true,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.1f,
                    source = "test"
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        // UNSUPPORTED means shouldUseNightAssist=false, so no low-light strategy tag
        assertFalse(signal.strategy.saveRequest.metadata.customTags.containsKey("photoLowLightStrategy"),
            "UNSUPPORTED should not produce low-light strategy tag (shouldUseNightAssist=false)")
    }

    // ── Photo normal capture clean of low-light tags ────────────────────

    @Test
    fun `photo normal capture has no low light strategy tag`() = runBlocking {
        val controller = createPhotoController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertFalse(metadata.containsKey("photoLowLightStrategy"))
        assertFalse(metadata.containsKey("photoLowLightNightAssist"))
    }

    // ── CheckIn clarity routing ──────────────────────────────────────────

    @Test
    fun `checkin clarity routes through multi frame when supported`() = runBlocking {
        val controller = createCheckInController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = true
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)

        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("check-in", metadata["mode"])
        assertEquals("clarity", metadata["checkInScenario"])
        assertEquals(2, signal.strategy.captureProfile.frameCount)
        assertEquals("focus-stack:auto-near-far-v1", signal.strategy.captureProfile.focusStackSpec?.algorithmProfile)
        assertEquals(false, signal.strategy.captureProfile.focusStackSpec?.userGuidanceRequired)
        assertEquals("focus-stack-auto-near-far", metadata["degradation-policy"])
        assertEquals("automatic", metadata["focusStackGuidance"])
        assertEquals("near,far", metadata["focusStackRoles"])
    }

    @Test
    fun `checkin clarity degraded to single frame when unsupported`() = runBlocking {
        val controller = createCheckInController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)

        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("single-frame", metadata["captureStrategyFallback"])
        assertEquals("multi-frame-unsupported", metadata["degradationReason"])
        assertEquals("single-frame-best-frame", metadata["degradation-policy"])
    }

    @Test
    fun `checkin portrait scenarios always use single frame`() = runBlocking {
        for (scenarioId in listOf("portrait", "people-place", "object-place")) {
            val controller = createCheckInController(
                initialScenarioId = scenarioId,
                supportsNightMultiFrame = true
            )
            val signal = controller.handle(ModeIntent.ShutterPressed)
            assertIs<ModeSignal.SubmitCapture>(signal)
            assertIs<CaptureStrategy.SingleFrame>(signal.strategy,
                "Scenario $scenarioId must use SingleFrame regardless of multi-frame support")
        }
    }

    // ── Pipeline notes: no placeholder for successful fusion ────────────

    @Test
    fun `pipeline notes never contain placeholder even when fusion cannot decode`() = runTest {
        val fakePath = File(System.getProperty("java.io.tmpdir"), "placeholder-test-${System.nanoTime()}")
        fakePath.mkdirs()
        val fakeFile = File(fakePath, "fake.jpg")
        fakeFile.createNewFile()
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(fakeFile.absolutePath),
                    outputFormat = "image/jpeg"),
                FrameBundleFrame(1, PixelReference.File(fakeFile.absolutePath),
                    outputFormat = "image/jpeg"),
                FrameBundleFrame(2, PixelReference.File(fakeFile.absolutePath),
                    frameRole = FrameRole.FUSION_ANCHOR, outputFormat = "image/jpeg")
            )
            val bundle = FrameBundle(shotId = "placeholder-regression", frames = frames)
            val result = ShotResult(
                shotId = "placeholder-regression",
                mediaType = MediaType.PHOTO,
                outputPath = File(fakePath, "output.jpg").absolutePath,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = 3),
                metadata = MediaMetadata(algorithmProfile = "photo-low-light-fusion"),
                frameBundle = bundle,
                intermediateOutputPaths = listOf(fakeFile.absolutePath)
            )

            val composite = CompositeMediaPostProcessor(
                listOf(MultiFrameFusionProcessor(), PipelineMetadataPostProcessor())
            )
            val processed = composite.process(result)

            assertFalse(processed.pipelineNotes.any { it == "merge:placeholder" },
                "Fusion must never produce 'merge:placeholder', got: ${processed.pipelineNotes}")
            assertTrue(processed.pipelineNotes.any { it.startsWith("merge:") },
                "Must produce merge-related pipeline notes")
            assertTrue(processed.pipelineNotes.any { it.startsWith("algorithm:") },
                "Algorithm profile should be surfaced in pipeline notes")
        } finally {
            fakePath.deleteRecursively()
        }
    }

    // ── Degraded paths remain explicit ──────────────────────────────────

    @Test
    fun `degraded fusion produces explicit skip or degraded note`() = runTest {
        val fakePath = File(System.getProperty("java.io.tmpdir"), "degraded-regression-${System.nanoTime()}")
        fakePath.mkdirs()
        val fakeFile = File(fakePath, "degraded.jpg")
        fakeFile.createNewFile()
        try {
            val frames = listOf(
                FrameBundleFrame(0, PixelReference.File(fakeFile.absolutePath),
                    outputFormat = "image/jpeg",
                    isDegraded = true,
                    degradationReasons = listOf("corrupted-header"))
            )
            val bundle = FrameBundle(shotId = "degraded-regression", frames = frames)
            val result = ShotResult(
                shotId = "degraded-regression",
                mediaType = MediaType.PHOTO,
                outputPath = File(fakePath, "output.jpg").absolutePath,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailSource = ThumbnailSource.None,
                captureProfile = CaptureProfile(frameCount = 3),
                metadata = MediaMetadata(),
                frameBundle = bundle
            )

            val processed = MultiFrameFusionProcessor().process(result)
            assertTrue(
                processed.pipelineNotes.any {
                    it.startsWith("merge:skipped=") ||
                    it.startsWith("merge:degraded=") ||
                    it == "merge:applied=false"
                },
                "Degraded fusion must produce explicit note, got: ${processed.pipelineNotes}"
            )
        } finally {
            fakePath.deleteRecursively()
        }
    }

    // ── FrameBundle status helpers ──────────────────────────────────────

    @Test
    fun `frameBundleStatus returns ABSENT for null bundle`() {
        val result = ShotResult(
            shotId = "status-test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/status.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata()
        )
        assertEquals(FrameBundleStatus.ABSENT, result.frameBundleStatus())
    }

    @Test
    fun `frameBundleStatus returns DEGRADED for degraded bundle`() {
        val bundle = FrameBundle(
            shotId = "degraded-bundle",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/tmp/f.jpg"),
                    isDegraded = true)
            )
        )
        val result = ShotResult(
            shotId = "status-test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/status.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata(),
            frameBundle = bundle
        )
        assertEquals(FrameBundleStatus.DEGRADED, result.frameBundleStatus())
    }

    @Test
    fun `frameBundleStatus returns PRESENT for healthy bundle`() {
        val bundle = FrameBundle(
            shotId = "healthy-bundle",
            frames = listOf(
                FrameBundleFrame(0, PixelReference.File("/tmp/f.jpg"),
                    noiseModel = com.opencamera.core.media.NoiseModel.Known("test-profile"),
                    motionScore = com.opencamera.core.media.MotionScore.Known(0.1f))
            )
        )
        val result = ShotResult(
            shotId = "status-test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/status.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata(),
            frameBundle = bundle
        )
        assertEquals(FrameBundleStatus.PRESENT, result.frameBundleStatus())
    }

    // ── Helpers ──────────────────────────────────────────────────────────

    private fun createPhotoController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        lowLightState: PhotoLowLightRuntimeState = PhotoLowLightRuntimeState(
            settingEnabled = false,
            sceneSignal = PhotoSceneSignal(),
            support = PhotoLowLightStrategySupport.UNSUPPORTED
        )
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities,
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            settingsSnapshotProvider = { SessionSettingsSnapshot() },
            photoLowLightRuntimeStateProvider = { lowLightState }
        )
        return PhotoModePlugin().create(context)
    }

    private fun createCheckInController(
        initialScenarioId: String = "portrait",
        supportsNightMultiFrame: Boolean = true
    ): ModeController {
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsNightMultiFrame = supportsNightMultiFrame
        )
        val context = ModeContext(
            deviceCapabilities = caps,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = caps,
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    persisted = PersistedSettings(
                        photo = PhotoSettings(defaultCheckInScenario = initialScenarioId)
                    )
                )
            }
        )
        return CheckInModePlugin().create(context)
    }
}
