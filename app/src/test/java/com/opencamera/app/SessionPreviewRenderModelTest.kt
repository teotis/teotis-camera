package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.session.CaptureStatus
import com.opencamera.core.session.PermissionState
import com.opencamera.core.session.PreviewMeteringFeedback
import com.opencamera.core.session.PreviewMeteringFeedbackStatus
import com.opencamera.core.session.PreviewMetrics
import com.opencamera.core.session.PreviewStatus
import com.opencamera.core.session.RecordingStatus
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionState
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.settings.AudioProfile
import com.opencamera.core.settings.CompositionGridMode
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
import kotlin.test.assertTrue

class SessionPreviewRenderModelTest {
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

    // --- focusReticleRenderModel metering status mapping ---

    @Test
    fun `focusReticleRenderModel maps REQUESTED correctly`() {
        val feedback = meteringFeedback(PreviewMeteringFeedbackStatus.REQUESTED)
        val model = focusReticleRenderModel(feedback)
        assertEquals(FocusReticleStatus.REQUESTED, model.status)
        assertEquals(0.5f, model.normalizedX)
        assertEquals(0.3f, model.normalizedY)
    }

    @Test
    fun `focusReticleRenderModel maps SUCCEEDED correctly`() {
        val feedback = meteringFeedback(PreviewMeteringFeedbackStatus.SUCCEEDED)
        val model = focusReticleRenderModel(feedback)
        assertEquals(FocusReticleStatus.SUCCEEDED, model.status)
    }

    @Test
    fun `focusReticleRenderModel maps DEGRADED_AUTO_EXPOSURE_ONLY to DEGRADED`() {
        val feedback = meteringFeedback(PreviewMeteringFeedbackStatus.DEGRADED_AUTO_EXPOSURE_ONLY)
        val model = focusReticleRenderModel(feedback)
        assertEquals(FocusReticleStatus.DEGRADED, model.status)
    }

    @Test
    fun `focusReticleRenderModel maps FAILED correctly`() {
        val feedback = meteringFeedback(PreviewMeteringFeedbackStatus.FAILED)
        val model = focusReticleRenderModel(feedback)
        assertEquals(FocusReticleStatus.FAILED, model.status)
    }

    @Test
    fun `focusReticleRenderModel maps UNSUPPORTED correctly`() {
        val feedback = meteringFeedback(PreviewMeteringFeedbackStatus.UNSUPPORTED)
        val model = focusReticleRenderModel(feedback)
        assertEquals(FocusReticleStatus.UNSUPPORTED, model.status)
    }

    private fun meteringFeedback(status: PreviewMeteringFeedbackStatus) = PreviewMeteringFeedback(
        requestId = "test-req-1",
        normalizedX = 0.5f,
        normalizedY = 0.3f,
        status = status
    )

    private fun defaultSessionState(): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.HUMANISTIC, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(title = "PHOTO", shutterLabel = "Capture PHOTO"),
                state = ModeState(headline = "PHOTO mode active", detail = "Ready")
            ),
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            ),
            previewMetrics = PreviewMetrics(),
            settings = SessionSettingsSnapshot(
                persisted = PersistedSettings(
                    common = com.opencamera.core.settings.CommonSettings(
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
                lastAction = "Ready"
            )
        )
    }
}
