package com.opencamera.feature.fullclear

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class FullClearModePluginTest {

    @Test
    fun `plugin id is FULL_CLEAR`() {
        val plugin = FullClearModePlugin()
        assertEquals(ModeId.FULL_CLEAR, plugin.id)
    }

    @Test
    fun `isSupported returns true when still capture is supported`() {
        val plugin = FullClearModePlugin()
        assertTrue(plugin.isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when still capture is unsupported`() {
        val plugin = FullClearModePlugin()
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertFalse(plugin.isSupported(caps))
    }

    @Test
    fun `create returns controller with FULL_CLEAR id`() {
        val plugin = FullClearModePlugin()
        val controller = plugin.create(ModeContext())
        assertEquals(ModeId.FULL_CLEAR, controller.id)
    }

    @Test
    fun `initial snapshot has V1 Full Clear ready headline`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals(ModeId.FULL_CLEAR, snap.id)
        assertTrue(snap.state.headline.contains("V1 Full Clear ready"))
    }

    @Test
    fun `initial snapshot has honest V1 labels in detail`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertTrue(snap.state.detail.contains("focus-bracket-capture V1"))
        assertTrue(snap.state.detail.contains("focus-stack-fusion V1"))
        assertTrue(snap.state.detail.contains("honest best-frame fallback"))
    }

    @Test
    fun `initial snapshot ui spec has Full Clear labels`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals("Full Clear", snap.uiSpec.title)
        assertEquals("Capture Full Clear", snap.uiSpec.shutterLabel)
    }

    @Test
    fun `onEnter fires fullclear enter event and activates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onEnter()

        assertTrue(events.contains("fullclear.enter"))
        assertEquals("Full Clear active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `onExit fires fullclear exit event and deactivates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertTrue(events.contains("fullclear.exit"))
        assertEquals("Full Clear inactive", controller.snapshot.value.state.headline)
    }

    @Test
    fun `shutter pressed returns submit capture with single frame`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed capture strategy has full clear mode metadata`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        val tags = strategy.metadata.customTags
        assertEquals("fullclear", tags["mode"])
        assertEquals("V1", tags["focus-bracket-capture"])
        assertEquals("V1", tags["focus-stack-fusion"])
        assertEquals("honest-best-frame", tags["degradation-policy"])
    }

    @Test
    fun `shutter pressed capture strategy has V1 pipeline tags`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        val pipelineTags = strategy.postProcessSpec.pipelineTags
        assertTrue(pipelineTags.contains("mode:fullclear"))
        assertTrue(pipelineTags.contains("focus-bracket-capture:V1"))
        assertTrue(pipelineTags.contains("focus-stack-fusion:V1"))
    }

    @Test
    fun `shutter pressed capture has Full Clear exif override`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        assertEquals("Full Clear", strategy.postProcessSpec.exifOverrides["SceneCaptureType"])
    }

    @Test
    fun `shutter pressed capture saves to FullClear path`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        assertTrue(strategy.saveRequest.relativePath.contains("FullClear"))
    }

    @Test
    fun `shutter pressed fires event`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.handle(ModeIntent.ShutterPressed)

        assertTrue(events.contains("fullclear.shutter.pressed"))
    }

    @Test
    fun `secondary action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    @Test
    fun `tertiary action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.TertiaryActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    @Test
    fun `pro action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.ProActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    @Test
    fun `frame ratio selected returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(
            ModeIntent.FrameRatioSelected(com.opencamera.core.media.FrameRatio.RATIO_4_3)
        )
        assertIs<ModeSignal.None>(signal)
    }

    @Test
    fun `photo shot started updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(photoShotRequest()))

        assertEquals("Full Clear capture in progress", controller.snapshot.value.state.headline)
    }

    @Test
    fun `photo shot completed updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotCompleted(photoShotResult()))

        assertEquals("Full Clear capture saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `photo shot failed updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(
            ModeSessionEvent.ShotFailed(
                shotId = "fc-1",
                mediaType = MediaType.PHOTO,
                reason = "write error"
            )
        )

        assertEquals("Full Clear capture failed", controller.snapshot.value.state.headline)
    }

    @Test
    fun `video events are ignored by full clear controller`() = runTest {
        val controller = createController()
        controller.onEnter()
        val beforeHeadline = controller.snapshot.value.state.headline

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        assertEquals(beforeHeadline, controller.snapshot.value.state.headline)
    }

    @Test
    fun `device graph is still capture type`() = runTest {
        val controller = createController()
        val graph = controller.deviceGraph()
        assertIs<DeviceGraphSpec>(graph)
    }

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {}
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities,
            eventSink = eventSink
        )
        return FullClearModePlugin().create(context)
    }

    private fun photoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "fc-1",
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
            shotId = "fc-1",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/fullclear.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/fullclear.jpg"),
            metadata = MediaMetadata()
        )

    private fun videoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "video-1",
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(),
            livePhotoSpec = null
        )
}
