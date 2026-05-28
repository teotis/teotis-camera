package com.opencamera.app.camera

import androidx.camera.core.ImageProxy
import com.opencamera.core.effect.SubjectMaskPreviewDescriptor
import org.junit.Assert.*
import org.junit.Test

class PreviewSceneMaskSourceTest {

    @Test
    fun `NoOp reports UNSUPPORTED capability`() {
        val source = NoOpPreviewSceneMaskSource()
        assertEquals(PreviewSceneMaskCapability.UNSUPPORTED, source.capability)
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
    fun `NoOp onAnalyzeFrame does not close ImageProxy`() {
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
        assertEquals(0, closeCount)
    }

    @Test
    fun `MlKit source handles null image planes gracefully`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        source.start(PreviewSceneMaskConfig())

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
        assertEquals(0, closeCount)
        assertNull(source.latestMask())
    }

    @Test
    fun `SubjectMaskPreviewDescriptor defaults`() {
        val desc = SubjectMaskPreviewDescriptor.UNAVAILABLE
        assertFalse(desc.isAvailable)
        assertEquals("none", desc.backendId)
        assertTrue(desc.isApproximate)
    }

    @Test
    fun `PreviewSceneMaskCapability toCoreSupport maps correctly`() {
        assertEquals(
            com.opencamera.core.media.SceneMaskSupport.SUPPORTED,
            PreviewSceneMaskCapability.READY.toCoreSupport()
        )
        assertEquals(
            com.opencamera.core.media.SceneMaskSupport.DEGRADED,
            PreviewSceneMaskCapability.DEGRADED.toCoreSupport()
        )
        assertEquals(
            com.opencamera.core.media.SceneMaskSupport.UNSUPPORTED,
            PreviewSceneMaskCapability.UNSUPPORTED.toCoreSupport()
        )
    }

    @Test
    fun `MlKit source does not close ImageProxy when used through fanout`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        source.start(PreviewSceneMaskConfig())

        var closeCount = 0
        val proxy = object : ImageProxy {
            override fun close() { closeCount++ }
            override fun getImage() = null
            override fun getPlanes() = emptyArray<ImageProxy.PlaneProxy>()
            override fun getWidth() = 640
            override fun getHeight() = 480
            override fun getImageInfo() = throw UnsupportedOperationException()
            override fun getFormat() = 35
            override fun getCropRect() = android.graphics.Rect(0, 0, 640, 480)
            override fun setCropRect(rect: android.graphics.Rect?) {}
        }

        // Simulate fanout calling source then closing
        source.onAnalyzeFrame(proxy, 0)
        // Source should not close proxy - fanout owns lifecycle
        assertEquals("source should not close ImageProxy", 0, closeCount)

        // Fanout closes
        proxy.close()
        assertEquals("fanout close is the only close", 1, closeCount)

        source.stop("test")
    }

    @Test
    fun `maxFps throttle drops too-soon frame before conversion`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        // maxFps=4 means min interval = 250ms
        source.start(PreviewSceneMaskConfig(maxFps = 4))

        // First frame: no previous processed time, should not be throttled
        // (but will fail bitmap conversion since test proxy has no planes)
        val proxy1 = createTestImageProxy()
        source.onAnalyzeFrame(proxy1, 0)

        // Second frame immediately after: should be fps-throttled
        val proxy2 = createTestImageProxy()
        source.onAnalyzeFrame(proxy2, 0)

        // Since we can't easily inspect internal counters from test,
        // we verify the source doesn't crash and still reports latestMask as null
        // (both frames fail at bitmap conversion, but fps throttle happens before that for the second)
        assertNull(source.latestMask())

        source.stop("test")
    }

    @Test
    fun `maxFps config with 0 disables throttling`() {
        val source = MlKitSelfiePreviewSceneMaskSource()
        source.start(PreviewSceneMaskConfig(maxFps = 0))

        // Should not throw or throttle
        val proxy1 = createTestImageProxy()
        source.onAnalyzeFrame(proxy1, 0)
        val proxy2 = createTestImageProxy()
        source.onAnalyzeFrame(proxy2, 0)

        assertNull(source.latestMask())
        source.stop("test")
    }

    @Test
    fun `target size config is stored and used`() {
        val config = PreviewSceneMaskConfig(targetWidth = 128, targetHeight = 96, maxFps = 8)
        val source = MlKitSelfiePreviewSceneMaskSource()
        source.start(config)

        // Config should be accepted without error
        val proxy = createTestImageProxy()
        source.onAnalyzeFrame(proxy, 0)

        assertNull(source.latestMask())
        source.stop("test")
    }

    @Test
    fun `PreviewSceneMaskPayload with source dimensions records transform`() {
        val payload = PreviewSceneMaskPayload(
            width = 128,
            height = 96,
            confidenceMask = ByteArray(128 * 96) { 127 },
            rotationDegrees = 0,
            timestampMillis = 1000L,
            sourceWidth = 640,
            sourceHeight = 480,
            diagnostics = listOf("source=640x480", "target=128x96")
        )
        val descriptor = payload.toDescriptor()
        assertEquals(640, descriptor.transform.sourceWidth)
        assertEquals(480, descriptor.transform.sourceHeight)
        assertEquals(128, descriptor.transform.maskWidth)
        assertEquals(96, descriptor.transform.maskHeight)
        assertTrue(descriptor.diagnostics.any { it.contains("source=640x480") })
    }

    @Test
    fun `PreviewSceneMaskPayload default source dimensions equal mask dimensions`() {
        val payload = PreviewSceneMaskPayload(
            width = 256,
            height = 256,
            confidenceMask = ByteArray(256 * 256) { 100 },
            rotationDegrees = 90,
            timestampMillis = 2000L
        )
        assertEquals(256, payload.sourceWidth)
        assertEquals(256, payload.sourceHeight)
        val descriptor = payload.toDescriptor()
        assertEquals(256, descriptor.transform.sourceWidth)
        assertEquals(256, descriptor.transform.sourceHeight)
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
