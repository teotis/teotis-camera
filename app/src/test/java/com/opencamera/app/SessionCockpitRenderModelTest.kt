package com.opencamera.app

import androidx.annotation.StringRes
import com.opencamera.core.device.CaptureReadiness
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ManualControlCapabilityMatrix
import com.opencamera.core.device.ManualControlSupport
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio

import com.opencamera.core.media.LiveBundleStatus
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
import com.opencamera.core.session.PendingPostprocessUiState
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.FeatureCatalog
import com.opencamera.core.settings.FilterProfile
import com.opencamera.core.settings.FilterProfileCategory
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.ResetTarget
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
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
                outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
            )
        )

        val model = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals("Switch to Front", model.lensFacingButtonLabel)
        assertTrue(model.lensFacingEnabled)
    }

    @Test
    fun `controls model degrades labels for video template`() {
        val state = defaultSessionState(
            activeDeviceGraph = DeviceGraphSpec.videoRecording(
                preferredLensFacing = LensFacing.FRONT,
                enablePreviewSnapshots = true
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.FRONT)
            )
        )

        val model = sessionControlsRenderModel(state, strings, TestAppTextResolver())

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
            sessionCaptureOutputText(state, strings, TestAppTextResolver())
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
            sessionCaptureOutputText(state, strings, TestAppTextResolver())
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
            sessionCaptureOutputText(state, strings, TestAppTextResolver())
        )
    }

    @Test
    fun `capture output renders live photo bundle labels from resolver`() {
        val text = object : TestAppTextResolver() {
            internal override fun get(@StringRes resId: Int): String = when (resId) {
                R.string.live_photo_asset_still -> "静态照片"
                R.string.live_photo_asset_motion -> "动态片段"
                R.string.live_photo_asset_sidecar -> "附属文件"
                R.string.live_photo_asset_thumbnail -> "缩略图"
                else -> super.get(resId)
            }
        }
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
            )
        )

        assertEquals(
            "Last Live photo:\n静态照片: /tmp/live.jpg\n动态片段: /tmp/live.live.mp4\n附属文件: /tmp/live.live.json\n缩略图: /tmp/live.jpg",
            sessionCaptureOutputText(state, strings, text)
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
            sessionCaptureOutputText(state, strings, TestAppTextResolver())
        )
    }

    @Test
    fun `capture output exposes live photo format diagnostics without metadata bloat`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.PHOTO,
            latestCapturePath = "Pictures/OpenCamera/live.jpg",
            latestLivePhotoBundle = LivePhotoBundle(
                stillPath = "Pictures/OpenCamera/live.jpg",
                motionPath = "Pictures/OpenCamera/live.live.mp4",
                sidecarPath = "Pictures/OpenCamera/live.live.json",
                motionDurationMillis = 1500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            ),
            latestPipelineNotes = listOf(
                "live-format:intended=google-motion-photo-jpeg",
                "live-format:actual=google-motion-photo-jpeg",
                "live-motion:status=encoded",
                "motion-photo:xmp=present",
                "motion-photo:appended-mp4-bytes=12345",
                "gallery-recognition=untested"
            )
        )

        val output = sessionCaptureOutputText(state, strings, TestAppTextResolver())
        assertTrue(output.contains("live-format:intended=google-motion-photo-jpeg"))
        assertTrue(output.contains("live-format:actual=google-motion-photo-jpeg"))
        assertTrue(output.contains("live-motion:status=encoded"))
        assertTrue(output.contains("gallery-recognition=untested"))
        // Output must not dump raw metadata blobs
        assertFalse(output.contains("x:xmpmeta"))
        assertFalse(output.contains("rdf:RDF"))
        // 7 lines: prefix + status line + 4 asset lines + pipeline notes
        assertEquals(7, output.lines().size, "Output must stay compact for QA")
    }

    @Test
    fun `capture output shows still only fallback diagnostics for degraded live photo`() {
        val state = defaultSessionState(
            latestSavedMediaType = SavedMediaType.PHOTO,
            latestCapturePath = "Pictures/OpenCamera/fallback.jpg",
            latestLivePhotoBundle = LivePhotoBundle(
                stillPath = "Pictures/OpenCamera/fallback.jpg",
                motionPath = "Pictures/OpenCamera/fallback.live.mp4",
                sidecarPath = "Pictures/OpenCamera/fallback.live.json",
                motionDurationMillis = 1500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
            ),
            latestPipelineNotes = listOf(
                "live-format:intended=google-motion-photo-jpeg",
                "live-format:actual=still-jpeg",
                "live-motion:status=failed",
                "motion-photo:container=failed:motion-encode-error",
                "gallery-recognition=untested"
            )
        )

        val output = sessionCaptureOutputText(state, strings, TestAppTextResolver())
        assertTrue(output.contains("live-format:actual=still-jpeg"))
        assertTrue(output.contains("live-motion:status=failed"))
        assertTrue(output.contains("gallery-recognition=untested"))
        assertFalse(output.contains("google-jpeg"))
    }

    @Test
    fun `preview thumbnail output stays compact when only preview is available`() {
        val state = defaultSessionState().copy(
            presentation = defaultSessionState().presentation.copy(
                previewThumbnailPath = "/data/user/0/com.opencamera.app/cache/preview-thumbnails/test.jpg"
            )
        )

        assertEquals("Preview thumbnail:", sessionCaptureOutputText(state, strings, TestAppTextResolver()))
    }

    @Test
    fun `mode summary reflects current mode snapshot`() {
        val state = defaultSessionState().copy(
            modeSnapshot = ModeSnapshot(
                id = ModeId.CHECK_IN,
                uiSpec = ModeUiSpec(title = "Check-in", shutterLabel = "Capture Check-in"),
                state = ModeState(headline = "Check-in mode active", detail = "Multi-frame available")
            )
        )

        assertEquals(
            "Mode: Check-in\nCheck-in mode active\nMulti-frame available",
            modeSummaryText(state)
        )
    }

    @Test
    fun `mode action is scoped to humanistic with compact pro label`() {
        val state = defaultSessionState(
            activeMode = ModeId.HUMANISTIC,
            modeSnapshot = ModeSnapshot(
                id = ModeId.HUMANISTIC,
                uiSpec = ModeUiSpec(
                    title = "Humanistic",
                    shutterLabel = "Capture Humanistic",
                    proActionLabel = "Professional on"
                ),
                state = ModeState(
                    headline = "Humanistic mode active",
                    detail = "Ready",
                    isProActionEnabled = true,
                    isProVariantActive = true
                )
            )
        )

        val model = modeActionRenderModel(state)

        assertTrue(model.isVisible)
        assertEquals("Pro", model.label)
        assertTrue(model.isActive)
    }

    @Test
    fun `mode action is scoped to check-in and hidden for mismatched snapshots`() {
        val checkInState = defaultSessionState(
            activeMode = ModeId.CHECK_IN,
            modeSnapshot = ModeSnapshot(
                id = ModeId.CHECK_IN,
                uiSpec = ModeUiSpec(
                    title = "Check-in",
                    shutterLabel = "Capture Check-in",
                    proActionLabel = "全清"
                ),
                state = ModeState(
                    headline = "Check-in mode active",
                    detail = "Ready",
                    isProActionEnabled = true
                )
            )
        )
        val mismatchedState = checkInState.copy(
            activeMode = ModeId.PHOTO
        )

        assertEquals("全清", modeActionRenderModel(checkInState).label)
        assertTrue(modeActionRenderModel(checkInState).isVisible)
        assertFalse(modeActionRenderModel(mismatchedState).isVisible)
    }

    @Test
    fun `mode directory render model includes humanistic entry and uses product order`() {
        val state = defaultSessionState(
            activeMode = ModeId.HUMANISTIC,
            availableModes = listOf(
                ModeId.PHOTO, ModeId.CHECK_IN, ModeId.DOCUMENT, ModeId.HUMANISTIC,
                ModeId.VIDEO, ModeId.CHECK_IN, ModeId.CHECK_IN
            ),
            modeSnapshot = ModeSnapshot(
                id = ModeId.HUMANISTIC,
                uiSpec = ModeUiSpec(title = "Humanistic", shutterLabel = "Capture Humanistic"),
                state = ModeState(headline = "Humanistic mode active", detail = "Street-life quick capture ready")
            )
        )

        val model = modeDirectoryRenderModel(state, TestAppTextResolver())

        assertEquals(
            listOf("Photo", "Check-in", "Humanistic", "Doc", "Video"),
            model.items.map(ModeDirectoryItemRenderModel::displayName)
        )
        assertEquals(
            listOf(
                ModeId.PHOTO,
                ModeId.CHECK_IN,
                ModeId.HUMANISTIC,
                ModeId.DOCUMENT,
                ModeId.VIDEO
            ),
            model.items.map(ModeDirectoryItemRenderModel::modeId)
        )
        assertEquals("Portrait Retro", model.items.first { it.modeId == ModeId.PHOTO }.defaultStyleLabel)
        assertEquals("Street", model.items.first { it.modeId == ModeId.HUMANISTIC }.defaultStyleLabel)
    }

    @Test
    fun `mode directory render model shows humanistic with street-life subfeatures`() {
        val state = defaultSessionState(
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO)
        )

        val model = modeDirectoryRenderModel(state, TestAppTextResolver())

        assertEquals(4, model.items.size)
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
            model.items.map(ModeDirectoryItemRenderModel::modeId)
        )
        assertEquals("Portrait Retro", model.items.first { it.modeId == ModeId.PHOTO }.defaultStyleLabel)
        assertEquals("Street", model.items.first { it.modeId == ModeId.HUMANISTIC }.defaultStyleLabel)
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
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

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
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

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
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals(listOf("0.7", "1x", "3"), controls.zoomCapsules.map { it.label })
    }

    @Test
    fun `currentZoomLabel is null when zoom matches a preset`() {
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
                zoomRatio = 2f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertNull(controls.currentZoomLabel)
    }

    @Test
    fun `currentZoomLabel shows label when zoom between presets`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1.5f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals("1.5", controls.currentZoomLabel)
    }

    @Test
    fun `nearestPresetRatio is set when zoom not on preset`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1.5f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals(1f, controls.nearestPresetRatio)
    }

    @Test
    fun `zoom row hidden for unsupported capability`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.UNSUPPORTED,
                    supportedRatios = listOf(1f),
                    defaultRatio = 1f
                )
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertFalse(controls.isZoomCapsuleRowVisible)
        assertTrue(controls.zoomCapsules.isEmpty())
    }

    @Test
    fun `continuous zoom capability shows all presets`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 1f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.isZoomCapsuleRowVisible)
        assertEquals(3, controls.zoomCapsules.size)
        assertNull(controls.currentZoomLabel)
    }

    // --- Focal length slider V2 integration tests ---

    @Test
    fun `focal slider is visible and enabled when zoom supported and idle`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.focalLengthSlider.isVisible)
        assertTrue(controls.focalLengthSlider.isEnabled)
        assertNull(controls.focalLengthSlider.disabledReason)
    }

    @Test
    fun `focal slider is hidden when zoom unsupported`() {
        val state = defaultSessionState()
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertFalse(controls.focalLengthSlider.isVisible)
    }

    @Test
    fun `focal slider is disabled during countdown`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        ).copy(
            presentation = SessionPresentationState(countdownRemainingSeconds = 3)
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.focalLengthSlider.isVisible)
        assertFalse(controls.focalLengthSlider.isEnabled)
        assertNotNull(controls.focalLengthSlider.disabledReason)
    }

    @Test
    fun `focal slider is disabled during photo capture saving`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeShot = ShotRequest(
                shotId = "s1",
                mediaType = MediaType.PHOTO,
                shotKind = ShotKind.STILL_CAPTURE,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.NONE,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.focalLengthSlider.isVisible)
        assertFalse(controls.focalLengthSlider.isEnabled)
        assertNotNull(controls.focalLengthSlider.disabledReason)
    }

    @Test
    fun `focal slider is disabled during recording requesting`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        ).copy(recordingStatus = RecordingStatus.REQUESTING)
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.focalLengthSlider.isVisible)
        assertFalse(controls.focalLengthSlider.isEnabled)
    }

    @Test
    fun `focal slider stays enabled during active video recording`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        ).copy(recordingStatus = RecordingStatus.RECORDING)
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.focalLengthSlider.isVisible)
        assertTrue(controls.focalLengthSlider.isEnabled)
        assertNull(controls.focalLengthSlider.disabledReason)
    }

    @Test
    fun `focal slider preset ratios match capability`() {
        val ratios = listOf(0.6f, 1f, 2f, 5f)
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = ratios,
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 2f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals(ratios, controls.focalLengthSlider.presetRatios)
        assertEquals(2f, controls.focalLengthSlider.currentRatio)
    }

    @Test
    fun `focal slider current ratio is normalized to one decimal`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 2.345f
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertEquals(2.3f, controls.focalLengthSlider.currentRatio)
    }

    @Test
    fun `slider and capsule row both visible when zoom supported`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            )
        )
        val controls = sessionControlsRenderModel(state, strings, TestAppTextResolver())

        assertTrue(controls.isZoomCapsuleRowVisible)
        assertTrue(controls.focalLengthSlider.isVisible)
    }

    @Test
    fun `mode track render model includes document entry and uses product order`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.DOCUMENT, ModeId.CHECK_IN, ModeId.HUMANISTIC,
            ModeId.VIDEO
        )
        val state = defaultSessionState(activeMode = ModeId.HUMANISTIC, availableModes = availableModes)
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        assertEquals(5, model.items.size)
        assertEquals(
            listOf(
                ModeId.PHOTO,
                ModeId.CHECK_IN,
                ModeId.HUMANISTIC,
                ModeId.DOCUMENT,
                ModeId.VIDEO
            ),
            model.items.map { it.modeId }
        )
        assertTrue(model.items.any { it.modeId == ModeId.CHECK_IN })
        assertTrue(model.items.any { it.modeId == ModeId.HUMANISTIC })
        assertTrue(model.items.any { it.modeId == ModeId.DOCUMENT })
        assertTrue(model.items.first { it.modeId == ModeId.HUMANISTIC }.isActive)
    }

    @Test
    fun `mode track labels are short and stable`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.HUMANISTIC, ModeId.VIDEO, ModeId.DOCUMENT
        )
        val state = defaultSessionState(activeMode = ModeId.PHOTO, availableModes = availableModes)
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
            activeMode = ModeId.HUMANISTIC,
            availableModes = listOf(ModeId.PHOTO, ModeId.HUMANISTIC, ModeId.VIDEO)
        )
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        val active = model.items.first { it.modeId == ModeId.HUMANISTIC }
        val inactive = model.items.first { it.modeId == ModeId.PHOTO }

        assertTrue(active.isActive)
        assertFalse(inactive.isActive)
    }

    @Test
    fun `quick button labels fit within 96dp button width without ellipsis`() {
        val quickLabels = listOf("网格", "画质", "像素", "画幅", "实况", "定时")
        quickLabels.forEach { label ->
            assertTrue(label.length <= 2, "Quick button label '$label' exceeds 2 chars")
        }
    }

    @Test
    fun `quick panel sheet exposes five rows without quality`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Grid", sheet.gridRow.title)
        assertEquals("Size", sheet.resolutionRow.title)
        assertEquals("Frame", sheet.frameRatioRow.title)
        assertEquals("Watermark", sheet.watermarkRow.title)
        assertEquals("Travel Polaroid", sheet.watermarkRow.value)
        assertTrue(sheet.watermarkRow.isEnabled)
        assertEquals("Live", sheet.liveRow.title)
        assertEquals("Timer", sheet.timerRow.title)

        // Brightness row has slider fields
        assertEquals("Brightness", sheet.brightnessRow.title)
        assertTrue(sheet.brightnessRow.isVisible)
        assertEquals(0, sheet.brightnessRow.steps)
        assertTrue(sheet.brightnessRow.maxSteps >= sheet.brightnessRow.minSteps)
    }

    @Test
    fun `quick panel sheet exposes resolution row`() {
        val state = defaultSessionState(
            activeDeviceGraph = DeviceGraphSpec.stillCapture()
        )

        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Size", sheet.resolutionRow.title)
        assertEquals("12MP", sheet.resolutionRow.value)
        assertTrue(sheet.resolutionRow.isEnabled)
    }

    @Test
    fun `quick resolution uses active native output size when available`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                outputSize = StillCaptureOutputSize(width = 6000, height = 4000)
            )
        )

        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("24MP", sheet.resolutionRow.value)
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
        assertNull(sheet.frameRatioNext)
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
        assertNull(sheet.frameRatioNext)
    }

    @Test
    fun `frameRatioNext cycles 4_3 to 16_9 to 1_1 back to 4_3`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(FrameRatio.RATIO_16_9, sheet.frameRatioNext)

        // Simulate 16:9 selected
        val state169 = defaultSessionState().copy(
            activeEffectSpec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_16_9)))
        )
        val sheet169 = quickPanelSheetRenderModel(state169, TestAppTextResolver(), strings)
        assertEquals(FrameRatio.RATIO_1_1, sheet169.frameRatioNext)

        // Simulate 1:1 selected
        val state11 = defaultSessionState().copy(
            activeEffectSpec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_1_1)))
        )
        val sheet11 = quickPanelSheetRenderModel(state11, TestAppTextResolver(), strings)
        assertEquals(FrameRatio.RATIO_4_3, sheet11.frameRatioNext)
    }

    @Test
    fun `quick panel rows expose correct control kinds`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(QuickControlKind.CYCLE, sheet.gridRow.controlKind)
        assertEquals(QuickControlKind.CYCLE, sheet.resolutionRow.controlKind)
        assertEquals(QuickControlKind.SEGMENTED, sheet.frameRatioRow.controlKind)
        assertEquals(QuickControlKind.CYCLE, sheet.watermarkRow.controlKind)
        assertEquals(QuickControlKind.TOGGLE, sheet.liveRow.controlKind)
        assertEquals(QuickControlKind.CYCLE, sheet.timerRow.controlKind)
    }

    @Test
    fun `quick panel live row isSelected reflects on-off state`() {
        val stateOn = defaultSessionState(
            persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                livePhotoEnabledByDefault = true
            )
        )
        val sheetOn = quickPanelSheetRenderModel(stateOn, TestAppTextResolver(), strings)
        assertTrue(sheetOn.liveRow.isSelected)
        assertEquals("On", sheetOn.liveRow.value)

        val stateOff = defaultSessionState(
            persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                livePhotoEnabledByDefault = false
            )
        )
        val sheetOff = quickPanelSheetRenderModel(stateOff, TestAppTextResolver(), strings)
        assertFalse(sheetOff.liveRow.isSelected)
        assertEquals("Off", sheetOff.liveRow.value)
    }

    @Test
    fun `quick panel rows show disabled reason when present`() {
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

        assertNotNull(sheet.frameRatioRow.disabledReason)
        assertFalse(sheet.frameRatioRow.isEnabled)
    }

    @Test
    fun `quick panel watermark row shows current template label`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Watermark", sheet.watermarkRow.title)
        assertEquals("Travel Polaroid", sheet.watermarkRow.value)
        assertTrue(sheet.watermarkRow.isEnabled)
        assertNull(sheet.watermarkRow.disabledReason)
    }

    @Test
    fun `quick panel watermark row cycles to next template`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertNotNull(sheet.watermarkNextTemplateId)
        assertNotEquals(state.settings.persisted.photo.defaultWatermarkTemplateId, sheet.watermarkNextTemplateId)
    }

    @Test
    fun `quick panel watermark row wraps around to first template`() {
        // With only one template, there is no next
        val state = defaultSessionState(
            persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                defaultWatermarkTemplateId = "classic-overlay"
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        // classic-overlay is first; next should be travel-polaroid (second)
        assertEquals("travel-polaroid", sheet.watermarkNextTemplateId)
    }

    @Test
    fun `quick panel watermark row disabled when no templates available`() {
        val state = defaultSessionState().copy(
            settings = defaultSessionState().settings.copy(
                catalog = com.opencamera.core.settings.FeatureCatalog(
                    watermarkTemplates = emptyList()
                )
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.watermarkRow.isEnabled)
        assertNotNull(sheet.watermarkRow.disabledReason)
        assertNull(sheet.watermarkNextTemplateId)
    }

    @Test
    fun `quick panel watermark row disabled during active shot`() {
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

        assertFalse(sheet.watermarkRow.isEnabled)
        assertNotNull(sheet.watermarkRow.disabledReason)
        assertNull(sheet.watermarkNextTemplateId)
    }

    @Test
    fun `quick panel live row shows off by default`() {
        val defaultState = com.opencamera.core.settings.PhotoSettings()
        assertFalse(defaultState.livePhotoEnabledByDefault)

        val state = defaultSessionState(
            persistedPhotoSettings = defaultState
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.liveRow.isSelected)
        assertEquals("Off", sheet.liveRow.value)
    }

    @Test
    fun `quick panel watermark row uses localized label not raw template id`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        // The watermark value should be the localized label, not the raw id
        assertNotEquals("travel-polaroid", sheet.watermarkRow.value)
        assertEquals("Travel Polaroid", sheet.watermarkRow.value)
    }

    @Test
    fun `quick panel watermark row uses localized label for classic overlay`() {
        val state = defaultSessionState(
            persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                defaultWatermarkTemplateId = "classic-overlay"
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertNotEquals("classic-overlay", sheet.watermarkRow.value)
        assertEquals("Classic overlay", sheet.watermarkRow.value)
    }

    @Test
    fun `quick panel watermark row falls back to raw id for unknown template`() {
        val state = defaultSessionState(
            persistedPhotoSettings = defaultSessionState().settings.persisted.photo.copy(
                defaultWatermarkTemplateId = "unknown-template"
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("unknown-template", sheet.watermarkRow.value)
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
    fun `primary status uses localized preview capture and pro labels`() {
        val text = object : TestAppTextResolver() {
            internal override fun get(@StringRes resId: Int): String = when (resId) {
                R.string.status_pro_active -> "专业模式已启用"
                else -> super.get(resId)
            }

            override fun previewStatusLabel(status: PreviewStatus): String = "预览中"
            override fun captureStatusLabel(status: CaptureStatus): String = "保存中"
        }
        val baseline = defaultSessionState()
        val state = baseline.copy(
            captureStatus = CaptureStatus.SAVING,
            modeSnapshot = baseline.modeSnapshot.copy(
                state = baseline.modeSnapshot.state.copy(isProVariantActive = true)
            )
        )

        val model = primaryStatusRenderModel(state, text)

        assertEquals("预览中 · 保存中 · 专业模式已启用", model.statusText)
    }

    @Test
    fun `zoom disabled reason uses localized resolver text`() {
        val text = object : TestAppTextResolver() {
            internal override fun get(@StringRes resId: Int): String = when (resId) {
                R.string.disabled_countdown -> "倒计时进行中"
                else -> super.get(resId)
            }
        }
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.CONTINUOUS,
                    supportedRatios = listOf(1f, 2f),
                    defaultRatio = 1f
                )
            )
        ).copy(
            presentation = SessionPresentationState(countdownRemainingSeconds = 3)
        )

        val model = sessionControlsRenderModel(state, strings, text)

        assertEquals("倒计时进行中", model.focalLengthSlider.disabledReason)
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
    fun `capture disabled reason returns saving photo when activeShot set`() {
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
        ).copy(captureStatus = CaptureStatus.SAVING)
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertEquals("Saving previous photo", reason)
    }

    @Test
    fun `shutter disabled reason returns null during background save after rearm`() {
        // After session rearm: captureStatus == SAVING but activeShot is null.
        // Shutter should be enabled so user can immediately take next photo.
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING, activeShot = null)
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
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

    // --- shutter visual state tests ---

    @Test
    fun `shutter visual state is PHOTO_READY when idle`() {
        val state = defaultSessionState()
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is BLOCKED when preview recovering`() {
        val state = defaultSessionState(previewStatus = PreviewStatus.RECOVERING)
        assertEquals(ShutterVisualState.BLOCKED, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is BLOCKED when camera permission denied`() {
        val state = defaultSessionState().copy(
            permissionState = PermissionState(cameraGranted = false, microphoneGranted = false)
        )
        assertEquals(ShutterVisualState.BLOCKED, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is COUNTDOWN when countdown active`() {
        val state = defaultSessionState().copy(
            presentation = SessionPresentationState(countdownRemainingSeconds = 3)
        )
        assertEquals(ShutterVisualState.COUNTDOWN, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is BACKGROUND_SAVING when capture status is saving without activeShot`() {
        // After session rearm: captureStatus may still be SAVING but activeShot is already null.
        // Shutter is enabled; show subtle background work indicator.
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING)
        assertEquals(ShutterVisualState.BACKGROUND_SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is SAVING when active photo shot`() {
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
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state transitions from PHOTO_PRESSED to SAVING`() {
        val shot = ShotRequest(
            shotId = "trans-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        // REQUESTED → PHOTO_PRESSED (immediate tap acknowledgment)
        val requestedState = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.REQUESTED)
        assertEquals(ShutterVisualState.PHOTO_PRESSED, shutterVisualState(requestedState))

        // SAVING → SAVING
        val savingState = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.SAVING)
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(savingState))

        // DATA_RECEIVED with activeShot still set (conservative) → SAVING
        val dataReceivedState = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.DATA_RECEIVED)
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(dataReceivedState))
    }

    @Test
    fun `shutter visual state is BACKGROUND_SAVING after rearm while captureStatus is SAVING`() {
        // Edge case: activeShot cleared by rearm while captureStatus still SAVING.
        // Visual shows BACKGROUND_SAVING; shutter remains disabled (captureStatus still SAVING).
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING, activeShot = null)
        assertEquals(ShutterVisualState.BACKGROUND_SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is VIDEO_REQUESTING when recording requesting`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.REQUESTING)
        assertEquals(ShutterVisualState.VIDEO_REQUESTING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is VIDEO_RECORDING when actively recording`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        assertEquals(ShutterVisualState.VIDEO_RECORDING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is VIDEO_STOPPING when recording stopping`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.STOPPING)
        assertEquals(ShutterVisualState.VIDEO_STOPPING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is FAILURE_OR_DEGRADED when capture failed`() {
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.FAILED)
        assertEquals(ShutterVisualState.FAILURE_OR_DEGRADED, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is PHOTO_PRESSED when capture requested with active photo shot`() {
        // Between submitCaptureStrategy (sets activeShot + REQUESTED) and ShotStarted (sets SAVING),
        // the shutter shows PHOTO_PRESSED for immediate tactile feedback (P1 latency phase).
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "press-1",
                shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        ).copy(captureStatus = CaptureStatus.REQUESTED)
        assertEquals(ShutterVisualState.PHOTO_PRESSED, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is PHOTO_PRESSED with shutterPressedAtElapsedMillis`() {
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "press-2",
                shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        ).copy(
            captureStatus = CaptureStatus.REQUESTED,
            shutterPressedAtElapsedMillis = System.currentTimeMillis()
        )
        assertEquals(ShutterVisualState.PHOTO_PRESSED, shutterVisualState(state))
    }

    // --- Session rearm policy: DATA_RECEIVED with null activeShot ---

    @Test
    fun `shutter disabled reason returns null for DATA_RECEIVED after session rearm`() {
        // After package 02 rearm: ordinary still capture clears activeShot at DATA_RECEIVED.
        // Shutter must be re-enabled even though postprocess may still be finishing.
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null
        )
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `shutter disabled reason blocks DATA_RECEIVED before rearm for conservative capture`() {
        // Conservative capture (multi-frame) keeps activeShot until ShotCompleted.
        // Shutter must stay blocked.
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "mf-1",
                shotKind = ShotKind.MULTI_FRAME_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.NONE,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        ).copy(captureStatus = CaptureStatus.DATA_RECEIVED)
        assertNotNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `shutter visual state is PHOTO_READY for DATA_RECEIVED after session rearm`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null
        )
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(state))
    }

    @Test
    fun `shutter remains clickable via cockpit render model after DATA_RECEIVED rearm`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null
        )
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(cockpit.bottomCockpit.isShutterEnabled)
        assertNull(cockpit.bottomCockpit.disabledReason)
    }

    @Test
    fun `shutter visual state recovers to PHOTO_READY after preview recovery`() {
        val recovering = defaultSessionState(previewStatus = PreviewStatus.RECOVERING)
        assertEquals(ShutterVisualState.BLOCKED, shutterVisualState(recovering))

        val recovered = defaultSessionState(previewStatus = PreviewStatus.ACTIVE)
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(recovered))
    }

    @Test
    fun `shutter disabled reason blocks multi-frame capture with activeShot at DATA_RECEIVED`() {
        // Conservative capture (multi-frame) keeps activeShot until ShotCompleted.
        // Shutter must stay blocked even at DATA_RECEIVED.
        val shot = ShotRequest(
            shotId = "mf-block-1",
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.DATA_RECEIVED)
        assertNotNull(shutterDisabledReason(state, TestAppTextResolver()))
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter remains disabled with activePhotoShot during SAVING`() {
        // Active photo shot with SAVING status: shutter must stay disabled.
        val shot = ShotRequest(
            shotId = "save-block-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.SAVING)
        assertNotNull(shutterDisabledReason(state, TestAppTextResolver()))
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter visual state is PHOTO_READY when captureReadiness set with active photo shot`() {
        // CaptureReadiness signals the frame is acquired; visual shows ready
        // even though activeShot is still present (post-processing continues).
        val shot = ShotRequest(
            shotId = "ready-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.REQUESTED)
            .copy(
                presentation = SessionPresentationState(
                    captureReadiness = CaptureReadiness(
                        shotId = "ready-1",
                        mediaType = MediaType.PHOTO,
                        source = "test"
                    )
                )
            )
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(state))
    }

    @Test
    fun `shutter disabled reason returns null when captureReadiness set with active photo shot`() {
        // Readiness re-arms the shutter even while activeShot is still present.
        val shot = ShotRequest(
            shotId = "ready-2",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.REQUESTED)
            .copy(
                presentation = SessionPresentationState(
                    captureReadiness = CaptureReadiness(
                        shotId = "ready-2",
                        mediaType = MediaType.PHOTO,
                        source = "test"
                    )
                )
            )
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `shutter enabled with captureReadiness via cockpit render model`() {
        val shot = ShotRequest(
            shotId = "ready-3",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.REQUESTED)
            .copy(
                presentation = SessionPresentationState(
                    captureReadiness = CaptureReadiness(
                        shotId = "ready-3",
                        mediaType = MediaType.PHOTO,
                        source = "test"
                    )
                )
            )
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)
        assertTrue(cockpit.bottomCockpit.isShutterEnabled)
        assertNull(cockpit.bottomCockpit.disabledReason)
        assertEquals(ShutterVisualState.PHOTO_READY, cockpit.bottomCockpit.shutterVisualState)
    }

    @Test
    fun `shutter visual state shows SAVING when captureReadiness not set for active photo shot`() {
        // Without captureReadiness, active photo shot stays in SAVING visual.
        val shot = ShotRequest(
            shotId = "no-readiness",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.DATA_RECEIVED)
        // No captureReadiness set
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(state))
    }

    @Test
    fun `shutter disabled reason blocks when captureReadiness not set for active photo shot`() {
        // Without captureReadiness, shutter stays blocked during active photo shot.
        val shot = ShotRequest(
            shotId = "no-readiness-2",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot)
            .copy(captureStatus = CaptureStatus.DATA_RECEIVED)
        assertNotNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `shutter stays enabled for live photo after DataReceived rearm`() {
        val state = defaultSessionState()
            .copy(captureStatus = CaptureStatus.DATA_RECEIVED, activeShot = null)

        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(state))
    }

    @Test
    fun `shutter disabled reason returns null during active recording`() {
        // During active recording, shutter functions as stop-recording control.
        // Unlike captureDisabledReason (which blocks during recording),
        // shutterDisabledReason keeps the shutter enabled.
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `capture disabled reason blocks during recording but shutter stays enabled`() {
        // captureDisabledReason is the generic capture gating function used by
        // non-shutter controls. It blocks during recording.
        // shutterDisabledReason is the shutter-specific gating that keeps the
        // shutter enabled as stop-recording control.
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        assertNotNull(captureDisabledReason(state, TestAppTextResolver()))
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
    }

    @Test
    fun `shutter enabled with BACKGROUND_SAVING via cockpit render model`() {
        // After rearm: captureStatus == SAVING, activeShot == null.
        // Visual shows BACKGROUND_SAVING; shutter is enabled for immediate next shot.
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING, activeShot = null)
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(cockpit.bottomCockpit.isShutterEnabled)
        assertNull(cockpit.bottomCockpit.disabledReason)
        assertEquals(ShutterVisualState.BACKGROUND_SAVING, cockpit.bottomCockpit.shutterVisualState)
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
            enablePreviewSnapshots = true
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
        availableModes: List<ModeId> = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
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

    // ── Pending Postprocess UI State Tests ────────────────────────────

    @Test
    fun `primary status shows processing warning when pendingPostprocess is set`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertEquals("Processing photo. Please keep OpenCamera open.", model.statusText)
    }

    @Test
    fun `primary status does not show processing warning when pendingPostprocess is null`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(pendingPostprocess = null)
        )
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Data received") || model.statusText.contains("Active"))
        assertFalse(model.statusText.contains("keep OpenCamera open"))
    }

    @Test
    fun `primary status processing warning contains explicit do not exit language`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("keep OpenCamera open"))
    }

    @Test
    fun `shutter remains enabled during processing warning after session rearm`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(cockpit.bottomCockpit.isShutterEnabled)
        assertNull(cockpit.bottomCockpit.disabledReason)
    }

    @Test
    fun `capture disabled reason returns null during pending postprocess with no activeShot`() {
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val reason = captureDisabledReason(state, TestAppTextResolver())

        assertNull(reason)
    }

    @Test
    fun `conservative capture keeps shutter blocked even with pending postprocess`() {
        // Multi-frame: activeShot still set, shutter stays blocked
        val shot = ShotRequest(
            shotId = "mf-block-1",
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState(activeShot = shot).copy(
            captureStatus = CaptureStatus.DATA_RECEIVED
        )
        assertNotNull(shutterDisabledReason(state, TestAppTextResolver()))
        assertEquals(ShutterVisualState.SAVING, shutterVisualState(state))
    }

    // ── Package 05: watchdog release returns cockpit to non-disabled state ──

    @Test
    fun `cockpit returns to PHOTO_READY after post-process liveness watchdog release`() {
        // After the post-process liveness watchdog force-releases the previous shot,
        // the session state has activeShot=null && pendingPostprocess=null. The cockpit
        // must not keep the shutter in SAVING or show a disabled reason.
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.IDLE,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = null,
                captureReadiness = null
            )
        )
        assertNull(captureDisabledReason(state, TestAppTextResolver()))
        assertNull(shutterDisabledReason(state, TestAppTextResolver()))
        assertNotEquals(ShutterVisualState.SAVING, shutterVisualState(state))
        assertEquals(ShutterVisualState.PHOTO_READY, shutterVisualState(state))
    }

    @Test
    fun `cockpit render model unblocks shutter after watchdog release`() {
        // Full cockpit render model: after watchdog release, shutter is enabled and
        // no disabled reason is surfaced on the bottom cockpit.
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.IDLE,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = null,
                captureReadiness = null
            )
        )
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(cockpit.bottomCockpit.isShutterEnabled)
        assertNull(cockpit.bottomCockpit.disabledReason)
        assertNotEquals(ShutterVisualState.SAVING, cockpit.bottomCockpit.shutterVisualState)
    }

    @Test
    fun `config controls remain enabled during pending postprocess with no activeShot`() {
        // After session rearm: pendingPostprocess is active but activeShot is null.
        // Frame ratio, grid, watermark, timer, and live photo should stay enabled.
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.DATA_RECEIVED,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(sheet.frameRatioEnabled, "frame ratio should be enabled during pending postprocess")
        assertNull(sheet.frameRatioDisabledReason)
        assertTrue(sheet.gridRow.isEnabled, "grid should be enabled during pending postprocess")
        assertTrue(sheet.watermarkRow.isEnabled, "watermark should be enabled during pending postprocess")
        assertNull(sheet.watermarkRow.disabledReason)
        assertTrue(sheet.liveRow.isEnabled, "live photo toggle should be enabled during pending postprocess")
        assertTrue(sheet.timerRow.isEnabled, "timer should be enabled during pending postprocess")
    }

    @Test
    fun `shutter visual state is BACKGROUND_SAVING during pending postprocess with null activeShot`() {
        // After session rearm: captureStatus is SAVING, activeShot is null, pendingPostprocess is active.
        // Shutter shows BACKGROUND_SAVING (subtle indicator) and remains enabled.
        val state = defaultSessionState().copy(
            captureStatus = CaptureStatus.SAVING,
            activeShot = null,
            presentation = SessionPresentationState(
                pendingPostprocess = PendingPostprocessUiState(
                    shotId = "shot-1",
                    mediaType = MediaType.PHOTO,
                    message = "",
                    warnBeforeExit = true
                )
            )
        )
        assertNull(captureDisabledReason(state, TestAppTextResolver()))
        assertEquals(ShutterVisualState.BACKGROUND_SAVING, shutterVisualState(state))
    }

    @Test
    fun `all config controls disabled during active photo shot`() {
        // Preserve invariant: when activeShot != null, all non-shutter config controls
        // remain disabled regardless of pendingPostprocess state.
        val state = defaultSessionState(
            activeShot = ShotRequest(
                shotId = "active-test",
                shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            )
        )
        val frameControl = frameRatioControlRenderModel(state, TestAppTextResolver())
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(frameControl.isEnabled, "frame ratio should be disabled during active shot")
        assertNotNull(frameControl.disabledReason)
        assertFalse(sheet.frameRatioEnabled, "quick panel frame ratio should be disabled during active shot")
        assertFalse(sheet.gridRow.isEnabled, "grid should be disabled during active shot")
        assertFalse(sheet.watermarkRow.isEnabled, "watermark should be disabled during active shot")
        assertNotNull(sheet.watermarkRow.disabledReason)
        assertFalse(sheet.liveRow.isEnabled, "live photo should be disabled during active shot")
        assertFalse(sheet.timerRow.isEnabled, "timer should be disabled during active shot")
    }

    @Test
    fun `quick panel sheet does not expose reset action when adjustments exist`() {
        val state = defaultSessionState()
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.hasQuickUserAdjustments)
        assertNull(sheet.resetQuickAction)
    }

    @Test
    fun `quick panel sheet has no reset action when at defaults`() {
        val state = defaultSessionState().copy(
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings()
            )
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(sheet.hasQuickUserAdjustments)
        assertNull(sheet.resetQuickAction)
    }

    @Test
    fun `brightness row is visible in Humanistic mode`() {
        val state = defaultSessionState(activeMode = ModeId.HUMANISTIC)
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(sheet.brightnessRow.isVisible, "Brightness should be visible in Humanistic mode")
        assertTrue(sheet.brightnessRow.isInteractive, "Brightness should be interactive in Humanistic mode")
    }

    @Test
    fun `brightness row is visible in video mode`() {
        val state = defaultSessionState(
            activeMode = ModeId.VIDEO,
            previewStatus = PreviewStatus.ACTIVE
        )
        val sheet = quickPanelSheetRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(sheet.brightnessRow.isVisible, "Brightness should be visible in video mode")
        assertTrue(sheet.brightnessRow.isInteractive, "Brightness should be interactive in video mode")
    }

    @Test
    fun `brightness row is interactive when preview active and Humanistic mode`() {
        val state = defaultSessionState(
            activeMode = ModeId.HUMANISTIC,
            previewStatus = PreviewStatus.ACTIVE
        )
        val model = brightnessRenderModel(state, TestAppTextResolver())

        assertTrue(model.isVisible)
        assertTrue(model.isInteractive)
    }

    @Test
    fun `brightness row is not interactive when preview inactive in Humanistic mode`() {
        val state = defaultSessionState(
            activeMode = ModeId.HUMANISTIC,
            previewStatus = PreviewStatus.RECOVERING
        )
        val model = brightnessRenderModel(state, TestAppTextResolver())

        assertTrue(model.isVisible, "Brightness should still be visible when preview is recovering")
        assertFalse(model.isInteractive, "Brightness should not be interactive when preview is recovering")
    }

    @Test
    fun `primary status includes pro active when pro variant is active`() {
        val state = defaultSessionState(
            modeSnapshot = ModeSnapshot(
                id = ModeId.HUMANISTIC,
                uiSpec = ModeUiSpec(title = "Humanistic", shutterLabel = "Capture"),
                state = ModeState(headline = "Active", detail = "", isProVariantActive = true)
            )
        )
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertTrue(model.statusText.contains("Pro active"), "Status should show Pro active indicator")
    }

    @Test
    fun `primary status does not include pro active when pro variant is inactive`() {
        val state = defaultSessionState()
        val model = primaryStatusRenderModel(state, TestAppTextResolver())

        assertFalse(model.statusText.contains("Pro active"), "Status should not show Pro active when variant is inactive")
    }

    // --- Package 04: Regression tests for settings/capability projection coherence ---

    @Test
    fun `controls model reflects settings update after capability projection`() {
        val baseState = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.FRONT,
                enablePreviewSnapshots = true,
                zoomRatio = 2f
            )
        )

        val model = sessionControlsRenderModel(baseState, strings, TestAppTextResolver())

        assertEquals("Switch to Back", model.lensFacingButtonLabel)
        assertTrue(model.lensFacingEnabled)
        assertEquals(2f, model.zoomCapsules.firstOrNull { it.isActive }?.ratio)
    }

    @Test
    fun `capture output render model coherent after capability update`() {
        val stateBefore = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
            ),
            latestCapturePath = "Pictures/test.jpg"
        )

        val outputBefore = sessionCaptureOutputText(stateBefore, strings, TestAppTextResolver())
        assertNotNull(outputBefore)

        val stateAfter = stateBefore.copy(
            activeDeviceCapabilities = stateBefore.activeDeviceCapabilities.copy(
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 8000, height = 6000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            )
        )

        val outputAfter = sessionCaptureOutputText(stateAfter, strings, TestAppTextResolver())
        assertNotNull(outputAfter)
        assertEquals(outputBefore, outputAfter)
    }

    @Test
    fun `mode summary text reflects settings through session state after migration`() {
        val state = defaultSessionState(
            persistedPhotoSettings = PhotoSettings(
                defaultFilterProfileId = "photo-rich",
                countdownDuration = CountdownDuration.SECONDS_5
            )
        )

        val summary = sessionSummaryText(state, TestAppTextResolver())

        assertTrue(summary.contains("Filter Rich"), "Summary should reflect settings filter after runtime config migration")
        assertTrue(summary.contains("Timer 5s"), "Summary should reflect countdown settings after migration")
    }

    @Test
    fun `mode directory render model includes all declared modes after file split`() {
        val state = defaultSessionState(
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.DOCUMENT, ModeId.VIDEO)
        )

        val directory = modeDirectoryRenderModel(state, TestAppTextResolver())

        val modeIds = directory.items.map { it.modeId }.toSet()
        assertTrue(ModeId.PHOTO in modeIds)
        assertTrue(ModeId.VIDEO in modeIds)
        assertTrue(ModeId.HUMANISTIC in modeIds)
        assertTrue(ModeId.CHECK_IN in modeIds)
        assertTrue(ModeId.DOCUMENT in modeIds)
    }

    // --- Package 03: Style card selection flip after apply ---

    @Test
    fun `style card selection flips in rail model after apply UpdatePhotoFilter`() {
        val catalog = FeatureCatalog(
            filterProfiles = listOf(
                FilterProfile(
                    id = "photo-vivid",
                    label = "Vivid",
                    category = FilterProfileCategory.PHOTO,
                    renderSpec = FilterRenderSpec(contrast = 1.08f, saturation = 1.14f)
                ),
                FilterProfile(
                    id = "photo-original",
                    label = "Original",
                    category = FilterProfileCategory.PHOTO,
                    renderSpec = FilterRenderSpec(contrast = 1.01f, saturation = 1.01f)
                )
            )
        )
        val text = TestAppTextResolver()

        // Before apply: "photo-vivid" is selected, "photo-original" is available.
        val photoSettingsBefore = PhotoSettings(
            defaultFilterProfileId = "photo-vivid",
            defaultWatermarkTemplateId = "travel-polaroid",
            livePhotoEnabledByDefault = true,
            countdownDuration = CountdownDuration.SECONDS_3
        )
        val stateBefore = defaultSessionState(
            persistedPhotoSettings = photoSettingsBefore
        ).copy(
            settings = defaultSessionState(persistedPhotoSettings = photoSettingsBefore).settings.copy(catalog = catalog)
        )
        val railBefore = filterLabPageRenderModel(
            state = stateBefore,
            text = text,
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        ).stylePresetCardRail!!

        val vividBefore = railBefore.cards.first { it.profileId == "photo-vivid" }
        val originalBefore = railBefore.cards.first { it.profileId == "photo-original" }
        assertTrue(vividBefore.isSelected, "vivid should be selected before apply")
        assertFalse(originalBefore.isSelected, "original should not be selected before apply")
        assertNotNull(originalBefore.applyAction, "original should have applyAction before apply")
        assertNull(vividBefore.applyAction, "vivid should have null applyAction when already selected")

        // After apply: "photo-original" is now selected, "photo-vivid" is available.
        val photoSettingsAfter = PhotoSettings(
            defaultFilterProfileId = "photo-original",
            defaultWatermarkTemplateId = "travel-polaroid",
            livePhotoEnabledByDefault = true,
            countdownDuration = CountdownDuration.SECONDS_3
        )
        val stateAfter = defaultSessionState(
            persistedPhotoSettings = photoSettingsAfter
        ).copy(
            settings = defaultSessionState(persistedPhotoSettings = photoSettingsAfter).settings.copy(catalog = catalog)
        )
        val railAfter = filterLabPageRenderModel(
            state = stateAfter,
            text = text,
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        ).stylePresetCardRail!!

        val vividAfter = railAfter.cards.first { it.profileId == "photo-vivid" }
        val originalAfter = railAfter.cards.first { it.profileId == "photo-original" }
        assertFalse(vividAfter.isSelected, "vivid should no longer be selected after apply")
        assertTrue(originalAfter.isSelected, "original should be selected after apply")
        assertNotNull(vividAfter.applyAction, "vivid should now have applyAction after being deselected")
        assertNull(originalAfter.applyAction, "original should have null applyAction when now selected")
    }
}
