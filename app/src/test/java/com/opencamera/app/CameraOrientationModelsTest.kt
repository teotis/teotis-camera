package com.opencamera.app

import android.view.OrientationEventListener
import com.opencamera.core.device.CameraOutputRotation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CameraOrientationModelsTest {

    @Test
    fun `0 degrees maps to PORTRAIT`() {
        assertEquals(CameraPhysicalOrientation.PORTRAIT, orientationBucketFromDegrees(0))
    }

    @Test
    fun `10 degrees maps to PORTRAIT`() {
        assertEquals(CameraPhysicalOrientation.PORTRAIT, orientationBucketFromDegrees(10))
    }

    @Test
    fun `359 degrees maps to PORTRAIT`() {
        assertEquals(CameraPhysicalOrientation.PORTRAIT, orientationBucketFromDegrees(359))
    }

    @Test
    fun `90 degrees maps to LANDSCAPE_LEFT`() {
        assertEquals(CameraPhysicalOrientation.LANDSCAPE_LEFT, orientationBucketFromDegrees(90))
    }

    @Test
    fun `180 degrees maps to REVERSE_PORTRAIT`() {
        assertEquals(CameraPhysicalOrientation.REVERSE_PORTRAIT, orientationBucketFromDegrees(180))
    }

    @Test
    fun `270 degrees maps to LANDSCAPE_RIGHT`() {
        assertEquals(CameraPhysicalOrientation.LANDSCAPE_RIGHT, orientationBucketFromDegrees(270))
    }

    @Test
    fun `ORIENTATION_UNKNOWN returns null`() {
        assertNull(orientationBucketFromDegrees(OrientationEventListener.ORIENTATION_UNKNOWN))
    }

    @Test
    fun `landscape left returns 90 degree content rotation`() {
        val model = orientationRenderModelFromBucket(CameraPhysicalOrientation.LANDSCAPE_LEFT)
        assertEquals(90f, model.contentRotationDegrees)
        assertEquals(CameraOutputRotation.ROTATION_90, model.outputRotation)
    }

    @Test
    fun `landscape right returns minus 90 degree content rotation`() {
        val model = orientationRenderModelFromBucket(CameraPhysicalOrientation.LANDSCAPE_RIGHT)
        assertEquals(-90f, model.contentRotationDegrees)
        assertEquals(CameraOutputRotation.ROTATION_270, model.outputRotation)
    }

    @Test
    fun `portrait returns zero content rotation`() {
        val model = orientationRenderModelFromBucket(CameraPhysicalOrientation.PORTRAIT)
        assertEquals(0f, model.contentRotationDegrees)
        assertEquals(CameraOutputRotation.ROTATION_0, model.outputRotation)
    }

    @Test
    fun `reverse portrait returns 180 content rotation`() {
        val model = orientationRenderModelFromBucket(CameraPhysicalOrientation.REVERSE_PORTRAIT)
        assertEquals(180f, model.contentRotationDegrees)
        assertEquals(CameraOutputRotation.ROTATION_180, model.outputRotation)
    }
}
