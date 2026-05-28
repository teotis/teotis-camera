package com.opencamera.feature.night

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class NightModePluginTest {

    @Test
    fun `plugin id is NIGHT`() {
        assertEquals(ModeId.NIGHT, NightModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(NightModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, NightModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits night enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("night.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onEnter effect spec has no FilterEffect for night mode`(): Unit = runBlocking {
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        val spec = effects[0]
        assertEquals(null, spec.find<FilterEffect>(), "Night mode EffectSpec should not contain FilterEffect")
    }

    @Test
    fun `onExit emits night exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("night.exit"), events)
        assertEquals("Scenery mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `shutter pressed with multi frame support returns MultiFrame`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed without multi frame support returns SingleFrame`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = false)
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes mode night in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("night", metadata["mode"])
        assertEquals("scenery", metadata["modeDisplay"])
        assertTrue(metadata.containsKey("profile"))
        assertTrue(metadata.containsKey("capturePath"))
        assertTrue(metadata.containsKey("stabilization"))
        assertTrue(metadata.containsKey("stillResolution"))
    }

    @Test
    fun `shutter pressed multi frame metadata uses multi-frame capture path`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("multi-frame", metadata["capturePath"])
        assertEquals("multi-frame", signal.strategy.postProcessSpec.exifOverrides["MergeStrategy"])
    }

    @Test
    fun `shutter pressed single frame metadata uses single frame fallback`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = false)
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("single-frame-fallback", metadata["capturePath"])
        assertEquals("bright-single-frame", signal.strategy.postProcessSpec.exifOverrides["MergeStrategy"])
    }

    @Test
    fun `shutter pressed post process includes night exif overrides`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertEquals("Night", exif["SceneCaptureType"])
        assertTrue(exif.containsKey("NightProfile"))
    }

    @Test
    fun `secondary action cycles night profile`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(
            eventSink = { events += it }
        )

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(events.any { it.startsWith("night.profile.selected.") })
    }

    @Test
    fun `secondary action cycles through multi frame profiles`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true),
            eventSink = { events += it }
        )

        controller.handle(ModeIntent.SecondaryActionPressed)
        controller.handle(ModeIntent.SecondaryActionPressed)

        val profileEvents = events.filter { it.startsWith("night.profile.selected.") }
        assertEquals(2, profileEvents.size)
        assertTrue(profileEvents[0] != profileEvents[1], "Profiles should cycle")
    }

    @Test
    fun `pro action toggles pro variant`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(
            eventSink = { events += it }
        )

        val signal = controller.handle(ModeIntent.ProActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(events.any { it.startsWith("night.pro-variant.") })
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

        assertEquals("Scenery capture in progress", controller.snapshot.value.state.headline)
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

        assertEquals("Scenery photo saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.NIGHT, snapshot.id)
        assertEquals("Scenery", snapshot.uiSpec.title)
        assertEquals("Capture Scenery", snapshot.uiSpec.shutterLabel)
    }

    @Test
    fun `snapshot headline after enter with multi frame`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = true)
        )

        controller.onEnter()

        assertEquals("Scenery mode active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot headline after enter without multi frame`(): Unit = runBlocking {
        val controller = createController(
            deviceCapabilities = DeviceCapabilities.DEFAULT.copy(supportsNightMultiFrame = false)
        )

        controller.onEnter()

        assertEquals("Scenery brightening active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot includes pro action enabled`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertTrue(snapshot.state.isProActionEnabled)
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {}
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
            settingsSnapshotProvider = { SessionSettingsSnapshot() }
        )
        return NightModePlugin().create(context)
    }
}
