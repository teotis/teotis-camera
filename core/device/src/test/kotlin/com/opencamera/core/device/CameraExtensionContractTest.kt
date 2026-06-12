package com.opencamera.core.device

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraExtensionContractTest {

    @Test
    fun `extension mode NONE has expected tag and label`() {
        assertEquals("none", CameraExtensionMode.NONE.tagValue)
        assertEquals("No extension", CameraExtensionMode.NONE.label)
    }

    @Test
    fun `extension mode NIGHT has expected tag`() {
        assertEquals("night", CameraExtensionMode.NIGHT.tagValue)
        assertEquals("Night", CameraExtensionMode.NIGHT.label)
    }

    @Test
    fun `extension mode HDR has expected tag`() {
        assertEquals("hdr", CameraExtensionMode.HDR.tagValue)
    }

    @Test
    fun `extension mode AUTO has expected tag`() {
        assertEquals("auto", CameraExtensionMode.AUTO.tagValue)
    }

    @Test
    fun `extension mode BOKEH has expected tag`() {
        assertEquals("bokeh", CameraExtensionMode.BOKEH.tagValue)
    }

    @Test
    fun `extension mode FACE_RETOUCH has expected tag`() {
        assertEquals("face-retouch", CameraExtensionMode.FACE_RETOUCH.tagValue)
    }

    @Test
    fun `default extension capture strategy is NONE`() {
        val strategy = ExtensionCaptureStrategy()
        assertEquals(CameraExtensionMode.NONE, strategy.desiredMode)
    }

    @Test
    fun `extension capture strategy with explicit mode`() {
        val strategy = ExtensionCaptureStrategy(desiredMode = CameraExtensionMode.HDR)
        assertEquals(CameraExtensionMode.HDR, strategy.desiredMode)
    }

    @Test
    fun `extension resolution isUsable only when AVAILABLE`() {
        val available = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.HDR,
            availability = CameraExtensionAvailability.AVAILABLE,
            reason = "OK"
        )
        assertTrue(available.isUsable)

        val unsupported = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.HDR,
            availability = CameraExtensionAvailability.UNSUPPORTED,
            reason = "Not supported"
        )
        assertFalse(unsupported.isUsable)
    }

    @Test
    fun `extension resolution pipelineNote includes mode tag and availability`() {
        val resolution = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.NIGHT,
            availability = CameraExtensionAvailability.AVAILABLE,
            reason = "OK"
        )
        assertEquals("extension:night=available", resolution.pipelineNote)
    }

    @Test
    fun `extension resolution fallback pipelineNote`() {
        val resolution = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.AUTO,
            availability = CameraExtensionAvailability.SELECTOR_ERROR,
            reason = "Selector rejected"
        )
        assertEquals("extension:auto=selector_error", resolution.pipelineNote)
    }

    @Test
    fun `NOT_REQUESTED resolution has expected pipeline note`() {
        val resolution = CameraExtensionResolution(
            requestedMode = CameraExtensionMode.NONE,
            availability = CameraExtensionAvailability.NOT_REQUESTED,
            reason = "No extension requested"
        )
        assertEquals("extension:none=not_requested", resolution.pipelineNote)
        assertFalse(resolution.isUsable)
    }

    @Test
    fun `StillCaptureConfig defaults to no extension strategy`() {
        val config = StillCaptureConfig()
        assertEquals(CameraExtensionMode.NONE, config.extensionStrategy.desiredMode)
    }

    @Test
    fun `StillCaptureConfig with extension strategy preserves other defaults`() {
        val config = StillCaptureConfig(
            extensionStrategy = ExtensionCaptureStrategy(desiredMode = CameraExtensionMode.HDR)
        )
        assertEquals(CameraExtensionMode.HDR, config.extensionStrategy.desiredMode)
        assertEquals(
            com.opencamera.core.media.StillCaptureQualityPreference.QUALITY,
            config.qualityPreference
        )
    }

    @Test
    fun `DeviceGraphSpec stillCapture factory defaults to no extension`() {
        val spec = DeviceGraphSpec.stillCapture()
        assertEquals(CameraExtensionMode.NONE, spec.stillCapture.extensionStrategy.desiredMode)
    }

    @Test
    fun `extension availability enum covers all expected states`() {
        val values = CameraExtensionAvailability.entries.map { it.name }.toSet()
        assertTrue("AVAILABLE" in values)
        assertTrue("UNSUPPORTED" in values)
        assertTrue("SELECTOR_ERROR" in values)
        assertTrue("MANAGER_UNAVAILABLE" in values)
        assertTrue("QUERY_ERROR" in values)
        assertTrue("NOT_REQUESTED" in values)
    }
}
