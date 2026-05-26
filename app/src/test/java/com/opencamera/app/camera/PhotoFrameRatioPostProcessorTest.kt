package com.opencamera.app.camera

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PhotoFrameRatioPostProcessorTest {
    @Test
    fun `jpeg with frame ratio tag is cropped through content uri`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioApplied(
                frameRatio = FrameRatio.RATIO_16_9,
                cropBounds = CropBounds(0, 120, 4000, 2380)
            )
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(
            photoResult(
                frameRatio = "16:9",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_1.jpg",
                    contentUri = "content://media/external/images/media/99"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val invocation = editor.invocations.single()
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/99"),
            invocation.target
        )
        assertEquals(FrameRatio.RATIO_16_9, invocation.frameRatio)
        assertTrue(result.pipelineNotes.contains("frame-ratio:applied:16:9"))
        assertTrue(result.pipelineNotes.any { it.startsWith("frame-ratio:bounds=") })
    }

    @Test
    fun `missing frame ratio tag leaves result untouched`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = ProcessorEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val input = photoResult(frameRatio = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `unsupported frame ratio tag records diagnostic`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = ProcessorEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(photoResult(frameRatio = "5:4"))

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("frame-ratio:skipped:unsupported-frame-ratio"))
    }

    @Test
    fun `missing editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = ProcessorEditorResult.Skipped("already-matched")
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        val result = processor.process(
            photoResult(
                frameRatio = "1:1",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_2.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("frame-ratio:skipped:missing-output-handle"))
    }

    @Test
    fun `compute center crop bounds trims top and bottom for widescreen`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_16_9
        )

        assertNotNull(bounds)
        assertEquals(0, bounds.left)
        assertEquals(375, bounds.top)
        assertEquals(4000, bounds.right)
        assertEquals(2625, bounds.bottom)
    }

    @Test
    fun `compute center crop bounds trims sides for square`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_1_1
        )

        assertNotNull(bounds)
        assertEquals(500, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(3500, bounds.right)
        assertEquals(3000, bounds.bottom)
    }

    @Test
    fun `compute center crop bounds returns null when already matched`() {
        val bounds = computeCenterCropBounds(
            width = 4000,
            height = 3000,
            frameRatio = FrameRatio.RATIO_4_3
        )

        assertNull(bounds)
    }

    @Test
    fun `compute center crop bounds handles portrait source 4_3 ratio`() {
        // Portrait photo: 3000w x 4000h (3:4 aspect)
        val bounds = computeCenterCropBounds(
            width = 3000,
            height = 4000,
            frameRatio = FrameRatio.RATIO_4_3
        )

        assertNull(bounds)
    }

    @Test
    fun `compute center crop bounds handles portrait source 16_9 ratio`() {
        // Portrait photo: 3000w x 4000h
        // Visible preview frame is oriented as 9:16, so saved output must crop
        // the sides to a portrait-tall frame instead of producing a landscape crop.
        val bounds = computeCenterCropBounds(
            width = 3000,
            height = 4000,
            frameRatio = FrameRatio.RATIO_16_9
        )

        assertNotNull(bounds)
        assertEquals(375, bounds.left)
        assertEquals(0, bounds.top)
        assertEquals(2625, bounds.right)
        assertEquals(4000, bounds.bottom)
    }

    // --- computeZoomCropBounds tests ---

    @Test
    fun `zoom 1x returns null crop bounds`() {
        assertNull(computeZoomCropBounds(4000, 3000, 1f))
    }

    @Test
    fun `zoom 2x halves dimensions centered`() {
        val bounds = computeZoomCropBounds(4000, 3000, 2f)
        assertNotNull(bounds)
        assertEquals(1000, bounds.left)
        assertEquals(750, bounds.top)
        assertEquals(3000, bounds.right)
        assertEquals(2250, bounds.bottom)
        assertEquals(2000, bounds.width())
        assertEquals(1500, bounds.height())
    }

    @Test
    fun `zoom 3x produces third-sized crop centered`() {
        val bounds = computeZoomCropBounds(3000, 3000, 3f)
        assertNotNull(bounds)
        assertEquals(1000, bounds.left)
        assertEquals(1000, bounds.top)
        assertEquals(2000, bounds.right)
        assertEquals(2000, bounds.bottom)
    }

    @Test
    fun `zoom crop preserves center point`() {
        val bounds = computeZoomCropBounds(4000, 3000, 2f)
        assertNotNull(bounds)
        assertEquals(2000, (bounds.left + bounds.right) / 2)
        assertEquals(1500, (bounds.top + bounds.bottom) / 2)
    }

    // --- zoom crop integration via decidePhotoFrameRatioWork ---

    @Test
    fun `capture crop zoom tag is passed through payload`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioApplied(
                frameRatio = FrameRatio.RATIO_4_3,
                cropBounds = CropBounds(0, 0, 4000, 3000)
            )
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        processor.process(
            photoResult(
                frameRatio = "4:3",
                captureCropZoom = "2.0"
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(2f, editor.invocations.single().captureCropZoom)
    }

    @Test
    fun `missing capture crop zoom defaults to 1x`() = runTest {
        val editor = FakePhotoFrameRatioEditor(
            result = PhotoFrameRatioApplied(
                frameRatio = FrameRatio.RATIO_4_3,
                cropBounds = CropBounds(0, 0, 4000, 3000)
            )
        )
        val processor = PhotoFrameRatioPostProcessor(editor)
        processor.process(photoResult(frameRatio = "4:3"))

        assertEquals(1f, editor.invocations.single().captureCropZoom)
    }

    // --- frame ratio plus zoom crop composition ---

    @Test
    fun `frame ratio plus zoom crop composes deterministically`() {
        // 4000x3000 image, 16:9 frame ratio, 2x zoom
        // Step 1: frame ratio crop → 4000x2250 (top=375, bottom=2625)
        val frameBounds = computeCenterCropBounds(4000, 3000, FrameRatio.RATIO_16_9)
        assertNotNull(frameBounds)
        assertEquals(4000, frameBounds.width())
        assertEquals(2250, frameBounds.height())

        // Step 2: zoom 2x crop on 4000x2250 → 2000x1125
        val zoomBounds = computeZoomCropBounds(frameBounds.width(), frameBounds.height(), 2f)
        assertNotNull(zoomBounds)
        assertEquals(2000, zoomBounds.width())
        assertEquals(1125, zoomBounds.height())
        assertEquals(1000, zoomBounds.left)
        assertEquals(562, zoomBounds.top)
    }

    // --- Acceptance: saved crop uses center-crop semantics matching overlay ---

    @Test
    fun `saved crop is always centered for all ratios`() {
        val configs = listOf(
            4000 to 3000,  // landscape
            3000 to 4000,  // portrait
            4000 to 4000,  // square
        )
        val ratios = listOf(FrameRatio.RATIO_4_3, FrameRatio.RATIO_16_9, FrameRatio.RATIO_1_1)
        for ((w, h) in configs) {
            for (ratio in ratios) {
                val bounds = computeCenterCropBounds(w, h, ratio) ?: continue
                val cropCenterX = (bounds.left + bounds.right) / 2
                val cropCenterY = (bounds.top + bounds.bottom) / 2
                assertEquals(
                    w / 2,
                    cropCenterX,
                    "Crop must be horizontally centered for $ratio on ${w}x$h"
                )
                assertEquals(
                    h / 2,
                    cropCenterY,
                    "Crop must be vertically centered for $ratio on ${w}x$h"
                )
            }
        }
    }

    @Test
    fun `saved crop aspect ratio matches requested ratio for all orientations`() {
        val configs = listOf(4000 to 3000, 3000 to 4000)
        for ((w, h) in configs) {
            for (ratio in listOf(FrameRatio.RATIO_16_9, FrameRatio.RATIO_1_1)) {
                val bounds = computeCenterCropBounds(w, h, ratio) ?: continue
                val cropW = bounds.right - bounds.left
                val cropH = bounds.bottom - bounds.top
                val expectedRatio = if (w <= h) {
                    minOf(ratio.width, ratio.height).toDouble() / maxOf(ratio.width, ratio.height).toDouble()
                } else {
                    maxOf(ratio.width, ratio.height).toDouble() / minOf(ratio.width, ratio.height).toDouble()
                }
                val actualRatio = cropW.toDouble() / cropH.toDouble()
                assertEquals(
                    expectedRatio,
                    actualRatio,
                    0.02,
                    "Crop aspect ratio must match $ratio for ${w}x$h image"
                )
            }
        }
    }

    private fun photoResult(
        frameRatio: String?,
        captureCropZoom: String? = null,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/frame-ratio.jpg",
            filePath = "/tmp/frame-ratio.jpg"
        )
    ): ShotResult {
        val tags = buildMap {
            frameRatio?.let { put("frameRatio", it) }
            captureCropZoom?.let { put("captureCropZoom", it) }
        }
        val saveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = tags
            )
        )
        return ShotResult(
            shotId = "shot-frame-ratio",
            mediaType = MediaType.PHOTO,
            outputPath = outputHandle.displayPath,
            outputHandle = outputHandle,
            saveRequest = saveRequest,
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = outputHandle.displayPath,
                renderUri = outputHandle.contentUri
            ),
            metadata = saveRequest.metadata
        )
    }

    private class FakePhotoFrameRatioEditor(
        private val result: ProcessorEditorResult
    ) : PhotoFrameRatioEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            frameRatio: FrameRatio,
            captureCropZoom: Float
        ): ProcessorEditorResult {
            invocations += Invocation(target, frameRatio, captureCropZoom)
            return result
        }
    }

    private data class Invocation(
        val target: ProcessorTarget,
        val frameRatio: FrameRatio,
        val captureCropZoom: Float = 1f
    )
}
