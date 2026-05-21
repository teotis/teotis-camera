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
}
