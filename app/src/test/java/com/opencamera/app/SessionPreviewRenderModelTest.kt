package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.effect.EffectSpec
import com.opencamera.core.effect.DocumentEffect
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.media.FrameRatio
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
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.effect.WatermarkPreviewShape
import com.opencamera.core.settings.PersistedSettings
import com.opencamera.core.settings.PhotoSettings
import com.opencamera.core.settings.SessionSettingsSnapshot
import com.opencamera.core.settings.WatermarkTextOpacity
import com.opencamera.core.settings.WatermarkTextPlacement
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

    @Test
    fun `preview frame uses discrete preview zoom ratio from active device graph`() {
        val baseline = defaultSessionState()
        val graph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true,
            zoomRatio = 3f
        ).let { spec ->
            spec.copy(preview = spec.preview.copy(previewZoomRatio = 2f))
        }

        val model = previewOverlayRenderModel(
            baseline.copy(
                activeDeviceGraph = graph,
                activeEffectSpec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_4_3)))
            )
        )

        val frame = assertNotNull(model.frame)
        assertEquals(3f, frame.zoomRatio)
        assertEquals(2f, frame.previewZoomRatio)
    }

    @Test
    fun `preview frame remains visible when preview status is unavailable`() {
        val baseline = defaultSessionState()

        val model = previewOverlayRenderModel(
            baseline.copy(
                previewStatus = PreviewStatus.ERROR,
                activeEffectSpec = EffectSpec(listOf(FrameEffect(FrameRatio.RATIO_16_9)))
            )
        )

        val frame = assertNotNull(model.frame)
        assertFalse(model.isGridVisible)
        assertEquals(FrameRatio.RATIO_16_9, frame.ratio)
        assertTrue(model.isVisible)
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

    // --- scan guide tests ---

    @Test
    fun `scan guide present for DOCUMENT mode when scanGuide is true`() {
        val state = documentModeState(scanGuide = true)
        val model = previewOverlayRenderModel(state)
        val guide = assertNotNull(model.scanGuide)
        assertTrue(guide.isVisible)
        assertEquals("对准文档", guide.label)
        assertNull(model.frame)
        assertTrue(model.isVisible)
    }

    @Test
    fun `scan guide absent for DOCUMENT mode when scanGuide is false`() {
        val state = documentModeState(scanGuide = false)
        val model = previewOverlayRenderModel(state)
        assertNull(model.scanGuide)
        assertNull(model.frame)
        assertFalse(model.isVisible)
    }

    @Test
    fun `scan guide absent when FrameEffect is present alongside DOCUMENT scanGuide`() {
        val state = documentModeState(scanGuide = true).copy(
            activeEffectSpec = EffectSpec(listOf(
                DocumentEffect(autoCrop = true, contrastProfile = "receipt", scanGuide = true),
                FrameEffect(FrameRatio.RATIO_4_3)
            ))
        )
        val model = previewOverlayRenderModel(state)
        assertNull(model.scanGuide)
        assertNotNull(model.frame)
    }

    private fun documentModeState(scanGuide: Boolean) = SessionState(
        lifecycle = SessionLifecycle.RUNNING,
        permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
        previewHostAvailable = true,
        previewStatus = PreviewStatus.ACTIVE,
        previewStatusDetail = null,
        activeMode = ModeId.DOCUMENT,
        availableModes = listOf(ModeId.PHOTO, ModeId.DOCUMENT, ModeId.VIDEO),
        captureStatus = CaptureStatus.IDLE,
        recordingStatus = RecordingStatus.IDLE,
        activeShot = null,
        modeSnapshot = ModeSnapshot(
            id = ModeId.DOCUMENT,
            uiSpec = ModeUiSpec(title = "DOCUMENT", shutterLabel = "Scan"),
            state = ModeState(headline = "Document mode", detail = "Ready")
        ),
        activeDeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        ),
        previewMetrics = PreviewMetrics(),
        activeEffectSpec = EffectSpec(listOf(
            DocumentEffect(autoCrop = true, contrastProfile = "receipt", scanGuide = scanGuide)
        ))
    )

    private fun defaultSessionState(): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.CHECK_IN, ModeId.HUMANISTIC, ModeId.VIDEO),
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

    // --- stagedWatermarkHint override ---

    @Test
    fun `staged watermark hint overrides effect model watermark hint`() {
        val baseline = defaultSessionState()
        val stagedHint = WatermarkHintSpec(
            templateId = "professional-bottom-bar",
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            previewText = "professional-bottom-bar",
            opacity = 0.6f,
            shape = WatermarkPreviewShape.BOTTOM_BAR,
            textScale = 1f,
            previewLabels = listOf("OpenCamera", "2026-05-28"),
            barBackground = 0xFE000000.toInt()
        )

        val model = previewOverlayRenderModel(
            baseline,
            stagedWatermarkHint = stagedHint
        )

        val hint = assertNotNull(model.effectModel?.watermarkHint)
        assertEquals("professional-bottom-bar", hint.templateId)
        assertEquals(WatermarkPreviewShape.BOTTOM_BAR, hint.shape)
        assertEquals(2, hint.previewLabels.size)
        assertEquals("OpenCamera", hint.previewLabels[0])
    }

    @Test
    fun `document mode suppresses staged watermark preview hint`() {
        val baseline = defaultSessionState().copy(activeMode = ModeId.DOCUMENT)
        val stagedHint = WatermarkHintSpec(
            templateId = "professional-bottom-bar",
            placement = WatermarkTextPlacement.BOTTOM_CENTER,
            previewText = "professional-bottom-bar",
            opacity = 0.6f,
            shape = WatermarkPreviewShape.BOTTOM_BAR,
            textScale = 1f,
            previewLabels = listOf("OpenCamera", "2026-05-28"),
            barBackground = 0xFE000000.toInt()
        )

        val model = previewOverlayRenderModel(
            baseline,
            stagedWatermarkHint = stagedHint
        )

        assertNull(model.effectModel?.watermarkHint)
    }

    @Test
    fun `null staged hint preserves existing watermark hint`() {
        val baseline = defaultSessionState()
        val model = previewOverlayRenderModel(
            baseline,
            stagedWatermarkHint = null
        )

        // Without an effectAdapter, watermarkHint is null; staged override is also null
        assertNull(model.effectModel?.watermarkHint)
    }
}
