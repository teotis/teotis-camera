package com.opencamera.app

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.device.StillCaptureOutputSize
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
import com.opencamera.core.session.SessionPresentationState
import com.opencamera.core.session.SessionLifecycle
import com.opencamera.core.session.SessionState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionStateRenderTest {
    @Test
    fun `still resolution toggle stays enabled for multiple native sizes on single preset`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureResolutionPresets = setOf(
                    StillCaptureResolutionPreset.LARGE_12MP
                ),
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            )
        )

        assertTrue(isStillResolutionToggleEnabled(state))
    }

    @Test
    fun `selected native output size falls back to current preset mapping when graph output is absent`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 3264, height = 2448),
                    StillCaptureOutputSize(width = 1920, height = 1080)
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.stillCapture(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true,
                outputSize = null
            )
        )

        assertEquals(
            StillCaptureOutputSize(width = 3264, height = 2448),
            selectedNativeStillCaptureOutputSizeOrNull(state)
        )
    }

    @Test
    fun `displayed still output size prefers explicit graph selection`() {
        val state = defaultSessionState(
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
            )
        )

        assertEquals(
            StillCaptureOutputSize(width = 4000, height = 3000),
            displayedStillCaptureOutputSize(state)
        )
    }

    @Test
    fun `still resolution toggle stays disabled outside still capture template`() {
        val state = defaultSessionState(
            activeDeviceCapabilities = DeviceCapabilities.DEFAULT.copy(
                availableStillCaptureResolutionPresets = setOf(
                    StillCaptureResolutionPreset.LARGE_12MP,
                    StillCaptureResolutionPreset.MEDIUM_8MP
                ),
                availableStillCaptureOutputSizes = listOf(
                    StillCaptureOutputSize(width = 6000, height = 4000),
                    StillCaptureOutputSize(width = 4000, height = 3000)
                )
            ),
            activeDeviceGraph = DeviceGraphSpec.videoRecording(
                preferredLensFacing = LensFacing.BACK,
                enablePreviewSnapshots = true
            )
        )

        assertFalse(isStillResolutionToggleEnabled(state))
    }

    private fun defaultSessionState(
        activeDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT,
        activeDeviceGraph: DeviceGraphSpec = DeviceGraphSpec.stillCapture(
            preferredLensFacing = LensFacing.BACK,
            enablePreviewSnapshots = true
        )
    ): SessionState {
        return SessionState(
            lifecycle = SessionLifecycle.RUNNING,
            permissionState = PermissionState(cameraGranted = true, microphoneGranted = true),
            previewHostAvailable = true,
            previewStatus = PreviewStatus.ACTIVE,
            previewStatusDetail = null,
            activeMode = ModeId.PHOTO,
            availableModes = listOf(ModeId.PHOTO, ModeId.NIGHT, ModeId.VIDEO),
            captureStatus = CaptureStatus.IDLE,
            recordingStatus = RecordingStatus.IDLE,
            activeShot = null,
            modeSnapshot = ModeSnapshot(
                id = ModeId.PHOTO,
                uiSpec = ModeUiSpec(
                    title = "PHOTO",
                    shutterLabel = "Capture PHOTO"
                ),
                state = ModeState(
                    headline = "PHOTO mode active",
                    detail = "Ready"
                )
            ),
            activeDeviceCapabilities = activeDeviceCapabilities,
            activeDeviceGraph = activeDeviceGraph,
            previewMetrics = PreviewMetrics(),
            presentation = SessionPresentationState(
                lastAction = "Ready"
            )
        )
    }
}
