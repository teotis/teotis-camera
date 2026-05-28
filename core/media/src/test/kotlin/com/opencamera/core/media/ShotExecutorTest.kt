package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.io.File

class ShotExecutorTest {
    @Test
    fun `single frame strategy creates photo shot plan and saved media thumbnail`() {
        val executor = ShotExecutor(idGenerator = { "shot-photo" })

        val plan = executor.plan(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(),
                postProcessSpec = PostProcessSpec(
                    watermarkText = "OpenCamera",
                    exifOverrides = mapOf("ISO" to "100"),
                    algorithmProfile = "single-frame"
                )
            )
        )
        val result = executor.resultFor(plan.saveTask, "Pictures/OpenCamera/OpenCamera_1.jpg")

        assertEquals("shot-photo", plan.request.shotId)
        assertEquals(ShotKind.STILL_CAPTURE, plan.request.shotKind)
        assertEquals(MediaType.PHOTO, plan.request.mediaType)
        assertEquals(
            ThumbnailSource.SavedMedia("Pictures/OpenCamera/OpenCamera_1.jpg"),
            result.thumbnailSource
        )
        assertEquals("OpenCamera", result.metadata.watermarkText)
        assertEquals("100", result.metadata.exifOverrides["ISO"])
        assertEquals("single-frame", result.metadata.algorithmProfile)
    }

    @Test
    fun `resultFor carries MediaStore output handle into thumbnail render uri`() {
        val executor = ShotExecutor(idGenerator = { "shot-photo-handle" })
        val plan = executor.plan(CaptureStrategy.SingleFrame())
        val outputHandle = MediaOutputHandle(
            displayPath = "Pictures/OpenCamera/OpenCamera_2.jpg",
            contentUri = "content://media/external/images/media/42"
        )

        val result = executor.resultFor(
            saveTask = plan.saveTask,
            outputPath = outputHandle.displayPath,
            outputHandle = outputHandle
        )

        assertEquals(outputHandle, result.outputHandle)
        assertEquals(
            ThumbnailSource.SavedMedia(
                outputPath = outputHandle.displayPath,
                renderUri = outputHandle.contentUri
            ),
            result.thumbnailSource
        )
        assertEquals(outputHandle.contentUri, result.thumbnailSource.renderUriOrNull())
    }

    @Test
    fun `pipeline metadata appends flash note for non default flash capture`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-photo-flash" })

        val plan = executor.plan(
            CaptureStrategy.SingleFrame(
                captureProfile = CaptureProfile(
                    flashMode = FlashMode.ON
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(plan.saveTask, "Pictures/OpenCamera/OpenCamera_FLASH_1.jpg")
        )

        assertTrue(result.pipelineNotes.contains("flash:on"))
    }

    @Test
    fun `pipeline metadata appends torch note for video capture`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-video-torch" })

        val plan = executor.plan(
            CaptureStrategy.VideoRecording(
                captureProfile = CaptureProfile(
                    torchEnabled = true
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(plan.saveTask, "Movies/OpenCamera/OpenCamera_VIDEO_1.mp4")
        )

        assertTrue(result.pipelineNotes.contains("torch:on"))
    }

    @Test
    fun `pipeline metadata appends watermark note for simple capture`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-photo-watermark" })

        val plan = executor.plan(
            CaptureStrategy.SingleFrame(
                postProcessSpec = PostProcessSpec(
                    watermarkText = "PHOTO Auto"
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(plan.saveTask, "Pictures/OpenCamera/OpenCamera_WATERMARK_1.jpg")
        )

        assertTrue(result.pipelineNotes.contains("watermark:PHOTO Auto"))
    }

    @Test
    fun `pipeline metadata appends live default and manual draft notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-photo-settings" })

        val plan = executor.plan(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(
                        customTags = mapOf(
                            "livePhotoDefault" to "on",
                            "liveWatermarkBehavior" to "follow-frame-luma-and-motion",
                            "manualDraftState" to "metadata-draft",
                            "manualDraftRaw" to "off",
                            "manualDraftIso" to "auto",
                            "manualDraftShutterSpeedMillis" to "auto",
                            "manualDraftWhiteBalanceKelvin" to "auto"
                        )
                    )
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(plan.saveTask, "Pictures/OpenCamera/OpenCamera_SETTINGS_1.jpg")
        )

        assertTrue(result.pipelineNotes.contains("live-default:on"))
        assertTrue(result.pipelineNotes.contains("live-watermark:follow-frame-luma-and-motion"))
        assertTrue(
            result.pipelineNotes.contains(
                "manual-draft:metadata-draft:raw-off:iso-auto:s-auto:wb-auto"
            )
        )

        // Verify stable ordering: algorithm notes (live-default, live-watermark) before transaction notes (manual-draft)
        val liveDefaultIndex = result.pipelineNotes.indexOf("live-default:on")
        val liveWatermarkIndex = result.pipelineNotes.indexOf("live-watermark:follow-frame-luma-and-motion")
        val manualDraftIndex = result.pipelineNotes.indexOfFirst { it.startsWith("manual-draft:") }
        assertTrue(liveDefaultIndex < manualDraftIndex, "live-default should come before manual-draft")
        assertTrue(liveWatermarkIndex < manualDraftIndex, "live-watermark should come before manual-draft")
    }

    @Test
    fun `live photo strategy creates dedicated shot kind and media bundle`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-live" })

        val plan = executor.plan(
            CaptureStrategy.LivePhoto(
                saveRequest = SaveRequest.photoLibrary(fileNamePrefix = "OpenCamera_LIVE"),
                livePhotoSpec = LivePhotoCaptureSpec(
                    motionDurationMillis = 1800,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json"
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/OpenCamera_LIVE_1.jpg",
                livePhotoBundle = LivePhotoBundle(
                    stillPath = "Pictures/OpenCamera/OpenCamera_LIVE_1.jpg",
                    motionPath = "Pictures/OpenCamera/OpenCamera_LIVE_1.live.mp4",
                    sidecarPath = "Pictures/OpenCamera/OpenCamera_LIVE_1.live.json",
                    motionDurationMillis = 1800,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json"
                )
            )
        )

        assertEquals(ShotKind.LIVE_PHOTO, plan.request.shotKind)
        assertEquals(1800, plan.request.livePhotoSpec?.motionDurationMillis)
        assertEquals("Pictures/OpenCamera/OpenCamera_LIVE_1.live.mp4", result.livePhotoBundle?.motionPath)
        assertTrue(result.pipelineNotes.contains("live-photo:bundle"))
        assertTrue(result.pipelineNotes.contains("live-photo:motion=video/mp4"))
    }

    @Test
    fun `multi frame strategy creates multi frame photo plan and pipeline notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-night" })

        val plan = executor.plan(
            CaptureStrategy.MultiFrame(
                saveRequest = SaveRequest.photoLibrary(fileNamePrefix = "OpenCamera_NIGHT"),
                postProcessSpec = PostProcessSpec(
                    algorithmProfile = "night-multiframe"
                ),
                captureProfile = CaptureProfile(
                    frameCount = 8,
                    longExposureMillis = 900,
                    requiresTripod = true
                )
            )
        )
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(plan.saveTask, "Pictures/OpenCamera/OpenCamera_NIGHT_1.jpg")
        )

        assertEquals("shot-night", plan.request.shotId)
        assertEquals(ShotKind.MULTI_FRAME_CAPTURE, plan.request.shotKind)
        assertEquals(8, plan.request.captureProfile.frameCount)
        assertEquals(900, plan.request.captureProfile.longExposureMillis)
        assertTrue(result.pipelineNotes.contains("frames:8"))
        assertTrue(result.pipelineNotes.contains("exposure:900ms"))
        assertTrue(result.pipelineNotes.contains("stability:tripod"))
    }

    @Test
    fun `video shot can be stopped but still shot cannot`() {
        val executor = ShotExecutor(idGenerator = { "shot-video" })
        val videoShot = executor.plan(CaptureStrategy.VideoRecording()).request

        assertEquals("shot-video", executor.requireStoppableShot(videoShot).shotId)

        val photoShot = executor.plan(
            CaptureStrategy.SingleFrame(),
            activeShot = null
        ).request
        val error = assertFailsWith<IllegalStateException> {
            executor.requireStoppableShot(photoShot)
        }
        assertTrue(error.message?.contains("Only video recording shots") == true)
    }

    @Test
    fun `single frame plan carries graph with one primary still capture node`() {
        val executor = ShotExecutor(idGenerator = { "shot-graph-photo" })
        val plan = executor.plan(CaptureStrategy.SingleFrame())

        assertEquals(1, plan.graph.captureNodes.size)
        assertEquals(CaptureNodeRole.PRIMARY_STILL, plan.graph.captureNodes[0].role)
        assertEquals(1, plan.graph.captureNodes[0].frameCount)
    }


    @Test
    fun `video plan produces primary video capture and output nodes`() {
        val executor = ShotExecutor(idGenerator = { "shot-graph-video" })
        val plan = executor.plan(CaptureStrategy.VideoRecording())

        assertEquals(1, plan.graph.captureNodes.size)
        assertEquals(CaptureNodeRole.PRIMARY_VIDEO, plan.graph.captureNodes[0].role)
        assertEquals(1, plan.graph.outputNodes.size)
        assertEquals(MediaArtifactRole.PRIMARY_VIDEO, plan.graph.outputNodes[0].role)
    }

    @Test
    fun `multi frame plan clamps graph temp frame count to at least 1`() {
        val executor = ShotExecutor(idGenerator = { "shot-graph-multiframe" })
        val plan = executor.plan(
            CaptureStrategy.MultiFrame(
                captureProfile = CaptureProfile(frameCount = 1)
            )
        )

        val tempNode = plan.graph.captureNodes.first { it.role == CaptureNodeRole.TEMPORARY_FRAME }
        assertTrue(tempNode.frameCount >= 1)
    }

    @Test
    fun `multi frame merge placeholder consumes intermediate outputs and appends merge notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-night-merge" })
        val tempDir = createTempDir(prefix = "night-burst-")
        val burstA = File(tempDir, "burst_a.jpg").apply { writeText("frame-a") }
        val burstB = File(tempDir, "burst_b.jpg").apply { writeText("frame-bb") }

        try {
            val plan = executor.plan(
                CaptureStrategy.MultiFrame(
                    captureProfile = CaptureProfile(
                        frameCount = 3,
                        longExposureMillis = 600
                    )
                )
            )
            val rawResult = executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/OpenCamera_NIGHT_merged.jpg",
                intermediateOutputPaths = listOf(
                    burstA.absolutePath,
                    burstB.absolutePath
                )
            )

            val result = MultiFrameMergePlaceholderPostProcessor().process(rawResult)

            assertTrue(result.pipelineNotes.contains("merge:placeholder"))
            assertTrue(result.pipelineNotes.contains("merge:inputs=3"))
            assertTrue(result.pipelineNotes.contains("merge:temp-frames=2"))
            assertTrue(result.pipelineNotes.any { it.startsWith("merge:temp-bytes=") })
            assertTrue(result.pipelineNotes.contains("merge:strategy=burst-placeholder"))
            assertTrue(burstA.exists().not())
            assertTrue(burstB.exists().not())
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `live photo with complete temporal window emits all temporal status notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-live-complete" })
        val plan = executor.plan(CaptureStrategy.LivePhoto())
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/live.jpg",
                livePhotoBundle = LivePhotoBundle(
                    stillPath = "Pictures/OpenCamera/live.jpg",
                    motionPath = "Pictures/OpenCamera/live.live.mp4",
                    sidecarPath = "Pictures/OpenCamera/live.live.json",
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
            )
        )

        assertTrue(result.pipelineNotes.contains("live:status=complete"))
        assertTrue(result.pipelineNotes.contains("live:source=preview-ring-buffer"))
        assertTrue(result.pipelineNotes.contains("live:frames=45"))
        assertTrue(result.pipelineNotes.contains("live:window=-1200ms,+300ms"))
        assertTrue(result.pipelineNotes.contains("live:sidecar=app-private"))
    }

    @Test
    fun `live photo with degraded motion emits degraded status note`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-live-degraded" })
        val plan = executor.plan(CaptureStrategy.LivePhoto())
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/live.jpg",
                livePhotoBundle = LivePhotoBundle(
                    stillPath = "Pictures/OpenCamera/live.jpg",
                    motionPath = "Pictures/OpenCamera/live.live.mp4",
                    sidecarPath = "Pictures/OpenCamera/live.live.json",
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json",
                    bundleStatus = LiveBundleStatus.DEGRADED_MOTION,
                    temporalWindow = LiveTemporalWindow(
                        requestedDurationMillis = 1_500,
                        preShutterMillis = 0,
                        postShutterMillis = 800,
                        frameCount = 24,
                        source = LiveMotionSource.POST_SHUTTER_FRAMES
                    )
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("live:status=degraded-motion"))
        assertTrue(result.pipelineNotes.contains("live:source=post-shutter-frames"))
    }

    @Test
    fun `live photo with still-only fallback emits fallback and degraded notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-live-fallback" })
        val plan = executor.plan(CaptureStrategy.LivePhoto())
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/live.jpg",
                livePhotoBundle = LivePhotoBundle(
                    stillPath = "Pictures/OpenCamera/live.jpg",
                    motionPath = "Pictures/OpenCamera/live.live.mp4",
                    sidecarPath = "Pictures/OpenCamera/live.live.json",
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json",
                    bundleStatus = LiveBundleStatus.STILL_ONLY_FALLBACK,
                    temporalWindow = LiveTemporalWindow(
                        requestedDurationMillis = 1_500,
                        preShutterMillis = 0,
                        postShutterMillis = 0,
                        frameCount = 0,
                        source = LiveMotionSource.METADATA_ONLY
                    )
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("live:status=still-only-fallback"))
        assertTrue(result.pipelineNotes.contains("live:degraded=metadata-only"))
    }

    @Test
    fun `live photo with null temporal window omits source and frame notes`() = runTest {
        val executor = ShotExecutor(idGenerator = { "shot-live-no-window" })
        val plan = executor.plan(CaptureStrategy.LivePhoto())
        val result = PipelineMetadataPostProcessor().process(
            executor.resultFor(
                saveTask = plan.saveTask,
                outputPath = "Pictures/OpenCamera/live.jpg",
                livePhotoBundle = LivePhotoBundle(
                    stillPath = "Pictures/OpenCamera/live.jpg",
                    motionPath = "Pictures/OpenCamera/live.live.mp4",
                    sidecarPath = "Pictures/OpenCamera/live.live.json",
                    motionDurationMillis = 1_500,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("live:status=complete"))
        assertTrue(result.pipelineNotes.none { it.startsWith("live:source=") })
        assertTrue(result.pipelineNotes.none { it.startsWith("live:frames=") })
        assertTrue(result.pipelineNotes.none { it.startsWith("live:window=") })
    }
}
