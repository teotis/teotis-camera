package com.opencamera.feature.humanistic

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
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

class HumanisticModePluginTest {

    @Test
    fun `plugin id is HUMANISTIC`() {
        assertEquals(ModeId.HUMANISTIC, HumanisticModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(HumanisticModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, HumanisticModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits humanistic enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("humanistic.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<FilterEffect>() != null, "EffectSpec should contain FilterEffect")
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onEnter does not include PortraitEffect`(): Unit = runBlocking {
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(onEffectSpecChanged = { effects += it })

        controller.onEnter()

        val spec = effects[0]
        assertEquals(null, spec.find<com.opencamera.core.effect.PortraitEffect>(),
            "Humanistic mode should not have PortraitEffect")
    }

    @Test
    fun `onExit emits humanistic exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("humanistic.exit"), events)
        assertEquals("Humanistic mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `onExit includes resume hint in detail`(): Unit = runBlocking {
        val controller = createController()

        controller.onExit()

        assertTrue(
            controller.snapshot.value.state.detail.contains("Switch back to Humanistic"),
            "Exit detail should include resume hint"
        )
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
        val controller = createController(livePhotoEnabled = true)

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.LivePhoto>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes humanistic mode in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("humanistic", metadata["mode"])
        assertEquals("humanistic", metadata["modeDisplay"])
        assertTrue(metadata.containsKey("style"))
        assertTrue(metadata.containsKey("algorithmProfile"))
        assertTrue(metadata.containsKey("watermarkTemplate"))
        assertTrue(metadata.containsKey("stillResolution"))
        assertTrue(metadata.containsKey("modeVariant"))
    }

    @Test
    fun `shutter pressed post process has humanistic exif overrides`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertEquals("Humanistic", exif["SceneCaptureType"])
        assertTrue(exif.containsKey("HumanisticStyle"))
    }

    @Test
    fun `shutter pressed post process has algorithm profile`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertTrue(signal.strategy.postProcessSpec.algorithmProfile != null)
    }

    @Test
    fun `shutter pressed save path is Humanistic`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertEquals("Pictures/OpenCamera/Humanistic", signal.strategy.saveRequest.relativePath)
        assertEquals("OpenCamera_HUMANISTIC", signal.strategy.saveRequest.fileNamePrefix)
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
        assertTrue(signal.message.contains("Humanistic style:"))
        assertTrue(events.any { it.startsWith("humanistic.style.selected.") })
        assertTrue(effects.isNotEmpty())
    }

    @Test
    fun `pro action toggles pro variant`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        val signal = controller.handle(ModeIntent.ProActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(events.any { it.startsWith("humanistic.pro-variant.") })
        assertTrue(controller.snapshot.value.state.isProVariantActive)
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
    fun `pro variant adds variant metadata`(): Unit = runBlocking {
        val controller = createController()

        controller.handle(ModeIntent.ProActionPressed)
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags

        assertEquals("pro", metadata["modeVariant"])
    }

    @Test
    fun `pro variant exif includes HumanisticVariant`(): Unit = runBlocking {
        val controller = createController()

        controller.handle(ModeIntent.ProActionPressed)
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides

        assertTrue(exif.containsKey("HumanisticVariant"))
        assertTrue(exif.containsKey("ManualDraft"))
    }

    @Test
    fun `pro variant watermark text includes Pro label`(): Unit = runBlocking {
        val controller = createController()

        controller.handle(ModeIntent.ProActionPressed)
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture

        assertTrue(signal.strategy.postProcessSpec.watermarkText!!.contains("Pro"))
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

        assertEquals("Humanistic capture in progress", controller.snapshot.value.state.headline)
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

        assertEquals("Humanistic photo saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.HUMANISTIC, snapshot.id)
        assertEquals("Humanistic", snapshot.uiSpec.title)
        assertEquals("Capture Humanistic", snapshot.uiSpec.shutterLabel)
        assertEquals("Cycle Humanistic Style", snapshot.uiSpec.secondaryActionLabel)
        assertEquals("Cycle Frame", snapshot.uiSpec.tertiaryActionLabel)
        assertTrue(snapshot.state.isTertiaryActionEnabled)
        assertTrue(snapshot.state.isProActionEnabled)
    }

    @Test
    fun `snapshot headline after enter is active`(): Unit = runBlocking {
        val controller = createController()

        controller.onEnter()

        assertEquals("Humanistic mode active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `style cycling wraps around`(): Unit = runBlocking {
        val controller = createController()

        // 5 default humanistic styles; cycle 5 times to return to first
        for (i in 0 until 5) {
            controller.handle(ModeIntent.SecondaryActionPressed)
        }

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("humanistic-original", metadata["style"])
    }

    @Test
    fun `default style is humanistic-original`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags

        assertEquals("humanistic-original", metadata["style"])
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
        livePhotoEnabled: Boolean = false
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
        return HumanisticModePlugin().create(context)
    }
}

private fun assertFalse(value: Boolean, message: String = "Expected false") {
    assertEquals(false, value, message)
}
