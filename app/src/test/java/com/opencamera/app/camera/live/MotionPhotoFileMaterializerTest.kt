package com.opencamera.app.camera.live

import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MotionPhotoContainerSpec
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.nio.file.Files

class MotionPhotoFileMaterializerTest {

    @Test
    fun `materialize creates combined motion photo file`() {
        val tempDir = Files.createTempDirectory("motion-photo-test").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply {
                writeBytes(makeMinimalJpeg())
            }
            val motionFile = File(tempDir, "capture.mp4").apply {
                writeBytes(makeFakeMp4())
            }
            val outputFile = File(tempDir, "capture_MP.jpg")

            val materializer = MotionPhotoFileMaterializer()
            val result = materializer.materialize(
                stillPath = stillFile.absolutePath,
                motionPath = motionFile.absolutePath,
                outputPath = outputFile.absolutePath,
                spec = MotionPhotoContainerSpec(motionLengthBytes = motionFile.length())
            )

            assertTrue(result.isSuccess)
            assertTrue(outputFile.exists())
            assertTrue(outputFile.length() > stillFile.length())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `materialize does not delete source still on failure`() {
        val tempDir = Files.createTempDirectory("motion-photo-fail").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply {
                writeBytes(makeMinimalJpeg())
            }
            val motionFile = File(tempDir, "nonexistent.mp4")

            val materializer = MotionPhotoFileMaterializer()
            val result = materializer.materialize(
                stillPath = stillFile.absolutePath,
                motionPath = motionFile.absolutePath,
                outputPath = File(tempDir, "output.jpg").absolutePath,
                spec = MotionPhotoContainerSpec(motionLengthBytes = 100)
            )

            assertTrue(result.isFailure)
            assertTrue(stillFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `materialize cleans up temp files on success`() {
        val tempDir = Files.createTempDirectory("motion-photo-cleanup").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply {
                writeBytes(makeMinimalJpeg())
            }
            val motionFile = File(tempDir, "capture.mp4").apply {
                writeBytes(makeFakeMp4())
            }
            val outputFile = File(tempDir, "capture_MP.jpg")

            val materializer = MotionPhotoFileMaterializer()
            val result = materializer.materialize(
                stillPath = stillFile.absolutePath,
                motionPath = motionFile.absolutePath,
                outputPath = outputFile.absolutePath,
                spec = MotionPhotoContainerSpec(motionLengthBytes = motionFile.length()),
                cleanupTempMotion = true
            )

            assertTrue(result.isSuccess)
            assertFalse(motionFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun makeMinimalJpeg(): ByteArray {
        return byteArrayOf(
            0xFF.toByte(), 0xD8.toByte(), // SOI
            0xFF.toByte(), 0xE0.toByte(), // APP0 marker
            0x00, 0x10, // Length
            0x4A, 0x46, 0x49, 0x46, 0x00, // "JFIF\0"
            0x01, 0x01, 0x00, 0x00, 0x01, 0x00, 0x01, 0x00, 0x00,
            0xFF.toByte(), 0xD9.toByte()  // EOI
        )
    }

    private fun makeFakeMp4(): ByteArray {
        return byteArrayOf(
            0x00, 0x00, 0x00, 0x1C,
            0x66, 0x74, 0x79, 0x70, // "ftyp"
            0x69, 0x73, 0x6F, 0x6D,
            0x00, 0x00, 0x02, 0x00,
            0x69, 0x73, 0x6F, 0x6D,
            0x69, 0x73, 0x6F, 0x32,
            0x61, 0x76, 0x63, 0x31
        )
    }
}
