package com.opencamera.app.camera.live

import androidx.camera.core.ImageProxy
import com.opencamera.core.media.*
import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer

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
    fun `lastStartReason is null initially and set after start`() {
        val source = FakeLivePreviewFrameSource()
        assertNull(source.lastStartReason)

        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        assertNotNull(source.lastStartReason)
        assertTrue(source.lastStartReason!!.contains("policy="))
    }

    @Test
    fun `lastStopReason is null initially and set after stop`() {
        val source = FakeLivePreviewFrameSource()
        assertNull(source.lastStopReason)

        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        source.stop("unbind")

        assertNotNull(source.lastStopReason)
        assertEquals("unbind", source.lastStopReason)
    }

    @Test
    fun `CameraXLivePreviewFrameSource tracks lastStartReason and lastStopReason`() {
        val source = CameraXLivePreviewFrameSource()
        assertNull(source.lastStartReason)
        assertNull(source.lastStopReason)

        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        assertNotNull(source.lastStartReason)

        source.stop("release")
        assertEquals("release", source.lastStopReason)
    }

    @Test
    fun `CameraXLivePreviewFrameSource compiles and has correct interface`() {
        // Verify CameraXLivePreviewFrameSource implements LivePreviewFrameSource
        val source: LivePreviewFrameSource = CameraXLivePreviewFrameSource()
        assertFalse(source.isActive)
    }

    @Test
    fun `CameraXLivePreviewFrameSource onAnalyzeFrame closes image when not started`() {
        val source = CameraXLivePreviewFrameSource()
        val proxy = FakeImageProxy(planes = emptyArray())

        source.onAnalyzeFrame(proxy, 0)

        assertEquals(1, proxy.closeCount)
    }

    @Test
    fun `CameraXLivePreviewFrameSource onAnalyzeFrame closes image after consuming when active`() {
        val source = CameraXLivePreviewFrameSource()
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        val proxy = yuvImageProxy(width = 4, height = 4)

        source.onAnalyzeFrame(proxy, 0)

        assertEquals(1, proxy.closeCount)
        val selected = source.selectForLive(
            shutterTimestampNanos = System.nanoTime(),
            spec = LivePhotoCaptureSpec()
        )
        assertTrue(selected.frames.isNotEmpty())
    }

    @Test
    fun `CameraXLivePreviewFrameSource onAnalyzeFrame closes image when plane copy throws`() {
        val source = CameraXLivePreviewFrameSource()
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        // Planes array has fewer than 3 entries -> toCapturedPreviewYuvFrame requires >= 3
        val proxy = FakeImageProxy(
            width = 4,
            height = 4,
            planes = arrayOf(FakePlaneProxy(ByteArray(0), 0, 0))
        )

        source.onAnalyzeFrame(proxy, 0)

        assertEquals(1, proxy.closeCount)
    }

    @Test
    fun `CameraXLivePreviewFrameSource onAnalyzeFrame closes image after stop`() {
        val source = CameraXLivePreviewFrameSource()
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        source.stop("unbind")
        val proxy = yuvImageProxy(width = 4, height = 4)

        source.onAnalyzeFrame(proxy, 0)

        assertEquals(1, proxy.closeCount)
    }

    @Test
    fun `CameraXLivePreviewFrameSource materializes selected yuv frames through encoder`() {
        val encoder = RecordingPreviewMotionSegmentEncoder()
        val source = CameraXLivePreviewFrameSource(motionSegmentEncoder = encoder)
        source.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        val shutterNanos = 2_000_000_000L
        val descriptor = makeDescriptor(
            frameId = "preview-1",
            timestampNanos = shutterNanos,
            width = 4,
            height = 4,
            payloadAccess = FramePayloadAccess.CPU_YUV
        )
        source.appendCapturedFrame(
            CapturedPreviewYuvFrame(
                descriptor = descriptor,
                yPlane = ByteArray(16) { it.toByte() },
                uPlane = ByteArray(4) { (it + 16).toByte() },
                vPlane = ByteArray(4) { (it + 20).toByte() },
                yRowStride = 4,
                yPixelStride = 1,
                uRowStride = 2,
                uPixelStride = 1,
                vRowStride = 2,
                vPixelStride = 1
            )
        )

        val selected = source.selectForLive(shutterNanos, LivePhotoCaptureSpec())
        val result = source.materializeMotionSegment(selected.frames, "/tmp/live.mp4")

        assertTrue(result.isSuccess)
        assertEquals("/tmp/live.mp4", result.getOrThrow())
        assertEquals(listOf(descriptor.frameId), encoder.encodedFrameIds)
    }

    private fun makeDescriptor(
        frameId: String,
        timestampNanos: Long,
        width: Int = 640,
        height: Int = 480,
        payloadAccess: FramePayloadAccess = FramePayloadAccess.METADATA_ONLY
    ) = FrameDescriptor(
        frameId = frameId,
        source = FrameSourceKind.PREVIEW_ANALYSIS,
        timestampNanos = timestampNanos,
        width = width,
        height = height,
        rotationDegrees = 0,
        payloadAccess = payloadAccess,
        lensFacingTag = "BACK",
        zoomRatio = 1.0f
    )

    private class RecordingPreviewMotionSegmentEncoder : PreviewMotionSegmentEncoder {
        var encodedFrameIds: List<String> = emptyList()

        override fun encode(
            frames: List<CapturedPreviewYuvFrame>,
            outputPath: String
        ): Result<String> {
            encodedFrameIds = frames.map { it.descriptor.frameId }
            return Result.success(outputPath)
        }
    }

    private fun yuvImageProxy(width: Int = 4, height: Int = 4): FakeImageProxy {
        val ySize = width * height
        val chromaSize = (width / 2) * (height / 2)
        val yPlane = FakePlaneProxy(ByteArray(ySize) { it.toByte() }, rowStride = width, pixelStride = 1)
        val uPlane = FakePlaneProxy(ByteArray(chromaSize) { (it + 1).toByte() }, rowStride = width / 2, pixelStride = 1)
        val vPlane = FakePlaneProxy(ByteArray(chromaSize) { (it + 2).toByte() }, rowStride = width / 2, pixelStride = 1)
        return FakeImageProxy(
            width = width,
            height = height,
            planes = arrayOf(yPlane, uPlane, vPlane)
        )
    }

    private class FakeImageProxy(
        private val width: Int = 0,
        private val height: Int = 0,
        private val planes: Array<ImageProxy.PlaneProxy> = emptyArray()
    ) : ImageProxy {
        var closeCount: Int = 0
            private set

        override fun close() { closeCount++ }
        override fun getImage() = null
        override fun getPlanes() = planes
        override fun getWidth() = width
        override fun getHeight() = height
        override fun getImageInfo() = throw UnsupportedOperationException()
        override fun getFormat() = 0
        override fun getCropRect() = throw UnsupportedOperationException()
        override fun setCropRect(rect: android.graphics.Rect?) {}
    }

    private class FakePlaneProxy(
        private val data: ByteArray,
        private val rowStride: Int,
        private val pixelStride: Int
    ) : ImageProxy.PlaneProxy {
        override fun getBuffer(): ByteBuffer = ByteBuffer.wrap(data)
        override fun getPixelStride(): Int = pixelStride
        override fun getRowStride(): Int = rowStride
    }
}
