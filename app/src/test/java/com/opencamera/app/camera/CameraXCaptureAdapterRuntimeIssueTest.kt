package com.opencamera.app.camera

import androidx.camera.core.CameraState
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraXCaptureAdapterRuntimeIssueTest {
    @Test
    fun `bind failure mentioning provider is classified as provider failure`() {
        val issue = classifyPreviewBindingFailure(
            IllegalStateException("Camera provider restarted")
        )

        assertEquals(DeviceRuntimeIssueKind.PROVIDER_FAILURE, issue.kind)
        assertTrue(issue.isRecoverable)
        assertEquals("Camera provider restarted", issue.reason)
    }

    @Test
    fun `thermal bind failure is marked unsupported for recovery`() {
        val issue = classifyPreviewBindingFailure(
            IllegalStateException("Thermal critical, preview paused")
        )

        assertEquals(DeviceRuntimeIssueKind.THERMAL_CRITICAL, issue.kind)
        assertFalse(issue.isRecoverable)
    }

    @Test
    fun `camera fatal state maps to non recoverable fatal issue`() {
        val issue = cameraStateRuntimeIssue(
            errorCode = CameraState.ERROR_CAMERA_FATAL_ERROR,
            causeMessage = "camera service died"
        )

        assertEquals(DeviceRuntimeIssueKind.CAMERA_FATAL, issue.kind)
        assertFalse(issue.isRecoverable)
        assertEquals("camera service died", issue.reason)
    }

    @Test
    fun `recoverable camera state keeps recovery semantics`() {
        val issue = cameraStateRuntimeIssue(
            errorCode = CameraState.ERROR_OTHER_RECOVERABLE_ERROR,
            causeMessage = "camera reopening"
        )

        assertEquals(DeviceRuntimeIssueKind.CAMERA_RECOVERABLE, issue.kind)
        assertTrue(issue.isRecoverable)
    }

    @Test
    fun `provider and fatal issues invalidate cached provider state`() {
        assertTrue(
            shouldInvalidateCachedProviderState(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.PROVIDER_FAILURE,
                    reason = "provider restarted",
                    isRecoverable = true
                )
            )
        )
        assertTrue(
            shouldInvalidateCachedProviderState(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.CAMERA_FATAL,
                    reason = "camera service died",
                    isRecoverable = false
                )
            )
        )
        assertFalse(
            shouldInvalidateCachedProviderState(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.CAMERA_RECOVERABLE,
                    reason = "camera reopening",
                    isRecoverable = true
                )
            )
        )
        assertFalse(
            shouldInvalidateCachedProviderState(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.THERMAL_CRITICAL,
                    reason = "thermal status critical",
                    isRecoverable = false
                )
            )
        )
    }
}
