package com.opencamera.feature.portrait

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.effect.PortraitEffect
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.CaptureProfile
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

class PortraitModePluginTest {

    @Test
    fun `plugin id is PORTRAIT`() {
        assertEquals(ModeId.PORTRAIT, PortraitModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(PortraitModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, PortraitModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits portrait enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("portrait.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<FilterEffect>() != null, "EffectSpec should contain FilterEffect")
        assertTrue(spec.find<PortraitEffect>() != null, "EffectSpec should contain PortraitEffect")
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onExit emits portrait exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("portrait.exit"), events)
        assertEquals("Portrait mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `shutter pressed returns SubmitCapture with SingleFrame by default`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed returns LivePhoto when live photo enabled`(): Unit = runBlocking {
        val controller = createController(
            livePhotoEnabled = true
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.LivePhoto>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes portrait mode in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait", metadata["mode"])
        assertEquals("Portrait", metadata["watermarkModeName"])
        assertTrue(metadata.containsKey("style"))
        assertTrue(metadata.containsKey("subjectTracking"))
        assertTrue(metadata.containsKey("stillResolution"))
        assertTrue(metadata.containsKey("modeVariant"))
        assertTrue(metadata.containsKey("bokehStrength"))
    }

    @Test
    fun `shutter pressed post process has portrait exif overrides`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertEquals("Portrait", exif["SceneCaptureType"])
        assertTrue(exif.containsKey("PortraitStyle"))
        assertTrue(exif.containsKey("PortraitProfile"))
        assertTrue(exif.containsKey("DepthEffect"))
    }

    @Test
    fun `shutter pressed post process has algorithm profile`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertTrue(signal.strategy.postProcessSpec.algorithmProfile != null)
    }

    @Test
    fun `shutter pressed save path is Portrait`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertEquals("Pictures/OpenCamera/Portrait", signal.strategy.saveRequest.relativePath)
        assertEquals("OpenCamera_PORTRAIT", signal.strategy.saveRequest.fileNamePrefix)
    }

    @Test
    fun `secondary action cycles style and shows hint`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("Portrait style:"))
        assertTrue(events.any { it.startsWith("portrait.style.selected.") })
        assertTrue(effects.isNotEmpty())
    }

    @Test
    fun `pro action toggles pro variant`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        val signal = controller.handle(ModeIntent.ProActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(events.any { it.startsWith("portrait.pro-variant.") })
        assertTrue(controller.snapshot.value.state.isProVariantActive)
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

        assertEquals("Portrait capture in progress", controller.snapshot.value.state.headline)
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

        assertEquals("Portrait saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.PORTRAIT, snapshot.id)
        assertEquals("Portrait", snapshot.uiSpec.title)
        assertEquals("Capture Portrait", snapshot.uiSpec.shutterLabel)
        assertEquals("Cycle Portrait Style", snapshot.uiSpec.secondaryActionLabel)
        assertEquals("Cycle Frame", snapshot.uiSpec.tertiaryActionLabel)
        assertTrue(snapshot.state.isTertiaryActionEnabled)
        assertTrue(snapshot.state.isProActionEnabled)
    }

    @Test
    fun `snapshot headline after enter reflects depth capability`(): Unit = runBlocking {
        val controller = createController()

        controller.onEnter()

        val headline = controller.snapshot.value.state.headline
        assertTrue(
            headline == "Portrait mode active" || headline == "Portrait focus active",
            "Expected depth-related headline, got: $headline"
        )
    }

    @Test
    fun `depth effect headline when depth supported`(): Unit = runBlocking {
        val controller = createController(
            supportsPortraitDepthEffect = true
        )

        controller.onEnter()

        assertEquals("Portrait mode active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `focus headline when depth not supported`(): Unit = runBlocking {
        val controller = createController(
            supportsPortraitDepthEffect = false
        )

        controller.onEnter()

        assertEquals("Portrait focus active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `style cycling wraps around`(): Unit = runBlocking {
        val controller = createController()

        // Cycle through all styles and back to first
        for (i in 0 until 8) {
            controller.handle(ModeIntent.SecondaryActionPressed)
        }

        // Should be back to original style
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait-original", metadata["style"])
    }

    @Test
    fun `pro variant toggle twice returns to standard`(): Unit = runBlocking {
        val controller = createController()

        controller.handle(ModeIntent.ProActionPressed)
        assertTrue(controller.snapshot.value.state.isProVariantActive)

        controller.handle(ModeIntent.ProActionPressed)
        assertFalse(controller.snapshot.value.state.isProVariantActive)
    }

    @Test
    fun `pro variant adds control mode metadata`(): Unit = runBlocking {
        val controller = createController()

        controller.handle(ModeIntent.ProActionPressed)
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags

        assertEquals("pro", metadata["modeVariant"])
        assertEquals("manual", metadata["controlMode"])
        assertEquals("metadata-draft", metadata["manualDraftState"])
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
        supportsPortraitDepthEffect: Boolean = true,
        livePhotoEnabled: Boolean = false
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities.copy(
                        supportsPortraitDepthEffect = supportsPortraitDepthEffect
                    ),
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged,
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings(
                        photo = com.opencamera.core.settings.PhotoSettings(
                            livePhotoEnabledByDefault = livePhotoEnabled
                        )
                    )
                )
            }
        )
        return PortraitModePlugin().create(context)
    }
}

private fun assertFalse(value: Boolean, message: String = "Expected false") {
    assertEquals(false, value, message)
}
