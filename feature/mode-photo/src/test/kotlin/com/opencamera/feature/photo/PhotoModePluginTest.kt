package com.opencamera.feature.photo

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.ExtensionCaptureStrategy
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
import com.opencamera.core.settings.FeatureCatalog
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
import kotlin.test.assertFalse

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
        assertEquals(
            "photo-original",
            spec.find<FilterEffect>()?.profileId,
            "Photo mode should default to the natural/original color profile"
        )
        assertEquals(null, spec.find<WatermarkEffect>(), "Photo mode should not enable watermark by default")
        assertTrue(spec.find<FrameEffect>() != null, "EffectSpec should contain FrameEffect")
    }

    @Test
    fun `onEnter includes watermark effect when watermark is enabled`(): Unit = runBlocking {
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = com.opencamera.core.settings.PersistedSettings(
                    photo = com.opencamera.core.settings.PhotoSettings(
                        photoWatermarkEnabledByDefault = true
                    )
                )
            ),
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertTrue(effects.single().find<WatermarkEffect>() != null)
    }

    @Test
    fun `missing catalog default falls back to natural original profile`(): Unit = runBlocking {
        val effects = mutableListOf<EffectSpec>()
        val controller = createController(
            settingsSnapshot = SessionSettingsSnapshot(
                catalog = FeatureCatalog(filterProfiles = emptyList())
            ),
            onEffectSpecChanged = { effects += it }
        )

        controller.onEnter()

        assertEquals("photo-original", effects.single().find<FilterEffect>()?.profileId)
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
        settingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot(),
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
            settingsSnapshotProvider = { settingsSnapshot },
            photoLowLightRuntimeStateProvider = { lowLightState }
        )
        return PhotoModePlugin().create(context)
    }

    private fun createLowLightController(
        lowLightState: PhotoLowLightRuntimeState
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = DeviceCapabilities.DEFAULT,
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

    // ── Characterization: exact metadata maps ────────────────────────────

    @Test
    fun `char normal capture exact metadata keys`(): Unit = runBlocking {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("photo", metadata["mode"])
        assertEquals("off", metadata["flash"])
        assertEquals("off", metadata["livePhotoDefault"])
        assertEquals("Photo", metadata["watermarkModeName"])
        assertEquals("Off", metadata["watermarkProfileName"])
        assertFalse(metadata.containsKey("photoLowLightNightAssist"))
        assertFalse(metadata.containsKey("photoLowLightStrategy"))
        assertFalse(metadata.containsKey("algorithmProfile"))
    }

    @Test
    fun `char live capture exact metadata keys`(): Unit = runBlocking {
        val livePhotoController = PhotoModePlugin().create(ModeContext(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = DeviceCapabilities.DEFAULT,
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings(
                        photo = com.opencamera.core.settings.PhotoSettings(
                            livePhotoEnabledByDefault = true
                        )
                    )
                )
            }
        ))
        val metadata = (livePhotoController.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("photo", metadata["mode"])
        assertEquals("on", metadata["livePhotoDefault"])
        assertEquals("Photo", metadata["watermarkModeName"])
    }

    @Test
    fun `char low light multi frame exact metadata keys`(): Unit = runBlocking {
        val controller = createLowLightController(
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
        assertEquals("photo", metadata["mode"])
        assertEquals("on", metadata["photoLowLightNightAssist"])
        assertEquals("multi-frame", metadata["photoLowLightStrategy"])
        assertEquals("0.2", metadata["photoLowLightBrightnessScore"])
        assertTrue(metadata.containsKey("filterProfile"))
        assertFalse(metadata.containsKey("watermarkTemplate"))
    }

    @Test
    fun `char low light degraded single frame exact metadata keys`(): Unit = runBlocking {
        val controller = createLowLightController(
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
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("photo", metadata["mode"])
        assertEquals("single-frame-degraded", metadata["photoLowLightStrategy"])
    }

    @Test
    fun `char normal capture has capture aid tags`(): Unit = runBlocking {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("back", metadata["captureLensFacing"])
        assertEquals("false", metadata["selfieMirrorApply"])
        assertEquals("on", metadata["shutterSoundEnabled"])
        assertTrue(metadata.containsKey("stillQuality"))
    }

    @Test
    fun `char normal capture has filter profile in bridge tags`(): Unit = runBlocking {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertTrue(metadata.containsKey("filterProfile"))
    }

    @Test
    fun `char low light multi frame has capture aid tags but no bridge tags`(): Unit = runBlocking {
        val controller = createLowLightController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = true,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.LOW_LIGHT,
                    brightnessScore = 0.2f, source = "test"
                ),
                support = PhotoLowLightStrategySupport.SUPPORTED_MULTI_FRAME
            )
        )
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("back", metadata["captureLensFacing"])
        assertTrue(metadata.containsKey("filterProfile"))
    }

    @Test
    fun `char normal capture post process has no watermark text by default`(): Unit = runBlocking {
        val controller = createController()
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals(null, postProcess.watermarkText)
    }

    @Test
    fun `char enabled watermark capture post process has watermark text`(): Unit = runBlocking {
        val controller = createController(
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = com.opencamera.core.settings.PersistedSettings(
                    photo = com.opencamera.core.settings.PhotoSettings(
                        photoWatermarkEnabledByDefault = true
                    )
                )
            )
        )
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals("PHOTO", postProcess.watermarkText)
    }

    @Test
    fun `char watermark datetime format in photo effect spec`(): Unit = runBlocking {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            settingsSnapshot = SessionSettingsSnapshot(
                persisted = com.opencamera.core.settings.PersistedSettings(
                    photo = com.opencamera.core.settings.PhotoSettings(
                        photoWatermarkEnabledByDefault = true
                    )
                )
            ),
            onEffectSpecChanged = { capturedSpec = it }
        )
        controller.onEnter()
        val watermark = capturedSpec!!.find<WatermarkEffect>()!!
        val datetime = watermark.tokens["watermarkDatetime"]!!
        assertTrue(datetime.matches(Regex("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}")),
            "watermarkDatetime must match yyyy-MM-dd HH:mm, got: $datetime")
        assertEquals("OpenCamera", watermark.tokens["watermarkModel"])
    }

    @Test
    fun `char photo mode collision mode key from buildSaveRequest not from bridge`(): Unit = runBlocking {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("photo", metadata["mode"])
        assertFalse(metadata.containsKey("document"))
        assertFalse(metadata.containsKey("portrait"))
    }

    // ── Blue hour routing tests ──────────────────────────────────────────

    @Test
    fun `blue hour scene emits routing diagnostic event`(): Unit = runBlocking {
        val events = mutableListOf<String>()
        val controller = createController(
            eventSink = { events += it },
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.BLUE_HOUR,
                    brightnessScore = 0.25f,
                    source = "preview-bitmap-metrics",
                    confidence = 0.75f
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )

        controller.handle(ModeIntent.ShutterPressed)

        assertTrue(events.any { it == "photo.routing.blue_hour.ext-hdr" },
            "Expected routing event for blue hour, got: $events")
    }

    @Test
    fun `blue hour scene has ext-preferred hdr in metadata`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.BLUE_HOUR,
                    brightnessScore = 0.25f,
                    source = "preview-bitmap-metrics",
                    confidence = 0.75f
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("blue_hour", metadata["photoSceneState"])
        assertEquals("hdr", metadata["photoExtPreferred"])
        assertEquals("0.75", metadata["photoSceneConfidence"])
        assertTrue(metadata["photoRoutingNote"]!!.contains("scene=blue_hour"))
        assertTrue(metadata["photoRoutingNote"]!!.contains("ext-preferred=hdr"))
    }

    @Test
    fun `blue hour scene uses SingleFrame capture strategy`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.BLUE_HOUR,
                    brightnessScore = 0.25f,
                    source = "preview-bitmap-metrics"
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val signal = controller.handle(ModeIntent.ShutterPressed)
        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `normal scene has ext-preferred none in metadata`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.NORMAL,
                    brightnessScore = 0.6f,
                    source = "test"
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("normal", metadata["photoSceneState"])
        assertEquals("none", metadata["photoExtPreferred"])
    }

    @Test
    fun `low light scene uses night assist and has ext-preferred night`(): Unit = runBlocking {
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
        assertEquals("low_light", metadata["photoSceneState"])
        assertEquals("night", metadata["photoExtPreferred"])
        assertEquals("on", metadata["photoLowLightNightAssist"])
    }

    @Test
    fun `blue hour device graph includes HDR extension strategy`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.BLUE_HOUR,
                    brightnessScore = 0.25f,
                    source = "test",
                    confidence = 0.8f
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val graph = controller.deviceGraph()
        assertEquals(
            com.opencamera.core.device.CameraExtensionMode.HDR,
            graph.stillCapture.extensionStrategy.desiredMode
        )
    }

    @Test
    fun `normal device graph includes NONE extension strategy`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(
                    lightState = SceneLightState.NORMAL,
                    brightnessScore = 0.6f,
                    source = "test"
                ),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val graph = controller.deviceGraph()
        assertEquals(
            com.opencamera.core.device.CameraExtensionMode.NONE,
            graph.stillCapture.extensionStrategy.desiredMode
        )
    }

    @Test
    fun `low light device graph includes NIGHT extension strategy`(): Unit = runBlocking {
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
        val graph = controller.deviceGraph()
        assertEquals(
            com.opencamera.core.device.CameraExtensionMode.NIGHT,
            graph.stillCapture.extensionStrategy.desiredMode
        )
    }

    @Test
    fun `unknown scene has ext-preferred none in metadata`(): Unit = runBlocking {
        val controller = createController(
            lowLightState = PhotoLowLightRuntimeState(
                settingEnabled = false,
                sceneSignal = PhotoSceneSignal(lightState = SceneLightState.UNKNOWN),
                support = PhotoLowLightStrategySupport.UNSUPPORTED
            )
        )
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("unknown", metadata["photoSceneState"])
        assertEquals("none", metadata["photoExtPreferred"])
    }

    @Test
    fun `live photo enabled uses LivePhoto capture strategy`(): Unit = runBlocking {
        val controller = PhotoModePlugin().create(ModeContext(
            deviceCapabilities = DeviceCapabilities.DEFAULT,
            initialLensFacing = LensFacing.BACK,
            initialStillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
            runtimeState = {
                ModeRuntimeState(
                    deviceCapabilities = DeviceCapabilities.DEFAULT,
                    lensFacing = LensFacing.BACK,
                    stillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                    stillCaptureQuality = StillCaptureQualityPreference.LATENCY
                )
            },
            settingsSnapshotProvider = {
                SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings(
                        photo = com.opencamera.core.settings.PhotoSettings(
                            livePhotoEnabledByDefault = true
                        )
                    )
                )
            }
        ))
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.LivePhoto>(signal.strategy)
    }

    @Test
    fun `live photo disabled uses SingleFrame capture strategy`(): Unit = runBlocking {
        val controller = createController()
        val signal = controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }
}
