package com.opencamera.app.camera.live

import com.opencamera.core.media.*
import org.junit.Assert.*
import org.junit.Test

class LivePreviewFrameSourceTest {

    @Test
    fun `start and stop lifecycle`() {
        val source = FakeLivePreviewFrameSource()

        assertFalse(source.isActive)

        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        assertTrue(source.isActive)

        source.stop("test-complete")
        assertFalse(source.isActive)
    }

    @Test
    fun `selectForLive returns empty when not started`() {
        val source = FakeLivePreviewFrameSource()

        val result = source.selectForLive(
            shutterTimestampNanos = 1_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertTrue(result.frames.isEmpty())
        assertTrue(result.diagnostics.any { it.contains("not-active") })
    }

    @Test
    fun `selectForLive returns frames when active`() {
        val source = FakeLivePreviewFrameSource()
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        // Simulate frames being added
        source.addFrame(makeDescriptor("f1", timestampNanos = 800_000_000L))
        source.addFrame(makeDescriptor("f2", timestampNanos = 1_000_000_000L))
        source.addFrame(makeDescriptor("f3", timestampNanos = 1_200_000_000L))

        val result = source.selectForLive(
            shutterTimestampNanos = 1_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertFalse(result.frames.isEmpty())
        assertTrue(result.preShutterCount > 0)
    }

    @Test
    fun `stop clears buffer and prevents further selection`() {
        val source = FakeLivePreviewFrameSource()
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        source.addFrame(makeDescriptor("f1", timestampNanos = 100_000_000L))
        source.stop("test")

        val result = source.selectForLive(
            shutterTimestampNanos = 100_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertTrue(result.frames.isEmpty())
        assertTrue(result.diagnostics.any { it.contains("not-active") })
    }

    @Test
    fun `CameraXLivePreviewFrameSource compiles and has correct interface`() {
        // Verify CameraXLivePreviewFrameSource implements LivePreviewFrameSource
        val source: LivePreviewFrameSource = CameraXLivePreviewFrameSource()
        assertFalse(source.isActive)
    }

    private fun makeDescriptor(
        frameId: String,
        timestampNanos: Long,
        width: Int = 640,
        height: Int = 480
    ) = FrameDescriptor(
        frameId = frameId,
        source = FrameSourceKind.PREVIEW_ANALYSIS,
        timestampNanos = timestampNanos,
        width = width,
        height = height,
        rotationDegrees = 0,
        payloadAccess = FramePayloadAccess.METADATA_ONLY,
        lensFacingTag = "BACK",
        zoomRatio = 1.0f
    )
}
