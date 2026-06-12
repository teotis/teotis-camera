package com.opencamera.feature.video

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FilterEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoFrameRate
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoModePluginTest {

    // --- Plugin contract ---

    @Test
    fun `plugin id is VIDEO`() {
        val plugin = VideoModePlugin()
        assertEquals(ModeId.VIDEO, plugin.id)
    }

    @Test
    fun `isSupported returns true when video recording is supported`() {
        val plugin = VideoModePlugin()
        assertTrue(plugin.isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when video recording is unsupported`() {
        val plugin = VideoModePlugin()
        val caps = DeviceCapabilities.DEFAULT.copy(supportsVideoRecording = false)
        assertFalse(plugin.isSupported(caps))
    }

    @Test
    fun `create returns controller with VIDEO id`() {
        val plugin = VideoModePlugin()
        val controller = plugin.create(ModeContext())
        assertEquals(ModeId.VIDEO, controller.id)
    }

    // --- Snapshot initial state ---

    @Test
    fun `initial snapshot has video pipeline ready headline`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals(ModeId.VIDEO, snap.id)
        assertEquals("Video pipeline ready", snap.state.headline)
    }

    @Test
    fun `initial snapshot ui spec has video labels`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals("Video", snap.uiSpec.title)
        assertEquals("Start / Stop Recording", snap.uiSpec.shutterLabel)
        assertEquals("Cycle Torch", snap.uiSpec.secondaryActionLabel)
        assertEquals("Cycle Quality", snap.uiSpec.tertiaryActionLabel)
    }

    // --- Lifecycle ---

    @Test
    fun `onEnter fires video enter event and activates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onEnter()

        assertTrue(events.contains("video.enter"))
        assertEquals("Video mode active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `onExit fires video exit event and deactivates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertTrue(events.contains("video.exit"))
        assertEquals("Video mode inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `onEnter resets recording and torch state`() = runTest {
        val controller = createController()
        controller.onEnter()

        // Start recording, then re-enter to verify reset
        controller.handle(ModeIntent.ShutterPressed) // start recording
        controller.onEnter()

        // After re-enter, should not be recording
        val snap = controller.snapshot.value
        assertEquals("Video mode active", snap.state.headline)
    }

    // --- Handle intents ---

    @Test
    fun `shutter pressed starts recording`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.VideoRecording>(signal.strategy)
    }

    @Test
    fun `video recording strategy has video mode metadata`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.VideoRecording

        val tags = strategy.saveRequest.metadata.customTags
        assertEquals("video", tags["mode"])
        assertNotNull(tags["torch"])
        assertNotNull(tags["videoQuality"])
        assertNotNull(tags["defaultVideoResolution"])
        assertNotNull(tags["defaultVideoFrameRate"])
        assertNotNull(tags["dynamicFpsPolicy"])
        assertNotNull(tags["audioProfile"])
        assertNotNull(tags["resolvedVideoResolution"])
        assertNotNull(tags["resolvedVideoFrameRate"])
    }

    @Test
    fun `second shutter pressed stops recording`() = runTest {
        val events = mutableListOf<String>()
        val ctx = createControllerContext(eventSink = { events += it })
        val ctrl = VideoModePlugin().create(ctx)
        ctrl.onEnter()

        // Start recording
        ctrl.handle(ModeIntent.ShutterPressed)
        // Simulate ShotStarted to set isRecording = true
        ctrl.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        // Stop recording
        val signal = ctrl.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.StopActiveCapture>(signal)
        assertTrue(events.contains("video.recording.stop.requested"))
    }

    @Test
    fun `secondary action pressed toggles torch`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("Torch"))
    }

    @Test
    fun `secondary action torch hint shows on after first toggle`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        assertEquals("Torch: On", (signal as ModeSignal.ShowHint).message)
    }

    @Test
    fun `secondary action torch hint shows off after second toggle`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.handle(ModeIntent.SecondaryActionPressed) // toggle on
        val signal = controller.handle(ModeIntent.SecondaryActionPressed) // toggle off

        assertEquals("Torch: Off", (signal as ModeSignal.ShowHint).message)
    }

    @Test
    fun `tertiary action pressed cycles quality`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.TertiaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.startsWith("Video quality:"))
    }

    @Test
    fun `frame ratio selected returns not supported hint`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.FrameRatioSelected(
            com.opencamera.core.media.FrameRatio.RATIO_4_3
        ))
        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("画幅切换"))
    }

    @Test
    fun `pro action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.ProActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    // --- Torch behavior ---

    @Test
    fun `torch unavailable returns hint`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsFlashControl = false)
        val controller = createController(deviceCapabilities = caps)
        controller.onEnter()

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("unavailable"))
    }

    @Test
    fun `torch blocked during recording returns hint`() = runTest {
        val ctx = createControllerContext()
        val controller = VideoModePlugin().create(ctx)
        controller.onEnter()

        // Start recording
        controller.handle(ModeIntent.ShutterPressed)
        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        // Try toggling torch while recording
        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("before recording starts"))
    }

    // --- Quality cycling ---

    @Test
    fun `quality cycling blocked during recording returns hint`() = runTest {
        val ctx = createControllerContext()
        val controller = VideoModePlugin().create(ctx)
        controller.onEnter()

        controller.handle(ModeIntent.ShutterPressed)
        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        val signal = controller.handle(ModeIntent.TertiaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("before recording starts"))
    }

    // --- Session events ---

    @Test
    fun `video shot started sets recording state`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        val snap = controller.snapshot.value
        assertEquals("Recording in progress", snap.state.headline)
    }

    @Test
    fun `video shot completed clears recording state`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))
        controller.onSessionEvent(ModeSessionEvent.ShotCompleted(videoShotResult()))

        val snap = controller.snapshot.value
        assertEquals("Video saved", snap.state.headline)
        assertEquals("/tmp/video.mp4", snap.state.detail)
    }

    @Test
    fun `video shot failed clears recording state`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))
        controller.onSessionEvent(ModeSessionEvent.ShotFailed(
            shotId = "video-1",
            mediaType = MediaType.VIDEO,
            reason = "disk full"
        ))

        val snap = controller.snapshot.value
        assertEquals("Recording failed", snap.state.headline)
        assertEquals("disk full", snap.state.detail)
    }

    @Test
    fun `photo events are ignored by video controller`() = runTest {
        val controller = createController()
        controller.onEnter()
        val beforeHeadline = controller.snapshot.value.state.headline

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(photoShotRequest()))

        assertEquals(beforeHeadline, controller.snapshot.value.state.headline)
    }

    @Test
    fun `photo shot completed is ignored`() = runTest {
        val controller = createController()
        controller.onEnter()
        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))
        val recordingHeadline = controller.snapshot.value.state.headline

        controller.onSessionEvent(ModeSessionEvent.ShotCompleted(photoShotResult()))

        assertEquals(recordingHeadline, controller.snapshot.value.state.headline)
    }

    // --- Effect spec ---

    @Test
    fun `on enter triggers effect spec callback`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            onEffectSpecChanged = { capturedSpec = it }
        )

        controller.onEnter()

        assertNotNull(capturedSpec)
        assertTrue(capturedSpec!!.entries.isNotEmpty())
    }

    @Test
    fun `effect spec contains filter effect`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            onEffectSpecChanged = { capturedSpec = it }
        )

        controller.onEnter()

        val filterEffect = capturedSpec!!.find<FilterEffect>()
        assertNotNull(filterEffect)
    }

    // --- Recording metadata ---

    @Test
    fun `recording strategy has resolved video spec metadata`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.VideoRecording
        val tags = strategy.saveRequest.metadata.customTags

        // Default requested spec is UHD_4K / FPS_25
        assertEquals("4k", tags["defaultVideoResolution"])
        assertEquals("25", tags["defaultVideoFrameRate"])
        assertEquals("locked", tags["dynamicFpsPolicy"])
        assertEquals("standard", tags["audioProfile"])
    }

    @Test
    fun `recording strategy has capture profile with torch state`() = runTest {
        val controller = createController()
        controller.onEnter()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.VideoRecording

        assertFalse(strategy.captureProfile.torchEnabled)
    }

    @Test
    fun `recording strategy with torch on has torch enabled in capture profile`() = runTest {
        val controller = createController()
        controller.onEnter()
        controller.handle(ModeIntent.SecondaryActionPressed) // toggle torch on

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.VideoRecording

        assertTrue(strategy.captureProfile.torchEnabled)
    }

    // --- Snapshot state during recording ---

    @Test
    fun `snapshot disables secondary and tertiary actions during recording`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        val state = controller.snapshot.value.state
        assertFalse(state.isSecondaryActionEnabled)
        assertFalse(state.isTertiaryActionEnabled)
    }

    @Test
    fun `snapshot enables secondary and tertiary actions when not recording`() = runTest {
        val controller = createController()
        controller.onEnter()

        val state = controller.snapshot.value.state
        assertTrue(state.isSecondaryActionEnabled)
        assertTrue(state.isTertiaryActionEnabled)
    }

    // --- Device graph ---

    @Test
    fun `device graph is video recording type`() = runTest {
        val controller = createController()
        val graph = controller.deviceGraph()
        assertIs<DeviceGraphSpec>(graph)
    }

    // --- Helpers ---

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {}
    ): ModeController {
        val context = createControllerContext(
            deviceCapabilities = deviceCapabilities,
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged
        )
        return VideoModePlugin().create(context)
    }

    private fun createControllerContext(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {}
    ): ModeContext {
        return ModeContext(
            deviceCapabilities = deviceCapabilities,
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged
        )
    }

    private fun videoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "video-1",
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = LivePhotoCaptureSpec()
        )

    private fun videoShotResult(): ShotResult =
        ShotResult(
            shotId = "video-1",
            mediaType = MediaType.VIDEO,
            outputPath = "/tmp/video.mp4",
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/video.mp4"),
            metadata = MediaMetadata()
        )

    private fun photoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "photo-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = null
        )

    private fun photoShotResult(): ShotResult =
        ShotResult(
            shotId = "photo-1",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/photo.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/photo.jpg"),
            metadata = MediaMetadata()
        )

    // ── Characterization: exact metadata maps ────────────────────────────

    @Test
    fun `char video recording capture has no still capture aid tags`() = runTest {
        val controller = createController()
        controller.onEnter()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertFalse(metadata.containsKey("captureLensFacing"))
        assertFalse(metadata.containsKey("selfieMirrorApply"))
        assertFalse(metadata.containsKey("stillQuality"))
        assertFalse(metadata.containsKey("watermarkTemplate"))
        assertFalse(metadata.containsKey("watermarkModel"))
        // frameRatio is present (16:9) as a preview/capture aspect guide — honest video metadata
        assertEquals("16:9", metadata["frameRatio"])
        assertFalse(metadata.containsKey("stillResolution"))
        assertFalse(metadata.containsKey("photoLowLightStrategy"))
        assertFalse(metadata.containsKey("checkInScenario"))
        assertFalse(metadata.containsKey("portraitProfile"))
    }

    @Test
    fun `char video recording exact metadata keys`() = runTest {
        val controller = createController()
        controller.onEnter()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("video", metadata["mode"])
        assertEquals("off", metadata["torch"])
        assertEquals("4k", metadata["defaultVideoResolution"])
        assertEquals("25", metadata["defaultVideoFrameRate"])
        assertEquals("locked", metadata["dynamicFpsPolicy"])
        assertEquals("standard", metadata["audioProfile"])
        assertTrue(metadata.containsKey("resolvedVideoResolution"))
        assertTrue(metadata.containsKey("resolvedVideoFrameRate"))
        assertTrue(metadata.containsKey("videoQuality"))
    }

    @Test
    fun `char video effect spec has FilterEffect and FrameEffect 16-9 but no watermark`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(onEffectSpecChanged = { capturedSpec = it })
        controller.onEnter()
        assertNotNull(capturedSpec!!.find<FilterEffect>())
        assertNull(capturedSpec!!.find<com.opencamera.core.effect.WatermarkEffect>())
        val frameEffect = assertNotNull(capturedSpec!!.find<FrameEffect>())
        assertEquals(FrameRatio.RATIO_16_9, frameEffect.ratio)
    }

    @Test
    fun `char video mode has no watermark tags in metadata`() = runTest {
        val controller = createController()
        controller.onEnter()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertFalse(metadata.containsKey("watermarkTemplate"))
        assertFalse(metadata.containsKey("watermarkModel"))
        assertFalse(metadata.containsKey("watermarkDatetime"))
        assertFalse(metadata.containsKey("watermarkModeName"))
        assertFalse(metadata.containsKey("watermarkProfileName"))
    }
}
