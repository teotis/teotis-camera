package com.opencamera.app.camera

import androidx.camera.core.ImageProxy
import com.opencamera.core.effect.SubjectMaskPreviewDescriptor
import org.junit.Assert.*
import org.junit.Test

class PreviewSceneMaskSourceTest {

    @Test
    fun `NoOp reports UNSUPPORTED capability`() {
        val source = NoOpPreviewSceneMaskSource()
        assertEquals(SceneMaskCapability.UNSUPPORTED, source.capability)
    }

    @Test
    fun `NoOp latestMask returns null`() {
        val source = NoOpPreviewSceneMaskSource()
        assertNull(source.latestMask())
    }

    @Test
    fun `NoOp start and stop do not throw`() {
        val source = NoOpPreviewSceneMaskSource()
        source.start(PreviewSceneMaskConfig())
        source.stop("test")
    }

    @Test
    fun `PreviewSceneMaskConfig defaults are correct`() {
        val config = PreviewSceneMaskConfig()
        assertEquals(256, config.targetWidth)
        assertEquals(256, config.targetHeight)
        assertEquals(8, config.maxFps)
        assertEquals("mlkit-selfie", config.backendId)
    }

    @Test
    fun `PreviewSceneMaskPayload equality considers all fields`() {
        val mask1 = PreviewSceneMaskPayload(
            width = 4,
            height = 4,
            confidenceMask = ByteArray(16) { 127 },
            rotationDegrees = 90,
            timestampMillis = 1000L
        )
        val mask2 = PreviewSceneMaskPayload(
            width = 4,
            height = 4,
            confidenceMask = ByteArray(16) { 127 },
            rotationDegrees = 90,
            timestampMillis = 1000L
        )
        assertEquals(mask1, mask2)
        assertEquals(mask1.hashCode(), mask2.hashCode())
    }

    @Test
    fun `PreviewSceneMaskPayload inequality on different mask bytes`() {
        val mask1 = PreviewSceneMaskPayload(
            width = 4,
            height = 4,
            confidenceMask = ByteArray(16) { 127 },
            rotationDegrees = 0,
            timestampMillis = 1000L
        )
        val mask2 = PreviewSceneMaskPayload(
            width = 4,
            height = 4,
            confidenceMask = ByteArray(16) { 0 },
            rotationDegrees = 0,
            timestampMillis = 1000L
        )
        assertNotEquals(mask1, mask2)
    }

    @Test
    fun `NoOp closes image proxy on analyze frame`() {
        val source = NoOpPreviewSceneMaskSource()
        var closeCount = 0
        val proxy = object : ImageProxy {
            override fun close() { closeCount++ }
            override fun getImage() = null
            override fun getPlanes() = emptyArray<ImageProxy.PlaneProxy>()
            override fun getWidth() = 0
            override fun getHeight() = 0
            override fun getImageInfo() = throw UnsupportedOperationException()
            override fun getFormat() = 0
            override fun getCropRect() = throw UnsupportedOperationException()
            override fun setCropRect(rect: android.graphics.Rect?) {}
        }
        source.onAnalyzeFrame(proxy, 0)
        assertEquals(1, closeCount)
    }

    @Test
    fun `MlKit source handles null media image gracefully`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        source.start(PreviewSceneMaskConfig())

        // Frame with null image should be dropped without crash
        val proxy = createTestImageProxy()
        source.onAnalyzeFrame(proxy, 0)

        assertNull(source.latestMask())
        source.stop("test")
    }

    @Test
    fun `MlKit source does nothing when not started`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        var closeCount = 0
        val proxy = object : ImageProxy {
            override fun close() { closeCount++ }
            override fun getImage() = null
            override fun getPlanes() = emptyArray<ImageProxy.PlaneProxy>()
            override fun getWidth() = 0
            override fun getHeight() = 0
            override fun getImageInfo() = throw UnsupportedOperationException()
            override fun getFormat() = 0
            override fun getCropRect() = throw UnsupportedOperationException()
            override fun setCropRect(rect: android.graphics.Rect?) {}
        }
        source.onAnalyzeFrame(proxy, 90)
        assertEquals(1, closeCount)
        assertNull(source.latestMask())
    }

    @Test
    fun `SubjectMaskPreviewDescriptor defaults`() {
        val desc = SubjectMaskPreviewDescriptor.UNAVAILABLE
        assertFalse(desc.isAvailable)
        assertEquals("none", desc.backendId)
        assertTrue(desc.isApproximate)
    }

    private fun createTestImageProxy(): ImageProxy {
        return object : ImageProxy {
            override fun close() {}
            override fun getImage() = null
            override fun getPlanes() = emptyArray<ImageProxy.PlaneProxy>()
            override fun getWidth() = 640
            override fun getHeight() = 480
            override fun getImageInfo() = throw UnsupportedOperationException()
            override fun getFormat() = 35
            override fun getCropRect() = android.graphics.Rect(0, 0, 640, 480)
            override fun setCropRect(rect: android.graphics.Rect?) {}
        }
    }
}
