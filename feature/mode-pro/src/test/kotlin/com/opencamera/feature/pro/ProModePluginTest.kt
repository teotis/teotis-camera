package com.opencamera.feature.pro

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

class ProModePluginTest {

    @Test
    fun `plugin id is PRO`() {
        assertEquals(ModeId.PRO, ProModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(ProModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, ProModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits pro enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("pro.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onEnter headline differentiates manual vs assist`(): Unit = runBlocking {
        val controllerManual = createController(
            supportsManualControls = true
        )
        controllerManual.onEnter()
        assertEquals("Pro mode active", controllerManual.snapshot.value.state.headline)

        val controllerAssist = createController(
            supportsManualControls = false
        )
        controllerAssist.onEnter()
        assertEquals("Pro assist active", controllerAssist.snapshot.value.state.headline)
    }

    @Test
    fun `onExit emits pro exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("pro.exit"), events)
        assertEquals("Pro mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `shutter pressed returns SubmitCapture with SingleFrame always`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes pro mode in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("pro", metadata["mode"])
        assertTrue(metadata.containsKey("preset"))
        assertTrue(metadata.containsKey("controlMode"))
        assertTrue(metadata.containsKey("manualDraftState"))
        assertTrue(metadata.containsKey("flash"))
        assertTrue(metadata.containsKey("stillResolution"))
    }

    @Test
    fun `shutter pressed save path is Pro`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertEquals("Pictures/OpenCamera/Pro", signal.strategy.saveRequest.relativePath)
        assertEquals("OpenCamera_PRO", signal.strategy.saveRequest.fileNamePrefix)
    }

    @Test
    fun `shutter pressed manual preset has exif overrides`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = true)

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertTrue(exif.containsKey("ISOSpeedRatings"))
        assertTrue(exif.containsKey("ExposureTime"))
        assertTrue(exif.containsKey("WhiteBalance"))
        assertTrue(exif.containsKey("FocusMode"))
    }

    @Test
    fun `shutter pressed assisted preset has no manual exif overrides`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = false)

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertTrue(exif.isEmpty(), "Assisted preset should have no manual EXIF overrides")
    }

    @Test
    fun `shutter pressed manual watermark text includes preset label`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = true)

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertTrue(signal.strategy.postProcessSpec.watermarkText!!.contains("PRO"))
    }

    @Test
    fun `shutter pressed assisted watermark text includes assist label`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = false)

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertTrue(signal.strategy.postProcessSpec.watermarkText!!.contains("Assist"))
    }

    @Test
    fun `effect spec has no filter effect`(): Unit = runBlocking {
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(onEffectSpecChanged = { effects += it })

        controller.onEnter()

        val spec = effects[0]
        assertEquals(null, spec.find<FilterEffect>(), "Pro mode should not have FilterEffect")
    }

    @Test
    fun `secondary action cycles preset and shows hint`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("Preset:"))
        assertTrue(events.any { it.startsWith("pro.preset.selected.") })
    }

    @Test
    fun `pro action returns None for pro mode`(): Unit = runBlocking {
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

        assertEquals("Pro capture in progress", controller.snapshot.value.state.headline)
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

        assertEquals("Pro photo saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.PRO, snapshot.id)
        assertEquals("Pro", snapshot.uiSpec.title)
        assertEquals("Capture Pro Still", snapshot.uiSpec.shutterLabel)
        assertEquals("Cycle Preset", snapshot.uiSpec.secondaryActionLabel)
        assertEquals("Cycle Frame", snapshot.uiSpec.tertiaryActionLabel)
        assertTrue(snapshot.state.isTertiaryActionEnabled)
    }

    @Test
    fun `manual presets have iso exposure and wb`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = true)

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides

        // First manual preset is "neutral" with ISO 100
        assertEquals("100", exif["ISOSpeedRatings"])
        assertEquals("1/125s", exif["ExposureTime"])
        assertEquals("5200K", exif["WhiteBalance"])
    }

    @Test
    fun `cycle through manual presets changes iso`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = true)

        // Move to second preset (street: ISO 200)
        controller.handle(ModeIntent.SecondaryActionPressed)
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides

        assertEquals("200", exif["ISOSpeedRatings"])
    }

    @Test
    fun `preset cycling wraps around`(): Unit = runBlocking {
        val controller = createController(supportsManualControls = true)

        // Cycle through 3 manual presets + 1 more = back to first
        for (i in 0 until 4) {
            controller.handle(ModeIntent.SecondaryActionPressed)
        }

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertEquals("100", exif["ISOSpeedRatings"])
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
        supportsManualControls: Boolean = true
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities.copy(
                supportsManualControls = supportsManualControls
            ),
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities.copy(
                        supportsManualControls = supportsManualControls
                    ),
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged,
            settingsSnapshotProvider = { SessionSettingsSnapshot() }
        )
        return ProModePlugin().create(context)
    }
}
