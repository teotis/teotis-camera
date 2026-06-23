package com.opencamera.app.permissions

import kotlin.test.Test
import kotlin.test.assertEquals

class CameraPermissionGateTest {

    private val gate = CameraPermissionGate()

    @Test
    fun `granted overrides all other inputs`() {
        assertEquals(
            CameraPermissionState.GRANTED,
            gate.resolve(cameraGranted = true, hasRequestedCamera = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `granted when requested and rationale both true`() {
        assertEquals(
            CameraPermissionState.GRANTED,
            gate.resolve(cameraGranted = true, hasRequestedCamera = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `never asked when never requested`() {
        assertEquals(
            CameraPermissionState.NEVER_ASKED,
            gate.resolve(cameraGranted = false, hasRequestedCamera = false, shouldShowRationale = false),
        )
    }

    @Test
    fun `never asked when never requested but rationale true`() {
        assertEquals(
            CameraPermissionState.NEVER_ASKED,
            gate.resolve(cameraGranted = false, hasRequestedCamera = false, shouldShowRationale = true),
        )
    }

    @Test
    fun `show rationale when requested and rationale true`() {
        assertEquals(
            CameraPermissionState.SHOW_RATIONALE,
            gate.resolve(cameraGranted = false, hasRequestedCamera = true, shouldShowRationale = true),
        )
    }

    @Test
    fun `permanently denied when requested but rationale false`() {
        assertEquals(
            CameraPermissionState.PERMANENTLY_DENIED,
            gate.resolve(cameraGranted = false, hasRequestedCamera = true, shouldShowRationale = false),
        )
    }

    @Test
    fun `never asked launches the system permission dialog`() {
        assertEquals(
            CameraPermissionAction.REQUEST_SYSTEM_PERMISSION,
            gate.actionFor(CameraPermissionState.NEVER_ASKED),
        )
    }

    @Test
    fun `permanently denied routes to application settings`() {
        assertEquals(
            CameraPermissionAction.OPEN_APPLICATION_SETTINGS,
            gate.actionFor(CameraPermissionState.PERMANENTLY_DENIED),
        )
    }
}
