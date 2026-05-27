package com.opencamera.feature.photo

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.mode.PhotoLowLightRuntimeState
import com.opencamera.core.device.PhotoLowLightStrategySupport
import com.opencamera.core.device.PhotoSceneSignal
import com.opencamera.core.device.SceneLightState
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.MediaMetadata
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PhotoModePluginTest {

    @Test
    fun `plugin id is PHOTO`() {
        assertEquals(ModeId.PHOTO, PhotoModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(PhotoModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, PhotoModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits photo enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("photo.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<FilterEffect>() != null, "EffectSpec should contain FilterEffect")
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onExit emits photo exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("photo.exit"), events)
        assertEquals("Photo mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `shutter pressed returns SubmitCapture with SingleFrame by default`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes mode photo in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("photo", metadata["mode"])
        assertTrue(metadata.containsKey("flash"))
        assertTrue(metadata.containsKey("stillResolution"))
    }

    @Test
    fun `shutter pressed with low light multi frame support returns MultiFrame`(): Unit = runBlocking {
        val controller = createController(
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

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("on", metadata["photoLowLightNightAssist"])
        assertEquals("multi-frame", metadata["photoLowLightStrategy"])
    }

    @Test
    fun `shutter pressed with low light degraded returns SingleFrame`(): Unit = runBlocking {
        val controller = createController(
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
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("single-frame-degraded", metadata["photoLowLightStrategy"])
    }

    @Test
    fun `secondary action cycles flash mode`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(events.any { it.startsWith("photo.flash.selected.") })
        assertTrue(effects.isNotEmpty())
    }

    @Test
    fun `secondary action shows hint when flash unsupported`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = false)
        )

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("unavailable"))
    }

    @Test
    fun `pro action returns None for photo mode`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ProActionPressed)

        assertEquals(ModeSignal.None, signal)
    }

    @Test
    fun `session event shot started updates snapshot headline`(): Unit = runBlocking {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(
            ModeSessionEvent.ShotStarted(
                shot = ShotRequest(
                    shotId = "test-shot",
                    shotKind = ShotKind.STILL_CAPTURE,
                    mediaType = MediaType.PHOTO,
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                    postProcessSpec = PostProcessSpec(),
                    captureProfile = CaptureProfile()
                )
            )
        )

        assertEquals("Photo capture in progress", controller.snapshot.value.state.headline)
    }

    @Test
    fun `session event shot completed updates snapshot headline`(): Unit = runBlocking {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(
            ModeSessionEvent.ShotCompleted(
                result = ShotResult(
                    shotId = "test-shot",
                    mediaType = MediaType.PHOTO,
                    outputPath = "/tmp/test.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.None,
                    metadata = MediaMetadata()
                )
            )
        )

        assertEquals("Photo saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.PHOTO, snapshot.id)
        assertEquals("Photo", snapshot.uiSpec.title)
        assertEquals("Capture Still", snapshot.uiSpec.shutterLabel)
    }

    @Test
    fun `snapshot headline changes after enter`(): Unit = runBlocking {
        val controller = createController()

        controller.onEnter()

        assertEquals("Photo mode active", controller.snapshot.value.state.headline)
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
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
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged,
            settingsSnapshotProvider = { SessionSettingsSnapshot() },
            photoLowLightRuntimeStateProvider = { lowLightState }
        )
        return PhotoModePlugin().create(context)
    }
}
