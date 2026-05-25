package com.opencamera.app.camera

import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
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
            result = PortraitRenderApplied()
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
            ProcessorTarget.ContentUri("content://media/external/images/media/64"),
            invocation.target
        )
        assertEquals(PortraitRenderMode.DEPTH, invocation.spec.mode)
        assertEquals(PortraitProfile.NATIVE, invocation.spec.portraitProfile)
        assertTrue(result.pipelineNotes.contains("portrait-render:applied:depth"))
    }

    @Test
    fun `non portrait result is ignored`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
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
            result = PortraitRenderApplied()
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
            result = PortraitRenderApplied()
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
            result = ProcessorEditorResult.Failed("decode-failed")
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

    // --- Profile responsibility tests ---

    @Test
    fun `changing portrait profile changes subject and background recipe`() {
        val base = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )
        val luminous = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.LUMINOUS,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )

        assertNotNull(base)
        assertNotNull(luminous)
        // LUMINOUS adds subject lift
        assertTrue(luminous.subjectLift > base.subjectLift)
        // LUMINOUS adds bloom
        assertTrue(luminous.highlightBloom >= base.highlightBloom)
        // LUMINOUS changes vignette
        assertTrue(luminous.vignetteStrength > base.vignetteStrength)
        // LUMINOUS increases overall strength offset
        assertTrue(luminous.strength > base.strength)
    }

    @Test
    fun `changing beauty preset affects only subject beauty fields`() {
        val authentic = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val radiant = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )

        assertNotNull(authentic)
        assertNotNull(radiant)
        // Radiant has more smoothing and lift
        assertTrue(radiant.subjectSmoothing > authentic.subjectSmoothing)
        assertTrue(radiant.subjectLift > authentic.subjectLift)
        assertTrue(radiant.subjectSaturationBoost > authentic.subjectSaturationBoost)
        // Background-only blur params stay identical (same bokehEffect + same profile)
        assertEquals(authentic.blurScale, radiant.blurScale)
        assertEquals(authentic.edgeSoftness, radiant.edgeSoftness)
    }

    @Test
    fun `changing beauty strength scales subject fields without altering background`() {
        val off = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.OFF,
            bokehEffect = PortraitBokehEffect.CREAMY
        )
        val elevated = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.ELEVATED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )

        assertNotNull(off)
        assertNotNull(elevated)
        // OFF strength means zero beauty intensity
        assertEquals(0f, off.subjectSmoothing)
        assertEquals(0f, off.subjectSaturationBoost)
        // Elevated has non-zero beauty
        assertTrue(elevated.subjectSmoothing > 0f)
        // Background blur params are unchanged by beauty strength
        assertEquals(off.blurScale, elevated.blurScale)
        assertEquals(off.edgeSoftness, elevated.edgeSoftness)
        assertEquals(off.focusRadiusXFraction, elevated.focusRadiusXFraction)
        assertEquals(off.focusRadiusYFraction, elevated.focusRadiusYFraction)
    }

    @Test
    fun `changing bokeh effect affects only background blur and light spot fields`() {
        val natural = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val dreamy = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 1.8f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )

        assertNotNull(natural)
        assertNotNull(dreamy)
        // Dreamy has different blur geometry
        assertTrue(dreamy.blurScale != natural.blurScale || dreamy.edgeSoftness != natural.edgeSoftness)
        assertTrue(dreamy.focusRadiusXFraction < natural.focusRadiusXFraction)
        // Subject beauty fields should be identical (same preset + strength + profile)
        assertEquals(natural.subjectSmoothing, dreamy.subjectSmoothing)
        assertEquals(natural.subjectLift, dreamy.subjectLift)
        assertEquals(natural.subjectSaturationBoost, dreamy.subjectSaturationBoost)
    }

    @Test
    fun `focus path bokeh effect also only affects background fields`() {
        val natural = resolvePortraitRenderSpec(
            renderPath = "focus",
            bokehStrength = null,
            subjectTracking = false,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val creamy = resolvePortraitRenderSpec(
            renderPath = "focus",
            bokehStrength = null,
            subjectTracking = false,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.RADIANT,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.CREAMY
        )

        assertNotNull(natural)
        assertNotNull(creamy)
        // Background blur differs
        assertTrue(creamy.blurScale != natural.blurScale)
        assertTrue(creamy.edgeSoftness > natural.edgeSoftness)
        // Subject beauty identical
        assertEquals(natural.subjectSmoothing, creamy.subjectSmoothing)
        assertEquals(natural.subjectLift, creamy.subjectLift)
    }

    // --- Light-spot spec mapping tests ---

    @Test
    fun `bokeh effect maps to correct light spot spec`() {
        val natural = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val creamy = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )
        val dreamy = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )

        assertNotNull(natural)
        assertNotNull(creamy)
        assertNotNull(dreamy)
        assertEquals(PortraitBackgroundLightSpotSpec.NONE, natural.lightSpot)
        assertEquals(PortraitBackgroundLightSpotSpec.SUBTLE, creamy.lightSpot)
        assertEquals(PortraitBackgroundLightSpotSpec.DREAMY, dreamy.lightSpot)
    }

    @Test
    fun `light spot mapping does not alter subject beauty fields`() {
        val natural = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL
        )
        val dreamy = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.DREAMY
        )

        assertNotNull(natural)
        assertNotNull(dreamy)
        assertEquals(natural.subjectSmoothing, dreamy.subjectSmoothing)
        assertEquals(natural.subjectLift, dreamy.subjectLift)
        assertEquals(natural.subjectSaturationBoost, dreamy.subjectSaturationBoost)
    }

    @Test
    fun `focus path light spot mapping matches depth path`() {
        val depth = resolvePortraitRenderSpec(
            renderPath = "depth", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )
        val focus = resolvePortraitRenderSpec(
            renderPath = "focus", bokehStrength = 1.8f, subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE, beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.CREAMY
        )

        assertNotNull(depth)
        assertNotNull(focus)
        assertEquals(depth.lightSpot, focus.lightSpot)
        assertEquals(PortraitBackgroundLightSpotSpec.SUBTLE, focus.lightSpot)
    }

    @Test
    fun `light spot layer note is present in pipeline output`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(
                bokehEffect = PortraitBokehEffect.DREAMY,
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-layer:light-spot=dreamy"))
    }

    @Test
    fun `natural bokeh produces none light spot layer note`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        val result = processor.process(
            photoResult(
                bokehEffect = PortraitBokehEffect.NATURAL,
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-layer:light-spot=none"))
    }

    // --- Mask-aware routing tests ---

    @Test
    fun `available mask routes to mask-aware editor`() = runTest {
        val maskEditor = FakeMaskAwarePortraitRenderEditor(
            result = PortraitRenderApplied(),
            maskNotes = listOf("portrait-mask:saved=applied", "portrait-render:subject-mask")
        )
        val mask = SceneMaskTestUtils.createCenterSubjectMask(50, 50)
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Available(mask)
        )
        val processor = PortraitRenderPostProcessor(
            maskEditor,
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertEquals(1, maskEditor.maskInvocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=applied"))
        assertTrue(result.pipelineNotes.contains("portrait-render:subject-mask"))
    }

    @Test
    fun `unavailable mask falls back and records degraded note`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
        )
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Unavailable("no-person-detected")
        )
        val processor = PortraitRenderPostProcessor(
            editor,
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:mask-unavailable"))
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"))
    }

    @Test
    fun `no mask provider falls back and records degraded note`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
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

        assertEquals(1, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:no-provider"))
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"))
    }

    @Test
    fun `mask-aware path preserves portrait mode metadata and downstream result`() = runTest {
        val maskEditor = FakeMaskAwarePortraitRenderEditor(
            result = PortraitRenderApplied(),
            maskNotes = listOf("portrait-mask:saved=applied", "portrait-render:subject-mask")
        )
        val mask = SceneMaskTestUtils.createCenterSubjectMask(50, 50)
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Available(mask)
        )
        val processor = PortraitRenderPostProcessor(
            maskEditor,
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-render:applied:depth"))
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=applied"))
        assertEquals("portrait", result.metadata.customTags["mode"])
    }

    @Test
    fun `failed mask falls back and records degraded note`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
        )
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Failed("segmentation-exception")
        )
        val processor = PortraitRenderPostProcessor(
            editor,
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:mask-unavailable"))
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"))
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
        private val result: ProcessorEditorResult
    ) : PortraitRenderEditor {
        val invocations = mutableListOf<Invocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PortraitRenderSpec
        ): ProcessorEditorResult {
            invocations += Invocation(target, spec)
            return result
        }
    }

    private data class Invocation(
        val target: ProcessorTarget,
        val spec: PortraitRenderSpec
    )

    private class FakeMaskAwarePortraitRenderEditor(
        private val result: ProcessorEditorResult,
        private val maskNotes: List<String> = emptyList()
    ) : MaskAwarePortraitRenderEditor {
        val invocations = mutableListOf<Invocation>()
        val maskInvocations = mutableListOf<MaskInvocation>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PortraitRenderSpec
        ): ProcessorEditorResult {
            invocations += Invocation(target, spec)
            return result
        }

        override suspend fun applyWithMask(
            bitmap: android.graphics.Bitmap,
            spec: PortraitRenderSpec,
            mask: SavedPhotoMaskPixels
        ): Pair<ProcessorEditorResult, List<String>> {
            maskInvocations += MaskInvocation(spec, mask)
            return Pair(result, maskNotes)
        }
    }

    private data class MaskInvocation(
        val spec: PortraitRenderSpec,
        val mask: SavedPhotoMaskPixels
    )

    @Test
    fun `lower depth strength produces less background blur`() {
        val low = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 0
        )
        val mid = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 50
        )

        assertNotNull(low)
        assertNotNull(mid)
        assertTrue(low.blurScale >= mid.blurScale)
        assertTrue(low.focusRadiusXFraction >= mid.focusRadiusXFraction)
        assertTrue(low.edgeSoftness <= mid.edgeSoftness)
    }

    @Test
    fun `higher depth strength produces stronger background blur`() {
        val mid = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 50
        )
        val high = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 100
        )

        assertNotNull(mid)
        assertNotNull(high)
        assertTrue(high.blurScale <= mid.blurScale)
        assertTrue(high.focusRadiusXFraction <= mid.focusRadiusXFraction)
        assertTrue(high.edgeSoftness >= mid.edgeSoftness)
        assertTrue(high.backgroundBloom >= mid.backgroundBloom)
    }

    @Test
    fun `depth strength does not change subject beauty values`() {
        val low = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 0
        )
        val high = resolvePortraitRenderSpec(
            renderPath = "depth",
            bokehStrength = 2.0f,
            subjectTracking = true,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.CLEAR,
            beautyStrength = PortraitBeautyStrength.BALANCED,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 100
        )

        assertNotNull(low)
        assertNotNull(high)
        assertEquals(low.subjectSmoothing, high.subjectSmoothing)
        assertEquals(low.subjectLift, high.subjectLift)
        assertEquals(low.subjectSaturationBoost, high.subjectSaturationBoost)
        assertEquals(low.strength, high.strength)
    }

    @Test
    fun `focus path depth strength modulates blur parameters`() {
        val low = resolvePortraitRenderSpec(
            renderPath = "focus",
            bokehStrength = 1.0f,
            subjectTracking = false,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 0
        )
        val high = resolvePortraitRenderSpec(
            renderPath = "focus",
            bokehStrength = 1.0f,
            subjectTracking = false,
            portraitProfile = PortraitProfile.NATIVE,
            beautyPreset = PortraitBeautyPreset.AUTHENTIC,
            beautyStrength = PortraitBeautyStrength.SOFT,
            bokehEffect = PortraitBokehEffect.NATURAL,
            depthStrength = 100
        )

        assertNotNull(low)
        assertNotNull(high)
        assertTrue(low.blurScale >= high.blurScale)
        assertTrue(low.focusRadiusXFraction >= high.focusRadiusXFraction)
        assertTrue(low.edgeSoftness <= high.edgeSoftness)
    }

    @Test
    fun `portrait depth strength from metadata tag is consumed`() = runTest {
        val editor = FakePortraitRenderEditor(
            result = PortraitRenderApplied()
        )
        val processor = PortraitRenderPostProcessor(editor)
        processor.process(
            photoResult(
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertEquals(1, editor.invocations.size)
        val spec = editor.invocations.single().spec
        assertEquals(PortraitRenderMode.DEPTH, spec.mode)
    }
}
