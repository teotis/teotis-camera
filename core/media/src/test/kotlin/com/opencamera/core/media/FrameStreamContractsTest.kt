package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FrameStreamContractsTest {

    private fun sampleDescriptor(
        id: String = "frame-1",
        source: FrameSourceKind = FrameSourceKind.PREVIEW_ANALYSIS
    ) = FrameDescriptor(
        frameId = id,
        source = source,
        timestampNanos = 1_000_000L,
        width = 1920,
        height = 1080,
        rotationDegrees = 0,
        payloadAccess = FramePayloadAccess.FILE_HANDLE,
        lensFacingTag = "back",
        zoomRatio = 1.0f
    )

    // --- Enum counts ---

    @Test
    fun `FrameSourceKind has five values`() {
        assertEquals(5, FrameSourceKind.entries.size)
    }

    @Test
    fun `FramePayloadAccess has five values`() {
        assertEquals(5, FramePayloadAccess.entries.size)
    }

    @Test
    fun `FrameDropPolicy has three values`() {
        assertEquals(3, FrameDropPolicy.entries.size)
    }

    // --- FrameDescriptor ---

    @Test
    fun `FrameDescriptor equality`() {
        val d1 = sampleDescriptor()
        val d2 = sampleDescriptor()
        assertEquals(d1, d2)
        assertEquals(d1.hashCode(), d2.hashCode())
    }

    @Test
    fun `FrameDescriptor copy changes fields`() {
        val d = sampleDescriptor()
        val d2 = d.copy(width = 1280, height = 720)
        assertEquals(1280, d2.width)
        assertEquals(720, d2.height)
        assertEquals(d.frameId, d2.frameId)
    }

    @Test
    fun `FrameDescriptor with metadata`() {
        val d = sampleDescriptor().copy(
            metadata = mapOf("iso" to "400", "exposure" to "16ms")
        )
        assertEquals("400", d.metadata["iso"])
        assertEquals("16ms", d.metadata["exposure"])
    }

    // --- FrameBufferPolicy ---

    @Test
    fun `PREVIEW_DEFAULT has correct values`() {
        val p = FrameBufferPolicy.PREVIEW_DEFAULT
        assertEquals(10, p.targetFps)
        assertEquals(12, p.maxFrames)
        assertEquals(1500L, p.retentionWindowMillis)
        assertEquals(64L * 1024 * 1024, p.maxBytes)
        assertEquals(FrameDropPolicy.KEEP_LATEST, p.dropPolicy)
    }

    @Test
    fun `LIVE_PREVIEW_DEFAULT has larger retention than PREVIEW_DEFAULT`() {
        val preview = FrameBufferPolicy.PREVIEW_DEFAULT
        val live = FrameBufferPolicy.LIVE_PREVIEW_DEFAULT
        assertTrue(live.maxFrames > preview.maxFrames)
        assertTrue(live.retentionWindowMillis > preview.retentionWindowMillis)
    }

    @Test
    fun `VIDEO_MOTION_DEFAULT has largest buffer`() {
        val video = FrameBufferPolicy.VIDEO_MOTION_DEFAULT
        assertEquals(30, video.targetFps)
        assertEquals(90, video.maxFrames)
    }

    // --- FramePayload subtypes ---

    @Test
    fun `FileRef equality`() {
        val desc = sampleDescriptor()
        val f1 = FramePayload.FileRef(desc, "/path/to/file.jpg", "image/jpeg")
        val f2 = FramePayload.FileRef(desc, "/path/to/file.jpg", "image/jpeg")
        assertEquals(f1, f2)
    }

    @Test
    fun `FileRef inequality on path`() {
        val desc = sampleDescriptor()
        val f1 = FramePayload.FileRef(desc, "/path/a.jpg", "image/jpeg")
        val f2 = FramePayload.FileRef(desc, "/path/b.jpg", "image/jpeg")
        assertNotEquals(f1, f2)
    }

    @Test
    fun `YuvPlanesRef equality with same byte arrays`() {
        val desc = sampleDescriptor()
        val y = byteArrayOf(1, 2, 3)
        val u = byteArrayOf(4, 5)
        val v = byteArrayOf(6, 7)
        val yuv1 = FramePayload.YuvPlanesRef(desc, y, u, v, rowStride = 1920, pixelStride = 1)
        val yuv2 = FramePayload.YuvPlanesRef(desc, y.copyOf(), u.copyOf(), v.copyOf(), rowStride = 1920, pixelStride = 1)
        assertEquals(yuv1, yuv2)
        assertEquals(yuv1.hashCode(), yuv2.hashCode())
    }

    @Test
    fun `YuvPlanesRef inequality with different byte arrays`() {
        val desc = sampleDescriptor()
        val yuv1 = FramePayload.YuvPlanesRef(desc, byteArrayOf(1), byteArrayOf(1), byteArrayOf(1), 1, 1)
        val yuv2 = FramePayload.YuvPlanesRef(desc, byteArrayOf(2), byteArrayOf(1), byteArrayOf(1), 1, 1)
        assertNotEquals(yuv1, yuv2)
    }

    @Test
    fun `RgbaBufferRef equality`() {
        val desc = sampleDescriptor()
        val buf = byteArrayOf(10, 20, 30, 40)
        val r1 = FramePayload.RgbaBufferRef(desc, buf, 2, 2)
        val r2 = FramePayload.RgbaBufferRef(desc, buf.copyOf(), 2, 2)
        assertEquals(r1, r2)
    }

    @Test
    fun `RgbaBufferRef inequality on dimensions`() {
        val desc = sampleDescriptor()
        val buf = byteArrayOf(1, 2, 3, 4)
        val r1 = FramePayload.RgbaBufferRef(desc, buf, 2, 2)
        val r2 = FramePayload.RgbaBufferRef(desc, buf, 4, 1)
        assertNotEquals(r1, r2)
    }

    // --- FrameLease ---

    @Test
    fun `FrameLease contract with fake`() {
        val desc = sampleDescriptor()
        val payload = FramePayload.FileRef(desc, "/test.jpg", "image/jpeg")
        var closed = false
        val lease = object : FrameLease {
            override val descriptor = desc
            override fun payload(): FramePayload = payload
            override fun close() { closed = true }
        }

        assertEquals(desc, lease.descriptor)
        assertNotNull(lease.payload())
        assertTrue(lease.payload() is FramePayload.FileRef)
        lease.close()
        assertTrue(closed)
    }

    @Test
    fun `FrameLease payload can return null`() {
        val desc = sampleDescriptor()
        val lease = object : FrameLease {
            override val descriptor = desc
            override fun payload(): FramePayload? = null
            override fun close() {}
        }
        assertNull(lease.payload())
    }
}
