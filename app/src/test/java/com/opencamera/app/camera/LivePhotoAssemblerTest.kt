package com.opencamera.app.camera

import com.opencamera.core.media.*
import com.opencamera.core.settings.LiveSaveFormat
import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class LivePhotoAssemblerTest {

    private fun fakeCapturedResult(
        outputPath: String,
        outputHandle: MediaOutputHandle = MediaOutputHandle(displayPath = outputPath, filePath = outputPath)
    ): CapturedPhotoResult = CapturedPhotoResult(
        outputPath = outputPath,
        outputHandle = outputHandle
    )

    private fun makeMotionSource(
        source: LiveMotionSource = LiveMotionSource.METADATA_ONLY,
        frames: List<FrameDescriptor> = emptyList()
    ): LiveMotionSourceResult = LiveMotionSourceResult(
        source = source,
        selectedFrameSet = SelectedFrameSet(
            frames = frames,
            preShutterCount = frames.size,
            postShutterCount = 0,
            coveredPreShutterMillis = 0,
            coveredPostShutterMillis = 0,
            diagnostics = emptyList()
        ),
        ringBufferDepthMillis = if (source == LiveMotionSource.PREVIEW_RING_BUFFER) 1_500 else 0,
        postShutterBudgetMillis = if (source == LiveMotionSource.PREVIEW_RING_BUFFER) 300 else 0,
        diagnostics = listOf("live:source=${source.name.lowercase()}")
    )

    private fun makeFrameDescriptor(
        frameId: String = "f1",
        timestampNanos: Long = 2_000_000_000L
    ) = FrameDescriptor(
        frameId = frameId,
        source = FrameSourceKind.PREVIEW_ANALYSIS,
        timestampNanos = timestampNanos,
        width = 640,
        height = 480,
        rotationDegrees = 0,
        payloadAccess = FramePayloadAccess.METADATA_ONLY,
        lensFacingTag = "BACK",
        zoomRatio = 1.0f
    )

    @Test
    fun `JPEG only format returns null bundle`() {
        val tempDir = Files.createTempDirectory("assembler-jpeg-only").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.STILL_JPEG_ONLY,
                motionSourceResult = makeMotionSource(),
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { Result.success(it) },
                writeContentUriPayload = { _, _ -> }
            )

            assertNull(outcome.livePhotoBundle)
            assertTrue(outcome.diagnostics.any { it.contains("live-export:format=still-jpeg-only") })
            assertTrue(outcome.diagnostics.any { it.contains("live-export:share-target=still") })
            assertTrue(outcome.diagnostics.any { it.contains("live-export:fallback=disabled-by-format") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `JPEG only format with metadata only source returns null bundle`() {
        val tempDir = Files.createTempDirectory("assembler-jpeg-meta").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.STILL_JPEG_ONLY,
                motionSourceResult = makeMotionSource(source = LiveMotionSource.METADATA_ONLY),
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { error("must not be called") },
                writeContentUriPayload = { _, _ -> }
            )

            assertNull(outcome.livePhotoBundle)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `google motion photo materialization success returns google container diagnostics`() {
        val tempDir = Files.createTempDirectory("assembler-gmp").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = motionSource,
                prepareMotionSegment = { frames, outputPath ->
                    assertEquals(listOf(frame), frames)
                    Result.success(outputPath)
                },
                materializeContainer = { motionPath ->
                    assertEquals(bundle.motionPath, motionPath)
                    Result.success(stillPath.replace(".jpg", "_MP.jpg"))
                },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertTrue(outcome.diagnostics.contains("motion-photo:motion-segment=materialized"))
            assertTrue(outcome.diagnostics.contains("motion-photo:container=google-jpeg"))
            assertTrue(outcome.diagnostics.contains("motion-photo:xmp=present"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `google motion photo container failure degrades to still only fallback`() {
        val tempDir = Files.createTempDirectory("assembler-gmp-fail").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = motionSource,
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { Result.failure(IllegalArgumentException("Motion file does not exist")) },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, outcome.livePhotoBundle!!.bundleStatus)
            assertFalse(outcome.diagnostics.contains("motion-photo:container=google-jpeg"))
            assertTrue(outcome.diagnostics.any { it.startsWith("motion-photo:container=failed:") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `mp4 sidecar materialization success returns materialized diagnostics`() {
        val tempDir = Files.createTempDirectory("assembler-mp4").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val motionPath = File(tempDir, "capture.live.mp4").absolutePath
            // Create motion file so bytes diagnostic is added
            File(motionPath).writeText("fake motion data")
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = motionPath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR,
                motionSourceResult = motionSource,
                prepareMotionSegment = { frames, motionPath ->
                    Result.success(motionPath)
                },
                materializeContainer = { error("must not be called for MP4 sidecar") },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertTrue(outcome.diagnostics.contains("motion-photo:motion-segment=materialized"))
            assertTrue(outcome.diagnostics.any { it.startsWith("motion-photo:appended-mp4-bytes=") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `mp4 sidecar with empty frames falls back to still only`() {
        val tempDir = Files.createTempDirectory("assembler-mp4-empty").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR,
                motionSourceResult = makeMotionSource(source = LiveMotionSource.METADATA_ONLY),
                prepareMotionSegment = { _, _ -> Result.success("") },
                materializeContainer = { error("must not be called") },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, outcome.livePhotoBundle!!.bundleStatus)
            assertTrue(outcome.diagnostics.contains("motion-photo:motion-segment=unavailable"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `mp4 sidecar motion segment failure degrades to still only fallback`() {
        val tempDir = Files.createTempDirectory("assembler-mp4-segfail").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR,
                motionSourceResult = motionSource,
                prepareMotionSegment = { _, _ -> Result.failure(IllegalStateException("no yuv payload")) },
                materializeContainer = { error("must not be called") },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, outcome.livePhotoBundle!!.bundleStatus)
            assertTrue(outcome.diagnostics.any { it.startsWith("motion-photo:motion-segment=failed:") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar write failure nulls out live photo bundle`() {
        val tempDir = Files.createTempDirectory("assembler-sidecar-fail").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            // Use content URI sidecar handle so writeContentUriPayload is called
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = "Pictures/OpenCamera/capture.live.json",
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                sidecarHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/capture.live.json",
                    contentUri = "content://media/external/file/99"
                )
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val sidecarWriteError = RuntimeException("Sidecar write failed")
            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = motionSource,
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { Result.success(stillPath) },
                writeContentUriPayload = { _, _ -> throw sidecarWriteError }
            )

            // Sidecar failure is non-fatal: still photo was already saved
            assertNull(outcome.livePhotoBundle)
            assertTrue(outcome.diagnostics.any { it.contains("device:live-sidecar=failed:") })
            assertTrue(outcome.diagnostics.contains("device:live-photo=still-only-fallback"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar success with absolute path marks materialized`() {
        val tempDir = Files.createTempDirectory("assembler-sidecar-ok").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = makeMotionSource(source = LiveMotionSource.METADATA_ONLY),
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { Result.success(stillPath) },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
            assertTrue(outcome.diagnostics.contains("device:live-sidecar=materialized"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar with relative path marks planned`() {
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            motionPath = "Pictures/OpenCamera/capture.live.mp4",
            sidecarPath = "Pictures/OpenCamera/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            sidecarHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.live.json",
                contentUri = "content://media/external/file/99"
            )
        )
        val result = fakeCapturedResult(
            outputPath = "Pictures/OpenCamera/capture.jpg",
            outputHandle = MediaOutputHandle(displayPath = "Pictures/OpenCamera/capture.jpg")
        )

        val outcome = LivePhotoAssembler.assembleLivePhoto(
            capturedResult = result,
            livePhotoBundle = bundle,
            saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
            motionSourceResult = makeMotionSource(source = LiveMotionSource.METADATA_ONLY),
            prepareMotionSegment = { _, p -> Result.success(p) },
            materializeContainer = { Result.success("Pictures/OpenCamera/capture_MP.jpg") },
            writeContentUriPayload = { _, _ -> }
        )

        assertNotNull(outcome.livePhotoBundle)
        assertTrue(outcome.diagnostics.contains("device:live-sidecar=planned"))
    }

    @Test
    fun `cancellation exception is rethrown not swallowed`() {
        val tempDir = Files.createTempDirectory("assembler-cancel").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val result = fakeCapturedResult(stillPath)
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            // CancellationException from prepareMotionSegment is rethrown
            // (it is not caught by the try/catch since it throws before sidecar write)
            var caughtCancellation = false
            try {
                LivePhotoAssembler.assembleLivePhoto(
                    capturedResult = result,
                    livePhotoBundle = bundle,
                    saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                    motionSourceResult = motionSource,
                    prepareMotionSegment = { _, _ ->
                        throw kotlinx.coroutines.CancellationException("user cancelled")
                    },
                    materializeContainer = { error("must not be called") },
                    writeContentUriPayload = { _, _ -> }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                caughtCancellation = true
            }

            assertTrue("CancellationException should be rethrown, not swallowed", caughtCancellation)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `motion photo with prepared motion segment uses prepared path`() {
        val tempDir = Files.createTempDirectory("assembler-prepared-seg").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = motionSource,
                prepareMotionSegment = { _, _ -> Result.success("/tmp/generated.mp4") },
                materializeContainer = { motionPath ->
                    assertEquals("/tmp/generated.mp4", motionPath)
                    Result.success(stillPath.replace(".jpg", "_MP.jpg"))
                },
                writeContentUriPayload = { _, _ -> }
            )

            assertNotNull(outcome.livePhotoBundle)
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `diagnostics include share target and motion status`() {
        val tempDir = Files.createTempDirectory("assembler-diagnostics").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )
            val frame = makeFrameDescriptor()
            val motionSource = makeMotionSource(
                source = LiveMotionSource.PREVIEW_RING_BUFFER,
                frames = listOf(frame)
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.GOOGLE_MOTION_PHOTO_JPEG,
                motionSourceResult = motionSource,
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { Result.success(stillPath.replace(".jpg", "_MP.jpg")) },
                writeContentUriPayload = { _, _ -> }
            )

            assertTrue(outcome.diagnostics.any { it.startsWith("live-export:format=") })
            assertTrue(outcome.diagnostics.contains("device:live-photo=bundle"))
            assertTrue(outcome.diagnostics.any { it.startsWith("live-format:intended=") })
            assertTrue(outcome.diagnostics.any { it.startsWith("live-format:actual=") })
            assertTrue(outcome.diagnostics.any { it.startsWith("live-motion:status=") })
            assertTrue(outcome.diagnostics.contains("gallery-recognition=untested"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `stilled only fallback shows still jpe g only as actual format`() {
        val tempDir = Files.createTempDirectory("assembler-still-actual").toFile()
        try {
            val stillPath = File(tempDir, "capture.jpg").absolutePath
            val result = fakeCapturedResult(stillPath)
            val bundle = LivePhotoBundle(
                stillPath = stillPath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = File(tempDir, "capture.live.json").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            val outcome = LivePhotoAssembler.assembleLivePhoto(
                capturedResult = result,
                livePhotoBundle = bundle,
                saveFormat = LiveSaveFormat.MOTION_MP4_SIDECAR,
                motionSourceResult = makeMotionSource(source = LiveMotionSource.METADATA_ONLY),
                prepareMotionSegment = { _, p -> Result.success(p) },
                materializeContainer = { error("must not be called") },
                writeContentUriPayload = { _, _ -> }
            )

            assertTrue(outcome.diagnostics.any {
                it == "live-format:actual=still-jpeg-only"
            })
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
