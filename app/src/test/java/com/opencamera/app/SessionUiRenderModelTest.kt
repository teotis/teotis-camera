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
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionTraceEvent
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import com.opencamera.core.settings.PersistedSettingsAction
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoFrameRate
import com.opencamera.core.settings.VideoResolution
import com.opencamera.core.settings.VideoSettings
import com.opencamera.core.settings.VideoSpec
import com.opencamera.core.settings.WatermarkFrameBackground
import com.opencamera.core.settings.WatermarkStyleSettings
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
import com.opencamera.core.settings.WatermarkTextScale
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionUiRenderModelTest {
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
        assertEquals("Zoom 2.0x", model.zoomButtonLabel)
        assertTrue(model.lensFacingEnabled)
        assertTrue(model.zoomEnabled)
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
        assertEquals("Zoom N/A", model.zoomButtonLabel)
        assertFalse(model.lensFacingEnabled)
        assertFalse(model.zoomEnabled)
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
            latestPipelineNotes = listOf("video-watermark:subtitle-created", "torch:on")
        )

        assertEquals(
            "Last video:\n/tmp/clip.mp4\nPipeline: video-watermark:subtitle-created, torch:on",
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
    fun `capture output preview thumbnail hides raw file path`() {
        val state = defaultSessionState().copy(
            presentation = defaultSessionState().presentation.copy(
                previewThumbnailPath = "/data/user/0/com.opencamera.app/cache/preview-thumbnails/test.jpg"
            )
        )

        assertEquals("Preview thumbnail:", sessionCaptureOutputText(state, strings))
    }

    @Test
    fun `session summary includes native output and action context`() {
        val baseState = defaultSessionState(
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
                ),
                availableStillCaptureResolutionPresets = setOf(
                    StillCaptureResolutionPreset.LARGE_12MP,
                    StillCaptureResolutionPreset.MEDIUM_8MP
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.FRONT,
                enablePreviewSnapshots = true,
                zoomRatio = 2f,
                qualityPreference = StillCaptureQualityPreference.QUALITY,
                resolutionPreset = StillCaptureResolutionPreset.MEDIUM_8MP,
                outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
            )
        )
        val state = baseState.copy(
            previewStatusDetail = "recover after surface lost",
            presentation = baseState.presentation.copy(
                lastAction = "Still resolution set to 4000x3000"
            )
        )

        val summary = sessionSummaryText(state, TestAppTextResolver())

        assertTrue(summary.contains("Lens: Front (available: back/front)"))
        assertTrue(summary.contains("Zoom: 2.0x (available: 1.0x/2.0x/5.0x | Preset steps)"))
        assertTrue(summary.contains("Still native output: 4000x3000"))
        assertTrue(summary.contains("Native size list: 6000x4000/4000x3000"))
        assertTrue(summary.contains("Settings: Grid 3x3 | Shutter sound Off | Selfie mirror On"))
        assertTrue(
            summary.contains(
                "Photo defaults: Filter Portrait Retro | Portrait Native Portrait | Watermark Travel Polaroid | Live On | Timer 3s"
            )
        )
        assertTrue(summary.contains("Video defaults: 4K 25fps | Mic Concert | Low-light auto 24fps | Filter Rich"))
        assertTrue(summary.contains("Catalog: 16 filters | 3 watermark templates | Live 1500 ms bundle"))
        assertTrue(summary.contains("Manual draft: RAW Off | ISO Auto | S Auto | WB Auto"))
        assertTrue(summary.contains("Action: Still resolution set to 4000x3000"))
    }

    @Test
    fun `session diagnostics text renders debug dump perf snapshot and recovery trace`() {
        val state = defaultSessionState(
            previewStatus = PreviewStatus.ACTIVE,
            previewMetrics = PreviewMetrics(
                bindCount = 4,
                recoveryCount = 2,
                lastFirstFrameLatencyMillis = 88,
                bestFirstFrameLatencyMillis = 80,
                worstFirstFrameLatencyMillis = 120,
                lastStartReason = "recover after provider restart"
            ),
            lastError = "provider restarted"
        )
        val traceEvents = listOf(
            SessionTraceEvent(1, "preview.surface.lost", "provider restarted", 1L),
            SessionTraceEvent(2, "preview.recovery.started", "recover after provider restart", 2L),
            SessionTraceEvent(3, "preview.first.frame", "88ms", 3L),
            SessionTraceEvent(4, "intent.received", "PreviewFirstFrameAvailable", 4L)
        )

        val diagnostics = sessionDiagnosticsText(state, traceEvents)

        assertTrue(diagnostics.contains("DebugDump: RUNNING | PHOTO | preview=ACTIVE"))
        assertTrue(
            diagnostics.contains(
                "PerfSnapshot: last=88 ms, best=80 ms, worst=120 ms, binds=4, recoveries=2, budget=within budget (Recovery, warn=180 ms, fail=320 ms)"
            )
        )
        assertTrue(
            diagnostics.contains(
                "RecoveryTrace: idle, last=recover after provider restart, recoveredFrame=88 ms, failure=provider restarted"
            )
        )
        assertTrue(diagnostics.contains("Error: provider restarted"))
        assertTrue(diagnostics.contains("2. preview.recovery.started -> recover after provider restart"))
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
    fun `settings render model resolves configured defaults from session snapshot`() {
        val state = defaultSessionState()

        val model = sessionSettingsRenderModel(state, TestAppTextResolver())

        assertEquals("Grid 3x3 | Shutter sound Off | Selfie mirror On", model.commonSummary)
        assertEquals(
            "Filter Portrait Retro | Portrait Native Portrait | Watermark Travel Polaroid | Live On | Timer 3s",
            model.photoSummary
        )
        assertEquals(
            "4K 25fps | Mic Concert | Low-light auto 24fps | Filter Rich",
            model.videoSummary
        )
        assertEquals("16 filters | 3 watermark templates | Live 1500 ms bundle", model.catalogSummary)
        assertEquals("RAW Off | ISO Auto | S Auto | WB Auto", model.manualDraftSummary)
    }

    @Test
    fun `runtime pro controls render model exposes editable controls for active pro variant`() {
        val baseline = defaultSessionState()
        val state = baseline.copy(
            activeMode = ModeId.NIGHT,
            modeSnapshot = baseline.modeSnapshot.copy(
                id = ModeId.NIGHT,
                state = baseline.modeSnapshot.state.copy(
                    isProVariantActive = true
                )
            ),
            settings = baseline.settings.copy(
                catalog = baseline.settings.catalog.copy(
                    manualCaptureDraft = com.opencamera.core.settings.ManualCaptureParams(
                        rawEnabled = true,
                        iso = 320,
                        shutterSpeedMillis = 33L,
                        whiteBalanceKelvin = 4800
                    )
                )
            )
        )

        val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

        assertTrue(model.isVisible)
        assertEquals("Scenery Pro Controls", model.headline)
        assertEquals(SettingsControlAvailability.DEGRADED, model.rawControl.availability)
        assertEquals(SettingsControlAvailability.SUPPORTED, model.isoControl.availability)
        assertEquals("320", model.isoControl.value)
        assertEquals("33ms", model.shutterControl.value)
        assertEquals("4800K", model.whiteBalanceControl.value)
        assertTrue(model.summary.contains("RAW On | ISO 320 | S 33ms | WB 4800K"))
        assertTrue(model.summary.contains("RAW / WB stay saved-only"))
        assertTrue(model.isoControl.nextAction != null)
    }

    @Test
    fun `runtime pro controls degrade to saved only on devices without manual support`() {
        val baseline = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                supportsManualControls = false
            )
        )
        val state = baseline.copy(
            activeMode = ModeId.HUMANISTIC,
            modeSnapshot = baseline.modeSnapshot.copy(
                id = ModeId.HUMANISTIC,
                state = baseline.modeSnapshot.state.copy(
                    isProVariantActive = true
                )
            )
        )

        val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

        assertTrue(model.isVisible)
        assertEquals(SettingsControlAvailability.DEGRADED, model.isoControl.availability)
        assertEquals("Saved only", model.isoControl.supportLabel)
        assertTrue(model.supportingText.contains("仅保存或暂时不支持"))
        assertTrue(model.summary.contains("stay saved-only"))
    }

    @Test
    fun `runtime pro controls surface unsupported controls from capability matrix`() {
        val baseline = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                manualControlCapabilities = ManualControlCapabilityMatrix(
                    raw = ManualControlSupport.SAVED_ONLY,
                    iso = ManualControlSupport.APPLY,
                    shutter = ManualControlSupport.APPLY,
                    exposureCompensation = ManualControlSupport.APPLY,
                    focusDistance = ManualControlSupport.APPLY,
                    aperture = ManualControlSupport.UNSUPPORTED,
                    whiteBalance = ManualControlSupport.UNSUPPORTED
                )
            )
        )
        val state = baseline.copy(
            activeMode = ModeId.PORTRAIT,
            modeSnapshot = baseline.modeSnapshot.copy(
                id = ModeId.PORTRAIT,
                state = baseline.modeSnapshot.state.copy(
                    isProVariantActive = true
                )
            )
        )

        val model = runtimeProControlsRenderModel(state, TestAppTextResolver())

        assertEquals(SettingsControlAvailability.UNSUPPORTED, model.apertureControl.availability)
        assertEquals("Temporarily unsupported", model.apertureControl.supportLabel)
        assertEquals(
            SettingsControlAvailability.UNSUPPORTED,
            model.whiteBalanceControl.availability
        )
        assertTrue(model.summary.contains("A / WB temporarily unsupported"))
    }

    @Test
    fun `preview overlay render model exposes active grid and countdown`() {
        val baseline = defaultSessionState()
        val model = previewOverlayRenderModel(
            baseline.copy(
                presentation = baseline.presentation.copy(countdownRemainingSeconds = 3),
                captureStatus = CaptureStatus.REQUESTED
            )
        )

        assertEquals(CompositionGridMode.RULE_OF_THIRDS, model.gridMode)
        assertTrue(model.isGridVisible)
        assertEquals("3s", model.countdownLabel)
        assertTrue(model.isCountdownVisible)
        assertTrue(model.isVisible)
    }

    @Test
    fun `preview overlay render model hides aids when preview is unavailable`() {
        val baseline = defaultSessionState()
        val blockedModel = previewOverlayRenderModel(
            baseline.copy(
                previewStatus = PreviewStatus.BLOCKED,
                presentation = baseline.presentation.copy(countdownRemainingSeconds = 3),
                captureStatus = CaptureStatus.REQUESTED
            )
        )
        val offGridModel = previewOverlayRenderModel(
            baseline.copy(
                settings = baseline.settings.copy(
                    persisted = baseline.settings.persisted.copy(
                        common = baseline.settings.persisted.common.copy(
                            gridMode = CompositionGridMode.OFF
                        )
                    )
                )
            )
        )

        assertFalse(blockedModel.isGridVisible)
        assertTrue(blockedModel.isCountdownVisible)
        assertFalse(offGridModel.isGridVisible)
        assertFalse(offGridModel.isCountdownVisible)
    }

    @Test
    fun `settings page render model exposes section controls and catalog hints`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        assertEquals("Lens Lab", model.headline)
        assertEquals(
            "Grid 3x3 | Shutter sound Off | Selfie mirror On • Filter Portrait Retro | Portrait Native Portrait | Watermark Travel Polaroid | Live On | Timer 3s",
            model.heroSummary
        )
        assertTrue(model.editingEnabled)
        assertEquals(
            "Composition grid\n3x3\nSupported • Cycle 3 layouts",
            model.commonSection.gridMode.buttonLabel
        )
        assertEquals(
            "Default photo filter\nPortrait Retro\nSupported • 16 curated looks",
            model.photoSection.defaultFilter.buttonLabel
        )
        assertEquals(
            "Portrait Lab\nNative Portrait / Authentic / Natural\nDegraded • Open profile + beauty + bokeh tuning",
            model.photoSection.portraitLab.buttonLabel
        )
        assertEquals(
            "Watermark Lab\nTravel Polaroid\nDegraded • Open selector + per-template tuning; 3 templates",
            model.photoSection.watermarkTemplate.buttonLabel
        )
        assertEquals(
            "Resolution\n4K\nSupported • 4 options",
            model.videoSection.resolution.buttonLabel
        )
        assertEquals(
            PersistedSettingsAction.UpdateGridMode(CompositionGridMode.GOLDEN_RATIO),
            model.commonSection.gridMode.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdatePhotoFilter("portrait-ccd"),
            model.photoSection.defaultFilter.nextAction
        )
        assertEquals(null, model.photoSection.portraitLab.nextAction)
        assertEquals(
            PersistedSettingsAction.UpdateDefaultVideoSpec(
                VideoSpec(
                    resolution = VideoResolution.FHD_1080P,
                    frameRate = VideoFrameRate.FPS_25,
                    dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                    audioProfile = AudioProfile.CONCERT
                )
            ),
            model.videoSection.resolution.nextAction
        )
        assertTrue(
            model.catalogFooter.contains("Still watermark templates now flow into metadata and photo rendering.")
        )
        assertTrue(
            model.catalogFooter.contains("Pro manual draft RAW Off | ISO Auto | S Auto | WB Auto.")
        )
        assertTrue(
            model.catalogFooter.contains("Manual drafts and Live/video defaults remain staged in the same settings spine.")
        )
    }

    @Test
    fun `settings page render model disables editing while shot is active`() {
        val model = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeShot = ShotRequest(
                    shotId = "shot-1",
                    shotKind = ShotKind.STILL_CAPTURE,
                    mediaType = MediaType.PHOTO,
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                    postProcessSpec = com.opencamera.core.media.PostProcessSpec(),
                    captureProfile = CaptureProfile()
                )
            ),
            TestAppTextResolver()
        )

        assertFalse(model.editingEnabled)
        assertEquals(
            "Finish the current capture before changing saved defaults.",
            model.editingHint
        )
    }

    @Test
    fun `settings page render model surfaces supported degraded and unsupported controls`() {
        val supportedModel = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())

        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.photoSection.livePhoto.availability)
        assertTrue(supportedModel.photoSection.livePhoto.isInteractive)
        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.photoSection.portraitLab.availability)
        assertFalse(supportedModel.photoSection.portraitLab.isInteractive)
        assertEquals(
            "Live photo default\nOn\nDegraded • Saved default only; 1500 ms bundle | dynamic watermark Follow Frame Luma + Motion",
            supportedModel.photoSection.livePhoto.buttonLabel
        )
        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.photoSection.watermarkTemplate.availability)
        assertFalse(supportedModel.photoSection.watermarkTemplate.isInteractive)
        assertEquals(SettingsControlAvailability.SUPPORTED, supportedModel.photoSection.countdown.availability)
        assertEquals(SettingsControlAvailability.SUPPORTED, supportedModel.videoSection.frameRate.availability)
        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.dynamicFps.availability)
        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.audioProfile.availability)
        assertEquals(SettingsControlAvailability.DEGRADED, supportedModel.videoSection.defaultFilter.availability)

        val unsupportedModel = sessionSettingsPageRenderModel(
            defaultSessionState(
                activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                    supportsStillCapture = false,
                    supportsVideoRecording = false,
                    supportsAudioRecording = false
                )
            ),
            TestAppTextResolver()
        )

        assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.livePhoto.availability)
        assertFalse(unsupportedModel.photoSection.livePhoto.isInteractive)
        assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.portraitLab.availability)
        assertFalse(unsupportedModel.photoSection.portraitLab.isInteractive)
        assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.watermarkTemplate.availability)
        assertFalse(unsupportedModel.photoSection.watermarkTemplate.isInteractive)
        assertEquals(null, unsupportedModel.photoSection.watermarkTemplate.nextAction)
        assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.photoSection.countdown.availability)
        assertEquals(null, unsupportedModel.photoSection.countdown.nextAction)
        assertEquals(SettingsControlAvailability.UNSUPPORTED, unsupportedModel.videoSection.resolution.availability)
        assertFalse(unsupportedModel.videoSection.resolution.isInteractive)
        assertEquals(null, unsupportedModel.videoSection.resolution.nextAction)
        assertEquals(
            "Audio scene\nConcert\nUnsupported • Video recording unavailable on this device",
            unsupportedModel.videoSection.audioProfile.buttonLabel
        )
    }

    @Test
    fun `watermark lab selector render model exposes selection and per template style entry`() {
        val model = watermarkLabSelectorRenderModel(defaultSessionState(), TestAppTextResolver())

        assertEquals("Watermark Lab", model.headline)
        assertTrue(model.heroSummary.contains("Default Travel Polaroid"))
        assertEquals(3, model.items.size)
        val selected = model.items.first { it.templateId == "travel-polaroid" }
        val classic = model.items.first { it.templateId == "classic-overlay" }
        assertTrue(selected.isSelected)
        assertEquals(null, selected.useAction)
        assertTrue(selected.supportingText.contains("Current default"))
        assertTrue(selected.editButtonLabel?.contains("Placement, scale, opacity, background") == true)
        assertEquals(
            PersistedSettingsAction.UpdatePhotoWatermarkTemplate("classic-overlay"),
            classic.useAction
        )
        assertTrue(classic.supportingText.contains("Classic overlay"))
        assertTrue(model.footer.contains("Classic Overlay keeps its border background fixed"))
    }

    @Test
    fun `portrait lab render model exposes profile beauty and bokeh controls`() {
        val baseline = defaultSessionState()
        val model = portraitLabPageRenderModel(
            baseline.copy(
                settings = baseline.settings.copy(
                    persisted = baseline.settings.persisted.copy(
                        photo = baseline.settings.persisted.photo.copy(
                            portraitProfile = PortraitProfile.LUMINOUS,
                            portraitBeautyPreset = PortraitBeautyPreset.RADIANT,
                            portraitBeautyStrength = PortraitBeautyStrength.ELEVATED,
                            portraitBokehEffect = PortraitBokehEffect.DREAMY
                        )
                    )
                )
            ),
            TestAppTextResolver()
        )

        assertEquals("Portrait Lab", model.headline)
        assertTrue(model.heroSummary.contains("Luminous Portrait"))
        assertEquals(
            PersistedSettingsAction.UpdatePortraitProfile(PortraitProfile.NATIVE),
            model.profileControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdatePortraitBeautyPreset(PortraitBeautyPreset.AUTHENTIC),
            model.beautyPresetControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdatePortraitBeautyStrength(PortraitBeautyStrength.OFF),
            model.beautyStrengthControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdatePortraitBokehEffect(PortraitBokehEffect.NATURAL),
            model.bokehEffectControl.nextAction
        )
        assertTrue(model.footer.contains("Tone Lab still owns portrait color style selection"))
    }

    @Test
    fun `watermark lab detail render model exposes frame controls for frame templates`() {
        val baselineState = defaultSessionState()
        val model = watermarkLabDetailRenderModel(
            state = baselineState.copy(
                settings = baselineState.settings.copy(
                    persisted = baselineState.settings.persisted.copy(
                        photo = baselineState.settings.persisted.photo.copy(
                            defaultWatermarkTemplateId = "travel-polaroid",
                            travelPolaroidWatermarkStyle = WatermarkStyleSettings(
                                textPlacement = WatermarkTextPlacement.TOP_RIGHT,
                                textScale = WatermarkTextScale.COMPACT,
                                textOpacity = WatermarkTextOpacity.SUBTLE,
                                frameBackground = WatermarkFrameBackground.SOURCE_LIGHT_BLUR
                            )
                        )
                    )
                )
            ),
            templateId = "travel-polaroid",
            text = TestAppTextResolver()
        )

        assertEquals("Travel Polaroid", model.headline)
        assertTrue(model.heroSummary.contains("Placement Top Right"))
        assertEquals(
            PersistedSettingsAction.UpdateWatermarkTextPlacement(
                templateId = "travel-polaroid",
                placement = WatermarkTextPlacement.BOTTOM_LEFT
            ),
            model.placementControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdateWatermarkTextScale(
                templateId = "travel-polaroid",
                scale = WatermarkTextScale.NORMAL
            ),
            model.textScaleControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdateWatermarkTextOpacity(
                templateId = "travel-polaroid",
                opacity = WatermarkTextOpacity.SOFT
            ),
            model.textOpacityControl.nextAction
        )
        assertEquals(
            PersistedSettingsAction.UpdateWatermarkFrameBackground(
                templateId = "travel-polaroid",
                background = WatermarkFrameBackground.SOURCE_VIVID_BLUR
            ),
            model.frameBackgroundControl?.nextAction
        )
    }

    @Test
    fun `watermark lab detail hides frame background control for classic overlay`() {
        val model = watermarkLabDetailRenderModel(
            state = defaultSessionState(),
            templateId = "classic-overlay",
            text = TestAppTextResolver()
        )

        assertEquals("Classic Overlay", model.headline)
        assertEquals(null, model.frameBackgroundControl)
        assertTrue(model.footer.contains("Classic overlay stays inside the source image"))
    }

    @Test
    fun `filter lab render model follows active portrait mode and cycles portrait defaults`() {
        val model = filterLabPageRenderModel(
            defaultSessionState(
                activeMode = ModeId.PORTRAIT,
                availableModes = listOf(ModeId.PHOTO, ModeId.PORTRAIT, ModeId.HUMANISTIC, ModeId.VIDEO),
                modeSnapshot = ModeSnapshot(
                    id = ModeId.PORTRAIT,
                    uiSpec = ModeUiSpec(
                        title = "Portrait",
                        shutterLabel = "Capture Portrait"
                    ),
                    state = ModeState(
                        headline = "Portrait mode active",
                        detail = "Ready"
                    )
                )
            ),
            TestAppTextResolver()
        )

        assertEquals("Tone Lab", model.headline)
        assertTrue(model.heroSummary.contains("Portrait default Portrait Original"))
        assertTrue(model.portraitTab.isSelected)
        assertFalse(model.photoTab.isSelected)
        assertEquals(
            PersistedSettingsAction.UpdatePortraitFilter("portrait-chasing-light"),
            model.cycleControl.nextAction
        )
        assertTrue(model.currentFilterSummary.contains("Current default Portrait Original"))
        assertTrue(model.saveCustomControl.isEnabled)
        assertEquals("portrait-original", model.saveCustomControl.sourceProfileId)
        assertTrue(model.footer.contains("独立色调实验室优先交付"))
    }

    @Test
    fun `filter lab render model switches to humanistic family and keeps import export deferred`() {
        val model = filterLabPageRenderModel(
            state = defaultSessionState(),
            text = TestAppTextResolver(),
            selectedFamily = FilterLabFamily.HUMANISTIC
        )

        assertTrue(model.humanisticTab.isSelected)
        assertEquals(
            "Next Humanistic look\nHumanistic Street\nSupported • 5 looks | import/export deferred",
            model.cycleControl.buttonLabel
        )
        assertEquals(
            PersistedSettingsAction.UpdateHumanisticFilter("humanistic-portrait"),
            model.cycleControl.nextAction
        )
        assertTrue(model.rosterText.contains("• Humanistic Street"))
        assertTrue(model.saveCustomControl.isEnabled)
        assertTrue(model.supportingText.contains("Import and export stay deferred"))
    }

    @Test
    fun `filter lab render model exposes light adjustment panel for selected custom filter`() {
        val state = defaultSessionState().copy(
            settings = defaultSessionState().settings.copy(
                persisted = defaultSessionState().settings.persisted.copy(
                    photo = defaultSessionState().settings.persisted.photo.copy(
                        defaultPortraitFilterProfileId = "custom-portrait-original-1"
                    )
                ),
                catalog = defaultSessionState().settings.catalog.withImportedFilterProfile(
                    com.opencamera.core.settings.FilterProfile(
                        id = "custom-portrait-original-1",
                        label = "Portrait Original Custom 1",
                        category = com.opencamera.core.settings.FilterProfileCategory.CUSTOM,
                        builtIn = false,
                        renderSpec = com.opencamera.core.settings.FilterRenderSpec(
                            brightnessShift = 8,
                            contrast = 1.06f,
                            saturation = 1.02f,
                            warmthShift = 3,
                            softGlowStrength = 0.1f,
                            grainStrength = 0.1f
                        )
                    )
                )
            ),
            activeMode = ModeId.PORTRAIT
        )

        val model = filterLabPageRenderModel(
            state = state,
            text = TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PORTRAIT,
            showAdjustmentPanel = true,
            adjustmentMode = FilterAdjustmentMode.LIGHT
        )

        assertTrue(model.adjustControl.isEnabled)
        assertFalse(model.adjustControl.willCreateCustomCopy)
        assertTrue(model.adjustmentPanel.isVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, model.adjustmentPanel.mode)
        assertEquals("custom-portrait-original-1", model.adjustmentPanel.selectedProfileId)
        assertTrue(model.adjustmentPanel.lightPalette.supportingText.contains("Horizontal swipe"))
        val selectedItem = model.filterItems.first { it.filterProfileId == "custom-portrait-original-1" }
        assertTrue(selectedItem.isSelected)
        assertTrue(selectedItem.supportingText.contains("Selected default"))
        assertTrue(selectedItem.adjustButtonLabel?.contains("Adjust Selected") == true)
    }

    @Test
    fun `filter lab render model exposes advanced adjustment controls for selected portrait filter`() {
        val model = filterLabPageRenderModel(
            state = defaultSessionState(activeMode = ModeId.PORTRAIT),
            text = TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PORTRAIT,
            showAdjustmentPanel = true,
            adjustmentMode = FilterAdjustmentMode.ADVANCED
        )

        assertTrue(model.adjustmentPanel.isVisible)
        assertEquals(FilterAdjustmentMode.ADVANCED, model.adjustmentPanel.mode)
        assertEquals(12, model.adjustmentPanel.advancedControls.size)
        assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.EXPOSURE })
        assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.HALO })
        assertTrue(model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.WARM_BOOST })
        assertTrue(
            model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.TEMPERATURE_SHIFT }
        )
        assertTrue(
            model.adjustmentPanel.advancedControls.any { it.control == FilterAdvancedControl.TINT_SHIFT }
        )
        assertEquals(
            "Temp Shift\nOff\nTap to cycle",
            model.adjustmentPanel.advancedControls.first {
                it.control == FilterAdvancedControl.TEMPERATURE_SHIFT
            }.buttonLabel
        )
        val selectedItem = model.filterItems.first { it.filterProfileId == "portrait-original" }
        val unselectedItem = model.filterItems.first { it.filterProfileId == "portrait-blue" }
        assertTrue(selectedItem.adjustButtonLabel?.contains("editable custom copy") == true)
        assertEquals(null, unselectedItem.adjustButtonLabel)
    }

    @Test
    fun `advanced control cycling covers halo temperature and tint shifts`() {
        val halo = com.opencamera.core.settings.FilterRenderSpec()
            .nextAdvancedControl(FilterAdvancedControl.HALO)
        val warm = com.opencamera.core.settings.FilterRenderSpec()
            .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
        val magenta = com.opencamera.core.settings.FilterRenderSpec()
            .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)
        val cool = warm
            .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
            .nextAdvancedControl(FilterAdvancedControl.TEMPERATURE_SHIFT)
        val green = magenta
            .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)
            .nextAdvancedControl(FilterAdvancedControl.TINT_SHIFT)

        assertEquals(0.1f, halo.haloStrength)
        assertEquals(6, warm.warmthShift)
        assertEquals(6, magenta.tintShift)
        assertEquals(-6, cool.warmthShift)
        assertEquals(-6, green.tintShift)
    }

    @Test
    fun `mode summary reflects current mode snapshot`() {
        val state = defaultSessionState().copy(
            modeSnapshot = ModeSnapshot(
                id = ModeId.NIGHT,
                uiSpec = ModeUiSpec(
                    title = "Scenery",
                    shutterLabel = "Capture Scenery"
                ),
                state = ModeState(
                    headline = "Scenery mode active",
                    detail = "Tripod multi-frame available"
                )
            )
        )

        assertEquals(
            "Mode: Scenery\nScenery mode active\nTripod multi-frame available",
            modeSummaryText(state)
        )
    }

    @Test
    fun `mode directory render model productizes scenery and humanistic entries`() {
        val state = defaultSessionState(
            activeMode = ModeId.NIGHT,
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.VIDEO),
            modeSnapshot = ModeSnapshot(
                id = ModeId.NIGHT,
                uiSpec = ModeUiSpec(
                    title = "Scenery",
                    shutterLabel = "Capture Scenery"
                ),
                state = ModeState(
                    headline = "Scenery mode active",
                    detail = "Tripod multi-frame available"
                )
            )
        )

        val model = modeDirectoryRenderModel(state, TestAppTextResolver())

        assertEquals(
            listOf("Photo", "Scenery", "Humanistic", "Video"),
            model.items.map(ModeDirectoryItemRenderModel::displayName)
        )
        assertEquals("Portrait Retro", model.items.first { it.modeId == ModeId.PHOTO }.defaultStyleLabel)
        assertEquals("Handheld", model.items.first { it.modeId == ModeId.NIGHT }.defaultStyleLabel)
        assertEquals(
            "Scenery style, Pro variant, night fusion, frame ratio",
            model.items.first { it.modeId == ModeId.NIGHT }.declaredSubfeatures
        )
        assertEquals("Human", model.items.first { it.modeId == ModeId.HUMANISTIC }.buttonLabel)
        assertEquals(
            "Humanistic Street",
            model.items.first { it.modeId == ModeId.HUMANISTIC }.defaultStyleLabel
        )
        assertEquals(
            "• Scenery | Default Handheld | Scenery style, Pro variant, night fusion, frame ratio",
            modeDirectoryText(state, TestAppTextResolver()).lineSequence().elementAt(1)
        )
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
        assertEquals(
            "Scenery style, Pro variant, brightening fallback, frame ratio",
            model.items.first { it.modeId == ModeId.NIGHT }.declaredSubfeatures
        )
        assertEquals("Portrait Original", model.items.first { it.modeId == ModeId.PORTRAIT }.defaultStyleLabel)
        assertEquals(
            "Portrait style, Pro variant, focus fallback, frame ratio",
            model.items.first { it.modeId == ModeId.PORTRAIT }.declaredSubfeatures
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
        availableModes: List<ModeId> = listOf(
            ModeId.PHOTO,
            ModeId.NIGHT,
            ModeId.HUMANISTIC,
            ModeId.VIDEO
        ),
        modeSnapshot: ModeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(
                title = "PHOTO",
                shutterLabel = "Capture PHOTO"
            ),
            state = ModeState(
                headline = "PHOTO mode active",
                detail = "Ready"
            )
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
                        gridMode = CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = PhotoSettings(
                        defaultFilterProfileId = "portrait-retro",
                        defaultHumanisticFilterProfileId = "humanistic-street",
                        defaultPortraitFilterProfileId = "portrait-original",
                        defaultWatermarkTemplateId = "travel-polaroid",
                        livePhotoEnabledByDefault = true,
                        countdownDuration = CountdownDuration.SECONDS_3
                    ),
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

    @Test
    fun `cockpit controls remain available for floating focus layout`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f),
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
                zoomRatio = 1f,
                qualityPreference = StillCaptureQualityPreference.QUALITY,
                resolutionPreset = StillCaptureResolutionPreset.LARGE_12MP,
                outputSize = StillCaptureOutputSize(width = 4000, height = 3000)
            )
        )

        val controls = sessionControlsRenderModel(state, strings)

        assertTrue(controls.zoomCapsules.isNotEmpty())
        assertTrue(controls.zoomEnabled)
        assertTrue(controls.lensFacingEnabled)
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
    fun `mode track render model preserves available mode order and identity`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT,
            ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO
        )
        val state = defaultSessionState(
            activeMode = ModeId.HUMANISTIC,
            availableModes = availableModes
        )
        val model = modeTrackRenderModel(state, TestAppTextResolver())

        assertEquals(7, model.items.size)
        assertEquals(
            listOf(ModeId.PHOTO, ModeId.DOCUMENT, ModeId.NIGHT,
                   ModeId.HUMANISTIC, ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO),
            model.items.map { it.modeId }
        )
        assertTrue(model.items.first { it.modeId == ModeId.HUMANISTIC }.isActive)
        assertFalse(model.items.first { it.modeId == ModeId.PHOTO }.isActive)
        assertTrue(model.items.all { it.isAvailable })
    }

    @Test
    fun `save as custom button label is localized via text resolver`() {
        val customResolver = object : TestAppTextResolver() {
            override fun saveAsCustom(): String = "保存为自定义"
        }
        val model = filterLabPageRenderModel(
            state = defaultSessionState(activeMode = ModeId.PORTRAIT),
            text = customResolver,
            selectedFamily = FilterLabFamily.PORTRAIT
        )
        assertTrue(model.saveCustomControl.buttonLabel.startsWith("保存为自定义"))
    }

    @Test
    fun `portrait lab page render model uses text resolver for all labels`() {
        val customResolver = object : TestAppTextResolver() {
            override fun portraitLab(): String = "人像实验室"
            override fun portraitProfileLabel(): String = "人像配置文件"
            override fun beautyPresetLabel(): String = "美颜预设"
            override fun beautyStrengthLabel(): String = "美颜强度"
            override fun bokehEffectLabel(): String = "散景效果"
        }
        val model = portraitLabPageRenderModel(defaultSessionState(), customResolver)
        assertEquals("人像实验室", model.headline)
        assertTrue(model.profileControl.buttonLabel.startsWith("人像配置文件"))
        assertTrue(model.beautyPresetControl.buttonLabel.startsWith("美颜预设"))
        assertTrue(model.beautyStrengthControl.buttonLabel.startsWith("美颜强度"))
        assertTrue(model.bokehEffectControl.buttonLabel.startsWith("散景效果"))
    }

    @Test
    fun `filter lab page uses text resolver for all key labels`() {
        val customResolver = object : TestAppTextResolver() {
            override fun filterLab(): String = "滤镜实验室"
            override fun saveAsCustom(): String = "保存"
        }
        val model = filterLabPageRenderModel(
            state = defaultSessionState(activeMode = ModeId.PHOTO),
            text = customResolver
        )
        assertEquals("滤镜实验室", model.headline)
        assertTrue(model.saveCustomControl.buttonLabel.startsWith("保存"))
    }

    @Test
    fun `availability labels use dedicated strings not quality level labels`() {
        val model = sessionSettingsPageRenderModel(defaultSessionState(), TestAppTextResolver())
        assertTrue(model.photoSection.livePhoto.buttonLabel.contains("Degraded"))
        assertTrue(model.photoSection.portraitLab.buttonLabel.contains("Degraded"))
        assertTrue(model.photoSection.watermarkTemplate.buttonLabel.contains("Degraded"))
        assertTrue(model.photoSection.countdown.buttonLabel.contains("Supported"))
        assertTrue(model.videoSection.frameRate.buttonLabel.contains("Supported"))
        assertFalse(model.photoSection.livePhoto.buttonLabel.contains("Fast"))
        assertFalse(model.photoSection.livePhoto.buttonLabel.contains("Max"))
        assertFalse(model.videoSection.frameRate.buttonLabel.contains("Fast"))
        assertFalse(model.videoSection.frameRate.buttonLabel.contains("N/A"))
    }

    @Test
    fun `cockpit right rail exposes tone quick and lens lab entries`() {
        val state = defaultSessionState()
        val text = TestAppTextResolver()
        val cockpit = cameraCockpitRenderModel(state, text, strings)

        val visibleEntries = cockpit.rightRail.entries.filter { it.isVisible }
        assertEquals(3, visibleEntries.size)
        assertEquals("Tone", visibleEntries[0].label)
        assertEquals("Quick", visibleEntries[1].label)
        assertEquals("Lens Lab", visibleEntries[2].label)

        assertTrue(visibleEntries[0].route is CockpitPanelRoute.FilterLab)
        assertTrue(visibleEntries[1].route is CockpitPanelRoute.QuickBubble)
        assertTrue(visibleEntries[2].route is CockpitPanelRoute.Settings)
    }

    @Test
    fun `cockpit bottom reflects recording state`() {
        val state = defaultSessionState().copy(
            recordingStatus = RecordingStatus.RECORDING
        )
        val text = TestAppTextResolver()
        val cockpit = cameraCockpitRenderModel(state, text, strings)

        assertTrue(cockpit.bottomCockpit.isRecording)
    }

    @Test
    fun `tone lab entry label is Chinese via text resolver`() {
        val chineseResolver = object : TestAppTextResolver() {
            override fun tone(): String = "色调"
            override fun quickLauncher(): String = "快捷"
            override fun lensLab(): String = "镜头实验室"
        }
        val state = defaultSessionState()
        val cockpit = cameraCockpitRenderModel(state, chineseResolver, strings)

        val visibleEntries = cockpit.rightRail.entries.filter { it.isVisible }
        assertEquals("色调", visibleEntries[0].label)
        assertEquals("快捷", visibleEntries[1].label)
        assertEquals("镜头实验室", visibleEntries[2].label)
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
}
