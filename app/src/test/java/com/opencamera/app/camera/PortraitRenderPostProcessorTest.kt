package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PortraitRenderPostProcessorTest {
    @Test
    fun `portrait depth render with content uri is applied`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderEditorResult.Applied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Portrait/OpenCamera_PORTRAIT_1.jpg",
                    contentUri = "content://media/external/images/media/64"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val invocation = editor.invocations.single()
        assertEquals(
            PortraitRenderTarget.ContentUri("content://media/external/images/media/64"),
            invocation.target
        )
        assertEquals(PortraitRenderMode.DEPTH, invocation.spec.mode)
        assertEquals(PortraitProfile.NATIVE, invocation.spec.portraitProfile)
        assertTrue(result.pipelineNotes.contains("portrait-render:applied:depth"))
    }

    @Test
    fun `non portrait result is ignored`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderEditorResult.Applied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val input = photoResult(mode = "photo")
        val result = processor.process(input)

        assertEquals(0, editor.invocations.size)
        assertEquals(input, result)
    }

    @Test
    fun `missing editable handle records diagnostic skip`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderEditorResult.Applied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "Pictures/OpenCamera/Portrait/OpenCamera_PORTRAIT_2.jpg"
                )
            )
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-render:skipped:missing-output-handle"))
    }

    @Test
    fun `unsupported render path records diagnostic skip`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderEditorResult.Applied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(renderPath = "unknown")
        )

        assertEquals(0, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-render:skipped:unsupported-render-path"))
    }

    @Test
    fun `editor failure is captured as pipeline diagnostic`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderEditorResult.Failed("decode-failed")
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-render:failed:decode-failed"))
    }

    @Test
    fun `spec resolution covers depth and focus render paths`() {
        val depth = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.4f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val focus = resolvePortraitRenderSpec(
            renderPath = "focus",
            bokehStrength = null,
            subjectTracking = false,
            portraitProfile = PortraitProfile.LUMINOUS,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.ELEVATED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )

        assertNotNull(depth)
        assertNotNull(focus)
        assertEquals(PortraitRenderMode.DEPTH, depth.mode)
        assertEquals(PortraitRenderMode.FOCUS, focus.mode)
        assertEquals(PortraitProfile.LUMINOUS, focus.portraitProfile)
        assertEquals(PortraitBeautyPreset.RADIANT, focus.beautyPreset)
        assertEquals(PortraitBeautyStrength.ELEVATED, focus.beautyStrengthLevel)
        assertEquals(PortraitBokehEffect.DREAMY, focus.bokehEffect)
        assertTrue(focus.highlightBloom > depth.highlightBloom)
    }

    @Test
    fun `luminous radiant dreamy spec amplifies beauty and bokeh over native authentic`() {
        val native = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val luminous = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.LUMINOUS,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.ELEVATED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )

        assertNotNull(native)
        assertNotNull(luminous)
        assertTrue(luminous.subjectSmoothing > native.subjectSmoothing)
        assertTrue(luminous.subjectLift > native.subjectLift)
        assertTrue(luminous.highlightBloom > native.highlightBloom)
        assertTrue(luminous.strength > native.strength)
        assertFalse(luminous.focusRadiusXFraction > native.focusRadiusXFraction)
    }

    private fun photoResult(
        mode: String = "portrait",
        renderPath: String = "depth",
        subjectTracking: Boolean = true,
        bokehStrength: Float? = 2.4f,
        portraitProfile: PortraitProfile = PortraitProfile.NATIVE,
        beautyPreset: PortraitBeautyPreset = PortraitBeautyPreset.AUTHENTIC,
        beautyStrength: PortraitBeautyStrength = PortraitBeautyStrength.SOFT,
        bokehEffect: PortraitBokehEffect = PortraitBokehEffect.NATURAL,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = "/tmp/portrait.jpg",
            filePath = "/tmp/portrait.jpg"
        ),
        mediaType: MediaType = MediaType.PHOTO,
        saveRequest: SaveRequest = SaveRequest.photoLibrary(
            metadata = MediaMetadata(
                customTags = buildMap {
                    put("mode", mode)
                    put("renderPath", renderPath)
                    put("subjectTracking", subjectTracking.toString())
                    put("portraitProfile", portraitProfile.storageKey)
                    put("portraitBeautyPreset", beautyPreset.storageKey)
                    put("portraitBeautyStrength", beautyStrength.storageKey)
                    put("portraitBokehEffect", bokehEffect.storageKey)
                    bokehStrength?.let { put("bokehStrength", it.toString()) }
                }
            )
        )
    ): ShotResult {
        return ShotResult(
            shotId = "shot-portrait",
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

    private class FakePortraitRenderEditor(
        private val result: PortraitRenderEditorResult
    ) : PortraitRenderEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: PortraitRenderTarget,
            spec: PortraitRenderSpec
        ): PortraitRenderEditorResult {
            invocations += Invocation(target, spec)
            return result
        }
    }

    private data class Invocation(
        val target: PortraitRenderTarget,
        val spec: PortraitRenderSpec
    )
}
