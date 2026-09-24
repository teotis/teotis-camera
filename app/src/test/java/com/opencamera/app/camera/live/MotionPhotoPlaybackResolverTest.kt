package com.opencamera.app.camera.live

import com.opencamera.core.media.LiveBundleStatus
import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MotionPhotoContainerSpec
import com.opencamera.core.media.MotionPhotoJpegContainer
import com.opencamera.core.settings.LiveSaveFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MotionPhotoPlaybackResolverTest {

    @Test
    fun `resolve embedded reads xmp offset and motion length from file`() {
        val tempDir = Files.createTempDirectory("playback-resolve").toFile()
        try {
            val jpeg = makeMinimalJpeg()
            val mp4 = makeFakeMp4()
            val motionPhoto = MotionPhotoJpegContainer.write(
                jpegBytes = jpeg,
                motionBytes = mp4,
                spec = MotionPhotoContainerSpec(
                    motionLengthBytes = mp4.size.toLong(),
                    presentationTimestampUs = 7L
                )
            )
            val file = File(tempDir, "capture_MP.jpg").apply { writeBytes(motionPhoto) }

            val segment = MotionPhotoPlaybackResolver.resolveEmbedded(file.absolutePath).getOrThrow()

            assertEquals(MotionPlaybackKind.EMBEDDED_MP4, segment.kind)
            assertEquals(motionPhoto.size - mp4.size, segment.offsetBytes!!.toInt())
            assertEquals(mp4.size.toLong(), segment.lengthBytes)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve embedded fails for plain jpeg without motion xmp`() {
        val tempDir = Files.createTempDirectory("playback-plain").toFile()
        try {
            val file = File(tempDir, "plain.jpg").apply { writeBytes(makeMinimalJpeg()) }
            val result = MotionPhotoPlaybackResolver.resolveEmbedded(file.absolutePath)
            assertTrue(result.isFailure)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `resolve embedded fails for missing file`() {
        val result = MotionPhotoPlaybackResolver.resolveEmbedded("/nonexistent/missing.jpg")
        assertTrue(result.isFailure)
    }

    @Test
    fun `resolve chooses sidecar content uri for mp4 sidecar format`() {
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/OpenCamera/x.jpg",
            motionPath = "Pictures/OpenCamera/x.live.mp4",
            sidecarPath = "Pictures/OpenCamera/x.live.json",
            motionDurationMillis = 1500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            bundleStatus = LiveBundleStatus.COMPLETE,
            motionHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/x.live.mp4",
                contentUri = "content://media/external/video/media/7"
            )
        )
        val segment = MotionPhotoPlaybackResolver.resolve(
            bundle = bundle,
            saveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR,
            pipelineNotes = listOf("motion-photo:sidecar-mp4=mediastore-inserted"),
            readFileBytes = { _, _ -> Result.failure(IllegalStateException("not used")) },
            readContentHead = { _, _, _ -> Result.failure(IllegalStateException("not used")) },
            fileLength = { Result.failure(IllegalStateException("not used")) }
        ).getOrThrow()

        assertEquals(MotionPlaybackKind.SIDECAR_MP4, segment.kind)
        assertEquals("content://media/external/video/media/7", segment.location)
    }

    @Test
    fun `resolve embedded refuses when motion photo not materialized`() {
        val bundle = LivePhotoBundle(
            stillPath = "/tmp/capture.jpg",
            motionPath = "/tmp/capture.live.mp4",
            sidecarPath = "/tmp/capture.live.json",
            motionDurationMillis = 1500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK
        )
        val result = MotionPhotoPlaybackResolver.resolve(
            bundle = bundle,
            saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
            pipelineNotes = listOf("live-motion:status=missing"),
            readFileBytes = { _, _ -> Result.failure(IllegalStateException("must not read")) },
            readContentHead = { _, _, _ -> Result.failure(IllegalStateException("must not read")) },
            fileLength = { Result.failure(IllegalStateException("must not read")) }
        )
        assertTrue(result.isFailure)
    }

    @Test
    fun `resolve embedded works through injected lambdas for content uri`() {
        val jpeg = makeMinimalJpeg()
        val mp4 = makeFakeMp4()
        val motionPhoto = MotionPhotoJpegContainer.write(
            jpegBytes = jpeg,
            motionBytes = mp4,
            spec = MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
        )

        val segment = MotionPhotoPlaybackResolver.resolveEmbedded(
            location = "content://media/external/images/media/42",
            readFileBytes = { _, maxBytes -> Result.success(motionPhoto.take(maxBytes).toByteArray()) },
            fileLength = { Result.success(motionPhoto.size.toLong()) }
        ).getOrThrow()

        assertNotNull(segment)
        assertEquals(motionPhoto.size - mp4.size, segment.offsetBytes!!.toInt())
        assertEquals(mp4.size.toLong(), segment.lengthBytes)
    }

    private fun makeMinimalJpeg(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(),
            0xFF.toByte(), 0xE0.toByte(),
            0x00, 0x10,
            0x4A, 0x46, 0x49, 0x46, 0x00,
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xDA.toByte(),
            0x00, 0x08,
            0x01, 0x01, 0x00, 0x00, 0x3F, 0x00, 0x7F, 0x00,
            0xFF.toByte(), 0xD9.toByte()
        )
    }

    private fun makeFakeMp4(): ByteArray {
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            0x66, 0x74, 0x79, 0x70,
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31
        )
    }
}
