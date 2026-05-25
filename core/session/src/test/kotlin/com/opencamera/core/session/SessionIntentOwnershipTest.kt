package com.opencamera.core.session

import com.opencamera.core.device.CameraOutputRotation
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.media.CameraThermalState
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.mode.ModeId
import kotlin.test.Test
import kotlin.test.assertEquals

class SessionIntentOwnershipTest {

    @Test
    fun `Boot maps to LIFECYCLE`() {
        assertEquals(SessionIntentOwner.LIFECYCLE, SessionIntent.Boot.owner())
    }

    @Test
    fun `PermissionsUpdated maps to LIFECYCLE`() {
        assertEquals(
            SessionIntentOwner.LIFECYCLE,
            SessionIntent.PermissionsUpdated(cameraGranted = true, microphoneGranted = true).owner()
        )
    }

    @Test
    fun `SwitchMode maps to MODE_CONTROL`() {
        assertEquals(
            SessionIntentOwner.MODE_CONTROL,
            SessionIntent.SwitchMode(ModeId.PHOTO).owner()
        )
    }

    @Test
    fun `ShutterPressed maps to MODE_CONTROL`() {
        assertEquals(SessionIntentOwner.MODE_CONTROL, SessionIntent.ShutterPressed.owner())
    }

    @Test
    fun `PreviewRuntimeIssue maps to PREVIEW_RECOVERY`() {
        assertEquals(
            SessionIntentOwner.PREVIEW_RECOVERY,
            SessionIntent.PreviewRuntimeIssue(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.PREVIEW_STALL,
                    reason = "test",
                    isRecoverable = true
                )
            ).owner()
        )
    }

    @Test
    fun `PreviewTapToFocus maps to PREVIEW_RECOVERY`() {
        assertEquals(
            SessionIntentOwner.PREVIEW_RECOVERY,
            SessionIntent.PreviewTapToFocus(normalizedX = 0.5f, normalizedY = 0.5f).owner()
        )
    }

    @Test
    fun `CountdownTick maps to CAPTURE_RECORDING`() {
        assertEquals(
            SessionIntentOwner.CAPTURE_RECORDING,
            SessionIntent.CountdownTick(remainingSeconds = 3).owner()
        )
    }

    @Test
    fun `ShotCompleted maps to CAPTURE_RECORDING`() {
        assertEquals(
            SessionIntentOwner.CAPTURE_RECORDING,
            SessionIntent.ShotCompleted(
                ShotResult(
                    shotId = "test-shot",
                    mediaType = MediaType.PHOTO,
                    outputPath = "test.jpg",
                    saveRequest = SaveRequest.photoLibrary(),
                    thumbnailSource = ThumbnailSource.None,
                    metadata = SaveRequest.photoLibrary().metadata
                )
            ).owner()
        )
    }

    @Test
    fun `ThermalStateChanged maps to DIAGNOSTICS`() {
        assertEquals(
            SessionIntentOwner.DIAGNOSTICS,
            SessionIntent.ThermalStateChanged(CameraThermalState.NORMAL).owner()
        )
    }

    @Test
    fun `OutputRotationChanged maps to MODE_CONTROL`() {
        assertEquals(
            SessionIntentOwner.MODE_CONTROL,
            SessionIntent.OutputRotationChanged(CameraOutputRotation.ROTATION_0).owner()
        )
    }

    @Test
    fun `PreviewMeteringFeedbackExpired maps to PREVIEW_RECOVERY`() {
        assertEquals(
            SessionIntentOwner.PREVIEW_RECOVERY,
            SessionIntent.PreviewMeteringFeedbackExpired(requestId = "meter-1").owner()
        )
    }
}
