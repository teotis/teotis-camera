package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.MediaMetadata
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.SceneMaskPayload
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.toMetadataTags
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoAlgorithmPostProcessorTest {
    @Test
    fun `recognized profile with content uri is rendered`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "document-whiteboard-scan",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Documents/OpenCamera_DOC_1.jpg",
                    contentUri = "content://media/external/images/media/88"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val invocation = editor.invocations.single()
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/88"),
            invocation.target
        )
        assertEquals("document-whiteboard-scan", invocation.spec.profile)
        assertTrue(result.pipelineNotes.contains("algorithm-render:applied:document-whiteboard-scan"))
    }

    @Test
    fun `missing algorithm profile leaves result untouched`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val input = photoResult(algorithmProfile = null)
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `unsupported profile is ignored without diagnostics`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val input = photoResult(algorithmProfile = "custom-unmapped-profile")
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `missing editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "portrait-depth-dramatic",
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Portrait/OpenCamera_PORTRAIT_1.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("algorithm-render:skipped:missing-output-handle"))
    }

    @Test
    fun `editor failure is captured as pipeline diagnostic`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "night-multiframe-street",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/night-render.jpg",
                    filePath = "/tmp/night-render.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:decode-failed"))
    }

    @Test
    fun `editor exception during decode is caught and degrades gracefully`() = runTest {
        val editor = ThrowingPhotoAlgorithmEditor(OutOfMemoryError("bitmap too large"))
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/color-lab-photo.jpg",
                    filePath = "/tmp/color-lab-photo.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:oom"))
    }

    @Test
    fun `postprocess exception preserves original result with failure note`() = runTest {
        val editor = ThrowingPhotoAlgorithmEditor(RuntimeException("unexpected"))
        val processor = PhotoAlgorithmPostProcessor(editor)
        val input = photoResult(
            algorithmProfile = "photo-vivid",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/lab-photo.jpg",
                filePath = "/tmp/lab-photo.jpg"
            )
        )
        val result = processor.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:render-exception"))
    }

    @Test
    fun `editor cancellation propagates instead of becoming recoverable diagnostic`() = runTest {
        val processor = PhotoAlgorithmPostProcessor(
            ThrowingPhotoAlgorithmEditor(CancellationException("job cancelled"))
        )

        assertFailsWith<CancellationException> {
            processor.process(
                photoResult(
                    algorithmProfile = "photo-vivid",
                    outputHandle = MediaOutputHandle(
                        displayPath = "/tmp/cancel-photo.jpg",
                        filePath = "/tmp/cancel-photo.jpg"
                    )
                )
            )
        }
    }

    @Test
    fun `editor vm error propagates instead of becoming recoverable diagnostic`() = runTest {
        val processor = PhotoAlgorithmPostProcessor(
            ThrowingPhotoAlgorithmEditor(StackOverflowError("deep recursion"))
        )

        assertFailsWith<StackOverflowError> {
            processor.process(
                photoResult(
                    algorithmProfile = "photo-vivid",
                    outputHandle = MediaOutputHandle(
                        displayPath = "/tmp/error-photo.jpg",
                        filePath = "/tmp/error-photo.jpg"
                    )
                )
            )
        }
    }

    @Test
    fun `editor exception with custom filter spec degrades gracefully`() = runTest {
        val editor = ThrowingPhotoAlgorithmEditor(RuntimeException("decode-crash"))
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "custom-vivid-1",
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(
                        algorithmProfile = "custom-vivid-1",
                        customTags = FilterRenderSpec(
                            brightnessShift = -2,
                            contrast = 1.11f,
                            saturation = 1.19f,
                            warmthShift = 2
                        ).toMetadataTags() + mapOf("filterProfile" to "custom-vivid-1")
                    )
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:render-exception"))
    }

    @Test
    fun `shared custom filter spec is rendered without built in algorithm mapping`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "custom-amber-street",
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(
                        algorithmProfile = "custom-amber-street",
                        customTags = FilterRenderSpec(
                            brightnessShift = 7,
                            contrast = 1.11f,
                            saturation = 0.93f,
                            warmthShift = 5,
                            tintShift = -4,
                            vignetteStrength = 0.17f,
                            softGlowStrength = 0.1f,
                            haloStrength = 0.2f,
                            grainStrength = 0.08f,
                            highlightCompression = 0.2f
                        ).toMetadataTags() + mapOf(
                            "filterProfile" to "custom-amber-street"
                        )
                    )
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val invocation = editor.invocations.single()
        assertEquals("custom-amber-street", invocation.spec.profile)
        assertEquals(7, invocation.spec.brightnessShift)
        assertEquals(1.11f, invocation.spec.contrast)
        assertEquals(0.1f, invocation.spec.softGlowStrength)
        assertEquals(0.2f, invocation.spec.haloStrength)
        assertEquals(0.08f, invocation.spec.grainStrength)
        assertEquals(-4, invocation.spec.tintShift)
        assertTrue(result.pipelineNotes.contains("algorithm-render:applied:custom-amber-street"))
    }

    @Test
    fun `style resolution covers representative current mode profiles`() {
        val photo = resolvePhotoAlgorithmSpec("photo-vivid")
        val photoRich = resolvePhotoAlgorithmSpec("photo-rich")
        val document = resolvePhotoAlgorithmSpec("document-receipt-scan")
        val portrait = resolvePhotoAlgorithmSpec("portrait-depth-studio")
        val night = resolvePhotoAlgorithmSpec("night-fallback-warm")
        val pro = resolvePhotoAlgorithmSpec("pro-assisted-contrast")
        val checkInClarity = resolvePhotoAlgorithmSpec("checkin-clarity-best-frame-v1")
        val humanisticProfessional = resolvePhotoAlgorithmSpec("photo-vivid-pro")

        assertNotNull(photo)
        assertNotNull(photoRich)
        assertNotNull(document)
        assertNotNull(portrait)
        assertNotNull(night)
        assertNotNull(pro)
        assertNotNull(checkInClarity)
        assertNotNull(humanisticProfessional)
        assertEquals("checkin-clarity-best-frame-v1", checkInClarity.profile)
        assertEquals("photo-vivid-pro", humanisticProfessional.profile)
        assertTrue(checkInClarity.sharpnessBoost > 0f)
        assertTrue(humanisticProfessional.saturation > 1f)
    }

    @Test
    fun `checkin clarity profile is not near neutral and is applied`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "checkin-clarity-best-frame-v1",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/checkin-clarity.jpg",
                    filePath = "/tmp/checkin-clarity.jpg"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals("checkin-clarity-best-frame-v1", editor.invocations.single().spec.profile)
        assertTrue(editor.invocations.single().spec.sharpnessBoost > 0f)
        assertTrue(result.pipelineNotes.contains("algorithm-render:applied:checkin-clarity-best-frame-v1"))
    }

    @Test
    fun `unknown checkin profile is safely ignored without crash`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmApplied()
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val input = photoResult(algorithmProfile = "checkin-custom-effect")
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `custom vivid metadata from capture log is rendered`() = runTest {
        val editor = FakePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "custom-vivid-1",
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(
                        algorithmProfile = "custom-vivid-1",
                        customTags = FilterRenderSpec(
                            brightnessShift = -2,
                            contrast = 1.1108352f,
                            saturation = 1.1945403f,
                            warmthShift = 2
                        ).toMetadataTags() + mapOf("filterProfile" to "custom-vivid-1")
                    )
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val inv = editor.invocations.single()
        assertEquals("custom-vivid-1", inv.spec.profile)
        assertEquals(-2, inv.spec.brightnessShift)
        assertEquals(1.1108352f, inv.spec.contrast)
        assertEquals(1.1945403f, inv.spec.saturation)
        assertEquals(2, inv.spec.warmthShift)
        assertTrue(result.pipelineNotes.contains("algorithm-render:applied:custom-vivid-1"))
    }

    @Test
    fun `saved mask available applies mask-aware render and marks preview unsupported`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Available(
                SavedPhotoMaskPixels(
                    maskPixels = IntArray(16) { 0x88000000.toInt() },
                    maskWidth = 4,
                    maskHeight = 4,
                    confidence = 0.9f
                )
            )
        )
        val testBitmap = mockBitmap(8, 8)
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { testBitmap }
        )
        val result = processor.process(
            photoResult(algorithmProfile = "portrait-depth-natural")
        )

        assertEquals(1, editor.applyWithMaskInvocations.size)
        val maskNotes = result.pipelineNotes
        assertTrue(maskNotes.any { it.contains("scene-mask:saved=applied") })
        assertTrue(maskNotes.any { it.contains("scene-mask:preview=unsupported") })
        assertFalse(maskNotes.any { it.contains("scene-mask:preview=applied") })
        val maskTagKeys = result.metadata.customTags.keys
        assertTrue(maskTagKeys.any { it.startsWith("scene-mask:") })
    }

    @Test
    fun `saved mask unavailable falls back to global render with unsupported note`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Unavailable("ml-kit-not-installed")
        )
        val testBitmap = mockBitmap(8, 8)
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { testBitmap }
        )
        val result = processor.process(
            photoResult(algorithmProfile = "portrait-depth-natural")
        )

        assertEquals(1, editor.applyInvocations.size)
        assertEquals(0, editor.applyWithMaskInvocations.size)
        val notes = result.pipelineNotes
        assertTrue(notes.any { it.contains("scene-mask:saved=unsupported") })
        assertTrue(notes.any { it.contains("scene-mask:reason=ml-kit-not-installed") })
        assertTrue(notes.any { it.contains("algorithm-render:applied:portrait-depth-natural") })
    }

    @Test
    fun `saved mask failed falls back to global render with degraded note`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Failed("segmentation-model-crash")
        )
        val testBitmap = mockBitmap(8, 8)
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { testBitmap }
        )
        val result = processor.process(
            photoResult(algorithmProfile = "portrait-depth-dramatic")
        )

        assertEquals(1, editor.applyInvocations.size)
        val notes = result.pipelineNotes
        assertTrue(notes.any { it.contains("scene-mask:saved=degraded") })
        assertTrue(notes.any { it.contains("scene-mask:reason=segmentation-model-crash") })
        assertTrue(notes.any { it.contains("algorithm-render:applied:portrait-depth-dramatic") })
    }

    @Test
    fun `mask source exception preserves original result with failure note`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Unavailable("not-used")
        )
        val input = photoResult(algorithmProfile = "portrait-depth-natural")
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { throw IllegalStateException("decode source failed") }
        )

        val result = processor.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertEquals(0, editor.applyInvocations.size)
        assertEquals(0, editor.applyWithMaskInvocations.size)
        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:render-exception"))
    }

    @Test
    fun `no mask provider falls back to legacy render without scene mask notes`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val processor = PhotoAlgorithmPostProcessor(editor, maskProvider = null)
        val result = processor.process(
            photoResult(algorithmProfile = "photo-vivid")
        )

        assertEquals(1, editor.applyInvocations.size)
        assertEquals(0, editor.applyWithMaskInvocations.size)
        val notes = result.pipelineNotes
        assertFalse(notes.any { it.contains("scene-mask:saved=") })
        assertTrue(notes.any { it.contains("algorithm-render:applied:photo-vivid") })
    }

    @Test
    fun `mask available but editor not mask aware falls back with degraded note`() = runTest {
        val editor = FakePhotoAlgorithmEditor(PhotoAlgorithmApplied())
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Available(
                SavedPhotoMaskPixels(
                    maskPixels = IntArray(16) { 0x88000000.toInt() },
                    maskWidth = 4,
                    maskHeight = 4,
                    confidence = 0.9f
                )
            )
        )
        val testBitmap = mockBitmap(8, 8)
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { testBitmap }
        )
        val result = processor.process(
            photoResult(algorithmProfile = "portrait-focus-balanced")
        )

        assertEquals(1, editor.invocations.size)
        val notes = result.pipelineNotes
        assertTrue(notes.any { it.contains("scene-mask:saved=degraded:editor-not-mask-aware") })
        assertTrue(notes.any { it.contains("algorithm-render:applied:portrait-focus-balanced") })
    }

    @Test
    fun `mask aware write failure omits applied from pipeline notes`() = runTest {
        val editor = FakeMaskAwarePhotoAlgorithmEditor(
            result = ProcessorEditorResult.Failed("output-unavailable"),
            maskNotes = listOf(
                "scene-mask:saved=degraded",
                "scene-mask:reason=output-unavailable"
            )
        )
        val maskProvider = FakeSavedPhotoMaskProvider(
            result = SceneMaskResult.Available(
                SavedPhotoMaskPixels(
                    maskPixels = IntArray(16) { 0x88000000.toInt() },
                    maskWidth = 4,
                    maskHeight = 4,
                    confidence = 0.9f
                )
            )
        )
        val testBitmap = mockBitmap(8, 8)
        val processor = PhotoAlgorithmPostProcessor(
            editor,
            maskProvider = maskProvider,
            maskBitmapSource = { testBitmap }
        )
        val result = processor.process(
            photoResult(algorithmProfile = "portrait-depth-natural")
        )

        val notes = result.pipelineNotes
        assertFalse(notes.any { it.contains("scene-mask:saved=applied") })
        assertTrue(notes.any { it.contains("scene-mask:saved=degraded") })
        assertTrue(notes.any { it.contains("scene-mask:reason=output-unavailable") })
        assertTrue(notes.any { it.contains("algorithm-render:failed:output-unavailable") })
    }

    @Test
    fun `mask aware editor writes modified bytes to file target`() = runTest {
        val tempFile = java.io.File.createTempFile("mask-writeback-test", ".dat")
        try {
            tempFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03, 0x04))
            val initialBytes = tempFile.readBytes()

            val editor = WritingFakeMaskAwareEditor()
            val maskProvider = FakeSavedPhotoMaskProvider(
                result = SceneMaskResult.Available(
                    SavedPhotoMaskPixels(
                        maskPixels = IntArray(16) { 0x88000000.toInt() },
                        maskWidth = 4,
                        maskHeight = 4,
                        confidence = 0.9f
                    )
                )
            )
            val testBitmap = mockBitmap(8, 8)
            val processor = PhotoAlgorithmPostProcessor(
                editor,
                maskProvider = maskProvider,
                maskBitmapSource = { testBitmap }
            )
            val result = processor.process(
                photoResult(
                    algorithmProfile = "portrait-depth-natural",
                    outputHandle = MediaOutputHandle(
                        displayPath = tempFile.absolutePath,
                        filePath = tempFile.absolutePath
                    )
                )
            )

            assertEquals(1, editor.applyWithMaskInvocations.size)
            assertTrue(result.pipelineNotes.any { it.contains("scene-mask:saved=applied") })
            val outputBytes = tempFile.readBytes()
            assertFalse(outputBytes.contentEquals(initialBytes), "Output bytes should differ after mask-aware write")
        } finally {
            tempFile.delete()
        }
    }

    // ── Structured failure mapping ──────────────────────────────────────────

    @Test
    fun `algorithmFailureMapping maps decode-failed correctly`() {
        val (cause, integrity) = algorithmFailureMapping("decode-failed")
        assertEquals(PostProcessFailureCause.DECODE_FAILED, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `algorithmFailureMapping maps decode-oom correctly`() {
        val (cause, integrity) = algorithmFailureMapping("decode-oom")
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `algorithmFailureMapping maps style-oom correctly`() {
        val (cause, integrity) = algorithmFailureMapping("style-oom")
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `algorithmFailureMapping maps encode-failed correctly`() {
        val (cause, integrity) = algorithmFailureMapping("encode-failed")
        assertEquals(PostProcessFailureCause.ENCODE, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `algorithmFailureMapping maps output-unavailable with possibly-modified`() {
        val (cause, integrity) = algorithmFailureMapping("output-unavailable")
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, integrity)
    }

    @Test
    fun `algorithmFailureMapping maps unknown reason to EXCEPTION`() {
        val (cause, integrity) = algorithmFailureMapping("something-else")
        assertEquals(PostProcessFailureCause.EXCEPTION, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `editor Failed decode-failed produces structured failure`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/test.jpg",
                    filePath = "/tmp/test.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("algorithm-render:failed:decode-failed") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size, "Should have one structured failure")
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.ALGORITHM, failure.stage)
        assertEquals(PostProcessFailureCause.DECODE_FAILED, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `editor Failed output-unavailable produces structured failure with possibly-modified`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = ProcessorEditorResult.Failed("output-unavailable")
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/test.jpg",
                    filePath = "/tmp/test.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.any { it.contains("algorithm-render:failed:output-unavailable") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.ALGORITHM, failure.stage)
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, failure.cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, failure.integrity)
    }

    @Test
    fun `OOM during process produces structured failure`() = runTest {
        val processor = PhotoAlgorithmPostProcessor(
            ThrowingPhotoAlgorithmEditor(OutOfMemoryError("bitmap too large"))
        )

        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/test.jpg",
                    filePath = "/tmp/test.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:oom"))
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.ALGORITHM, failure.stage)
        assertEquals(PostProcessFailureCause.OUT_OF_MEMORY, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
    }

    @Test
    fun `exception during process produces structured failure`() = runTest {
        val processor = PhotoAlgorithmPostProcessor(
            ThrowingPhotoAlgorithmEditor(RuntimeException("unexpected"))
        )

        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/test.jpg",
                    filePath = "/tmp/test.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("algorithm-render:failed:render-exception"))
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.ALGORITHM, failure.stage)
        assertEquals(PostProcessFailureCause.EXCEPTION, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
    }

    @Test
    fun `structured failure legacy projection matches legacy pipeline note`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoAlgorithmPostProcessor(editor)
        val result = processor.process(
            photoResult(
                algorithmProfile = "photo-vivid",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/test.jpg",
                    filePath = "/tmp/test.jpg"
                )
            )
        )

        val legacyNote = result.structuredPostProcessFailures.single().toLegacyNote()
        assertTrue(result.pipelineNotes.contains(legacyNote),
            "Legacy projection '$legacyNote' should be among pipeline notes: ${result.pipelineNotes}")
    }

    private fun photoResult(
        algorithmProfile: String?,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/photo-style.jpg",
            filePath = "/tmp/photo-style.jpg"
        ),
        mediaType: MediaType = MediaType.PHOTO,
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                algorithmProfile = algorithmProfile
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-style",
            mediaType = mediaType,
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

    private class FakePhotoAlgorithmEditor(
        private val result: ProcessorEditorResult
    ) : PhotoAlgorithmEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult {
            invocations += Invocation(target, spec)
            return result
        }
    }

    private class ThrowingPhotoAlgorithmEditor(
        private val error: Throwable
    ) : PhotoAlgorithmEditor {
        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult {
            throw error
        }
    }

    private open class FakeMaskAwarePhotoAlgorithmEditor(
        private val result: ProcessorEditorResult,
        private val maskNotes: List<String> = listOf("scene-mask:saved=applied", "color-render:subject-protected")
    ) : MaskAwarePhotoAlgorithmEditor {
        val applyInvocations = mutableListOf<Invocation>()
        val applyWithMaskInvocations = mutableListOf<MaskInvocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult {
            applyInvocations += Invocation(target, spec)
            return result
        }

        override suspend fun applyWithMask(
            target: ProcessorTarget,
            bitmap: Bitmap,
            spec: PhotoAlgorithmSpec,
            mask: SavedPhotoMaskPixels
        ): Pair<ProcessorEditorResult, List<String>> {
            applyWithMaskInvocations += MaskInvocation(spec, mask)
            return Pair(result, maskNotes)
        }
    }

    private class FakeSavedPhotoMaskProvider(
        private val result: SceneMaskResult
    ) : SavedPhotoSceneMaskProvider {
        override suspend fun createSubjectMask(
            bitmap: Bitmap,
            request: SavedPhotoSceneMaskRequest
        ): SceneMaskResult = result
    }

    private class WritingFakeMaskAwareEditor : MaskAwarePhotoAlgorithmEditor {
        val applyWithMaskInvocations = mutableListOf<MaskInvocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult = PhotoAlgorithmApplied()

        override suspend fun applyWithMask(
            target: ProcessorTarget,
            bitmap: Bitmap,
            spec: PhotoAlgorithmSpec,
            mask: SavedPhotoMaskPixels
        ): Pair<ProcessorEditorResult, List<String>> {
            applyWithMaskInvocations += MaskInvocation(spec, mask)
            val written = when (target) {
                is ProcessorTarget.FilePath -> {
                    java.io.File(target.path).outputStream().use {
                        it.write(MASK_AWARE_WRITE_SENTINEL)
                        true
                    }
                }
                is ProcessorTarget.ContentUri -> false
            }
            val result: ProcessorEditorResult = if (written) {
                PhotoAlgorithmApplied()
            } else {
                ProcessorEditorResult.Failed("output-unavailable")
            }
            return Pair(result, listOf("scene-mask:saved=applied", "color-render:subject-protected"))
        }

        companion object {
            val MASK_AWARE_WRITE_SENTINEL = byteArrayOf(
                0xDE.toByte(), 0xAD.toByte(), 0xBE.toByte(), 0xEF.toByte(),
                0x4D, 0x41, 0x53, 0x4B
            )
        }
    }

    private data class Invocation(
        val target: ProcessorTarget,
        val spec: PhotoAlgorithmSpec
    )

    private data class MaskInvocation(
        val spec: PhotoAlgorithmSpec,
        val mask: SavedPhotoMaskPixels
    )

    private fun mockBitmap(width: Int, height: Int): Bitmap {
        val bitmap = mock(Bitmap::class.java)
        `when`(bitmap.width).thenReturn(width)
        `when`(bitmap.height).thenReturn(height)
        return bitmap
    }
}
