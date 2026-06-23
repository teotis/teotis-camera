package com.opencamera.app.camera.live

import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.settings.LiveSaveFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LivePhotoStatusProjectionTest {

    private val defaultBundle = LivePhotoBundle(
        stillPath = "/sdcard/DCIM/photo.jpg",
        motionPath = "/sdcard/DCIM/photo.mp4",
        sidecarPath = "/sdcard/DCIM/photo.json",
        motionDurationMillis = 1500,
        motionMimeType = "video/mp4",
        sidecarMimeType = "application/vnd.opencamera.live+json",
        bundleStatus = LiveBundleStatus.COMPLETE
    )

    // --- Materialized ---

    @Test
    fun `materialized when live-motion status is materialized`() {
        val notes = listOf(
            "live-export:format=google-motion-photo-jpeg",
            "live-format:intended=google-motion-photo-jpeg",
            "live-format:actual=google-motion-photo-jpeg",
            "live-motion:status=materialized"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assert(status is LivePhotoStatus.Materialized)
        val mat = status as LivePhotoStatus.Materialized
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, mat.format)
    }

    @Test
    fun `materialized uses actual format from diagnostics`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=motion-mp4-sidecar",
            "live-motion:status=materialized"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Materialized)
        val mat = status as LivePhotoStatus.Materialized
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, mat.format)
    }

    // --- Degraded ---

    @Test
    fun `degraded when live-motion status is failed`() {
        val notes = listOf(
            "live-format:intended=google-motion-photo-jpeg",
            "live-format:actual=still-jpeg-only",
            "live-motion:status=failed"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals(LiveSaveFormat.STILL_JPEG_ONLY, deg.format)
        assertEquals("motion-segment-failed", deg.reason)
    }

    @Test
    fun `degraded when live-motion status is missing`() {
        val notes = listOf(
            "live-format:intended=google-motion-photo-jpeg",
            "live-format:actual=still-jpeg-only",
            "live-motion:status=missing"
        )
        val status = LivePhotoStatusProjection.project(notes, null, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals("motion-segment-missing", deg.reason)
    }

    @Test
    fun `degraded sidecar failed when live-motion status is degraded`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=motion-mp4-sidecar",
            "live-motion:status=degraded"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals("sidecar-failed", deg.reason)
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, deg.format)
    }

    // --- NotRequested ---

    @Test
    fun `not requested when save format is still jpeg only`() {
        val notes = listOf("live-export:format=still-jpeg-only")
        val status = LivePhotoStatusProjection.project(notes, null, LiveSaveFormat.STILL_JPEG_ONLY)
        assertEquals(LivePhotoStatus.NotRequested, status)
    }

    @Test
    fun `not requested ignores any diagnostics when format is still jpeg only`() {
        val notes = listOf("live-motion:status=materialized")
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.STILL_JPEG_ONLY)
        assertEquals(LivePhotoStatus.NotRequested, status)
    }

    // --- Unknown / Fallback ---

    @Test
    fun `unknown when no diagnostics and bundle is null`() {
        val status = LivePhotoStatusProjection.project(emptyList(), null, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assertEquals(LivePhotoStatus.Unknown, status)
    }

    @Test
    fun `materialized when no diagnostics but bundle is complete`() {
        val bundle = defaultBundle.copy(bundleStatus = LiveBundleStatus.COMPLETE)
        val status = LivePhotoStatusProjection.project(emptyList(), bundle, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assert(status is LivePhotoStatus.Materialized)
        assertEquals(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG, (status as LivePhotoStatus.Materialized).format)
    }

    @Test
    fun `degraded when no diagnostics and bundle is still only fallback`() {
        val bundle = defaultBundle.copy(bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK)
        val status = LivePhotoStatusProjection.project(emptyList(), bundle, LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        assert(status is LivePhotoStatus.Degraded)
        assertEquals("unknown-reason", (status as LivePhotoStatus.Degraded).reason)
    }

    // --- Status text ---

    @Test
    fun `statusText returns null for not requested`() {
        assertNull(LivePhotoStatusProjection.statusText(LivePhotoStatus.NotRequested))
    }

    @Test
    fun `statusText includes format label for materialized`() {
        val status = LivePhotoStatus.Materialized(LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG)
        val text = LivePhotoStatusProjection.statusText(status)
        assert(text!!.contains("Motion Photo"))
        assert(text.contains("实况已生成"))
    }

    @Test
    fun `statusText includes reason for degraded`() {
        val status = LivePhotoStatus.Degraded(LiveSaveFormat.MOTION_MP4_SIDECAR, "motion-segment-failed")
        val text = LivePhotoStatusProjection.statusText(status)
        assert(text!!.contains("动态片段编码失败"))
        assert(text.contains("实况降级"))
    }

    @Test
    fun `statusText shows unknown message`() {
        val text = LivePhotoStatusProjection.statusText(LivePhotoStatus.Unknown)
        assert(text!!.contains("实况状态未知"))
    }

    @Test
    fun `statusText uses custom format label when provided`() {
        val status = LivePhotoStatus.Materialized(LiveSaveFormat.MOTION_MP4_SIDECAR)
        val text = LivePhotoStatusProjection.statusText(status, formatLabel = "MP4 伴随文件")
        assert(text!!.contains("MP4 伴随文件"))
    }

    // --- MP4 Sidecar priority diagnostics ---

    @Test
    fun `mp4 sidecar mediastore-inserted projects to Materialized`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=motion-mp4-sidecar",
            "live-motion:status=materialized",
            "motion-photo:sidecar-mp4=mediastore-inserted",
            "motion-photo:sidecar-mp4=verify:capture.live.mp4|Pictures/OpenCamera|video/mp4|1024|1500|0"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Materialized)
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, (status as LivePhotoStatus.Materialized).format)
    }

    @Test
    fun `mp4 sidecar mediastore-failed projects to Degraded with sidecar-mp4-not-inserted`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=still-jpeg-only",
            "live-motion:status=failed",
            "motion-photo:sidecar-mp4=mediastore-failed:Failed to insert MP4 sidecar into MediaStore"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, deg.format)
        assertEquals("sidecar-mp4-not-inserted", deg.reason)
    }

    @Test
    fun `mp4 sidecar mediastore-skipped projects to Degraded with sidecar-mp4-not-inserted`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=still-jpeg-only",
            "live-motion:status=failed",
            "motion-photo:sidecar-mp4=mediastore-skipped"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals(LiveSaveFormat.MOTION_MP4_SIDECAR, deg.format)
        assertEquals("sidecar-mp4-not-inserted", deg.reason)
    }

    @Test
    fun `mp4 sidecar statusText for sidecar-mp4-not-inserted`() {
        val status = LivePhotoStatus.Degraded(LiveSaveFormat.MOTION_MP4_SIDECAR, "sidecar-mp4-not-inserted")
        val text = LivePhotoStatusProjection.statusText(status)
        assert(text!!.contains("附属 MP4 写入相册失败"))
        assert(text.contains("实况降级为静态照片"))
    }

    @Test
    fun `mp4 sidecar without sidecar diagnostic falls back to motion status`() {
        val notes = listOf(
            "live-export:format=motion-mp4-sidecar",
            "live-format:intended=motion-mp4-sidecar",
            "live-format:actual=still-jpeg-only",
            "live-motion:status=failed"
        )
        val status = LivePhotoStatusProjection.project(notes, defaultBundle, LiveSaveFormat.MOTION_MP4_SIDECAR)
        assert(status is LivePhotoStatus.Degraded)
        val deg = status as LivePhotoStatus.Degraded
        assertEquals("motion-segment-failed", deg.reason)
    }
}
