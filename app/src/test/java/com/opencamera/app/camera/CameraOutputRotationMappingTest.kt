package com.opencamera.app.camera

import android.view.Surface
import com.opencamera.core.device.CameraOutputRotation
import org.junit.Assert.assertEquals
import org.junit.Test

class CameraOutputRotationMappingTest {

    @Test
    fun `ROTATION_0 maps to Surface`() {
        assertEquals(Surface.ROTATION_0, mapOutputRotationToSurface(CameraOutputRotation.ROTATION_0))
    }

    @Test
    fun `ROTATION_90 maps to Surface`() {
        assertEquals(Surface.ROTATION_90, mapOutputRotationToSurface(CameraOutputRotation.ROTATION_90))
    }

    @Test
    fun `ROTATION_180 maps to Surface`() {
        assertEquals(Surface.ROTATION_180, mapOutputRotationToSurface(CameraOutputRotation.ROTATION_180))
    }

    @Test
    fun `ROTATION_270 maps to Surface`() {
        assertEquals(Surface.ROTATION_270, mapOutputRotationToSurface(CameraOutputRotation.ROTATION_270))
    }
}
