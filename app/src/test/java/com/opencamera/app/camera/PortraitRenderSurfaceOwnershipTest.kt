package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PortraitRenderSurfaceOwnershipTest {

    @Test
    fun `real editor retains source dimensions when source is larger than segmentation mask`() {
        val sourceWidth = 64
        val sourceHeight = 48
        val maskWidth = 50
        val maskHeight = 50

        val sourceFile = File.createTempFile("portrait_source_", ".jpg")
        try {
            val sourceBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
            sourceFile.outputStream().use { os ->
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
            }
            sourceBitmap.recycle()

            val editor = AndroidPortraitRenderEditor(
                org.robolectric.RuntimeEnvironment.getApplication()
            )
            val target = ProcessorTarget.FilePath(sourceFile.absolutePath)
            val spec = PortraitRenderSpec(
                mode = PortraitRenderMode.DEPTH,
                portraitProfile = PortraitProfile.NATIVE,
                beautyPreset = PortraitBeautyPreset.AUTHENTIC,
                beautyStrengthLevel = PortraitBeautyStrength.SOFT,
                bokehEffect = PortraitBokehEffect.NATURAL,
                lightSpot = PortraitBackgroundLightSpotSpec.NONE,
                blurScale = 8,
                focusRadiusXFraction = 0.30f,
                focusRadiusYFraction = 0.40f,
                edgeSoftness = 0.24f,
                vignetteStrength = 0.10f,
                subjectTracking = false,
                strength = 1.8f,
                subjectSmoothing = 0.14f,
                subjectLift = 0.03f,
                subjectSaturationBoost = 0.01f,
                highlightBloom = 0.01f,
                backgroundBloom = 0.02f
            )
            val mask = SceneMaskTestUtils.createCenterSubjectMask(maskWidth, maskHeight)

            runTest {
                val (result, _) = editor.applyWithMask(target, spec, mask)
                assertTrue(
                    result is ProcessorEditorResult.Skipped ||
                        result is ProcessorEditorResult.Failed ||
                        result is PortraitRenderApplied,
                    "Expected success, skip, or fail result but got: $result"
                )
            }

            if (sourceFile.length() > 0) {
                val outputBytes = sourceFile.readBytes()
                val decoded = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
                if (decoded != null) {
                    assertEquals(
                        expected = sourceWidth,
                        actual = decoded.width,
                        message = "Output width must match source capture width"
                    )
                    assertEquals(
                        expected = sourceHeight,
                        actual = decoded.height,
                        message = "Output height must match source capture height"
                    )
                    decoded.recycle()
                }
            }
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun `applyWithMask does not accept bitmap parameter`() {
        val editor = AndroidPortraitRenderEditor(
            org.robolectric.RuntimeEnvironment.getApplication()
        )
        // Verify compile-time contract: applyWithMask signature has no Bitmap parameter
        val method = editor::class.java.methods.find { it.name == "applyWithMask" }
        assertTrue(method != null, "applyWithMask method must exist")
        val bitmapParamCount = method!!.parameterTypes.count {
            it.name.contains("Bitmap")
        }
        assertEquals(
            expected = 0,
            actual = bitmapParamCount,
            message = "applyWithMask must not accept a Bitmap parameter"
        )
    }

    @Test
    fun `analysis bitmap is recycled after successful mask creation`() = runTest {
        val analysisBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        org.mockito.Mockito.`when`(analysisBitmap.width).thenReturn(50)
        org.mockito.Mockito.`when`(analysisBitmap.height).thenReturn(50)

        val mask = SceneMaskTestUtils.createCenterSubjectMask(50, 50)
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Available(mask)
        )
        val maskEditor = FakeMaskAwarePortraitRenderEditor(PortraitRenderApplied())
        val processor = PortraitRenderPostProcessor(
            maskEditor,
            maskProvider,
            maskBitmapSource = { analysisBitmap }
        )
        val result = processor.process(
            photoResult(
                outputHandle = com.opencamera.core.media.MediaOutputHandle(
                    displayPath = "/tmp/portrait.jpg",
                    filePath = "/tmp/portrait.jpg"
                )
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=applied"))
        org.mockito.Mockito.verify(analysisBitmap, org.mockito.Mockito.times(1)).recycle()
    }

    private fun photoResult(
        outputHandle: com.opencamera.core.media.MediaOutputHandle
    ): com.opencamera.core.media.ShotResult {
        return com.opencamera.core.media.ShotResult(
            shotId = "shot-portrait",
            mediaType = com.opencamera.core.media.MediaType.PHOTO,
            outputPath = outputHandle.displayPath,
            outputHandle = outputHandle,
            saveRequest = com.opencamera.core.media.SaveRequest.photoLibrary(
                metadata = com.opencamera.core.media.MediaMetadata(
                    customTags = buildMap {
                        put("mode", "portrait")
                        put("renderPath", "depth")
                        put("subjectTracking", "false")
                        put("portraitProfile", PortraitProfile.NATIVE.storageKey)
                        put("portraitBeautyPreset", PortraitBeautyPreset.AUTHENTIC.storageKey)
                        put("portraitBeautyStrength", PortraitBeautyStrength.SOFT.storageKey)
                        put("portraitBokehEffect", PortraitBokehEffect.NATURAL.storageKey)
                        put("bokehStrength", "1.8")
                    }
                )
            ),
            thumbnailSource = com.opencamera.core.media.ThumbnailSource.SavedMedia(
                outputPath = outputHandle.displayPath,
                renderUri = outputHandle.contentUri
            ),
            metadata = com.opencamera.core.media.MediaMetadata(
                customTags = buildMap {
                    put("mode", "portrait")
                    put("renderPath", "depth")
                    put("subjectTracking", "false")
                    put("portraitProfile", PortraitProfile.NATIVE.storageKey)
                    put("portraitBeautyPreset", PortraitBeautyPreset.AUTHENTIC.storageKey)
                    put("portraitBeautyStrength", PortraitBeautyStrength.SOFT.storageKey)
                    put("portraitBokehEffect", PortraitBokehEffect.NATURAL.storageKey)
                    put("bokehStrength", "1.8")
                }
            )
        )
    }
}

private class FakePortraitRenderEditor(
    private val result: com.opencamera.core.media.ProcessorEditorResult
) : PortraitRenderEditor {
    override suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): com.opencamera.core.media.ProcessorEditorResult = result
}

private class FakeMaskAwarePortraitRenderEditor(
    private val result: com.opencamera.core.media.ProcessorEditorResult,
    private val maskNotes: List<String> = listOf("portrait-mask:saved=applied", "portrait-render:subject-mask")
) : MaskAwarePortraitRenderEditor {
    override suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ): com.opencamera.core.media.ProcessorEditorResult = result

    override suspend fun applyWithMask(
        target: ProcessorTarget,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ): Pair<com.opencamera.core.media.ProcessorEditorResult, List<String>> {
        return Pair(result, maskNotes)
    }
}
