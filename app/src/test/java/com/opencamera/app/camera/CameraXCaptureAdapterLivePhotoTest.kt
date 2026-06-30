package com.opencamera.app.camera

import com.opencamera.app.camera.live.FakeLivePreviewFrameSource
import com.opencamera.core.media.*
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

    @Test
    fun `materialize live photo sidecar writes to filePath handle when no contentUri`() {
        val tempDir = Files.createTempDirectory("live-fallback").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                thumbnailPath = File(tempDir, "capture.jpg").absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                sidecarHandle = MediaOutputHandle(
                    displayPath = sidecarFile.absolutePath,
                    filePath = sidecarFile.absolutePath
                )
            )

            materializeLivePhotoSidecar(bundle)

            assertTrue(sidecarFile.exists())
            val payload = sidecarFile.readText()
            assertTrue(payload.contains("\"stillPath\""))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup when primary still missing still deletes motion and sidecar`() {
        val tempDir = Files.createTempDirectory("live-cleanup-missing-still").toFile()
        try {
            val motionFile = File(tempDir, "capture.live.mp4").apply { writeText("motion") }
            val sidecarFile = File(tempDir, "capture.live.json").apply { writeText("sidecar") }

            val deletedPaths = cleanupStillCaptureArtifacts(
                outputPath = File(tempDir, "nonexistent.jpg").absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = File(tempDir, "nonexistent.jpg").absolutePath,
                    filePath = File(tempDir, "nonexistent.jpg").absolutePath
                ),
                livePhotoBundle = LivePhotoBundle(
                    stillPath = File(tempDir, "nonexistent.jpg").absolutePath,
                    motionPath = motionFile.absolutePath,
                    sidecarPath = sidecarFile.absolutePath,
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/json"
                ),
                intermediateOutputPaths = emptyList(),
                deleteContentUri = {}
            )

            assertFalse(motionFile.exists())
            assertFalse(sidecarFile.exists())
            assertTrue(deletedPaths.contains(motionFile.absolutePath))
            assertTrue(deletedPaths.contains(sidecarFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup when sidecar missing still deletes still and motion`() {
        val tempDir = Files.createTempDirectory("live-cleanup-missing-sidecar").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply { writeText("still") }
            val motionFile = File(tempDir, "capture.live.mp4").apply { writeText("motion") }

            val deletedPaths = cleanupStillCaptureArtifacts(
                outputPath = stillFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = stillFile.absolutePath,
                    filePath = stillFile.absolutePath
                ),
                livePhotoBundle = LivePhotoBundle(
                    stillPath = stillFile.absolutePath,
                    motionPath = motionFile.absolutePath,
                    sidecarPath = File(tempDir, "nonexistent.live.json").absolutePath,
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/json"
                ),
                intermediateOutputPaths = emptyList(),
                deleteContentUri = {}
            )

            assertFalse(stillFile.exists())
            assertFalse(motionFile.exists())
            assertTrue(deletedPaths.contains(stillFile.absolutePath))
            assertTrue(deletedPaths.contains(motionFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup when motion missing still deletes still and sidecar`() {
        val tempDir = Files.createTempDirectory("live-cleanup-missing-motion").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply { writeText("still") }
            val sidecarFile = File(tempDir, "capture.live.json").apply { writeText("sidecar") }

            val deletedPaths = cleanupStillCaptureArtifacts(
                outputPath = stillFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = stillFile.absolutePath,
                    filePath = stillFile.absolutePath
                ),
                livePhotoBundle = LivePhotoBundle(
                    stillPath = stillFile.absolutePath,
                    motionPath = File(tempDir, "nonexistent.live.mp4").absolutePath,
                    sidecarPath = sidecarFile.absolutePath,
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/json"
                ),
                intermediateOutputPaths = emptyList(),
                deleteContentUri = {}
            )

            assertFalse(stillFile.exists())
            assertFalse(sidecarFile.exists())
            assertTrue(deletedPaths.contains(stillFile.absolutePath))
            assertTrue(deletedPaths.contains(sidecarFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `cleanup with null live photo bundle handles gracefully`() {
        val tempDir = Files.createTempDirectory("live-cleanup-null-bundle").toFile()
        try {
            val stillFile = File(tempDir, "capture.jpg").apply { writeText("still") }
            val intermediateFile = File(tempDir, "frame_0.jpg").apply { writeText("temp") }

            val deletedPaths = cleanupStillCaptureArtifacts(
                outputPath = stillFile.absolutePath,
                outputHandle = MediaOutputHandle(
                    displayPath = stillFile.absolutePath,
                    filePath = stillFile.absolutePath
                ),
                livePhotoBundle = null,
                intermediateOutputPaths = listOf(intermediateFile.absolutePath),
                deleteContentUri = {}
            )

            assertFalse(stillFile.exists())
            assertFalse(intermediateFile.exists())
            assertTrue(deletedPaths.contains(stillFile.absolutePath))
            assertTrue(deletedPaths.contains(intermediateFile.absolutePath))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar payload includes temporal window and status fields`() {
        val tempDir = Files.createTempDirectory("live-sidecar-temporal").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                bundleStatus = LiveBundleStatus.COMPLETE,
                temporalWindow = LiveTemporalWindow(
                    requestedDurationMillis = 1_500,
                    preShutterMillis = 1_200,
                    postShutterMillis = 300,
                    frameCount = 45,
                    source = LiveMotionSource.PREVIEW_RING_BUFFER
                )
            )

            materializeLivePhotoSidecar(bundle)

            val payload = sidecarFile.readText()
            assertTrue(payload.contains("\"bundleStatus\": \"complete\""))
            assertTrue(payload.contains("\"temporalWindow\""))
            assertTrue(payload.contains("\"requestedDurationMillis\": 1500"))
            assertTrue(payload.contains("\"preShutterMillis\": 1200"))
            assertTrue(payload.contains("\"postShutterMillis\": 300"))
            assertTrue(payload.contains("\"frameCount\": 45"))
            assertTrue(payload.contains("\"source\": \"preview_ring_buffer\""))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar payload omits temporal window when null`() {
        val tempDir = Files.createTempDirectory("live-sidecar-no-window").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                bundleStatus = LiveBundleStatus.COMPLETE
            )

            materializeLivePhotoSidecar(bundle)

            val payload = sidecarFile.readText()
            assertTrue(payload.contains("\"bundleStatus\": \"complete\""))
            assertFalse(payload.contains("\"temporalWindow\""))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `precreated media store still handle remains editable when camerax omits saved uri`() {
        val handle = resolvePhotoOutputHandle(
            outputHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.jpg",
                contentUri = "content://media/external/images/media/42"
            ),
            savedUriString = null
        )

        assertEquals("content://media/external/images/media/42", handle.contentUri)
    }

    @Test
    fun `camerax saved uri is merged when media store still handle starts without editable target`() {
        val handle = resolvePhotoOutputHandle(
            outputHandle = MediaOutputHandle(
                displayPath = "Pictures/OpenCamera/capture.jpg"
            ),
            savedUriString = "content://media/external/images/media/43"
        )

        assertEquals("content://media/external/images/media/43", handle.contentUri)
    }

    @Test
    fun `resolveLiveMotionSource returns PREVIEW_RING_BUFFER when frame source has frames`() {
        val frameSource = FakeLivePreviewFrameSource()
        frameSource.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        // Add frames around shutter time
        val shutterNanos = 2_000_000_000L
        frameSource.addFrame(makeDescriptor("f1", timestampNanos = shutterNanos - 500_000_000L))
        frameSource.addFrame(makeDescriptor("f2", timestampNanos = shutterNanos))

        val result = resolveLiveMotionSource(
            frameSource = frameSource,
            shutterTimestampNanos = shutterNanos,
            spec = LivePhotoCaptureSpec()
        )

        assertEquals(LiveMotionSource.PREVIEW_RING_BUFFER, result.source)
        assertTrue(result.selectedFrameSet.frames.isNotEmpty())
        assertTrue(result.ringBufferDepthMillis > 0)
        assertTrue(result.selectedFrameSet.diagnostics.any { it == "frame-source:active=true" })
        assertTrue(result.diagnostics.any { it == "frame-source:active=true" })
    }

    @Test
    fun `resolveLiveMotionSource returns METADATA_ONLY when frame source is not active`() {
        val frameSource = FakeLivePreviewFrameSource()

        val result = resolveLiveMotionSource(
            frameSource = frameSource,
            shutterTimestampNanos = 1_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertEquals(LiveMotionSource.METADATA_ONLY, result.source)
        assertTrue(result.selectedFrameSet.frames.isEmpty())
        assertTrue(result.selectedFrameSet.diagnostics.any { it == "frame-source:active=false" })
        assertTrue(result.selectedFrameSet.diagnostics.any { it.contains("not-active") })
    }

    @Test
    fun `resolveLiveMotionSource inactive reports lastStopReason and lastStartReason diagnostics`() {
        val frameSource = FakeLivePreviewFrameSource()
        frameSource.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        frameSource.stop("unbind")

        val result = resolveLiveMotionSource(
            frameSource = frameSource,
            shutterTimestampNanos = 1_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertEquals(LiveMotionSource.METADATA_ONLY, result.source)
        assertTrue(result.diagnostics.any { it == "frame-source:last-stop-reason=unbind" })
        assertTrue(result.diagnostics.any { it.startsWith("frame-source:last-start-reason=") })
    }

    @Test
    fun `resolveLiveMotionSource returns METADATA_ONLY when no frames near shutter`() {
        val frameSource = FakeLivePreviewFrameSource()
        frameSource.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        // Add frames far from shutter time
        frameSource.addFrame(makeDescriptor("f1", timestampNanos = 100_000_000L))

        val result = resolveLiveMotionSource(
            frameSource = frameSource,
            shutterTimestampNanos = 5_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertEquals(LiveMotionSource.METADATA_ONLY, result.source)
        assertTrue(result.selectedFrameSet.frames.isEmpty())
    }

    @Test
    fun `resolveLiveMotionSource propagates frame-buffer diagnostics when no frames near shutter`() {
        val frameSource = FakeLivePreviewFrameSource()
        frameSource.start(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        // Add a frame far from shutter so selectForLive returns empty with frame-buffer diagnostics
        frameSource.addFrame(makeDescriptor("f1", timestampNanos = 100_000_000L))

        val result = resolveLiveMotionSource(
            frameSource = frameSource,
            shutterTimestampNanos = 5_000_000_000L,
            spec = LivePhotoCaptureSpec()
        )

        assertEquals(LiveMotionSource.METADATA_ONLY, result.source)
        // Top-level diagnostics must surface the truthful fallback reason
        assertTrue(result.diagnostics.any { it == "live:degraded=no-frames-near-shutter" })
        assertTrue(result.diagnostics.any { it == "frame-source:active=true" })
        assertTrue(result.diagnostics.any { it.startsWith("frame-buffer:selected=") })
        assertTrue(result.diagnostics.any { it.startsWith("frame-buffer:window=") })
        assertTrue(result.diagnostics.any { it == "frame-buffer:degraded=no-frames-near-shutter" })
        // lastStartReason should be surfaced so real-device logs can correlate start time with shutter
        assertTrue(result.diagnostics.any { it.startsWith("frame-source:last-start-reason=") })
    }

    @Test
    fun `motion photo materialization failure does not claim google container success`() {
        val shutterNanos = 2_000_000_000L
        val motionSource = LiveMotionSourceResult(
            source = LiveMotionSource.PREVIEW_RING_BUFFER,
            selectedFrameSet = SelectedFrameSet(
                frames = listOf(makeDescriptor("f1", timestampNanos = shutterNanos)),
                preShutterCount = 1,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = listOf("frame-buffer:selected=1")
            ),
            ringBufferDepthMillis = 1_500,
            postShutterBudgetMillis = 300,
            diagnostics = listOf("live:source=preview-ring-buffer")
        )
        val bundle = LivePhotoBundle(
            stillPath = "/tmp/capture.jpg",
            motionPath = "/tmp/missing.live.mp4",
            sidecarPath = "/tmp/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            bundleStatus = LiveBundleStatus.COMPLETE
        )

        val result = materializeMotionPhotoBundleIfPossible(
            bundle = bundle,
            motionSourceResult = motionSource,
            prepareMotionSegment = { _, motionPath -> Result.success(motionPath) },
            materialize = { Result.failure(IllegalArgumentException("Motion file does not exist")) }
        )

        assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, result.bundle.bundleStatus)
        assertFalse(result.diagnostics.contains("motion-photo:container=google-jpeg"))
        assertFalse(result.diagnostics.contains("motion-photo:xmp=present"))
        assertTrue(result.diagnostics.any { it.startsWith("motion-photo:container=failed:") })
    }

    @Test
    fun `motion photo materialization uses prepared preview motion segment`() {
        val shutterNanos = 2_000_000_000L
        val selectedFrame = makeDescriptor("f1", timestampNanos = shutterNanos)
        val motionSource = LiveMotionSourceResult(
            source = LiveMotionSource.PREVIEW_RING_BUFFER,
            selectedFrameSet = SelectedFrameSet(
                frames = listOf(selectedFrame),
                preShutterCount = 1,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = emptyList()
            ),
            ringBufferDepthMillis = 1_500,
            postShutterBudgetMillis = 300,
            diagnostics = emptyList()
        )
        val bundle = LivePhotoBundle(
            stillPath = "/tmp/capture.jpg",
            motionPath = "/tmp/capture.live.mp4",
            sidecarPath = "/tmp/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            bundleStatus = LiveBundleStatus.COMPLETE
        )
        var materializedMotionPath: String? = null

        val result = materializeMotionPhotoBundleIfPossible(
            bundle = bundle,
            motionSourceResult = motionSource,
            prepareMotionSegment = { frames, motionPath ->
                assertEquals(listOf(selectedFrame), frames)
                assertEquals(bundle.motionPath, motionPath)
                Result.success("/tmp/generated-preview.mp4")
            },
            materialize = { motionPath ->
                materializedMotionPath = motionPath
                Result.success("/tmp/capture_MP.jpg")
            }
        )

        assertEquals("/tmp/generated-preview.mp4", materializedMotionPath)
        assertEquals("/tmp/capture_MP.jpg", result.bundle.stillPath)
        assertEquals("/tmp/generated-preview.mp4", result.bundle.motionPath)
        assertTrue(result.diagnostics.contains("motion-photo:motion-segment=materialized"))
        assertTrue(result.diagnostics.contains("motion-photo:container=google-jpeg"))
        assertTrue(result.diagnostics.contains("motion-photo:xmp=present"))
    }

    @Test
    fun `motion segment preparation failure does not invoke google container materializer`() {
        val shutterNanos = 2_000_000_000L
        val motionSource = LiveMotionSourceResult(
            source = LiveMotionSource.PREVIEW_RING_BUFFER,
            selectedFrameSet = SelectedFrameSet(
                frames = listOf(makeDescriptor("f1", timestampNanos = shutterNanos)),
                preShutterCount = 1,
                postShutterCount = 0,
                coveredPreShutterMillis = 0,
                coveredPostShutterMillis = 0,
                diagnostics = emptyList()
            ),
            ringBufferDepthMillis = 1_500,
            postShutterBudgetMillis = 300,
            diagnostics = emptyList()
        )
        val bundle = LivePhotoBundle(
            stillPath = "/tmp/capture.jpg",
            motionPath = "/tmp/capture.live.mp4",
            sidecarPath = "/tmp/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            bundleStatus = LiveBundleStatus.COMPLETE
        )

        val result = materializeMotionPhotoBundleIfPossible(
            bundle = bundle,
            motionSourceResult = motionSource,
            prepareMotionSegment = { _, _ -> Result.failure(IllegalStateException("no yuv payload")) },
            materialize = { error("container materializer must not run without a motion segment") }
        )

        assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, result.bundle.bundleStatus)
        assertTrue(result.diagnostics.any { it.startsWith("motion-photo:motion-segment=failed:") })
        assertFalse(result.diagnostics.contains("motion-photo:container=google-jpeg"))
        assertFalse(result.diagnostics.contains("motion-photo:xmp=present"))
    }

    @Test
    fun `resolveLiveWatermarkOutcome returns STILL_ONLY when still watermark is configured`() {
        val plan = buildShotPlan(
            watermarkText = "PHOTO Auto",
            liveWatermarkBehavior = "follow-frame-luma-and-motion"
        )
        val outcome = resolveLiveWatermarkOutcome(plan)

        assertEquals("follow-frame-luma-and-motion", outcome.requested)
        assertEquals(LiveWatermarkResult.STILL_ONLY, outcome.result)
        assertEquals("motion-burn-in-not-implemented", outcome.degradeReason)
    }

    @Test
    fun `resolveLiveWatermarkOutcome returns null result when no watermark is configured`() {
        val plan = buildShotPlan(watermarkText = null, liveWatermarkBehavior = null)
        val outcome = resolveLiveWatermarkOutcome(plan)

        assertEquals(null, outcome.requested)
        assertEquals(null, outcome.result)
        assertEquals(null, outcome.degradeReason)
    }

    @Test
    fun `resolveLiveWatermarkOutcome returns UNSUPPORTED when behavior is set but no watermark text`() {
        val plan = buildShotPlan(
            watermarkText = null,
            liveWatermarkBehavior = "follow-frame-luma-and-motion"
        )
        val outcome = resolveLiveWatermarkOutcome(plan)

        assertEquals("follow-frame-luma-and-motion", outcome.requested)
        assertEquals(LiveWatermarkResult.UNSUPPORTED, outcome.result)
        assertEquals(null, outcome.degradeReason)
    }

    @Test
    fun `resolveLiveWatermarkOutcome never returns MOTION_BURNED_IN`() {
        val testCases = listOf(
            buildShotPlan(watermarkText = "PHOTO Auto", liveWatermarkBehavior = "follow-frame-luma"),
            buildShotPlan(watermarkText = "PHOTO Flash", liveWatermarkBehavior = "static-overlay"),
            buildShotPlan(watermarkText = null, liveWatermarkBehavior = null),
            buildShotPlan(watermarkText = null, liveWatermarkBehavior = "follow-frame-luma-and-motion")
        )
        for (plan in testCases) {
            val outcome = resolveLiveWatermarkOutcome(plan)
            assertTrue(
                outcome.result != LiveWatermarkResult.MOTION_BURNED_IN,
                "MOTION_BURNED_IN should never be returned but got ${outcome.result}"
            )
        }
    }

    @Test
    fun `sidecar payload includes watermark fields when present`() {
        val tempDir = Files.createTempDirectory("live-watermark-sidecar").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json",
                watermarkRequested = "follow-frame-luma",
                watermarkResult = LiveWatermarkResult.STILL_ONLY,
                watermarkDegradeReason = "motion-burn-in-not-implemented"
            )

            materializeLivePhotoSidecar(bundle)

            val payload = sidecarFile.readText()
            assertTrue(payload.contains("\"watermarkRequested\": \"follow-frame-luma\""))
            assertTrue(payload.contains("\"watermarkResult\": \"still-only\""))
            assertTrue(payload.contains("\"watermarkDegradeReason\": \"motion-burn-in-not-implemented\""))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `sidecar payload omits watermark fields when absent`() {
        val tempDir = Files.createTempDirectory("live-watermark-no-wm").toFile()
        try {
            val sidecarFile = File(tempDir, "capture.live.json")
            val bundle = LivePhotoBundle(
                stillPath = File(tempDir, "capture.jpg").absolutePath,
                motionPath = File(tempDir, "capture.live.mp4").absolutePath,
                sidecarPath = sidecarFile.absolutePath,
                motionDurationMillis = 1_500,
                motionMimeType = "video/mp4",
                sidecarMimeType = "application/vnd.opencamera.live+json"
            )

            materializeLivePhotoSidecar(bundle)

            val payload = sidecarFile.readText()
            assertFalse(payload.contains("watermarkRequested"))
            assertFalse(payload.contains("watermarkResult"))
            assertFalse(payload.contains("watermarkDegradeReason"))
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `temporalNotes includes watermark diagnostics when watermark is present`() {
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            motionPath = "Pictures/OpenCamera/capture.live.mp4",
            sidecarPath = "Pictures/OpenCamera/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json",
            watermarkRequested = "follow-frame-luma-and-motion",
            watermarkResult = LiveWatermarkResult.STILL_ONLY,
            watermarkDegradeReason = "motion-burn-in-not-implemented"
        )

        val notes = bundle.temporalNotes()

        assertTrue(notes.contains("live-watermark:requested=follow-frame-luma-and-motion"))
        assertTrue(notes.contains("live-watermark:actual=still-only"))
        assertTrue(notes.contains("live-watermark:reason=motion-burn-in-not-implemented"))
    }

    @Test
    fun `temporalNotes omits watermark diagnostics when watermark is absent`() {
        val bundle = LivePhotoBundle(
            stillPath = "Pictures/OpenCamera/capture.jpg",
            motionPath = "Pictures/OpenCamera/capture.live.mp4",
            sidecarPath = "Pictures/OpenCamera/capture.live.json",
            motionDurationMillis = 1_500,
            motionMimeType = "video/mp4",
            sidecarMimeType = "application/vnd.opencamera.live+json"
        )

        val notes = bundle.temporalNotes()

        assertFalse(notes.any { it.startsWith("live-watermark:") })
    }

    private fun buildShotPlan(
        watermarkText: String?,
        liveWatermarkBehavior: String?
    ): ShotPlan {
        val customTags = buildMap {
            put("mode", "photo")
            liveWatermarkBehavior?.let { put("liveWatermarkBehavior", it) }
        }
        return ShotPlan(
            request = ShotRequest(
                shotId = "test-shot",
                shotKind = ShotKind.LIVE_PHOTO,
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(customTags = customTags)
                ),
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(watermarkText = watermarkText),
                captureProfile = CaptureProfile()
            ),
            saveTask = MediaSaveTask(
                shotId = "test-shot",
                mediaType = MediaType.PHOTO,
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(customTags = customTags)
                ),
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(watermarkText = watermarkText),
                captureProfile = CaptureProfile()
            ),
            graph = ShotGraph(
                shotId = "test-shot",
                captureNodes = emptyList(),
                algorithmNodes = emptyList(),
                outputNodes = emptyList()
            )
        )
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
