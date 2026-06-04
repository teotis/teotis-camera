package com.opencamera.app.camera

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VendorCameraProbeTest {
    @Test
    fun `probe section records failures instead of throwing`() {
        val output = StringBuilder()

        val completed = appendProbeSection(output, "fragile-section") {
            throw IllegalStateException("vendor key rejected")
        }

        assertFalse(completed)
        assertTrue(output.toString().contains("[fragile-section] ERROR: vendor key rejected"))
    }
}
