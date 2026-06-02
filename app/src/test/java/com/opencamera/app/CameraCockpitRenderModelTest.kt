package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.ZoomControlSupport
import com.opencamera.core.device.ZoomRatioCapability
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewRatio
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SavedMediaType
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.CommonSettings
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.CountdownDuration
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.VideoSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraCockpitRenderModelTest {

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

    @Test
    fun `cockpit render model top status carries app name and mode label`() {
        val state = defaultSessionState(activeMode = ModeId.HUMANISTIC)
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("OpenCamera", model.topStatus.appName)
        assertEquals("Humanistic", model.topStatus.modeLabel)
        assertEquals("Color Lab", model.topStatus.labEntryLabel)
        assertEquals("Settings", model.topStatus.settingsEntryLabel)
    }

    @Test
    fun `cockpit render model top status reflects preview status`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.topStatus.statusText.isNotEmpty())
    }

    @Test
    fun `right rail includes style quick and dev entries`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(3, model.rightRail.entries.size)
        assertEquals("Style", model.rightRail.entries[0].label)
        assertEquals("Quick", model.rightRail.entries[1].label)
        assertEquals("DEV", model.rightRail.entries[2].label)
    }

    @Test
    fun `right rail dev entry is visible by default`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.rightRail.entries[2].isVisible)
    }

    @Test
    fun `right rail marks active route`() {
        val state = defaultSessionState()
        val activeRoute = CockpitPanelRoute.StyleLab
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings, activeRoute)

        assertTrue(model.rightRail.entries[0].isActive)
        assertFalse(model.rightRail.entries[1].isActive)
    }

    @Test
    fun `zoom strip chips match zoom ratio capability`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.7f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                zoomRatio = 2f
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.zoomStrip.isVisible)
        assertEquals(4, model.zoomStrip.chips.size)
        assertEquals("2", model.zoomStrip.chips.first { it.isActive }.label)
    }

    @Test
    fun `zoom strip is hidden when unsupported`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(model.zoomStrip.isVisible)
    }

    @Test
    fun `mode track preserves available mode order`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC,
            ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO
        )
        val state = defaultSessionState(
            activeMode = ModeId.VIDEO,
            availableModes = availableModes
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        val modeIds = model.modeTrack.items.map { it.modeId }
        assertTrue(modeIds.contains(ModeId.PHOTO))
        assertTrue(modeIds.contains(ModeId.VIDEO))
        assertTrue(model.modeTrack.items.first { it.modeId == ModeId.VIDEO }.isActive)
    }

    @Test
    fun `bottom cockpit carries lens and zoom labels`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Shutter", model.bottomCockpit.shutterLabel)
        assertEquals("Switch to Front", model.bottomCockpit.lensButtonLabel)
        assertTrue(model.bottomCockpit.lensButtonEnabled)
    }

    @Test
    fun `bottom cockpit lens disabled with single lens`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(model.bottomCockpit.lensButtonEnabled)
    }

    @Test
    fun `bottom cockpit shutter visual state defaults to PHOTO_READY`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(ShutterVisualState.PHOTO_READY, model.bottomCockpit.shutterVisualState)
    }

    @Test
    fun `bottom cockpit shutter visual state reflects recording`() {
        val state = defaultSessionState().copy(recordingStatus = RecordingStatus.RECORDING)
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(ShutterVisualState.VIDEO_RECORDING, model.bottomCockpit.shutterVisualState)
    }

    @Test
    fun `bottom cockpit shutter visual state is PHOTO_PRESSED when shot requested`() {
        val shot = ShotRequest(
            shotId = "cockpit-1",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.NONE,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val state = defaultSessionState().copy(
            activeShot = shot,
            captureStatus = CaptureStatus.REQUESTED
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(ShutterVisualState.PHOTO_PRESSED, model.bottomCockpit.shutterVisualState)
        assertFalse(model.bottomCockpit.isShutterEnabled)
    }

    @Test
    fun `bottom cockpit shutter visual state is BACKGROUND_SAVING when saving without activeShot`() {
        // captureStatus == SAVING with activeShot == null: visual shows BACKGROUND_SAVING,
        // and shutter is enabled (session rearmed) so user can take next photo immediately.
        val state = defaultSessionState().copy(captureStatus = CaptureStatus.SAVING)
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(ShutterVisualState.BACKGROUND_SAVING, model.bottomCockpit.shutterVisualState)
        assertTrue(model.bottomCockpit.isShutterEnabled)
    }

    @Test
    fun `cockpit render model carries active route through`() {
        val state = defaultSessionState()
        val route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT)
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings, route)

        assertTrue(model.activePanelRoute.isSettingsOpen)
        assertEquals(SettingsSubpage.ROOT, (model.activePanelRoute as CockpitPanelRoute.Settings).subpage)
    }

    @Test
    fun `cockpit includes preview ratio chip with default full ratio`() {
        val state = defaultSessionState()
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Full", cockpit.previewRatioChip.label)
        assertEquals(PreviewRatio.FULL, cockpit.previewRatioChip.ratio)
        assertTrue(cockpit.previewRatioChip.isActive)
    }

    @Test
    fun `cockpit preview ratio chip reflects 16 9 ratio`() {
        val state = defaultSessionState(previewRatio = PreviewRatio.RATIO_16_9)
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("16:9", cockpit.previewRatioChip.label)
        assertEquals(PreviewRatio.RATIO_16_9, cockpit.previewRatioChip.ratio)
    }

    // --- orientation render model tests ---

    @Test
    fun `orientation render model returns portrait for ROTATION_0`() {
        // Surface.ROTATION_0 = 0
        val model = orientationRenderModel(0)
        assertEquals(CockpitDisplayOrientation.PORTRAIT, model.orientation)
        assertEquals(0f, model.controlRotationDegrees)
    }

    @Test
    fun `orientation render model returns landscape left for ROTATION_90`() {
        // Surface.ROTATION_90 = 1
        val model = orientationRenderModel(1)
        assertEquals(CockpitDisplayOrientation.LANDSCAPE_LEFT, model.orientation)
        assertEquals(90f, model.controlRotationDegrees)
    }

    @Test
    fun `orientation render model returns landscape right for ROTATION_270`() {
        // Surface.ROTATION_270 = 3
        val model = orientationRenderModel(3)
        assertEquals(CockpitDisplayOrientation.LANDSCAPE_RIGHT, model.orientation)
        assertEquals(-90f, model.controlRotationDegrees)
    }

    @Test
    fun `orientation render model returns portrait for ROTATION_180`() {
        // Surface.ROTATION_180 = 2
        val model = orientationRenderModel(2)
        assertEquals(CockpitDisplayOrientation.PORTRAIT, model.orientation)
        assertEquals(0f, model.controlRotationDegrees)
    }

    @Test
    fun `cockpit render model defaults to portrait orientation`() {
        val state = defaultSessionState()
        val cockpit = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(CockpitDisplayOrientation.PORTRAIT, cockpit.orientation.orientation)
        assertEquals(0f, cockpit.orientation.controlRotationDegrees)
    }

    @Test
    fun `StyleLab route maps to Style label in right rail`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        val styleEntry = model.rightRail.entries[0]
        assertEquals("Style", styleEntry.label)
        assertEquals(CockpitPanelRoute.StyleLab, styleEntry.route)
    }

    @Test
    fun `ColorLab route maps to Color Lab label in top bar`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals("Color Lab", model.topStatus.labEntryLabel)
    }

    @Test
    fun `StyleLab and ColorLab routes are distinct`() {
        val state = defaultSessionState()
        val filterModel = cameraCockpitRenderModel(state, TestAppTextResolver(), strings, CockpitPanelRoute.StyleLab)
        val lensModel = cameraCockpitRenderModel(state, TestAppTextResolver(), strings, CockpitPanelRoute.ColorLab)

        assertTrue(filterModel.rightRail.entries[0].isActive)
        assertFalse(lensModel.rightRail.entries[0].isActive)
    }

    @Test
    fun `cockpit render model includes both zoom strip and mode track`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.7f, 1f, 2f),
                    defaultRatio = 1f
                )
            ),
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.VIDEO)
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.zoomStrip.isVisible)
        assertTrue(model.zoomStrip.chips.isNotEmpty())
    }

    @Test
    fun `recording indicator is hidden when idle`() {
        val state = defaultSessionState()
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertFalse(model.bottomCockpit.recordingIndicator.isVisible)
        assertEquals("", model.bottomCockpit.recordingIndicator.label)
    }

    @Test
    fun `recording indicator shows starting status when requesting`() {
        val state = defaultSessionState().copy(
            recordingStatus = RecordingStatus.REQUESTING
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.bottomCockpit.recordingIndicator.isVisible)
        assertEquals("Starting…", model.bottomCockpit.recordingIndicator.label)
    }

    @Test
    fun `recording indicator shows saving status when stopping`() {
        val state = defaultSessionState().copy(
            recordingStatus = RecordingStatus.STOPPING
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.bottomCockpit.recordingIndicator.isVisible)
        assertEquals("Saving…", model.bottomCockpit.recordingIndicator.label)
    }

    @Test
    fun `recording indicator shows elapsed time when recording`() {
        val state = defaultSessionState().copy(
            recordingStatus = RecordingStatus.RECORDING,
            presentation = SessionPresentationState(
                recordingElapsedMillis = 84_000L
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.bottomCockpit.recordingIndicator.isVisible)
        assertEquals("01:24", model.bottomCockpit.recordingIndicator.label)
    }

    @Test
    fun `formatRecordingElapsed formats zero as 00_00`() {
        assertEquals("00:00", formatRecordingElapsed(0L))
    }

    @Test
    fun `formatRecordingElapsed formats seconds correctly`() {
        assertEquals("00:03", formatRecordingElapsed(3_000L))
    }

    @Test
    fun `formatRecordingElapsed formats minutes and seconds`() {
        assertEquals("01:24", formatRecordingElapsed(84_000L))
    }

    @Test
    fun `formatRecordingElapsed formats hours minutes seconds`() {
        assertEquals("1:02:09", formatRecordingElapsed(3_729_000L))
    }

    private fun defaultSessionState(
        activeMode: ModeId = ModeId.PHOTO,
        availableModes: List<ModeId> = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.VIDEO),
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        ),
        previewRatio: PreviewRatio = PreviewRatio.FULL
    ): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = activeMode,
            availableModes = availableModes,
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = activeDeviceGraph,
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = CommonSettings(
                        gridMode = CompositionGridMode.RULE_OF_THIRDS,
                        shutterSoundEnabled = false,
                        selfieMirrorEnabled = true
                    ),
                    photo = PhotoSettings(
                        defaultFilterProfileId = "portrait-retro",
                        countdownDuration = CountdownDuration.SECONDS_3
                    ),
                    video = VideoSettings()
                )
            ),
            previewRatio = previewRatio,
            presentation = SessionPresentationState(
                lastAction = "Ready",
                latestCapturePath = null,
                latestVideoPath = null,
                latestLivePhotoBundle = null,
                latestSavedMediaType = null,
                latestPipelineNotes = emptyList(),
                lastError = null
            )
        )
    }

    // --- Cockpit bottom layout spacing contract tests ---
    // Spacing contract (2026-06-02):
    //   bottomSheet marginBottom = 8dp (lifts controls above screen edge)
    //   modeTrackScroll paddingVertical = 4dp (was 1dp, provides breathing room)
    //   main control row marginTop = 8dp (was 1dp, separates thumbnail/shutter from mode track)
    // These tests verify the render model maintains its structural contract under the new layout.

    @Test
    fun `cockpit bottom layout spacing - bottom cockpit model carries all required layout fields`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        val bottom = model.bottomCockpit
        assertTrue(bottom.shutterLabel.isNotEmpty())
        assertTrue(bottom.lensButtonLabel.isNotEmpty())
        assertTrue(bottom.recordingIndicator != null)
    }

    @Test
    fun `cockpit bottom layout spacing - mode track preserves mode list under increased padding`() {
        val availableModes = listOf(
            ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC,
            ModeId.PORTRAIT, ModeId.PRO, ModeId.VIDEO, ModeId.DOCUMENT
        )
        val state = defaultSessionState(
            activeMode = ModeId.VIDEO,
            availableModes = availableModes
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertEquals(availableModes.size, model.modeTrack.items.size)
        assertTrue(model.modeTrack.items.first { it.modeId == ModeId.VIDEO }.isActive)
        assertTrue(model.modeTrack.items.all { it.isAvailable })
    }

    @Test
    fun `cockpit bottom layout spacing - control row maintains thumbnail and lens presence`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableLensFacings = setOf(LensFacing.BACK, LensFacing.FRONT)
            )
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.bottomCockpit.lensButtonEnabled)
        assertFalse(model.bottomCockpit.isRecording)
        assertFalse(model.bottomCockpit.recordingIndicator.isVisible)
    }

    @Test
    fun `cockpit bottom layout spacing - model is consistent with both zoom and mode track`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                zoomRatioCapability = ZoomRatioCapability(
                    support = ZoomControlSupport.DISCRETE_PRESET,
                    supportedRatios = listOf(0.7f, 1f, 2f, 5f),
                    defaultRatio = 1f
                )
            ),
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.VIDEO)
        )
        val model = cameraCockpitRenderModel(state, TestAppTextResolver(), strings)

        assertTrue(model.zoomStrip.isVisible)
        assertEquals(4, model.zoomStrip.chips.size)
        assertTrue(model.modeTrack.items.isNotEmpty())
    }
}
