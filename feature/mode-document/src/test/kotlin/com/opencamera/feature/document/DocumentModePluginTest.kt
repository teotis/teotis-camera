package com.opencamera.feature.document

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.DocumentColorMode
import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.WatermarkEffect
import com.opencamera.core.media.CaptureProfile
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
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.settings.SessionSettingsSnapshot
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DocumentModePluginTest {

    // --- Plugin contract ---

    @Test
    fun `plugin id is DOCUMENT`() {
        val plugin = DocumentModePlugin()
        assertEquals(ModeId.DOCUMENT, plugin.id)
    }

    @Test
    fun `isSupported returns true when still capture is supported`() {
        val plugin = DocumentModePlugin()
        assertTrue(plugin.isSupported(DeviceCapabilities.DEFAULT))
    }

    @Test
    fun `isSupported returns false when still capture is unsupported`() {
        val plugin = DocumentModePlugin()
        val caps = DeviceCapabilities.DEFAULT.copy(supportsStillCapture = false)
        assertFalse(plugin.isSupported(caps))
    }

    @Test
    fun `create returns controller with DOCUMENT id`() {
        val plugin = DocumentModePlugin()
        val controller = plugin.create(ModeContext())
        assertEquals(ModeId.DOCUMENT, controller.id)
    }

    // --- Snapshot initial state ---

    @Test
    fun `initial snapshot has document pipeline ready headline`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals(ModeId.DOCUMENT, snap.id)
        assertEquals("Document pipeline ready", snap.state.headline)
    }

    @Test
    fun `initial snapshot ui spec has document labels`() = runTest {
        val controller = createController()
        val snap = controller.snapshot.value
        assertEquals("Document", snap.uiSpec.title)
        assertEquals("Scan Document", snap.uiSpec.shutterLabel)
        assertEquals("切换扫描场景", snap.uiSpec.secondaryActionLabel)
    }

    // --- Lifecycle ---

    @Test
    fun `onEnter fires document enter event and activates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onEnter()

        assertTrue(events.contains("document.enter"))
        val snap = controller.snapshot.value
        assertTrue(
            snap.state.headline == "Document scan active" ||
                snap.state.headline == "Document archive active"
        )
    }

    @Test
    fun `onExit fires document exit event and deactivates snapshot`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.onExit()

        assertTrue(events.contains("document.exit"))
        assertEquals("Document mode inactive", controller.snapshot.value.state.headline)
    }

    // --- Handle intents ---

    @Test
    fun `shutter pressed returns submit capture with single frame`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)

        assertIs<ModeSignal.SubmitCapture>(signal)
        assertIs<CaptureStrategy.SingleFrame>(signal.strategy)
    }

    @Test
    fun `shutter pressed capture strategy has document mode metadata`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        val tags = strategy.saveRequest.metadata.customTags
        assertEquals("document", tags["mode"])
        assertNotNull(tags["profile"])
        assertNotNull(tags["scanMode"])
        assertEquals("scan", tags["outputClass"])
    }

    @Test
    fun `shutter pressed capture strategy has document effect bridge tags`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        val tags = strategy.saveRequest.metadata.customTags
        assertTrue(tags.containsKey("autoCrop"))
    }

    @Test
    fun `shutter pressed capture has document post process spec with watermark`() = runTest {
        val controller = createController(watermarkEnabled = true)

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        val postProcess = strategy.postProcessSpec
        assertNotNull(postProcess.watermarkText)
        assertTrue(postProcess.watermarkText!!.contains("OpenCamera"))
        assertTrue(postProcess.watermarkText!!.contains("Document"))
        assertEquals("Document", postProcess.exifOverrides["SceneCaptureType"])
    }

    @Test
    fun `shutter pressed capture saves to documents path`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.ShutterPressed)
        val strategy = (signal as ModeSignal.SubmitCapture).strategy as CaptureStrategy.SingleFrame

        assertTrue(strategy.saveRequest.relativePath.contains("Documents"))
    }

    @Test
    fun `secondary action pressed cycles profile and returns hint`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)

        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.startsWith("扫描场景:"))
    }

    @Test
    fun `secondary action pressed fires profile selected event`() = runTest {
        val events = mutableListOf<String>()
        val controller = createController(eventSink = { events += it })

        controller.handle(ModeIntent.SecondaryActionPressed)

        assertTrue(events.any { it.startsWith("document.profile.selected.") })
    }

    @Test
    fun `tertiary action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.TertiaryActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    @Test
    fun `frame ratio selected returns original-frame order hint`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.FrameRatioSelected(
            com.opencamera.core.media.FrameRatio.RATIO_4_3
        ))
        assertIs<ModeSignal.ShowHint>(signal)
        assertTrue(signal.message.contains("保留原图"))
        assertTrue(signal.message.contains("左侧"))
    }

    @Test
    fun `pro action pressed returns none`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.ProActionPressed)
        assertIs<ModeSignal.None>(signal)
    }

    // --- Profile cycling ---

    @Test
    fun `cycling profiles wraps around`() = runTest {
        val controller = createController()

        // Default is enhanced mode (supportsDocumentScanEnhancement = true by default)
        // Enhanced has 3 profiles: receipt, whiteboard, contract
        val hints = mutableListOf<String>()
        for (i in 0..3) {
            val signal = controller.handle(ModeIntent.SecondaryActionPressed)
            hints.add((signal as ModeSignal.ShowHint).message)
        }

        // After 3 cycles, we should be back to the first profile
        assertEquals(hints[0], hints[3])
    }

    // --- Session events ---

    @Test
    fun `photo shot started updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(photoShotRequest()))

        assertEquals("Document scan in progress", controller.snapshot.value.state.headline)
    }

    @Test
    fun `photo shot completed updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotCompleted(photoShotResult()))

        assertEquals("Document saved", controller.snapshot.value.state.headline)
    }

    @Test
    fun `photo shot failed updates snapshot`() = runTest {
        val controller = createController()
        controller.onEnter()

        controller.onSessionEvent(ModeSessionEvent.ShotFailed(
            shotId = "doc-1",
            mediaType = MediaType.PHOTO,
            reason = "write error"
        ))

        assertEquals("Document capture failed", controller.snapshot.value.state.headline)
    }

    @Test
    fun `video events are ignored by document controller`() = runTest {
        val controller = createController()
        controller.onEnter()
        val beforeHeadline = controller.snapshot.value.state.headline

        controller.onSessionEvent(ModeSessionEvent.ShotStarted(videoShotRequest()))

        assertEquals(beforeHeadline, controller.snapshot.value.state.headline)
    }

    // --- Effect spec ---

    @Test
    fun `on enter triggers effect spec callback`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            watermarkEnabled = true,
            onEffectSpecChanged = { capturedSpec = it }
        )

        controller.onEnter()

        assertNotNull(capturedSpec)
        assertTrue(capturedSpec!!.hasDocumentEffect())
    }

    @Test
    fun `effect spec keeps original document frame without auto crop`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            onEffectSpecChanged = { capturedSpec = it }
        )

        controller.onEnter()

        val docEffect = capturedSpec!!.find<DocumentEffect>()
        assertNotNull(docEffect)
        assertFalse(docEffect.autoCrop)
    }

    @Test
    fun `effect spec contains watermark effect`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            onEffectSpecChanged = { capturedSpec = it },
            watermarkEnabled = true
        )

        controller.onEnter()

        val watermarkEffect = capturedSpec!!.find<WatermarkEffect>()
        assertNotNull(watermarkEffect)
        assertEquals("classic-overlay", watermarkEffect.templateId)
        assertEquals("OpenCamera", watermarkEffect.tokens["watermarkModel"])
        assertTrue(watermarkEffect.tokens["watermarkCameraParams"]!!.contains("Document"))
    }

    @Test
    fun `basic mode profile produces no contrast profile in document effect`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsDocumentScanEnhancement = false
        )
        var capturedSpec: EffectSpec? = null
        val controller = createController(
            deviceCapabilities = caps,
            onEffectSpecChanged = { capturedSpec = it }
        )

        controller.onEnter()

        val docEffect = capturedSpec!!.find<DocumentEffect>()
        assertNotNull(docEffect)
        assertEquals(null, docEffect.contrastProfile)
    }

    // --- Enhancement vs basic mode ---

    @Test
    fun `enhancement enabled uses enhanced profiles`() = runTest {
        val controller = createController()

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        val hint = (signal as ModeSignal.ShowHint).message

        // Enhanced profiles: Receipt, Whiteboard, Contract
        assertTrue(
            hint.contains("Receipt") || hint.contains("Whiteboard") || hint.contains("Contract")
        )
    }

    @Test
    fun `enhancement disabled uses basic profiles`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(
            supportsDocumentScanEnhancement = false
        )
        val controller = createController(deviceCapabilities = caps)

        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        val hint = (signal as ModeSignal.ShowHint).message

        // Basic profiles: Archive, Color Copy
        assertTrue(
            hint.contains("Archive") || hint.contains("Color Copy")
        )
    }

    // --- Device graph ---

    @Test
    fun `device graph is still capture type`() = runTest {
        val controller = createController()
        val graph = controller.deviceGraph()
        assertIs<DeviceGraphSpec>(graph)
    }

    // --- Helpers ---

    private fun createController(
        deviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        eventSink: suspend (String) -> Unit = {},
        onEffectSpecChanged: suspend (EffectSpec) -> Unit = {},
        watermarkEnabled: Boolean = false
    ): ModeController {
        val context = ModeContext(
            deviceCapabilities = deviceCapabilities,
            eventSink = eventSink,
            onEffectSpecChanged = onEffectSpecChanged,
            settingsSnapshotProvider = {
                com.opencamera.core.settings.SessionSettingsSnapshot(
                    persisted = com.opencamera.core.settings.PersistedSettings(
                        photo = com.opencamera.core.settings.PhotoSettings(
                            photoWatermarkEnabledByDefault = watermarkEnabled
                        )
                    )
                )
            }
        )
        return DocumentModePlugin().create(context)
    }

    private fun photoShotRequest(): ShotRequest =
        ShotRequest(
            shotId = "doc-1",
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
            shotId = "doc-1",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/doc.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia("/tmp/doc.jpg"),
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
            livePhotoSpec = LivePhotoCaptureSpec()
        )

    private fun EffectSpec.hasDocumentEffect(): Boolean =
        entries.any { it is DocumentEffect }

    // ── Characterization: exact metadata maps ────────────────────────────

    @Test
    fun `char enhanced scan receipt exact metadata keys`(): Unit = runTest {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("document", metadata["mode"])
        assertEquals("receipt", metadata["profile"])
        assertEquals("enhanced", metadata["scanMode"])
        assertEquals("scan", metadata["outputClass"])
    }

    @Test
    fun `char enhanced scan receipt effect bridge tags`(): Unit = runTest {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("false", metadata["autoCrop"])
        assertEquals("document", metadata["mode"])
    }

    @Test
    fun `char basic archive exact metadata keys`(): Unit = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("document", metadata["mode"])
        assertEquals("archive", metadata["profile"])
        assertEquals("basic", metadata["scanMode"])
        assertEquals("scan", metadata["outputClass"])
    }

    @Test
    fun `char enhanced scan post process exif includes watermark`(): Unit = runTest {
        val controller = createController(watermarkEnabled = true)
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals("Document", postProcess.exifOverrides["SceneCaptureType"])
        assertEquals("enhanced-scan:color-neutral", postProcess.exifOverrides["ProcessingRendered"])
        assertNotNull(postProcess.watermarkText)
        assertTrue(postProcess.watermarkText!!.contains("Document"))
    }

    @Test
    fun `char basic archive post process exif includes watermark`(): Unit = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps, watermarkEnabled = true)
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals("Document", postProcess.exifOverrides["SceneCaptureType"])
        assertEquals("basic-archive", postProcess.exifOverrides["ProcessingRendered"])
        assertNotNull(postProcess.watermarkText)
        assertTrue(postProcess.watermarkText!!.contains("Document"))
    }

    @Test
    fun `char document always uses SingleFrame strategy`(): Unit = runTest {
        val controller = createController()
        val strategy = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture).strategy
        assertIs<CaptureStrategy.SingleFrame>(strategy)
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val basicController = createController(deviceCapabilities = caps)
        val basicStrategy = (basicController.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture).strategy
        assertIs<CaptureStrategy.SingleFrame>(basicStrategy)
    }

    @Test
    fun `char document has capture aid tags`(): Unit = runTest {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("back", metadata["captureLensFacing"])
        assertEquals("false", metadata["selfieMirrorApply"])
    }

    @Test
    fun `char document mode collision - mode key from both effect bridge and save request`(): Unit = runTest {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("document", metadata["mode"])
    }

    // ── Migration characterization: profile coercion ───────────────────

    @Test
    fun `enhanced mode starts with enhanced profile list`() = runTest {
        val controller = createController()
        // Cycling once goes from index 0 → 1 (Whiteboard)
        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        val hint = (signal as ModeSignal.ShowHint).message
        assertTrue(
            hint.contains("Receipt") || hint.contains("Whiteboard") || hint.contains("Contract"),
            "Enhanced profiles should be Receipt/Whiteboard/Contract: $hint"
        )
    }

    @Test
    fun `basic mode starts with basic profile list`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        val hint = (signal as ModeSignal.ShowHint).message
        assertTrue(
            hint.contains("Archive") || hint.contains("Color Copy"),
            "Basic profiles should be Archive/Color Copy: $hint"
        )
    }

    // ── Migration characterization: headlines per capability ───────────

    @Test
    fun `enhanced onEnter headline is Document scan active`() = runTest {
        val controller = createController()
        controller.onEnter()
        assertEquals("Document scan active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `basic onEnter headline is Document archive active`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        controller.onEnter()
        assertEquals("Document archive active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `enhanced onDeviceCapabilitiesChanged headline is Document scan active`() = runTest {
        val controller = createController()
        controller.onEnter()
        controller.onDeviceCapabilitiesChanged(DeviceCapabilities.DEFAULT)
        assertEquals("Document scan active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `basic onDeviceCapabilitiesChanged headline is Document archive active`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        controller.onEnter()
        controller.onDeviceCapabilitiesChanged(caps)
        assertEquals("Document archive active", controller.snapshot.value.state.headline)
    }

    @Test
    fun `enhanced onStillCaptureResolutionChanged headline is Document resolution updated`() = runTest {
        val controller = createController()
        controller.onEnter()
        controller.onStillCaptureResolutionChanged(
            com.opencamera.core.media.StillCaptureResolutionPreset.LARGE_12MP
        )
        assertEquals("Document resolution updated", controller.snapshot.value.state.headline)
    }

    @Test
    fun `basic onStillCaptureResolutionChanged headline is Archive resolution updated`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        controller.onEnter()
        controller.onStillCaptureResolutionChanged(
            com.opencamera.core.media.StillCaptureResolutionPreset.LARGE_12MP
        )
        assertEquals("Archive resolution updated", controller.snapshot.value.state.headline)
    }

    // ── Migration characterization: document effects keep watermark support ─

    @Test
    fun `document effect spec keeps watermark after resolution changes`() = runTest {
        var capturedSpec: com.opencamera.core.effect.EffectSpec? = null
        val controller = createController(
            watermarkEnabled = true,
            onEffectSpecChanged = { capturedSpec = it }
        )
        controller.onEnter()

        val watermarkEffect = capturedSpec!!.find<com.opencamera.core.effect.WatermarkEffect>()
        assertNotNull(watermarkEffect)
    }

    // ── Migration characterization: exact metadata per profile ─────────

    @Test
    fun `enhanced receipt metadata has correct keys`() = runTest {
        val controller = createController()
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("document", metadata["mode"])
        assertEquals("receipt", metadata["profile"])
        assertEquals("enhanced", metadata["scanMode"])
        assertEquals("scan", metadata["outputClass"])
        assertNotNull(metadata["stillResolution"])
    }

    @Test
    fun `enhanced receipt post process has correct exif`() = runTest {
        val controller = createController()
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals("Document", postProcess.exifOverrides["SceneCaptureType"])
        assertEquals("enhanced-scan:color-neutral", postProcess.exifOverrides["ProcessingRendered"])
        assertEquals("High", postProcess.exifOverrides["Contrast"])
        assertEquals("document-receipt-scan", postProcess.algorithmProfile)
    }

    @Test
    fun `basic archive metadata has correct keys`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        val metadata = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.saveRequest.metadata.customTags
        assertEquals("document", metadata["mode"])
        assertEquals("archive", metadata["profile"])
        assertEquals("basic", metadata["scanMode"])
        assertEquals("scan", metadata["outputClass"])
    }

    @Test
    fun `basic archive post process has no contrast exif`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertEquals("Document", postProcess.exifOverrides["SceneCaptureType"])
        assertEquals("basic-archive", postProcess.exifOverrides["ProcessingRendered"])
        assertFalse(postProcess.exifOverrides.containsKey("Contrast"))
        assertEquals("document-basic-archive", postProcess.algorithmProfile)
    }

    // ── Migration characterization: profile detail text ─────────────────

    @Test
    fun `enhanced profile snapshot detail contains contrast label and color mode`() = runTest {
        val controller = createController()
        controller.onEnter()
        val detail = controller.snapshot.value.state.detail
        assertTrue(detail.contains("Receipt") || detail.contains("Whiteboard") || detail.contains("Contract"))
        assertFalse(detail.contains("Auto crop"))
        assertTrue(detail.contains("Contrast"))
        assertTrue(detail.contains("Color:"))
    }

    @Test
    fun `basic profile snapshot detail mentions basic capture only`() = runTest {
        val caps = DeviceCapabilities.DEFAULT.copy(supportsDocumentScanEnhancement = false)
        val controller = createController(deviceCapabilities = caps)
        controller.onEnter()
        val detail = controller.snapshot.value.state.detail
        assertTrue(detail.contains("Basic capture only"))
    }

    // ── Default color mode: COLOR_NEUTRAL ──────────────────────────────

    @Test
    fun `default effect spec has COLOR_NEUTRAL`() = runTest {
        var capturedSpec: EffectSpec? = null
        val controller = createController(onEffectSpecChanged = { capturedSpec = it })
        controller.onEnter()
        val docEffect = capturedSpec!!.find<DocumentEffect>()
        assertNotNull(docEffect)
        assertEquals(DocumentColorMode.COLOR_NEUTRAL, docEffect.colorMode)
    }

    // ── Secondary action hint includes color mode label ────────────────

    @Test
    fun `secondary action hint includes color mode label`() = runTest {
        val controller = createController()
        val signal = controller.handle(ModeIntent.SecondaryActionPressed)
        val hint = (signal as ModeSignal.ShowHint).message
        assertTrue(hint.contains("色彩: 原色"), "Hint should contain color label: $hint")
        assertTrue(hint.startsWith("扫描场景:"), "Hint should start with scan scene prefix: $hint")
    }

    // ── EXIF ProcessingRendered includes color mode tag ────────────────

    @Test
    fun `enhanced scan ProcessingRendered includes color mode tag`() = runTest {
        val controller = createController()
        val postProcess = (controller.handle(ModeIntent.ShutterPressed) as ModeSignal.SubmitCapture)
            .strategy.postProcessSpec
        assertTrue(
            postProcess.exifOverrides["ProcessingRendered"]!!.contains("color-neutral"),
            "ProcessingRendered should include color tag: ${postProcess.exifOverrides["ProcessingRendered"]}"
        )
    }
}
