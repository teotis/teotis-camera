package com.opencamera.app.camera

import com.opencamera.core.media.LivePhotoBundle
import com.opencamera.core.media.MediaOutputHandle
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraXCaptureAdapterLivePhotoTest {
    @Test
    fun `materialize live photo sidecar writes thumbnail aware metadata`() {
        val tempDir = Files.createTempDirectory("live-photo-sidecar").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                thumbnailPath = File(tempDir, "capture.thumb.jpg").absolutePath,
                motionDurationMillis = 1_800,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            materializeLivePhotoSidecar(bundle)

            val payload = sidecarFile.readText()
            assertTrue(sidecarFile.exists())
            assertTrue(payload.contains("\"stillPath\": \"${bundle.stillPath}\""))
            assertTrue(payload.contains("\"motionPath\": \"${bundle.motionPath}\""))
            assertTrue(payload.contains("\"thumbnailPath\": \"${bundle.thumbnailPath}\""))
            assertTrue(payload.contains("\"motionDurationMillis\": 1800"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `materialize live photo sidecar writes content uri payload when handle is provided`() {
        val writes = mutableMapOf<String, String>()
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            motionPath = "Pictures/OpenCamera/capture.live.mp4",
            sidecarPath = "Pictures/OpenCamera/capture.live.json",
            thumbnailPath = "Pictures/OpenCamera/capture.jpg",
            motionDurationMillis = 1_800,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            sidecarHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.live.json",
                contentUri = "content://media/external/file/99"
            )
        )

        materializeLivePhotoSidecar(bundle) { contentUri, payload ->
            writes[contentUri] = payload
        }

        assertEquals(setOf("content://media/external/file/99"), writes.keys)
        assertTrue(
            writes.getValue("content://media/external/file/99")
                .contains("\"sidecarPath\": \"Pictures/OpenCamera/capture.live.json\"")
        )
    }

    @Test
    fun `cleanup still capture artifacts deletes live bundle outputs and reports deleted content uri`() {
        val tempDir = Files.createTempDirectory("live-photo-cleanup").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply { writeText("still") }
            val motionFile = File(tempDir, "capture.live.mp4").apply { writeText("motion") }
            val sidecarFile = File(tempDir, "capture.live.json").apply { writeText("sidecar") }
            val intermediateFile = File(tempDir, "capture_frame_0.jpg").apply { writeText("temp") }
            val deletedContentUris = mutableListOf<String>()

            val deletedPaths = cleanupStillCaptureArtifacts(
                outputPath = stillFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = stillFile.absolutePath,
                    filePath = stillFile.absolutePath,
                    contentUri = "content://media/external/images/media/42"
                ),
                livePhotoBundle = LivePhotoBundle(
                    stillPath = stillFile.absolutePath,
                    motionPath = motionFile.absolutePath,
                    sidecarPath = sidecarFile.absolutePath,
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/json",
                    sidecarHandle = MediaOutputHandle(
                        displayPath = sidecarFile.absolutePath,
                        filePath = sidecarFile.absolutePath,
                        contentUri = "content://media/external/files/7"
                    )
                ),
                intermediateOutputPaths = listOf(
                    intermediateFile.absolutePath,
                    motionFile.absolutePath
                ),
                deleteContentUri = deletedContentUris::add
            )

            assertEquals(
                listOf(
                    "content://media/external/images/media/42",
                    "content://media/external/files/7"
                ),
                deletedContentUris
            )
            assertEquals(
                setOf(
                    stillFile.absolutePath,
                    motionFile.absolutePath,
                    sidecarFile.absolutePath,
                    intermediateFile.absolutePath
                ),
                deletedPaths.toSet()
            )
            assertFalse(stillFile.exists())
            assertFalse(motionFile.exists())
            assertFalse(sidecarFile.exists())
            assertFalse(intermediateFile.exists())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `still capture cleanup paths ignore duplicates and non absolute paths`() {
        val paths = stillCaptureCleanupPaths(
            outputPath = "Pictures/OpenCamera/capture.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.jpg",
                filePath = "/tmp/capture.jpg"
            ),
            livePhotoBundle = LivePhotoBundle(
                stillPath = "/tmp/capture.jpg",
                motionPath = "/tmp/capture.live.mp4",
                sidecarPath = "Pictures/OpenCamera/capture.live.json",
                thumbnailPath = "/tmp/capture.thumb.jpg",
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/json"
            ),
            intermediateOutputPaths = listOf(
                "/tmp/capture.live.mp4",
                "Pictures/OpenCamera/temp.jpg"
            )
        )

        assertEquals(
            listOf(
                "/tmp/capture.jpg",
                "/tmp/capture.live.mp4",
                "/tmp/capture.thumb.jpg"
            ),
            paths
        )
        assertTrue(paths.all { File(it).isAbsolute })
    }

    @Test
    fun `still capture cleanup content uris include live bundle handles without duplicates`() {
        val uris = stillCaptureCleanupContentUris(
            outputHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.jpg",
                contentUri = "content://media/external/images/media/42"
            ),
            livePhotoBundle = LivePhotoBundle(
                stillPath = "Pictures/OpenCamera/capture.jpg",
                motionPath = "Pictures/OpenCamera/capture.live.mp4",
                sidecarPath = "Pictures/OpenCamera/capture.live.json",
                thumbnailPath = "Pictures/OpenCamera/capture.jpg",
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/json",
                motionHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/capture.live.mp4",
                    contentUri = "content://media/external/video/media/77"
                ),
                sidecarHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/capture.live.json",
                    contentUri = "content://media/external/files/99"
                ),
                thumbnailHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/capture.jpg",
                    contentUri = "content://media/external/images/media/42"
                )
            )
        )

        assertEquals(
            listOf(
                "content://media/external/images/media/42",
                "content://media/external/video/media/77",
                "content://media/external/files/99"
            ),
            uris
        )
    }
}
