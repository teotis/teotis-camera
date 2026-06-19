package com.opencamera.app.camera

import androidx.camera.core.ImageProxy
import org.junit.Assert.*
import org.junit.Test

class PreviewAnalysisFanoutTest {

    @Test
    fun `close called exactly once when both consumers succeed`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var sceneMaskCalled = false
        var livePreviewCalled = false

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { img, rot ->
                sceneMaskCalled = true
            },
            livePreviewConsumer = { img, rot ->
                livePreviewCalled = true
            }
        )

        fanout.analyze(proxy, 0)

        assertTrue("scene mask consumer should be called", sceneMaskCalled)
        assertTrue("live preview consumer should be called", livePreviewCalled)
        assertEquals("ImageProxy should be closed exactly once by fanout", 1, closeCount)
    }

    @Test
    fun `scene mask throws does not break live preview and close is called once`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var livePreviewCalled = false

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> throw RuntimeException("ML Kit crash") },
            livePreviewConsumer = { img, rot ->
                livePreviewCalled = true
            }
        )

        fanout.analyze(proxy, 90)

        assertTrue("live preview consumer should still be called after scene mask failure", livePreviewCalled)
        assertEquals("ImageProxy should be closed exactly once", 1, closeCount)
    }

    @Test
    fun `live preview throws does not break scene mask and close is called once`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var sceneMaskCalled = false

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { img, rot ->
                sceneMaskCalled = true
            },
            livePreviewConsumer = { _, _ -> throw RuntimeException("frame copy crash") }
        )

        fanout.analyze(proxy, 270)

        assertTrue("scene mask consumer should still be called after live preview failure", sceneMaskCalled)
        assertEquals("ImageProxy should be closed exactly once", 1, closeCount)
    }

    @Test
    fun `close is called when no consumers are configured`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = null,
            livePreviewConsumer = null
        )

        fanout.analyze(proxy, 0)

        assertEquals("ImageProxy should be closed exactly once when no consumers", 1, closeCount)
    }

    @Test
    fun `close is called when only scene mask consumer is configured`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var sceneMaskCalled = false
        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> sceneMaskCalled = true },
            livePreviewConsumer = null
        )

        fanout.analyze(proxy, 0)

        assertTrue("scene mask consumer should be called", sceneMaskCalled)
        assertEquals("ImageProxy should be closed exactly once", 1, closeCount)
    }

    @Test
    fun `close is called when only live preview consumer is configured`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var livePreviewCalled = false
        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = null,
            livePreviewConsumer = { _, _ -> livePreviewCalled = true }
        )

        fanout.analyze(proxy, 180)

        assertTrue("live preview consumer should be called", livePreviewCalled)
        assertEquals("ImageProxy should be closed exactly once", 1, closeCount)
    }

    @Test
    fun `both consumers receive correct rotation degrees`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var sceneMaskRotation = -1
        var livePreviewRotation = -1

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, rot -> sceneMaskRotation = rot },
            livePreviewConsumer = { _, rot -> livePreviewRotation = rot }
        )

        fanout.analyze(proxy, 270)

        assertEquals("scene mask should receive rotation", 270, sceneMaskRotation)
        assertEquals("live preview should receive rotation", 270, livePreviewRotation)
        assertEquals(1, closeCount)
    }

    @Test
    fun `both consumers receive same ImageProxy instance`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        var sceneMaskProxy: ImageProxy? = null
        var livePreviewProxy: ImageProxy? = null

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { img, _ -> sceneMaskProxy = img },
            livePreviewConsumer = { img, _ -> livePreviewProxy = img }
        )

        fanout.analyze(proxy, 0)

        assertSame("both consumers should receive the same ImageProxy", sceneMaskProxy, livePreviewProxy)
        assertEquals(1, closeCount)
    }

    @Test
    fun `both consumers throw does not crash and close is called once`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> throw RuntimeException("scene mask crash") },
            livePreviewConsumer = { _, _ -> throw RuntimeException("live preview crash") }
        )

        // Must not throw
        fanout.analyze(proxy, 0)

        assertEquals("ImageProxy should be closed exactly once even when both throw", 1, closeCount)
    }

    @Test
    fun `fanout close is safe even if ImageProxy close throws`() {
        var closeAttempts = 0
        val proxy = object : ImageProxy {
            override fun close() { closeAttempts++; throw RuntimeException("close failed") }
            override fun getImage() = null
            override fun getPlanes() = emptyArray<ImageProxy.PlaneProxy>()
            override fun getWidth() = 640
            override fun getHeight() = 480
            override fun getImageInfo() = throw UnsupportedOperationException()
            override fun getFormat() = 35
            override fun getCropRect() = android.graphics.Rect(0, 0, 640, 480)
            override fun setCropRect(rect: android.graphics.Rect?) {}
        }

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> },
            livePreviewConsumer = { _, _ -> }
        )

        // Must not throw even though close() throws
        fanout.analyze(proxy, 0)
        assertEquals("close should be attempted once", 1, closeAttempts)
    }

    @Test
    fun `fanout owns close with MlKit scene mask source`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }

        val mlKitSource = MlKitSelfiePreviewSceneMaskSource()
        mlKitSource.start(PreviewSceneMaskConfig())

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { img, rot -> mlKitSource.onAnalyzeFrame(img, rot) },
            livePreviewConsumer = { _, _ -> }
        )

        fanout.analyze(proxy, 0)

        // Fanout is the sole close owner; MlKit source must not close
        assertEquals("fanout should close exactly once", 1, closeCount)

        mlKitSource.stop("test")
    }

    @Test
    fun `onConsumerError called when scene mask consumer throws`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }
        val errors = mutableListOf<Pair<String, Throwable>>()

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> throw RuntimeException("ML Kit crash") },
            livePreviewConsumer = { _, _ -> },
            onConsumerError = { tag, error -> errors.add(tag to error) }
        )

        fanout.analyze(proxy, 0)

        assertEquals("onConsumerError should be called once", 1, errors.size)
        assertEquals("scene-mask", errors[0].first)
        assertEquals("ML Kit crash", errors[0].second.message)
        assertEquals(1, closeCount)
    }

    @Test
    fun `onConsumerError called when live preview consumer throws`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }
        val errors = mutableListOf<Pair<String, Throwable>>()

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> },
            livePreviewConsumer = { _, _ -> throw RuntimeException("frame copy crash") },
            onConsumerError = { tag, error -> errors.add(tag to error) }
        )

        fanout.analyze(proxy, 0)

        assertEquals("onConsumerError should be called once", 1, errors.size)
        assertEquals("live-preview", errors[0].first)
        assertEquals("frame copy crash", errors[0].second.message)
        assertEquals(1, closeCount)
    }

    @Test
    fun `onConsumerError called for both consumers when both throw`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }
        val errors = mutableListOf<Pair<String, Throwable>>()

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> throw RuntimeException("scene crash") },
            livePreviewConsumer = { _, _ -> throw RuntimeException("live crash") },
            onConsumerError = { tag, error -> errors.add(tag to error) }
        )

        fanout.analyze(proxy, 0)

        assertEquals("onConsumerError should be called twice", 2, errors.size)
        assertEquals("live-preview", errors[0].first)
        assertEquals("scene-mask", errors[1].first)
        assertEquals(1, closeCount)
    }

    @Test
    fun `onConsumerError not called when no errors occur`() {
        var closeCount = 0
        val proxy = createCountingProxy { closeCount++ }
        val errors = mutableListOf<Pair<String, Throwable>>()

        val fanout = PreviewAnalysisFanout(
            sceneMaskConsumer = { _, _ -> },
            livePreviewConsumer = { _, _ -> },
            onConsumerError = { tag, error -> errors.add(tag to error) }
        )

        fanout.analyze(proxy, 0)

        assertTrue("onConsumerError should not be called", errors.isEmpty())
        assertEquals(1, closeCount)
    }

    private fun createCountingProxy(onClose: () -> Unit): ImageProxy {
        return object : ImageProxy {
            override fun close() { onClose() }
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
