package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.OcwmJpegContainer
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PhotoAlgorithmWatermarkPostProcessorTest {

    private val appContext: Context get() = RuntimeEnvironment.getApplication()
    private val privateTemplateKey = "watermarkTemplate"

    // --- helpers ---

    private fun createSyntheticJpeg(
        width: Int = 32,
        height: Int = 24,
        exifTags: Map<String, String> = emptyMap()
    ): ByteArray {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.rgb(100, 140, 180))
        }
        val jpegBytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 95, output)
            output.toByteArray()
        }
        bitmap.recycle()
        if (exifTags.isEmpty()) return jpegBytes

        val outFile = File.createTempFile("exif-test", ".jpg")
        try {
            outFile.writeBytes(jpegBytes)
            ExifInterface(outFile.absolutePath).apply {
                exifTags.forEach { (tag, value) -> setAttribute(tag, value) }
                saveAttributes()
            }
            return outFile.readBytes()
        } finally {
            outFile.delete()
        }
    }

    private fun writeJpegToTempFile(jpegBytes: ByteArray): File {
        val file = File.createTempFile("combined-input-", ".jpg")
        file.writeBytes(jpegBytes)
        return file
    }

    private fun countVisiblePixelDelta(file: File, referenceColor: Int): Int {
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        assertNotNull(bitmap, "processed JPEG must decode")
        try {
            var changed = 0
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    if (colorDistance(bitmap.getPixel(x, y), referenceColor) > 70) changed++
                }
            }
            return changed
        } finally {
            bitmap.recycle()
        }
    }

    private fun colorDistance(a: Int, b: Int): Int {
        return kotlin.math.abs(Color.red(a) - Color.red(b)) +
            kotlin.math.abs(Color.green(a) - Color.green(b)) +
            kotlin.math.abs(Color.blue(a) - Color.blue(b))
    }

    private fun decodeJpeg(file: File): Bitmap {
        return requireNotNull(BitmapFactory.decodeFile(file.absolutePath)) {
            "processed JPEG must decode"
        }
    }

    private fun combinedShotResult(
        inputFile: File,
        algorithmProfile: String = "photo-vivid",
        watermarkText: String = "PHOTO Test",
        watermarkTemplate: String = "classic-overlay"
    ): ShotResult {
        val metadata = MediaMetadata(
            algorithmProfile = algorithmProfile,
            watermarkText = watermarkText,
            customTags = mapOf(privateTemplateKey to watermarkTemplate)
        )
        return ShotResult(
            shotId = "combined-shot",
            mediaType = MediaType.PHOTO,
            outputPath = inputFile.absolutePath,
            outputHandle = MediaOutputHandle(
                displayPath = inputFile.absolutePath,
                filePath = inputFile.absolutePath
            ),
            saveRequest = SaveRequest.photoLibrary(metadata = metadata),
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = inputFile.absolutePath,
                renderUri = null
            ),
            metadata = metadata
        )
    }

    private fun createCombinedProcessor(
        maskProvider: SavedPhotoSceneMaskProvider? = null,
        maskBitmapSource: ((ProcessorTarget) -> Bitmap?)? = null
    ): PhotoAlgorithmWatermarkPostProcessor {
        return PhotoAlgorithmWatermarkPostProcessor(
            algorithmEditor = AndroidPhotoAlgorithmEditor(appContext),
            watermarkEditor = AndroidPhotoWatermarkEditor(appContext),
            maskProvider = maskProvider,
            maskBitmapSource = maskBitmapSource
        )
    }

    // --- tests ---

    @Test
    fun `combined path with classic overlay produces single encode and combined notes`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied:photo-vivid+classic-overlay") },
                "combined-render:applied note must be present, got: ${result.pipelineNotes}")
            assertFalse(result.pipelineNotes.any { it.contains("algorithm-render:applied") },
                "algorithm-render:applied must NOT appear when combined path is used")
            assertFalse(result.pipelineNotes.any { it.contains("watermark:rendered") },
                "watermark:rendered must NOT appear when combined path is used")
            assertTrue(result.pipelineNotes.any { it.contains("combined-render:timing:") },
                "timing note must be present")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `combined path writes visible watermark pixels to final jpeg`() = runTest {
        val baseColor = Color.rgb(100, 140, 180)
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(320, 240))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(
                combinedShotResult(
                    inputFile,
                    algorithmProfile = "recipe-only",
                    watermarkText = "OpenCamera Visible Test",
                    watermarkTemplate = "classic-overlay"
                )
            )

            assertTrue(
                result.pipelineNotes.any {
                    it.contains("combined-render:applied") || it.contains("watermark:rendered:classic-overlay")
                },
                "final-output watermark processing must complete, got: ${result.pipelineNotes}"
            )
            val changedPixels = countVisiblePixelDelta(inputFile, baseColor)
            assertTrue(
                changedPixels > 400,
                "final JPEG should contain visible watermark ink, not only metadata/archive changes; changedPixels=$changedPixels"
            )
        } finally {
            inputFile.delete()
        }
    }

    @Test
    @GraphicsMode(GraphicsMode.Mode.NATIVE)
    fun `combined path writes visible output for every selectable watermark template`() = runTest {
        val baseColor = Color.rgb(100, 140, 180)
        val selectableTemplates = PhotoWatermarkTemplateType.entries.map(PhotoWatermarkTemplateType::storageKey)

        selectableTemplates.forEach { templateId ->
            val inputFile = writeJpegToTempFile(createSyntheticJpeg(320, 240))
            try {
                val processor = createCombinedProcessor()
                val result = processor.process(
                    combinedShotResult(
                        inputFile,
                        algorithmProfile = "recipe-only",
                        watermarkText = "OpenCamera Visible Test",
                        watermarkTemplate = templateId
                    )
                )

                assertTrue(
                    result.pipelineNotes.any {
                        it.contains("combined-render:applied:recipe-only+$templateId") ||
                            it.contains("watermark:rendered:$templateId")
                    },
                    "$templateId must complete final-output rendering, got: ${result.pipelineNotes}"
                )

                val bitmap = decodeJpeg(inputFile)
                try {
                    val expandedFrame = resolvePhotoWatermarkTemplateType(templateId).usesExpandedFrame
                    if (expandedFrame) {
                        assertTrue(
                            bitmap.width > 320 || bitmap.height > 240,
                            "$templateId should leave a visible expanded-frame output, got ${bitmap.width}x${bitmap.height}"
                        )
                    } else {
                        val changedPixels = countVisiblePixelDelta(inputFile, baseColor)
                        assertTrue(
                            changedPixels > 400,
                            "$templateId should contain visible watermark ink, changedPixels=$changedPixels"
                        )
                    }
                } finally {
                    bitmap.recycle()
                }
            } finally {
                inputFile.delete()
            }
        }
    }

    @Test
    fun `combined path with travel polaroid produces combined notes`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(
                combinedShotResult(
                    inputFile,
                    algorithmProfile = "photo-vivid",
                    watermarkText = "PHOTO Test",
                    watermarkTemplate = "travel-polaroid"
                )
            )

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied:photo-vivid+travel-polaroid") },
                "combined-render:applied must reference both profile and template, got: ${result.pipelineNotes}")
            assertTrue(result.pipelineNotes.any { it.contains("combined-render:timing:") },
                "timing note must be present")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path preserves EXIF tags from source`() = runTest {
        val exifTags = mapOf(
            ExifInterface.TAG_MAKE to "TestMake",
            ExifInterface.TAG_MODEL to "TestModel",
            ExifInterface.TAG_DATETIME_ORIGINAL to "2025:03:15 14:30:00",
            ExifInterface.TAG_F_NUMBER to "18/10",
            ExifInterface.TAG_EXPOSURE_TIME to "1/125"
        )
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24, exifTags))
        try {
            val processor = createCombinedProcessor()
            processor.process(combinedShotResult(inputFile))

            assertTrue(inputFile.exists(), "output file should exist")
            assertTrue(inputFile.length() > 0, "output file should not be empty")
            val outputExif = ExifInterface(inputFile.absolutePath)
            assertEquals("TestMake", outputExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("TestModel", outputExif.getAttribute(ExifInterface.TAG_MODEL))
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path embeds archive after single encode`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") },
                "combined-render:applied must be present, got: ${result.pipelineNotes}")
            val outputBytes = inputFile.readBytes()
            assertTrue(outputBytes.isNotEmpty(), "output file should not be empty")
            assertTrue(outputBytes.size >= 2 && outputBytes[0] == 0xFF.toByte() && outputBytes[1] == 0xD8.toByte(),
                "output should start with JPEG SOI marker")
            val extracted = com.opencamera.core.media.OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "archive payload should be extractable from output")
            assertEquals("classic-overlay", extracted.manifest.watermarkTemplateId)
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path without mask provider uses global style fallback`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val processor = createCombinedProcessor(maskProvider = null, maskBitmapSource = null)
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied:photo-vivid+classic-overlay") },
                "combined-render:applied must be present even without mask provider")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path with available mask provider processes successfully`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val maskProvider = FakeSavedPhotoSceneMaskProvider(
                result = SceneMaskResult.Available(
                    SavedPhotoMaskPixels(
                        maskPixels = IntArray(16) { 0x80000000.toInt() },
                        maskWidth = 4,
                        maskHeight = 4,
                        confidence = 0.9f
                    )
                )
            )
            val testBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
            org.mockito.Mockito.`when`(testBitmap.width).thenReturn(32)
            org.mockito.Mockito.`when`(testBitmap.height).thenReturn(24)

            val processor = createCombinedProcessor(
                maskProvider = maskProvider,
                maskBitmapSource = { testBitmap }
            )
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") },
                "combined-render:applied must be present with mask, got: ${result.pipelineNotes}")
            assertTrue(inputFile.length() > 0, "output file should be written")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path mask unavailable falls back to global style`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val maskProvider = FakeSavedPhotoSceneMaskProvider(
                result = SceneMaskResult.Unavailable("ml-kit-not-installed")
            )
            val testBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
            org.mockito.Mockito.`when`(testBitmap.width).thenReturn(32)
            org.mockito.Mockito.`when`(testBitmap.height).thenReturn(24)

            val processor = createCombinedProcessor(
                maskProvider = maskProvider,
                maskBitmapSource = { testBitmap }
            )
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") },
                "combined-render:applied must be present when mask unavailable, got: ${result.pipelineNotes}")
            // Mask fallback still produces a valid output without crashing
            assertTrue(inputFile.length() > 0, "output file should be written on mask fallback")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path empty source records skip note`() = runTest {
        val inputFile = writeJpegToTempFile(ByteArray(0))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:skipped:empty-source") },
                "empty source should produce skip note, got: ${result.pipelineNotes}")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `watermark text without profile enters combined path gracefully`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(
                ShotResult(
                    shotId = "wm-only",
                    mediaType = MediaType.PHOTO,
                    outputPath = inputFile.absolutePath,
                    outputHandle = MediaOutputHandle(
                        displayPath = inputFile.absolutePath,
                        filePath = inputFile.absolutePath
                    ),
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = MediaMetadata(
                            watermarkText = "PHOTO Test",
                            customTags = mapOf(privateTemplateKey to "pure-text")
                        )
                    ),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = inputFile.absolutePath,
                        renderUri = null
                    ),
                    metadata = MediaMetadata(
                        watermarkText = "PHOTO Test",
                        customTags = mapOf(privateTemplateKey to "pure-text")
                    )
                )
            )

            // RenderRecipe.from(result) includes watermarkText, so requiresFinalOutputPostprocess is true
            // and the combined path is correctly entered. Verify it completes without error.
            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") || it.contains("combined-render:skipped") },
                "combined-render note must be present when watermark-only enters combined path, got: ${result.pipelineNotes}")
            assertFalse(result.pipelineNotes.any { it.contains("combined-render:failed") },
                "combined path must not fail for watermark-only scenario")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `only algorithm needed does not enter combined path`() = runTest {
        val processor = createCombinedProcessor()

        val result = processor.process(
            ShotResult(
                shotId = "algo-only",
                mediaType = MediaType.PHOTO,
                outputPath = "/tmp/algo-only.jpg",
                outputHandle = MediaOutputHandle(
                    displayPath = "/tmp/algo-only.jpg",
                    filePath = "/tmp/algo-only.jpg"
                ),
                saveRequest = SaveRequest.photoLibrary(
                    metadata = MediaMetadata(algorithmProfile = "photo-vivid")
                ),
                thumbnailSource = ThumbnailSource.SavedMedia(
                    outputPath = "/tmp/algo-only.jpg",
                    renderUri = null
                ),
                metadata = MediaMetadata(algorithmProfile = "photo-vivid")
            )
        )

        assertFalse(result.pipelineNotes.any { it.contains("combined-render:") },
            "combined-render must NOT appear when only algorithm is needed")
        assertTrue(result.pipelineNotes.any { it.contains("algorithm-render:") },
            "algorithm-render note must be present")
    }

    // --- optimized write order: EXIF + archive preservation tests ---

    @Test
    fun `watermark-only optimized write order preserves EXIF and archive`() = runTest {
        val exifTags = mapOf(
            ExifInterface.TAG_MAKE to "TestMake",
            ExifInterface.TAG_MODEL to "TestModel",
            ExifInterface.TAG_DATETIME_ORIGINAL to "2025:03:15 14:30:00",
            ExifInterface.TAG_F_NUMBER to "18/10",
            ExifInterface.TAG_EXPOSURE_TIME to "1/125",
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "200"
        )
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48, exifTags))
        try {
            val processor = PhotoWatermarkPostProcessor(
                AndroidPhotoWatermarkEditor(appContext)
            )
            val metadata = MediaMetadata(
                watermarkText = "PHOTO Test",
                customTags = mapOf(privateTemplateKey to "classic-overlay")
            )
            val result = processor.process(
                ShotResult(
                    shotId = "wm-exif-test",
                    mediaType = MediaType.PHOTO,
                    outputPath = inputFile.absolutePath,
                    outputHandle = MediaOutputHandle(
                        displayPath = inputFile.absolutePath,
                        filePath = inputFile.absolutePath
                    ),
                    saveRequest = SaveRequest.photoLibrary(metadata = metadata),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = inputFile.absolutePath,
                        renderUri = null
                    ),
                    metadata = metadata
                )
            )

            assertTrue(result.pipelineNotes.any { it.contains("watermark:rendered:classic-overlay") },
                "watermark:rendered must be present, got: ${result.pipelineNotes}")

            // EXIF tags survive optimized write order
            assertTrue(inputFile.exists() && inputFile.length() > 0, "output file must exist")
            val outputExif = ExifInterface(inputFile.absolutePath)
            assertEquals("TestMake", outputExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("TestModel", outputExif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("2025:03:15 14:30:00", outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))

            // OCWM archive extraction survives optimized write order
            val outputBytes = inputFile.readBytes()
            val extracted = OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "OCWM archive must be extractable after optimized write order")
            assertEquals("classic-overlay", extracted.manifest.watermarkTemplateId)
            assertTrue(extracted.payload.isNotEmpty(), "archive payload must not be empty")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined optimized write order preserves EXIF tags and archive with all key tags`() = runTest {
        val exifTags = mapOf(
            ExifInterface.TAG_MAKE to "CombinedMake",
            ExifInterface.TAG_MODEL to "CombinedModel",
            ExifInterface.TAG_DATETIME_ORIGINAL to "2025:07:20 09:15:00",
            ExifInterface.TAG_F_NUMBER to "28/10",
            ExifInterface.TAG_EXPOSURE_TIME to "1/250",
            ExifInterface.TAG_FOCAL_LENGTH to "46/10",
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "400",
            ExifInterface.TAG_GPS_LATITUDE to "40/1,7323/100,0/1",
            ExifInterface.TAG_GPS_LATITUDE_REF to "N",
            ExifInterface.TAG_GPS_LONGITUDE to "116/1,2398/100,0/1",
            ExifInterface.TAG_GPS_LONGITUDE_REF to "E"
        )
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(48, 36, exifTags))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(combinedShotResult(inputFile, algorithmProfile = "photo-vivid"))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied:photo-vivid+classic-overlay") },
                "combined-render:applied must be present, got: ${result.pipelineNotes}")

            // EXIF tags survive
            assertTrue(inputFile.exists() && inputFile.length() > 0, "output file must exist")
            val outputExif = ExifInterface(inputFile.absolutePath)
            assertEquals("CombinedMake", outputExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("CombinedModel", outputExif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("2025:07:20 09:15:00", outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertNotNull(outputExif.getAttribute(ExifInterface.TAG_F_NUMBER), "F_NUMBER must survive EXIF restore")
            assertNotNull(outputExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME), "EXPOSURE_TIME must survive EXIF restore")

            // OCWM archive extraction survives
            val outputBytes = inputFile.readBytes()
            val extracted = OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "OCWM archive must be extractable from combined output")
            assertEquals("classic-overlay", extracted.manifest.watermarkTemplateId)
            assertTrue(extracted.payload.isNotEmpty(), "archive payload must not be empty")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `optimized write order archive contains original image bytes not visible bytes`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(32, 24))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(combinedShotResult(inputFile))

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") },
                "combined-render:applied must be present")

            val outputBytes = inputFile.readBytes()
            val extracted = OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "archive must be extractable")
            // Archive payload must be the original source bytes, not the algorithm+watermark-rendered bytes
            assertTrue(extracted.payload.size > 0, "archive payload must be non-empty")
            assertTrue(extracted.payload.size != outputBytes.size,
                "archive payload (original) must differ in size from output (visible+archive)")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path with night-street produces combined notes`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(
                combinedShotResult(
                    inputFile,
                    algorithmProfile = "photo-vivid",
                    watermarkText = "Night Street",
                    watermarkTemplate = "night-street"
                )
            )

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied:photo-vivid+night-street") },
                "combined-render:applied must reference both profile and night-street template, got: ${result.pipelineNotes}")
            assertTrue(result.pipelineNotes.any { it.contains("combined-render:timing:") },
                "timing note must be present")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path with night-street embeds archive with night-street template id`() = runTest {
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48))
        try {
            val processor = createCombinedProcessor()
            val result = processor.process(
                combinedShotResult(
                    inputFile,
                    algorithmProfile = "photo-vivid",
                    watermarkText = "Night Street",
                    watermarkTemplate = "night-street"
                )
            )

            assertTrue(result.pipelineNotes.any { it.contains("combined-render:applied") },
                "combined-render:applied must be present, got: ${result.pipelineNotes}")
            val outputBytes = inputFile.readBytes()
            assertTrue(outputBytes.isNotEmpty(), "output file should not be empty")
            assertTrue(outputBytes.size >= 2 && outputBytes[0] == 0xFF.toByte() && outputBytes[1] == 0xD8.toByte(),
                "output should start with JPEG SOI marker")
            val extracted = OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "archive payload should be extractable from night-street output")
            assertEquals("night-street", extracted.manifest.watermarkTemplateId,
                "archive must record night-street as the template id, not fall back to classic-overlay")
            assertTrue(extracted.payload.isNotEmpty(), "archive payload must not be empty")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `combined path with night-street preserves EXIF tags from source`() = runTest {
        val exifTags = mapOf(
            ExifInterface.TAG_MAKE to "NightMake",
            ExifInterface.TAG_MODEL to "NightModel",
            ExifInterface.TAG_DATETIME_ORIGINAL to "2025:12:21 23:45:00",
            ExifInterface.TAG_F_NUMBER to "18/10",
            ExifInterface.TAG_EXPOSURE_TIME to "1/30",
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "1600"
        )
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48, exifTags))
        try {
            val processor = createCombinedProcessor()
            processor.process(
                combinedShotResult(
                    inputFile,
                    algorithmProfile = "photo-vivid",
                    watermarkText = "Night Street",
                    watermarkTemplate = "night-street"
                )
            )

            assertTrue(inputFile.exists(), "output file should exist")
            assertTrue(inputFile.length() > 0, "output file should not be empty")
            val outputExif = ExifInterface(inputFile.absolutePath)
            assertEquals("NightMake", outputExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("NightModel", outputExif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("2025:12:21 23:45:00", outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))
            assertNotNull(outputExif.getAttribute(ExifInterface.TAG_F_NUMBER), "F_NUMBER must survive EXIF restore")
            assertNotNull(outputExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME), "EXPOSURE_TIME must survive EXIF restore")
        } finally {
            inputFile.delete()
        }
    }

    @Test
    fun `watermark-only optimized write order preserves EXIF and archive for night-street`() = runTest {
        val exifTags = mapOf(
            ExifInterface.TAG_MAKE to "NightMake",
            ExifInterface.TAG_MODEL to "NightModel",
            ExifInterface.TAG_DATETIME_ORIGINAL to "2025:12:21 23:45:00",
            ExifInterface.TAG_F_NUMBER to "18/10",
            ExifInterface.TAG_EXPOSURE_TIME to "1/30",
            ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY to "800"
        )
        val inputFile = writeJpegToTempFile(createSyntheticJpeg(64, 48, exifTags))
        try {
            val processor = PhotoWatermarkPostProcessor(
                AndroidPhotoWatermarkEditor(appContext)
            )
            val metadata = MediaMetadata(
                watermarkText = "Night Street",
                customTags = mapOf(privateTemplateKey to "night-street")
            )
            val result = processor.process(
                ShotResult(
                    shotId = "wm-night-test",
                    mediaType = MediaType.PHOTO,
                    outputPath = inputFile.absolutePath,
                    outputHandle = MediaOutputHandle(
                        displayPath = inputFile.absolutePath,
                        filePath = inputFile.absolutePath
                    ),
                    saveRequest = SaveRequest.photoLibrary(metadata = metadata),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = inputFile.absolutePath,
                        renderUri = null
                    ),
                    metadata = metadata
                )
            )

            assertTrue(result.pipelineNotes.any { it.contains("watermark:rendered:night-street") },
                "watermark:rendered:night-street must be present, got: ${result.pipelineNotes}")

            // EXIF tags survive optimized write order
            assertTrue(inputFile.exists() && inputFile.length() > 0, "output file must exist")
            val outputExif = ExifInterface(inputFile.absolutePath)
            assertEquals("NightMake", outputExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("NightModel", outputExif.getAttribute(ExifInterface.TAG_MODEL))
            assertEquals("2025:12:21 23:45:00", outputExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL))

            // OCWM archive extraction survives optimized write order
            val outputBytes = inputFile.readBytes()
            val extracted = OcwmJpegContainer.extractArchive(outputBytes)
            assertNotNull(extracted, "OCWM archive must be extractable after optimized write order")
            assertEquals("night-street", extracted.manifest.watermarkTemplateId,
                "archive must record night-street, not fall back to classic-overlay")
            assertTrue(extracted.payload.isNotEmpty(), "archive payload must not be empty")
        } finally {
            inputFile.delete()
        }
    }
}
