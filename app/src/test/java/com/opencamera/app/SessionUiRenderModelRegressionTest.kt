package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FrameRatio
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
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.DynamicVideoFpsPolicy
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PersistedSettings
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

/**
 * Regression tests for UI render models moved to split files by package 02.
 * Verifies that split files produce consistent, unchanged values.
 */
class SessionUiRenderModelRegressionTest {

    @Test
    fun `cockpit controls render from split cockpit file`() {
        val state = defaultPhotoState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f), defaultRatio = 1f
                )
            )
        )
        val model = sessionControlsRenderModel(state, strings, TestAppTextResolver())
        assertEquals("Switch to Front", model.lensFacingButtonLabel)
        assertTrue(model.lensFacingEnabled)
        assertTrue(model.isZoomCapsuleRowVisible)
        assertTrue(model.focalLengthSlider.isVisible)
    }

    @Test
    fun `cockpit controls with zoom disabled shows hidden row`() {
        val state = defaultPhotoState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.UNSUPPORTED,
                    supportedRatios = listOf(1f), defaultRatio = 1f
                )
            )
        )
        val model = sessionControlsRenderModel(state, strings, TestAppTextResolver())
        assertFalse(model.isZoomCapsuleRowVisible)
        assertTrue(model.zoomCapsules.isEmpty())
        assertFalse(model.focalLengthSlider.isVisible)
    }

    @Test
    fun `cockpit primary status from split cockpit file`() {
        val model = primaryStatusRenderModel(defaultPhotoState(), TestAppTextResolver())
        assertEquals("Photo", model.modeLabel)
        assertFalse(model.statusText.contains("Recording"))
    }

    @Test
    fun `cockpit primary status shows recording state`() {
        val model = primaryStatusRenderModel(
            defaultPhotoState().copy(recordingStatus = RecordingStatus.RECORDING),
            TestAppTextResolver()
        )
        assertTrue(model.statusText.contains("Recording"))
    }

    @Test
    fun `cockpit primary status shows countdown`() {
        val model = primaryStatusRenderModel(
            defaultPhotoState().copy(
                presentation = SessionPresentationState(countdownRemainingSeconds = 3),
                captureStatus = CaptureStatus.REQUESTED
            ),
            TestAppTextResolver()
        )
        assertTrue(model.statusText.contains("3s"))
    }

    @Test
    fun `preview overlay from split preview file shows grid`() {
        val overlay = previewOverlayRenderModel(defaultPhotoState())
        assertEquals(CompositionGridMode.RULE_OF_THIRDS, overlay.gridMode)
        assertTrue(overlay.isGridVisible)
        assertFalse(overlay.isCountdownVisible)
    }

    @Test
    fun `preview overlay countdown visibility from split preview file`() {
        val overlay = previewOverlayRenderModel(
            defaultPhotoState().copy(
                presentation = SessionPresentationState(countdownRemainingSeconds = 2),
                captureStatus = CaptureStatus.REQUESTED
            )
        )
        assertEquals("2s", overlay.countdownLabel)
        assertTrue(overlay.isCountdownVisible)
    }

    @Test
    fun `preview overlay hides grid when preview unavailable`() {
        val overlay = previewOverlayRenderModel(
            defaultPhotoState().copy(previewStatus = PreviewStatus.BLOCKED)
        )
        assertFalse(overlay.isGridVisible)
    }

    @Test
    fun `preview overlay frame carries zoom ratios`() {
        val graph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK, enablePreviewSnapshots = true, zoomRatio = 3f
        ).let { spec -> spec.copy(preview = spec.preview.copy(previewZoomRatio = 2f)) }
        val state = SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true, previewStatus = PreviewStatus.ACTIVE, previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE, recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT, activeDeviceGraph = graph,
            previewMetrics = PreviewMetrics(),
            activeEffectSpec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_4_3)))
        )
        val frame = assertNotNull(previewOverlayRenderModel(state).frame)
        assertEquals(3f, frame.zoomRatio)
        assertEquals(2f, frame.previewZoomRatio)
        assertEquals(FrameRatio.RATIO_4_3, frame.ratio)
    }

    @Test
    fun `preview ratio content aspect from split preview file`() {
        assertEquals(null, previewRatioToContentAspect(com.opencamera.core.session.PreviewRatio.FULL))
        assertEquals(PreviewContentAspect(4, 3), previewRatioToContentAspect(com.opencamera.core.session.PreviewRatio.RATIO_4_3))
        assertEquals(PreviewContentAspect(16, 9), previewRatioToContentAspect(com.opencamera.core.session.PreviewRatio.RATIO_16_9))
        assertEquals(PreviewContentAspect(1, 1), previewRatioToContentAspect(com.opencamera.core.session.PreviewRatio.RATIO_1_1))
    }

    @Test
    fun `settings summary from split settings file resolves defaults`() {
        val model = sessionSettingsRenderModel(defaultPhotoState(), TestAppTextResolver())
        assertEquals("Grid 3x3 | Shutter sound Off | Selfie mirror On", model.commonSummary)
        assertTrue(model.photoSummary.contains("Filter Portrait Retro"))
        assertTrue(model.videoSummary.contains("4K 25fps"))
    }

    @Test
    fun `settings page disables editing during shot`() {
        val state = defaultPhotoState(
            activeShot = ShotRequest(
                shotId = "regression-shot", shotKind = ShotKind.STILL_CAPTURE,
                mediaType = MediaType.PHOTO, saveRequest = SaveRequest.photoLibrary(),
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(), captureProfile = CaptureProfile()
            )
        )
        assertFalse(sessionSettingsPageRenderModel(state, TestAppTextResolver()).editingEnabled)
    }

    @Test
    fun `settings page disables editing during countdown`() {
        val state = defaultPhotoState().copy(
            presentation = SessionPresentationState(countdownRemainingSeconds = 3),
            captureStatus = CaptureStatus.REQUESTED
        )
        assertFalse(sessionSettingsPageRenderModel(state, TestAppTextResolver()).editingEnabled)
    }

    @Test
    fun `capture output text from cockpit file renders photo path`() {
        val state = defaultPhotoState(
            latestSavedMediaType = com.opencamera.core.session.SavedMediaType.PHOTO,
            latestCapturePath = "Pictures/OpenCamera/regression.jpg",
            latestPipelineNotes = listOf("algorithm:photo-default")
        )
        val text = sessionCaptureOutputText(state, strings, TestAppTextResolver())
        assertTrue(text.contains("Last photo:"))
        assertTrue(text.contains("Pictures/OpenCamera/regression.jpg"))
    }

    @Test
    fun `capture output text from cockpit file renders video path`() {
        val state = defaultPhotoState(
            latestSavedMediaType = com.opencamera.core.session.SavedMediaType.VIDEO,
            latestVideoPath = "Movies/OpenCamera/regression.mp4",
            latestPipelineNotes = listOf("torch:on")
        )
        val text = sessionCaptureOutputText(state, strings, TestAppTextResolver())
        assertTrue(text.contains("Last video:"))
        assertTrue(text.contains("Movies/OpenCamera/regression.mp4"))
    }

    @Test
    fun `session summary from cockpit file includes key fields`() {
        val state = defaultPhotoState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(1f, 2f), defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK, enablePreviewSnapshots = true, zoomRatio = 2f
            )
        )
        val summary = sessionSummaryText(state, TestAppTextResolver())
        assertTrue(summary.contains("Lens: Back"))
        assertTrue(summary.contains("Zoom: 2.0x"))
        assertTrue(summary.contains("Lifecycle: RUNNING"))
    }

    @Test
    fun `filterLabPageRenderModel includes card rail in StyleLab context`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        )

        assertNotNull(model.stylePresetCardRail)
        assertTrue(model.stylePresetCardRail!!.cards.isNotEmpty())
        assertTrue(model.stylePresetCardRail!!.isEnabled)
    }

    @Test
    fun `filterLabPageRenderModel card rail shows selected preset`() {
        // Use photo-vivid which is a built-in PHOTO profile in the default catalog
        val state = SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true, previewStatus = PreviewStatus.ACTIVE, previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE, recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK, enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(persisted = PersistedSettings(
                common = CommonSettings(
                    gridMode = CompositionGridMode.RULE_OF_THIRDS,
                    shutterSoundEnabled = false, selfieMirrorEnabled = true
                ),
                photo = PhotoSettings(
                    defaultFilterProfileId = "photo-vivid",
                    defaultHumanisticFilterProfileId = "humanistic-street",
                    defaultPortraitFilterProfileId = "portrait-original",
                    defaultWatermarkTemplateId = "travel-polaroid",
                    livePhotoEnabledByDefault = true,
                    countdownDuration = CountdownDuration.SECONDS_3
                ),
                video = VideoSettings(
                    defaultVideoSpec = VideoSpec(
                        resolution = VideoResolution.UHD_4K, frameRate = VideoFrameRate.FPS_25,
                        dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                        audioProfile = AudioProfile.CONCERT
                    ),
                    defaultFilterProfileId = "photo-rich"
                )
            )),
            presentation = SessionPresentationState(lastAction = "Ready")
        )
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        )

        val rail = model.stylePresetCardRail!!
        val selectedCards = rail.cards.filter { it.isSelected }
        assertEquals(1, selectedCards.size, "Exactly one card should be selected")
        assertEquals("photo-vivid", selectedCards[0].profileId)
    }

    @Test
    fun `filterLabPageRenderModel card rail applies action on non-selected card tap`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        )

        val rail = model.stylePresetCardRail!!
        val nonSelectedCards = rail.cards.filter { !it.isSelected }
        assertTrue(nonSelectedCards.isNotEmpty(), "Should have non-selected cards")
        nonSelectedCards.forEach { card ->
            assertNotNull(card.applyAction, "Non-selected card must have an apply action")
        }
    }

    @Test
    fun `filterLabPageRenderModel card rail has all cards with non-empty titles`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        )

        val rail = model.stylePresetCardRail!!
        rail.cards.forEach { card ->
            assertTrue(card.title.isNotBlank(), "Card title must not be blank")
            assertTrue(card.moodLabel.isNotBlank(), "Card mood label must not be blank")
        }
    }

    @Test
    fun `filterLabPageRenderModel card rail has matching family on all cards`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.STYLE
        )

        val rail = model.stylePresetCardRail!!
        assertEquals(FilterLabFamily.PHOTO, rail.activeFamily)
        rail.cards.forEach { card ->
            assertEquals(FilterLabFamily.PHOTO, card.family)
        }
    }

    @Test
    fun `filterLabPageRenderModel humanistic family card rail works`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.HUMANISTIC,
            panelRole = StyleAndColorLabRole.STYLE
        )

        val rail = model.stylePresetCardRail!!
        assertTrue(rail.cards.isNotEmpty())
        rail.cards.forEach { card ->
            assertEquals(FilterLabFamily.HUMANISTIC, card.family)
        }
    }

    @Test
    fun `filterLabPageRenderModel card rail not shown in ColorLab context`() {
        val state = defaultPhotoState()
        val model = filterLabPageRenderModel(
            state,
            TestAppTextResolver(),
            selectedFamily = FilterLabFamily.PHOTO,
            panelRole = StyleAndColorLabRole.COLOR_LAB
        )

        assertNull(model.stylePresetCardRail, "ColorLab should not show the style card rail")
    }

    @Test
    fun `cockpit render model top bar style entry label is present`() {
        val state = defaultPhotoState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertNotNull(model.topStatus.labEntryLabel)
        assertTrue(model.topStatus.labEntryLabel.isNotBlank())
    }

    @Test
    fun `cockpit render model does not regress after style rail addition`() {
        val state = defaultPhotoState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT),
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.6f, 1f, 2f, 5f), defaultRatio = 1f
                )
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Switch to Front", model.bottomCockpit.lensButtonLabel)
        assertTrue(model.bottomCockpit.isShutterEnabled)
        assertTrue(model.zoomStrip.isVisible)
    }

    private fun defaultPhotoState(
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK, enablePreviewSnapshots = true
        ),
        activeShot: ShotRequest? = null,
        latestSavedMediaType: com.opencamera.core.session.SavedMediaType? = null,
        latestCapturePath: String? = null,
        latestVideoPath: String? = null,
        latestPipelineNotes: List<String> = emptyList()
    ): SessionState = SessionState(
        lifecycle = SessionLifecycle.RUNNING,
        permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
        previewHostAvailable = true, previewStatus = PreviewStatus.ACTIVE, previewStatusDetail = null,
        activeMode = ModeId.PHOTO,
        availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
        captureStatus = CaptureStatus.IDLE, recordingStatus = RecordingStatus.IDLE,
        activeShot = activeShot,
        modeSnapshot = ModeSnapshot(
            id = ModeId.PHOTO,
            uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
            state = ModeState(headline = "PHOTO mode active", detail = "Ready")
        ),
        activeDeviceCapabilities = activeDeviceCapabilities, activeDeviceGraph = activeDeviceGraph,
        previewMetrics = PreviewMetrics(),
        settings = SessionSettingsSnapshot(persisted = PersistedSettings(
            common = CommonSettings(
                gridMode = CompositionGridMode.RULE_OF_THIRDS,
                shutterSoundEnabled = false, selfieMirrorEnabled = true
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
                    resolution = VideoResolution.UHD_4K, frameRate = VideoFrameRate.FPS_25,
                    dynamicFpsPolicy = DynamicVideoFpsPolicy.LOW_LIGHT_AUTO_24FPS,
                    audioProfile = AudioProfile.CONCERT
                ),
                defaultFilterProfileId = "photo-rich"
            )
        )),
        presentation = SessionPresentationState(
            lastAction = "Ready", latestCapturePath = latestCapturePath,
            latestVideoPath = latestVideoPath, latestSavedMediaType = latestSavedMediaType,
            latestPipelineNotes = latestPipelineNotes
        )
    )

    companion object {
        private val strings = SessionUiStrings(
            buttonSwitchToFront = "Switch to Front", buttonSwitchToBack = "Switch to Back",
            buttonSingleLens = "Single Lens", buttonZoomPrefix = "Zoom",
            buttonZoomUnavailable = "Zoom N/A", buttonStillFast = "Still Fast",
            buttonStillMax = "Still Max", buttonStillQualityUnavailable = "Still N/A",
            buttonStill12Mp = "Still 12MP", buttonStill8Mp = "Still 8MP",
            buttonStill2Mp = "Still 2MP", buttonStillResolutionUnavailable = "Size N/A",
            outputErrorPrefix = "Camera issue:", outputVideoPrefix = "Last video:",
            outputLivePrefix = "Last Live photo:", outputSavedPrefix = "Last photo:",
            outputPreviewPrefix = "Preview thumbnail:", outputWaiting = "No photo captured yet."
        )
    }
}
