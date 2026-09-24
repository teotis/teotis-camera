package com.opencamera.app.camera.live

import android.media.MediaPlayer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MotionPhotoPlaybackControllerTest {

    private val createdPlayers = mutableListOf<RecordingPlayer>()
    private lateinit var controller: MotionPhotoPlaybackController

    @Before
    fun setUp() {
        createdPlayers.clear()
        controller = MotionPhotoPlaybackController(
            context = RuntimeEnvironment.getApplication(),
            playerFactory = {
                RecordingPlayer().also { createdPlayers += it }
            }
        )
    }

    @Test
    fun `embedded playback sets datasource with offset and length`() {
        val tempDir = Files.createTempDirectory("playback-ctrl").toFile()
        try {
            val jpeg = makeMinimalJpeg()
            val mp4 = makeFakeMp4()
            val motionPhoto = com.opencamera.core.media.MotionPhotoJpegContainer.write(
                jpegBytes = jpeg,
                motionBytes = mp4,
                spec = com.opencamera.core.media.MotionPhotoContainerSpec(
                    motionLengthBytes = mp4.size.toLong()
                )
            )
            val file = File(tempDir, "capture_MP.jpg").apply { writeBytes(motionPhoto) }
            val segment = MotionPhotoPlaybackResolver.resolveEmbedded(file.absolutePath).getOrThrow()

            val result = controller.play(segment, surfaceHolder = null)

            assertTrue("play must succeed", result.isSuccess)
            assertEquals(1, createdPlayers.size)
            val player = createdPlayers[0]
            assertTrue(player.started)
            assertEquals(segment.offsetBytes, player.embeddedOffset)
            assertEquals(segment.lengthBytes, player.embeddedLength)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `embedded playback with nonexistent file fails and releases player`() {
        val segment = MotionPlaybackSegment(
            kind = MotionPlaybackKind.EMBEDDED_MP4,
            location = "/nonexistent/missing.jpg",
            offsetBytes = 100,
            lengthBytes = 50
        )
        val result = controller.play(segment, surfaceHolder = null)

        assertTrue(result.isFailure)
        assertTrue("failed player must be released", createdPlayers.first().released)
        assertFalse(controller.isPlaying())
    }

    @Test
    fun `stop releases the active player`() {
        val tempDir = Files.createTempDirectory("playback-stop").toFile()
        try {
            val mp4 = makeFakeMp4()
            val motionPhoto = com.opencamera.core.media.MotionPhotoJpegContainer.write(
                jpegBytes = makeMinimalJpeg(),
                motionBytes = mp4,
                spec = com.opencamera.core.media.MotionPhotoContainerSpec(motionLengthBytes = mp4.size.toLong())
            )
            val file = File(tempDir, "capture_MP.jpg").apply { writeBytes(motionPhoto) }
            val segment = MotionPhotoPlaybackResolver.resolveEmbedded(file.absolutePath).getOrThrow()

            controller.play(segment, surfaceHolder = null).getOrThrow()
            controller.stop()

            assertTrue(createdPlayers.first().released)
            assertFalse(controller.isPlaying())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private class RecordingPlayer : MediaPlayer() {
        var started = false
        var released = false
        var embeddedOffset: Long? = null
        var embeddedLength: Long? = null

        override fun setDataSource(fd: java.io.FileDescriptor, offset: Long, length: Long) {
            embeddedOffset = offset
            embeddedLength = length
        }

        override fun prepare() = Unit

        override fun start() {
            started = true
        }

        override fun release() {
            released = true
        }

        override fun isPlaying(): Boolean = started && !released
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
