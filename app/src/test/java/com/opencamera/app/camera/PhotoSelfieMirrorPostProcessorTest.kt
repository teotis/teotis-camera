package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.PostProcessFailureCause
import com.opencamera.core.media.PostProcessFailureDisposition
import com.opencamera.core.media.PostProcessFailureStage
import com.opencamera.core.media.PostProcessOutputIntegrity
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PhotoSelfieMirrorPostProcessorTest {
    @Test
    fun `photo result with selfie mirror request is rendered`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_2.jpg",
                    contentUri = "content://media/external/images/media/202"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertEquals(
            ProcessorTarget.ContentUri("content://media/external/images/media/202"),
            editor.invocations.single()
        )
        assertTrue(result.pipelineNotes.contains("selfie-mirror:applied"))
    }

    @Test
    fun `selfie mirror request without editable handle records diagnostic skip`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/OpenCamera_3.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("selfie-mirror:skipped:missing-output-handle"))
    }

    @Test
    fun `selfie mirror disabled leaves result untouched`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val input = photoResult(
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(
                    customTags = mapOf(
                        "captureLensFacing" to "front",
                        "selfieMirrorEnabled" to "on",
                        "selfieMirrorApply" to "false"
                    )
                )
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `non photo result is ignored`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val input = photoResult(
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary(
                metadata = MediaMetadata(
                    customTags = mapOf("selfieMirrorApply" to "true")
                )
            )
        )
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    // ── Editor result characterization ──────────────────────────────────────

    @Test
    fun `editor Failed mirror-exception produces failure note and structured failure`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("mirror-exception")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val input = photoResult()
        val result = processor.process(input)

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:mirror-exception") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertEquals(1, result.structuredPostProcessFailures.size, "Should have one structured failure")
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureStage.SELFIE_MIRROR, failure.stage)
        assertEquals(PostProcessFailureCause.BITMAP_OPERATION, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `editor Failed decode-failed produces failure note and structured failure`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:decode-failed") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureCause.DECODE_FAILED, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `editor Failed output-unavailable produces structured failure with possibly-modified`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("output-unavailable")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:output-unavailable") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, failure.cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, failure.integrity)
        assertEquals(PostProcessFailureDisposition.RECOVERABLE, failure.disposition)
    }

    @Test
    fun `editor Failed unknown reason produces structured failure with EXCEPTION cause`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("unknown-reason")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:unknown-reason") },
            "Should contain legacy failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
        val failure = result.structuredPostProcessFailures.single()
        assertEquals(PostProcessFailureCause.EXCEPTION, failure.cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, failure.integrity)
    }

    @Test
    fun `editor Skipped result produces skip note without structured failure`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Skipped("input-unavailable")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:skipped:input-unavailable") },
            "Should contain skip note: ${result.pipelineNotes}")
        assertTrue(result.structuredPostProcessFailures.isEmpty(),
            "Skipped should not produce structured failures")
    }

    @Test
    fun `editor Applied with warning produces both applied and warning notes`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied(warning = "exif-restore-partial")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.pipelineNotes.contains("selfie-mirror:applied"),
            "Should contain applied note: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:warning:exif-restore-partial") },
            "Should contain warning note: ${result.pipelineNotes}")
        assertTrue(result.structuredPostProcessFailures.isEmpty(),
            "Success should not produce structured failures")
    }

    @Test
    fun `skipped input does not produce structured failure`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = PhotoSelfieMirrorApplied()
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        assertTrue(result.structuredPostProcessFailures.isEmpty())
        assertTrue(result.pipelineNotes.none { it.contains(":failed:") })
    }

    @Test
    fun `editor not invoked when output handle has no usable target`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("mirror-exception")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(displayPath = "relative/photo.jpg")
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.any { it.contains("missing-output-handle") },
            "Should skip with missing-output-handle note: ${result.pipelineNotes}")
        assertTrue(result.structuredPostProcessFailures.isEmpty(),
            "Skip should not produce structured failures")
    }

    @Test
    fun `editor Failed result produces failure note with absolute output handle`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("mirror-exception")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(displayPath = "/tmp/no-handle.jpg")
            )
        )

        assertEquals(1, editor.invocations.size)
        assertTrue(result.pipelineNotes.any { it.contains("selfie-mirror:failed:mirror-exception") },
            "Should contain failure note: ${result.pipelineNotes}")
        assertEquals(1, result.structuredPostProcessFailures.size)
    }

    // ── Structured failure mapping ──────────────────────────────────────────

    @Test
    fun `selfieMirrorFailureMapping maps decode-failed correctly`() {
        val (cause, integrity) = selfieMirrorFailureMapping("decode-failed")
        assertEquals(PostProcessFailureCause.DECODE_FAILED, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `selfieMirrorFailureMapping maps mirror-exception correctly`() {
        val (cause, integrity) = selfieMirrorFailureMapping("mirror-exception")
        assertEquals(PostProcessFailureCause.BITMAP_OPERATION, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    @Test
    fun `selfieMirrorFailureMapping maps output-unavailable correctly`() {
        val (cause, integrity) = selfieMirrorFailureMapping("output-unavailable")
        assertEquals(PostProcessFailureCause.OUTPUT_UNAVAILABLE, cause)
        assertEquals(PostProcessOutputIntegrity.POSSIBLY_MODIFIED, integrity)
    }

    @Test
    fun `selfieMirrorFailureMapping maps unknown reason to EXCEPTION`() {
        val (cause, integrity) = selfieMirrorFailureMapping("something-else")
        assertEquals(PostProcessFailureCause.EXCEPTION, cause)
        assertEquals(PostProcessOutputIntegrity.ORIGINAL_INTACT, integrity)
    }

    // ── Legacy note compatibility ───────────────────────────────────────────

    @Test
    fun `structured failure legacy projection matches legacy pipeline note`() = runTest {
        val editor = FakePhotoSelfieMirrorEditor(
            result = ProcessorEditorResult.Failed("decode-failed")
        )
        val processor = PhotoSelfieMirrorPostProcessor(editor)
        val result = processor.process(photoResult())

        val legacyNote = result.structuredPostProcessFailures.single().toLegacyNote()
        assertTrue(result.pipelineNotes.contains(legacyNote),
            "Legacy projection '$legacyNote' should be among pipeline notes: ${result.pipelineNotes}")
    }

    private fun photoResult(
        mediaType: MediaType = MediaType.PHOTO,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/selfie.jpg",
            filePath = "/tmp/selfie.jpg"
        ),
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = mapOf(
                    "captureLensFacing" to "front",
                    "selfieMirrorEnabled" to "on",
                    "selfieMirrorApply" to "true"
                )
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-selfie",
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

    private class FakePhotoSelfieMirrorEditor(
        private val result: ProcessorEditorResult
    ) : PhotoSelfieMirrorEditor {
        val invocations = mutableListOf<ProcessorTarget>()

        override suspend fun apply(target: ProcessorTarget): ProcessorEditorResult {
            invocations += target
            return result
        }
    }
}
