package com.opencamera.app.camera

import com.opencamera.core.media.AlgorithmNode
import com.opencamera.core.media.AlgorithmRequest
import com.opencamera.core.media.AlgorithmRequirement
import com.opencamera.core.media.AlgorithmFallback
import com.opencamera.core.media.AlgorithmResult
import com.opencamera.core.media.AlgorithmType
import com.opencamera.core.media.MediaInputRef
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AlgorithmProcessorBridgesTest {

    // --- Fake editors that record invocations ---

    private class RecordingPhotoAlgorithmEditor(
        private val result: ProcessorEditorResult = PhotoAlgorithmApplied()
    ) : PhotoAlgorithmEditor {
        var lastTarget: ProcessorTarget? = null
            private set
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult {
            lastTarget = target
            callCount++
            return result
        }
    }

    private class RecordingWatermarkEditor(
        private val result: ProcessorEditorResult = PhotoWatermarkApplied()
    ) : PhotoWatermarkEditor {
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget,
            metadata: MediaMetadata,
            watermarkText: String,
            templateId: String
        ): ProcessorEditorResult {
            callCount++
            return result
        }
    }

    private class RecordingPortraitEditor(
        private val result: ProcessorEditorResult = PortraitRenderApplied()
    ) : PortraitRenderEditor {
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PortraitRenderSpec
        ): ProcessorEditorResult {
            callCount++
            return result
        }
    }

    private class RecordingDocumentEditor(
        private val result: ProcessorEditorResult = DocumentAutoCropApplied(
            cropBounds = android.graphics.Rect(10, 10, 90, 90)
        )
    ) : DocumentAutoCropEditor {
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget
        ): ProcessorEditorResult {
            callCount++
            return result
        }
    }

    private class RecordingFrameRatioEditor(
        private val result: ProcessorEditorResult = PhotoFrameRatioApplied(
            frameRatio = com.opencamera.core.media.FrameRatio.RATIO_16_9,
            cropBounds = CropBounds(0, 0, 100, 100)
        )
    ) : PhotoFrameRatioEditor {
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget,
            frameRatio: com.opencamera.core.media.FrameRatio
        ): ProcessorEditorResult {
            callCount++
            return result
        }
    }

    private class RecordingSelfieMirrorEditor(
        private val result: ProcessorEditorResult = PhotoSelfieMirrorApplied()
    ) : PhotoSelfieMirrorEditor {
        var callCount = 0
            private set

        override suspend fun apply(
            target: ProcessorTarget
        ): ProcessorEditorResult {
            callCount++
            return result
        }
    }

    // --- Helpers ---

    private fun sampleNode(
        id: String = "shot-1",
        type: AlgorithmType = AlgorithmType.FILTER_RENDER,
        output: String = "/tmp/output.jpg"
    ) = AlgorithmNode(
        id = id,
        type = type,
        inputs = listOf("shot-1:primary"),
        output = output,
        requirement = AlgorithmRequirement.REQUIRED,
        fallback = AlgorithmFallback.FAIL_SHOT
    )

    private fun sampleRequest(
        node: AlgorithmNode = sampleNode(),
        customTags: Map<String, String> = emptyMap(),
        algorithmProfile: String? = null,
        watermarkText: String? = null,
        inputs: List<MediaInputRef> = listOf(
            MediaInputRef(
                path = "/tmp/input.jpg",
                handle = MediaOutputHandle(displayPath = "/tmp/input.jpg"),
                mimeType = "image/jpeg"
            )
        )
    ) = AlgorithmRequest(
        node = node,
        inputs = inputs,
        metadata = MediaMetadata(
            algorithmProfile = algorithmProfile,
            watermarkText = watermarkText,
            customTags = customTags
        )
    )

    // --- PhotoAlgorithmPostProcessor bridge ---

    @Test
    fun `photo algorithm bridge type is FILTER_RENDER`() {
        val processor = RecordingPhotoAlgorithmEditor().let {
            PhotoAlgorithmPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.FILTER_RENDER, processor.type)
    }

    @Test
    fun `photo algorithm canProcess true with algorithmProfile`() {
        val processor = RecordingPhotoAlgorithmEditor().let {
            PhotoAlgorithmPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(algorithmProfile = "vivid")))
    }

    @Test
    fun `photo algorithm canProcess true with filterProfile tag`() {
        val processor = RecordingPhotoAlgorithmEditor().let {
            PhotoAlgorithmPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(
            customTags = mapOf("filterProfile" to "custom-vivid")
        )))
    }

    @Test
    fun `photo algorithm canProcess false without profile`() {
        val processor = RecordingPhotoAlgorithmEditor().let {
            PhotoAlgorithmPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest()))
    }

    @Test
    fun `photo algorithm process returns Applied`() = runTest {
        val tempFile = kotlin.io.path.createTempFile(prefix = "test", suffix = ".jpg").toFile()
        try {
            val editor = RecordingPhotoAlgorithmEditor()
            val processor = PhotoAlgorithmPostProcessor(editor).toAlgorithmProcessor()
            val result = processor.process(sampleRequest(
                algorithmProfile = "photo-vivid",
                inputs = listOf(
                    MediaInputRef(
                        path = tempFile.absolutePath,
                        handle = MediaOutputHandle(displayPath = tempFile.absolutePath),
                        mimeType = "image/jpeg"
                    )
                )
            ))
            assertIs<AlgorithmResult.Applied>(result)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `photo algorithm process returns Skipped when no profile`() = runTest {
        val editor = RecordingPhotoAlgorithmEditor()
        val processor = PhotoAlgorithmPostProcessor(editor).toAlgorithmProcessor()
        val result = processor.process(sampleRequest())
        assertIs<AlgorithmResult.Skipped>(result)
        assertEquals(0, editor.callCount)
    }

    // --- PhotoWatermarkPostProcessor bridge ---

    @Test
    fun `watermark bridge type is WATERMARK_RENDER`() {
        val processor = RecordingWatermarkEditor().let {
            PhotoWatermarkPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.WATERMARK_RENDER, processor.type)
    }

    @Test
    fun `watermark canProcess true with watermarkText`() {
        val processor = RecordingWatermarkEditor().let {
            PhotoWatermarkPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(watermarkText = "travel")))
    }

    @Test
    fun `watermark canProcess false without watermarkText`() {
        val processor = RecordingWatermarkEditor().let {
            PhotoWatermarkPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest()))
    }

    @Test
    fun `watermark process returns Applied`() = runTest {
        val editor = RecordingWatermarkEditor()
        val processor = PhotoWatermarkPostProcessor(editor).toAlgorithmProcessor()
        val result = processor.process(sampleRequest(watermarkText = "travel"))
        assertIs<AlgorithmResult.Applied>(result)
        assertEquals(1, editor.callCount)
    }

    // --- PortraitRenderPostProcessor bridge ---

    @Test
    fun `portrait bridge type is PORTRAIT_RENDER`() {
        val processor = RecordingPortraitEditor().let {
            PortraitRenderPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.PORTRAIT_RENDER, processor.type)
    }

    @Test
    fun `portrait canProcess true with portrait mode`() {
        val processor = RecordingPortraitEditor().let {
            PortraitRenderPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(
            customTags = mapOf("mode" to "portrait", "renderPath" to "/tmp/render.jpg")
        )))
    }

    @Test
    fun `portrait canProcess false without portrait mode`() {
        val processor = RecordingPortraitEditor().let {
            PortraitRenderPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest()))
    }

    // --- DocumentAutoCropPostProcessor bridge ---

    @Test
    fun `document bridge type is DOCUMENT_ENHANCE`() {
        val processor = RecordingDocumentEditor().let {
            DocumentAutoCropPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.DOCUMENT_ENHANCE, processor.type)
    }

    @Test
    fun `document canProcess true with document mode and autoCrop`() {
        val processor = RecordingDocumentEditor().let {
            DocumentAutoCropPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(
            customTags = mapOf("mode" to "document", "autoCrop" to "true")
        )))
    }

    @Test
    fun `document canProcess false without autoCrop`() {
        val processor = RecordingDocumentEditor().let {
            DocumentAutoCropPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest(
            customTags = mapOf("mode" to "document")
        )))
    }

    // --- PhotoFrameRatioPostProcessor bridge ---

    @Test
    fun `frame ratio bridge type is THUMBNAIL_SELECT`() {
        val processor = RecordingFrameRatioEditor().let {
            PhotoFrameRatioPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.THUMBNAIL_SELECT, processor.type)
    }

    @Test
    fun `frame ratio canProcess true with frameRatio tag`() {
        val processor = RecordingFrameRatioEditor().let {
            PhotoFrameRatioPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(
            customTags = mapOf("frameRatio" to "16:9")
        )))
    }

    @Test
    fun `frame ratio canProcess false without frameRatio tag`() {
        val processor = RecordingFrameRatioEditor().let {
            PhotoFrameRatioPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest()))
    }

    // --- PhotoSelfieMirrorPostProcessor bridge ---

    @Test
    fun `selfie mirror bridge type is FILTER_RENDER`() {
        val processor = RecordingSelfieMirrorEditor().let {
            PhotoSelfieMirrorPostProcessor(it).toAlgorithmProcessor()
        }
        assertEquals(AlgorithmType.FILTER_RENDER, processor.type)
    }

    @Test
    fun `selfie mirror canProcess true with selfieMirrorApply tag`() {
        val processor = RecordingSelfieMirrorEditor().let {
            PhotoSelfieMirrorPostProcessor(it).toAlgorithmProcessor()
        }
        assertTrue(processor.canProcess(sampleRequest(
            customTags = mapOf("selfieMirrorApply" to "true")
        )))
    }

    @Test
    fun `selfie mirror canProcess false without tag`() {
        val processor = RecordingSelfieMirrorEditor().let {
            PhotoSelfieMirrorPostProcessor(it).toAlgorithmProcessor()
        }
        assertFalse(processor.canProcess(sampleRequest()))
    }
}
