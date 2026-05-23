package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.toMetadataTags
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PhotoAlgorithmPostProcessorTest {
    @Test
    fun `recognized profile with content uri is rendered`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmEditorResult.Applied()
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
            PhotoAlgorithmTarget.ContentUri("content://media/external/images/media/88"),
            invocation.target
        )
        assertEquals("document-whiteboard-scan", invocation.spec.profile)
        assertTrue(result.pipelineNotes.contains("algorithm-render:applied:document-whiteboard-scan"))
    }

    @Test
    fun `missing algorithm profile leaves result untouched`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmEditorResult.Applied()
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
            result = PhotoAlgorithmEditorResult.Applied()
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
            result = PhotoAlgorithmEditorResult.Applied()
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
            result = PhotoAlgorithmEditorResult.Failed("decode-failed")
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
    fun `shared custom filter spec is rendered without built in algorithm mapping`() = runTest {
        val editor = FakePhotoAlgorithmEditor(
            result = PhotoAlgorithmEditorResult.Applied()
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

        assertNotNull(photo)
        assertNotNull(photoRich)
        assertNotNull(document)
        assertNotNull(portrait)
        assertNotNull(night)
        assertNotNull(pro)
    }

    @Test
    fun `custom vivid metadata from capture log is rendered`() = runTest {
        val editor = FakePhotoAlgorithmEditor(PhotoAlgorithmEditorResult.Applied())
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
        private val result: PhotoAlgorithmEditorResult
    ) : PhotoAlgorithmEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: PhotoAlgorithmTarget,
            spec: PhotoAlgorithmSpec
        ): PhotoAlgorithmEditorResult {
            invocations += Invocation(target, spec)
            return result
        }
    }

    private data class Invocation(
        val target: PhotoAlgorithmTarget,
        val spec: PhotoAlgorithmSpec
    )
}
