package com.opencamera.feature.checkin

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
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CheckInModePluginTest {

    @Test
    fun `plugin id is CHECK_IN`() {
        assertEquals(ModeId.CHECK_IN, CheckInModePlugin().id)
    }

    @Test
    fun `isSupported returns true for default capabilities`() {
        assertTrue(CheckInModePlugin().isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when supportsStillCapture is false`() {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertEquals(false, CheckInModePlugin().isSupported(caps))
    }

    @Test
    fun `onEnter emits checkin enter and calls onEffectSpecChanged`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            eventSink = { events += it },
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals(listOf("checkin.enter"), events)
        assertEquals(1, effects.size)
        val spec = effects[0]
        assertTrue(spec.find<FilterEffect>() != null, "EffectSpec should contain FilterEffect")
        assertTrue(spec.find<PortraitEffect>() != null, "EffectSpec should contain PortraitEffect")
        assertTrue(spec.find<WatermarkEffect>() != null, "EffectSpec should contain WatermarkEffect")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onExit emits checkin exit and updates snapshot`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertEquals(listOf("checkin.exit"), events)
        assertEquals("Check-in inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `default scenario shutter pressed returns SubmitCapture with SingleFrame`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed includes check-in mode in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("check-in", metadata["mode"])
        assertEquals("portrait", metadata["checkInScenario"])
        assertEquals("portrait", metadata["compatMode"])
        assertEquals("Check-in", metadata["watermarkModeName"])
        assertTrue(metadata.containsKey("style"))
        assertTrue(metadata.containsKey("subjectTracking"))
        assertTrue(metadata.containsKey("stillResolution"))
        assertTrue(metadata.containsKey("modeVariant"))
        assertTrue(metadata.containsKey("bokehStrength"))
    }

    @Test
    fun `shutter pressed post process has check-in exif overrides`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        val exif = signal.strategy.postProcessSpec.exifOverrides
        assertEquals("Check-in", exif["SceneCaptureType"])
        assertEquals("Portrait", exif["CompatSceneCaptureType"])
        assertTrue(exif.containsKey("CheckInScenario"))
        assertTrue(exif.containsKey("CheckInStyle"))
        assertTrue(exif.containsKey("DepthEffect"))
    }

    @Test
    fun `shutter pressed post process has check-in algorithm profile`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertTrue(signal.strategy.postProcessSpec.algorithmProfile != null)
        assertTrue(signal.strategy.postProcessSpec.algorithmProfile!!.startsWith("checkin-"))
    }

    @Test
    fun `shutter pressed save path is Check-in`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        signal as ModeSignal.SubmitCapture
        assertEquals("Pictures/OpenCamera/Check-in", signal.strategy.saveRequest.relativePath)
        assertEquals("OpenCamera_CHECKIN", signal.strategy.saveRequest.fileNamePrefix)
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
        assertTrue(signal.message.contains("Check-in"), "Hint should contain Check-in")
        assertTrue(events.any { it.startsWith("checkin.style.selected.") })
        assertTrue(effects.isNotEmpty())
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

        assertEquals("Check-in 人像 capture in progress", controller.snapshot.value.state.headline)
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

        assertEquals("Check-in 人像 saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `snapshot has correct ui spec with chinese title`(): Unit = runBlocking {
        val controller = createController()

        val snapshot = controller.snapshot.value

        assertEquals(ModeId.CHECK_IN, snapshot.id)
        assertEquals("打卡", snapshot.uiSpec.title)
        assertEquals("Check-in Capture", snapshot.uiSpec.shutterLabel)
        assertEquals("Cycle Check-in Style", snapshot.uiSpec.secondaryActionLabel)
        assertEquals("Cycle Frame", snapshot.uiSpec.tertiaryActionLabel)
        assertTrue(snapshot.state.isSecondaryActionEnabled)
        assertTrue(snapshot.state.isTertiaryActionEnabled)
    }

    @Test
    fun `depth effect headline when depth supported`(): Unit = runBlocking {
        val controller = createController(supportsPortraitDepthEffect = true)

        controller.onEnter()

        assertTrue(
            controller.snapshot.value.state.headline.contains("Check-in"),
            "Expected Check-in headline, got: ${controller.snapshot.value.state.headline}"
        )
        assertTrue(
            controller.snapshot.value.state.headline.contains("active"),
            "Expected active headline, got: ${controller.snapshot.value.state.headline}"
        )
    }

    @Test
    fun `focus headline when depth not supported`(): Unit = runBlocking {
        val controller = createController(supportsPortraitDepthEffect = false)

        controller.onEnter()

        assertTrue(
            controller.snapshot.value.state.headline.contains("focus"),
            "Expected focus fallback headline, got: ${controller.snapshot.value.state.headline}"
        )
    }

    @Test
    fun `style cycling wraps around all 7 default styles`(): Unit = runBlocking {
        val controller = createController()

        for (i in 0 until 7) {
            controller.handle(ModeIntent.SecondaryActionPressed)
        }

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait-original", metadata["style"])
    }

    @Test
    fun `mode detail communicates spend time for frame quality`(): Unit = runBlocking {
        val controller = createController()

        val detail = controller.snapshot.value.state.detail

        assertTrue(detail.contains("场景"), "Detail should mention scenario")
        assertTrue(detail.contains("Frame"), "Detail should mention frame quality")
    }

    @Test
    fun `pro action toggles scenario between atmosphere and clarity`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ProActionPressed)
        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("全清"), "Should toggle to clarity scenario")

        val signal2 = controller.handle(ModeIntent.ProActionPressed)
        assertIs<ModeSignal.ShowHint>(signal2)
        assertTrue(signal2.message.contains("氛围"), "Should toggle back to atmosphere scenario")
    }

    @Test
    fun `tertiary action cycles frame ratio`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.TertiaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("Frame:"), "Hint should contain Frame: info")
    }

    // ── Full Clear / Clarity scenario tests ──

    @Test
    fun `clarity scenario shutter pressed returns MultiFrame with best-frame metadata`(): Unit = runBlocking {
        val controller = createController(initialScenarioId = "clarity")

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("clarity", metadata["checkInScenario"])
        assertEquals("fullclear", metadata["compatMode"])
    }

    @Test
    fun `portrait scenario shutter pressed returns SingleFrame`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait", metadata["checkInScenario"])
        assertEquals("portrait", metadata["compatMode"])
    }

    @Test
    fun `portrait scenario compatMode is portrait in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait", metadata["compatMode"])
    }

    @Test
    fun `checkInScenario is correctly set in metadata`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait", metadata["checkInScenario"])
    }

    // ── Scenario selection via ScenarioSelected intent ──

    @Test
    fun `selecting people-place scenario updates snapshot headline`(): Unit = runBlocking {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ScenarioSelected("people-place"))
        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("人景"), "Hint should contain 人景 label")

        val headline = controller.snapshot.value.state.headline
        assertTrue(headline.contains("Check-in"), "Headline should mention Check-in")
        assertTrue(headline.contains("active"), "Headline should show active state")
    }

    @Test
    fun `selecting people-place scenario SingleFrame capture has correct metadata`(): Unit = runBlocking {
        val controller = createController()
        controller.handle(ModeIntent.ScenarioSelected("people-place"))

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("people-place", metadata["checkInScenario"])
        assertEquals("portrait", metadata["compatMode"])
    }

    @Test
    fun `selecting object-place scenario SingleFrame capture has correct metadata`(): Unit = runBlocking {
        val controller = createController()
        controller.handle(ModeIntent.ScenarioSelected("object-place"))

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("object-place", metadata["checkInScenario"])
        assertEquals("portrait", metadata["compatMode"])
    }

    @Test
    fun `selecting clarity scenario emits MultiFrame capture`(): Unit = runBlocking {
        val controller = createController()
        controller.handle(ModeIntent.ScenarioSelected("clarity"))

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("clarity", metadata["checkInScenario"])
        assertEquals("fullclear", metadata["compatMode"])
    }

    @Test
    fun `selecting portrait scenario emits SingleFrame capture`(): Unit = runBlocking {
        val controller = createController()
        controller.handle(ModeIntent.ScenarioSelected("portrait"))

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("portrait", metadata["checkInScenario"])
    }

    @Test
    fun `scenario selection dispatches settingsActionSink with UpdateCheckInScenario`(): Unit = runBlocking {
        val actions = mutableListOf<PersistedSettingsAction>()
        val controller = createController(
            settingsActionSink = { action -> actions += action }
        )

        controller.handle(ModeIntent.ScenarioSelected("clarity"))

        val update = actions.filterIsInstance<PersistedSettingsAction.UpdateCheckInScenario>()
        assertEquals(1, update.size, "Should dispatch exactly one UpdateCheckInScenario action")
        assertEquals("clarity", update[0].scenarioId)
    }

    @Test
    fun `toggleScenario also dispatches settingsActionSink`(): Unit = runBlocking {
        val actions = mutableListOf<PersistedSettingsAction>()
        val controller = createController(
            settingsActionSink = { action -> actions += action }
        )

        controller.handle(ModeIntent.ProActionPressed)

        val update = actions.filterIsInstance<PersistedSettingsAction.UpdateCheckInScenario>()
        assertEquals(1, update.size, "Toggle should dispatch UpdateCheckInScenario")
        assertEquals("clarity", update[0].scenarioId, "First toggle should go to clarity")
    }

    @Test
    fun `persisted scenario is restored from settings on controller creation`(): Unit = runBlocking {
        val controller = createController(initialScenarioId = "clarity")

        val headline = controller.snapshot.value.state.headline
        assertTrue(
            headline.contains("Check-in"),
            "Persisted clarity scenario should be active, got: $headline"
        )

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("clarity", metadata["checkInScenario"])
    }

    @Test
    fun `all four scenarios have distinct scenario ids`() {
        val ids = CheckInScenario.entries.map { it.id }
        assertEquals(4, ids.size, "Should have exactly 4 scenarios")
        assertEquals(setOf("portrait", "people-place", "object-place", "clarity"), ids.toSet())
    }

    @Test
    fun `scenario selection does not affect style cycling`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(
            eventSink = { events += it }
        )

        controller.handle(ModeIntent.ScenarioSelected("people-place"))
        controller.handle(ModeIntent.SecondaryActionPressed)

        assertTrue(
            events.any { it.startsWith("checkin.style.selected.people-place.") },
            "Style cycling should use the new scenario id in event"
        )
    }

    // ── Capture strategy and degradation tests ──

    @Test
    fun `clarity shutter pressed returns MultiFrame when multi-frame is supported`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = true
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.MultiFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("fullclear", metadata["compatMode"])
        assertEquals("clarity", metadata["checkInScenario"])
        assertEquals(3, signal.strategy.captureProfile.frameCount)
    }

    @Test
    fun `clarity shutter pressed returns SingleFrame fallback when multi-frame is unsupported`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
        val metadata = signal.strategy.saveRequest.metadata.customTags
        assertEquals("fullclear", metadata["compatMode"])
        assertEquals("clarity", metadata["checkInScenario"])
        assertEquals("single-frame", metadata["captureStrategyFallback"])
        assertEquals("multi-frame-unsupported", metadata["degradationReason"])
    }

    @Test
    fun `clarity degradation fallback preserves fullclear compatMode`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture

        assertEquals("fullclear", signal.strategy.saveRequest.metadata.customTags["compatMode"])
    }

    @Test
    fun `clarity degradation fallback includes degradation metadata tags`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        val metadata = signal.strategy.saveRequest.metadata.customTags

        assertEquals("single-frame", metadata["captureStrategyFallback"])
        assertEquals("multi-frame-unsupported", metadata["degradationReason"])
        assertEquals("single-frame-best-frame", metadata["degradation-policy"])
    }

    @Test
    fun `clarity degradation fallback keeps clarity algorithm profile`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture

        assertEquals("checkin-clarity-best-frame-v1", signal.strategy.postProcessSpec.algorithmProfile)
    }

    @Test
    fun `clarity degraded snapshot headline contains single-frame fallback`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        controller.onEnter()

        val headline = controller.snapshot.value.state.headline
        assertTrue(headline.contains("single-frame fallback"), "Headline should indicate single-frame fallback, got: $headline")
        assertTrue(headline.contains("超清"), "Headline should mention 超清, got: $headline")
    }

    @Test
    fun `clarity degraded detail contains degradation message`(): Unit = runBlocking {
        val controller = createController(
            initialScenarioId = "clarity",
            supportsNightMultiFrame = false
        )

        val detail = controller.snapshot.value.state.detail
        assertTrue(detail.contains("降级"), "Detail should mention 降级, got: $detail")
        assertTrue(detail.contains("超清"), "Detail should mention 超清, got: $detail")
    }

    @Test
    fun `shot started event includes scenario name in headline`(): Unit = runBlocking {
        val controller = createController(initialScenarioId = "people-place")
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

        val headline = controller.snapshot.value.state.headline
        assertTrue(headline.contains("人景"), "Shot started headline should include scenario name 人景, got: $headline")
        assertTrue(headline.contains("capture in progress"), "Should indicate capture in progress, got: $headline")
    }

    @Test
    fun `shot completed event includes scenario name in headline`(): Unit = runBlocking {
        val controller = createController(initialScenarioId = "object-place")
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

        val headline = controller.snapshot.value.state.headline
        assertTrue(headline.contains("物景"), "Shot completed headline should include scenario name 物景, got: $headline")
        assertTrue(headline.contains("saved"), "Should indicate saved, got: $headline")
    }

    @Test
    fun `shot failed event includes scenario name in headline`(): Unit = runBlocking {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(
            ModeSessionEvent.ShotFailed(
                shotId = "test-shot",
                mediaType = MediaType.PHOTO,
                reason = "test error"
            )
        )

        val headline = controller.snapshot.value.state.headline
        assertTrue(headline.contains("人像"), "Shot failed headline should include scenario name 人像, got: $headline")
        assertTrue(headline.contains("capture failed"), "Should indicate capture failed, got: $headline")
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
        settingsActionSink: suspend (PersistedSettingsAction) -> Unit = {},
        supportsPortraitDepthEffect: Boolean = true,
        supportsNightMultiFrame: Boolean = true,
        initialScenarioId: String = "portrait"
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = deviceCapabilities.copy(
                        supportsPortraitDepthEffect = supportsPortraitDepthEffect,
                        supportsNightMultiFrame = supportsNightMultiFrame
                    ),
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged,
            settingsActionSink = settingsActionSink,
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
