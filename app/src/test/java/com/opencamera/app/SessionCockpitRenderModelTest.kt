package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.SavedMediaType
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SessionCockpitRenderModelTest {
    @Test
    fun `controls model reflects active lens and still settings`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                ),
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 2f,
                qualityPreference = StillCaptureQualityPreference.QUALITY,
                resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
            )
        )

        val model = sessionControlsRenderModel(state, strings)

        assertEquals("Switch to Front", model.lensFacingButtonLabel)
        assertTrue(model.lensFacingEnabled)
    }

    @Test
    fun `controls model degrades labels for video template`() {
        val state = defaultSessionState(
            activeDeviceGraph = DeviceGraphSpec.videoRecording(
                preferredLensFacing = LensFacing.FRONT,
                enablePreviewSnapshots = true,
                stillResolutionPreset = StillCaptureResolutionPreset.SMALL_2MP
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.FRONT)
            )
        )

        val model = sessionControlsRenderModel(state, strings)

        assertEquals("Single Lens", model.lensFacingButtonLabel)
        assertFalse(model.lensFacingEnabled)
    }

    @Test
    fun `capture output prefers explicit error over saved media`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.PHOTO,
            latestCapturePath = "/tmp/capture.jpg",
            latestPipelineNotes = listOf("watermark:rendered"),
            lastError = "Preview binding failed"
        )

        assertEquals(
            "Camera issue: Preview binding failed",
            sessionCaptureOutputText(state, strings)
        )
    }

    @Test
    fun `capture output appends pipeline notes for saved video`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.VIDEO,
            latestVideoPath = "/tmp/clip.mp4",
            latestPipelineNotes = listOf("torch:on")
        )

        assertEquals(
            "Last video:\n/tmp/clip.mp4\nPipeline: torch:on",
            sessionCaptureOutputText(state, strings)
        )
    }

    @Test
    fun `capture output renders live photo bundle paths`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.PHOTO,
            latestCapturePath = "/tmp/live.jpg",
            latestLivePhotoBundle = LivePhotoBundle(
                stillPath = "/tmp/live.jpg",
                motionPath = "/tmp/live.live.mp4",
                sidecarPath = "/tmp/live.live.json",
                motionDurationMillis = 1500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            ),
            latestPipelineNotes = listOf("live-photo:bundle", "live-photo:motion=video/mp4")
        )

        assertEquals(
            "Last Live photo:\nStill: /tmp/live.jpg\nMotion: /tmp/live.live.mp4\nSidecar: /tmp/live.live.json\nThumbnail: /tmp/live.jpg\nPipeline: live-photo:bundle, live-photo:motion=video/mp4",
            sessionCaptureOutputText(state, strings)
        )
    }

    @Test
    fun `capture output renders live photo media store uris when available`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.PHOTO,
            latestCapturePath = "Pictures/OpenCamera/live.jpg",
            latestLivePhotoBundle = LivePhotoBundle(
                stillPath = "Pictures/OpenCamera/live.jpg",
                motionPath = "Pictures/OpenCamera/live.live.mp4",
                sidecarPath = "Pictures/OpenCamera/live.live.json",
                thumbnailPath = "Pictures/OpenCamera/live.jpg",
                motionDurationMillis = 1500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                sidecarHandle = com.opencamera.core.media.MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/live.live.json",
                    contentUri = "content://media/external/file/88"
                ),
                thumbnailHandle = com.opencamera.core.media.MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/live.jpg",
                    contentUri = "content://media/external/images/media/42"
                )
            ),
            latestPipelineNotes = listOf("live-photo:bundle")
        )

        assertEquals(
            "Last Live photo:\nStill: Pictures/OpenCamera/live.jpg (content://media/external/images/media/42)\nMotion: Pictures/OpenCamera/live.live.mp4\nSidecar: Pictures/OpenCamera/live.live.json (content://media/external/file/88)\nThumbnail: Pictures/OpenCamera/live.jpg (content://media/external/images/media/42)\nPipeline: live-photo:bundle",
            sessionCaptureOutputText(state, strings)
        )
    }

    @Test
    fun `preview thumbnail output stays compact when only preview is available`() {
        val state = defaultSessionState().copy(
            presentation = defaultSessionState().presentation.copy(
                previewThumbnailPath = "/data/user/0/com.opencamera.app/cache/preview-thumbnails/test.jpg"
            )
        )

        assertEquals("Preview thumbnail:", sessionCaptureOutputText(state, strings))
    }

    @Test
    fun `mode summary reflects current mode snapshot`() {
        val state = defaultSessionState().copy(
            modeSnapshot = ModeSnapshot(
                id = ModeId.NIGHT,
                uiSpec = ModeUiSpec(title = "Scenery", shutterLabel = "Capture Scenery"),
                state = ModeState(headline = "Scenery mode active", detail = "Tripod multi-frame available")
            )
        )

        assertEquals(
            "Mode: Scenery\nScenery mode active\nTripod multi-frame available",
            modeSummaryText(state)
        )
    }

    @Test
    fun `mode directory render model hides humanistic entry and uses product order`() {
        val state = defaultSessionState(
            activeMode = ModeId.NIGHT,
            availableModes = listOf(
                ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT,
                ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO
            ),
            modeSnapshot = ModeSnapshot(
                id = ModeId.NIGHT,
                uiSpec = ModeUiSpec(title = "Scenery", shutterLabel = "Capture Scenery"),
                state = ModeState(headline = "Scenery mode active", detail = "Tripod multi-frame available")
            )
        )

        val model = modeDirectoryRenderModel(state, TestAppTextResolver())

        assertEquals(
            listOf("Photo", "Scenery", "Port", "Pro", "Video", "Doc"),
            model.items.map(ModeDirectoryItemRenderModel::displayName)
        )
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO, ModeId.DOCUMENT),
            model.items.map(ModeDirectoryItemRenderModel::modeId)
        )
        assertEquals("Portrait Retro", model.items.first { it.modeId == ModeId.PHOTO }.defaultStyleLabel)
        assertEquals("Balanced", model.items.first { it.modeId == ModeId.NIGHT }.defaultStyleLabel)
    }

    @Test
    fun `mode directory render model degrades scenery and portrait features with capability fallback`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                supportsPortraitDepthEffect = false,
                supportsNightMultiFrame = false
            ),
            availableModes = listOf(ModeId.NIGHT, ModeId.PORTRAIT)
        )

        val model = modeDirectoryRenderModel(state, TestAppTextResolver())

        assertEquals("Balanced", model.items.first { it.modeId == ModeId.NIGHT }.defaultStyleLabel)
        assertEquals("Portrait Original", model.items.first { it.modeId == ModeId.PORTRAIT }.defaultStyleLabel)
    }

    @Test
    fun `zoom capsule render model carries individual ratio and active state`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1f
            )
        )
        val controls = sessionControlsRenderModel(state, strings)

        assertEquals(4, controls.zoomCapsules.size)
        assertEquals(listOf(0.6f, 1f, 2f, 5f), controls.zoomCapsules.map { it.ratio })
        assertEquals(listOf(false, true, false, false), controls.zoomCapsules.map { it.isActive })
    }

    @Test
    fun `zoom capsule labels use compact format`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1f
            )
        )
        val controls = sessionControlsRenderModel(state, strings)

        assertEquals(listOf("0.6", "1x", "2", "5"), controls.zoomCapsules.map { it.label })
    }

    @Test
    fun `zoom capsule compact label handles 1x and integer ratios`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.7f, 1f, 3f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1f
            )
        )
        val controls = sessionControlsRenderModel(state, strings)

        assertEquals(listOf("0.7", "1x", "3"), controls.zoomCapsules.map { it.label })
    }

    @Test
    fun `mode track render model hides humanistic entry and uses product order`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT,
            ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO
        )
        val state = defaultSessionState(activeMode = ModeId.HUMANISTIC, availableModes = availableModes)
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        assertEquals(6, model.items.size)
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO, ModeId.DOCUMENT),
            model.items.map { it.modeId }
        )
        assertTrue(model.items.none { it.modeId == ModeId.HUMANISTIC })
        assertFalse(model.items.any { it.isActive })
    }

    @Test
    fun `mode track labels are short and stable`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT,
            ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO
        )
        val state = defaultSessionState(availableModes = availableModes)
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        model.items.forEach { item ->
            assertTrue(item.trackLabel.length <= 10, "Mode track label '${item.trackLabel}' should be at most 10 chars")
            assertTrue(item.trackLabel.isNotBlank(), "Mode track label must not be blank")
        }
        val labels = model.items.map { it.trackLabel }
        assertEquals(labels.size, labels.toSet().size, "All mode track labels must be unique")
    }

    @Test
    fun `active mode track item has distinct visual state`() {
        val state = defaultSessionState(
            activeMode = ModeId.NIGHT,
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.VIDEO)
        )
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        val active = model.items.first { it.modeId == ModeId.NIGHT }
        val inactive = model.items.first { it.modeId == ModeId.PHOTO }

        assertTrue(active.isActive)
        assertFalse(inactive.isActive)
    }

    @Test
    fun `quick button labels fit within 96dp button width without ellipsis`() {
        val quickLabels = listOf("网格", "画质", "画幅", "实况", "定时")
        quickLabels.forEach { label ->
            assertTrue(label.length <= 2, "Quick button label '$label' exceeds 2 chars")
        }
    }

    @Test
    fun `quick panel sheet exposes all five rows`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Grid", sheet.gridRow.title)
        assertEquals("Quality", sheet.qualityRow.title)
        assertEquals("Frame", sheet.frameRatioRow.title)
        assertEquals("Live", sheet.liveRow.title)
        assertEquals("Timer", sheet.timerRow.title)
    }

    @Test
    fun `quick panel sheet frame ratio options remain present when one is selected`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(3, sheet.frameRatioOptions.size)
        val selected = sheet.frameRatioOptions.filter { it.isSelected }
        assertEquals(1, selected.size)
        assertTrue(sheet.frameRatioOptions.all { it.label.isNotBlank() })
    }

    @Test
    fun `quick panel sheet frame ratio disabled for video mode`() {
        val state = defaultSessionState(activeMode = ModeId.VIDEO)
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.frameRatioEnabled)
        assertNotNull(sheet.frameRatioDisabledReason)
        assertTrue(sheet.frameRatioOptions.all { !it.isEnabled })
    }

    @Test
    fun `quick panel sheet frame ratio disabled during active shot`() {
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "test-shot",
                shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.frameRatioEnabled)
        assertNotNull(sheet.frameRatioDisabledReason)
    }

    @Test
    fun `primary status shows recording starting`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.REQUESTING)
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Starting…"))
    }

    @Test
    fun `primary status shows recording active`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Recording"))
    }

    @Test
    fun `primary status shows saving`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.STOPPING)
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Saving…"))
    }

    @Test
    fun `primary status shows capture status when not idle`() {
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING)
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Saving"))
    }

    @Test
    fun `frame ratio control disabled for unsupported mode`() {
        val state = defaultSessionState(activeMode = ModeId.VIDEO)
        val model = frameRatioControlRenderModel(state, TestAppTextResolver())

        assertFalse(model.isEnabled)
        assertEquals("Frame ratio not supported in current mode", model.disabledReason)
    }

    @Test
    fun `frame ratio control disabled during active shot`() {
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "test-shot",
                shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        )
        val model = frameRatioControlRenderModel(state, TestAppTextResolver())

        assertFalse(model.isEnabled)
        assertEquals("Wait for current capture to finish", model.disabledReason)
    }

    @Test
    fun `capture disabled reason returns null when idle`() {
        val state = defaultSessionState()
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertNull(reason)
    }

    @Test
    fun `capture disabled reason returns saving photo`() {
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING)
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertEquals("Saving previous photo", reason)
    }

    @Test
    fun `capture disabled reason returns recording`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertEquals("Unavailable during recording", reason)
    }

    @Test
    fun `capture disabled reason returns permission required`() {
        val state = defaultSessionState().copy(
            permissionState = PermissionState(cameraGranted = false, microphoneGranted = false)
        )
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertEquals("Camera permission required", reason)
    }

    companion object {
        private val strings = SessionUiStrings(
            buttonSwitchToFront = "Switch to Front",
            buttonSwitchToBack = "Switch to Back",
            buttonSingleLens = "Single Lens",
            buttonZoomPrefix = "Zoom",
            buttonZoomUnavailable = "Zoom N/A",
            buttonStillFast = "Still Fast",
            buttonStillMax = "Still Max",
            buttonStillQualityUnavailable = "Still N/A",
            buttonStill12Mp = "Still 12MP",
            buttonStill8Mp = "Still 8MP",
            buttonStill2Mp = "Still 2MP",
            buttonStillResolutionUnavailable = "Size N/A",
            outputErrorPrefix = "Camera issue:",
            outputVideoPrefix = "Last video:",
            outputLivePrefix = "Last Live photo:",
            outputSavedPrefix = "Last photo:",
            outputPreviewPrefix = "Preview thumbnail:",
            outputWaiting = "No photo captured yet."
        )
    }

    private fun defaultSessionState(
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            qualityPreference = StillCaptureQualityPreference.LATENCY,
            resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
        ),
        previewStatus: PreviewStatus = PreviewStatus.ACTIVE,
        previewMetrics: PreviewMetrics = PreviewMetrics(),
        activeShot: ShotRequest? = null,
        latestSavedMediaType: SavedMediaType? = null,
        latestCapturePath: String? = null,
        latestVideoPath: String? = null,
        latestLivePhotoBundle: LivePhotoBundle? = null,
        latestPipelineNotes: List<String> = emptyList(),
        lastError: String? = null,
        activeMode: ModeId = ModeId.PHOTO,
        availableModes: List<ModeId> = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.VIDEO),
        modeSnapshot: ModeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
            state = ModeState(headline = "PHOTO mode active", detail = "Ready")
        ),
        persistedPhotoSettings: PhotoSettings = PhotoSettings(
            defaultFilterProfileId = "portrait-retro",
            defaultHumanisticFilterProfileId = "humanistic-street",
            defaultPortraitFilterProfileId = "portrait-original",
            defaultWatermarkTemplateId = "travel-polaroid",
            livePhotoEnabledByDefault = true,
            countdownDuration = CountdownDuration.SECONDS_3
        )
    ): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = previewStatus,
            previewStatusDetail = null,
            activeMode = activeMode,
            availableModes = availableModes,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = activeShot,
            modeSnapshot = modeSnapshot,
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = activeDeviceGraph,
            previewMetrics = previewMetrics,
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = CommonSettings(
                        gridMode = com.opencamera.core.settings.CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = persistedPhotoSettings,
                    video = VideoSettings(
                        defaultVideoSpec = VideoSpec(
                            resolution = VideoResolution.UHD_4K,
                            frameRate = VideoFrameRate.FPS_25,
                            dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                            audioProfile = AudioProfile.CONCERT
                        ),
                        defaultFilterProfileId = "photo-rich"
                    )
                )
            ),
            presentation = SessionPresentationState(
                lastAction = "Ready",
                latestCapturePath = latestCapturePath,
                latestVideoPath = latestVideoPath,
                latestLivePhotoBundle = latestLivePhotoBundle,
                latestSavedMediaType = latestSavedMediaType,
                latestPipelineNotes = latestPipelineNotes,
                lastError = lastError
            )
        )
    }
}
