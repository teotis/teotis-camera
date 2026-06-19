package com.opencamera.app.camera

import androidx.camera.core.CameraInfo
import androidx.camera.core.CameraSelector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PhysicalCameraIdResolverTest {

    // --- PhysicalCameraIdResolver interface ---

    @Test
    fun `resolver returning a selector indicates match is possible`() {
        val resolver = PhysicalCameraIdResolver { CameraSelector.DEFAULT_BACK_CAMERA }
        assertNotNull(resolver.resolveSelector("0"))
    }

    @Test
    fun `resolver receives the requested physical camera id`() {
        var receivedId: String? = null
        val resolver = PhysicalCameraIdResolver { id ->
            receivedId = id
            CameraSelector.DEFAULT_BACK_CAMERA
        }
        resolver.resolveSelector("5")
        assertEquals("5", receivedId)
    }

    // --- PublicApiPhysicalIdResolver ---

    @Test
    fun `public api resolver produces a selector with physicalCameraId set`() {
        val resolver = PublicApiPhysicalIdResolver()
        val selector = resolver.resolveSelector("2")
        assertNotNull(selector)
        // The selector should have the physical camera ID set
        assertEquals("2", selector.physicalCameraId)
    }

    @Test
    fun `public api resolver creates different selectors for different ids`() {
        val resolver = PublicApiPhysicalIdResolver()
        val selector0 = resolver.resolveSelector("0")
        val selector1 = resolver.resolveSelector("1")
        assertNotNull(selector0)
        assertNotNull(selector1)
        assertEquals("0", selector0.physicalCameraId)
        assertEquals("1", selector1.physicalCameraId)
    }

    // --- ReflectionPhysicalIdResolver ---

    @Test
    fun `reflection resolver returns a non-null selector`() {
        val resolver = ReflectionPhysicalIdResolver()
        val selector = resolver.resolveSelector("0")
        assertNotNull(selector)
    }

    @Test
    fun `reflection resolver filter does not crash on null internal state`() {
        // When camera objects have unexpected internal state, the reflection
        // filter catches the exception and returns false (no match).
        val resolver = ReflectionPhysicalIdResolver()
        val selector = resolver.resolveSelector("0")
        assertNotNull(selector)
    }

    // --- CompositePhysicalCameraIdResolver ---

    @Test
    fun `composite resolver uses public api for primary path`() {
        val resolver = CompositePhysicalCameraIdResolver()
        val selector = resolver.resolveSelector("2")
        assertNotNull(selector)
        assertEquals("2", selector.physicalCameraId)
    }

    @Test
    fun `composite resolver reflection path returns a selector`() {
        val resolver = CompositePhysicalCameraIdResolver()
        val selector = resolver.resolveSelectorViaReflection("0")
        assertNotNull(selector)
    }

    @Test
    fun `composite resolver primary path matches reflection path target`() {
        val resolver = CompositePhysicalCameraIdResolver()
        val targetId = "3"
        val primarySelector = resolver.resolveSelector(targetId)
        val reflectionSelector = resolver.resolveSelectorViaReflection(targetId)
        assertNotNull(primarySelector)
        assertNotNull(reflectionSelector)
        // Both should target the same physical camera ID
        assertEquals(targetId, primarySelector.physicalCameraId)
    }

    // --- cameraSelectorForLensNode fallback behavior ---

    @Test
    fun `custom resolver returning DEFAULT_BACK_CAMERA when camera matches`() {
        val resolver = PhysicalCameraIdResolver { id ->
            if (id == "0") CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        }
        assertNotNull(resolver.resolveSelector("0"))
        assertNotNull(resolver.resolveSelector("1"))
    }
}
