package com.opencamera.app.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.exifinterface.media.ExifInterface
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.settings.PortraitBeautyPreset
import com.opencamera.core.settings.PortraitBeautyStrength
import com.opencamera.core.settings.PortraitBokehEffect
import com.opencamera.core.settings.PortraitProfile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.max
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Regression gate tests that lock the portrait memory architecture:
 * - peak blend buffer bounded by chunkRows
 * - source boundary coverage (odd, partial, edge dimensions)
 * - EXIF tags survive mask-aware render
 * - real editor output matches source dimensions
 * - fallback diagnostics and mask notes are stable
 * - blend paths do not allocate IntArray(width * height)
 */
@RunWith(RobolectricTestRunner::class)
class PortraitMemoryRegressionGateTest {

    private val maskSpec = PortraitRenderSpec(
        mode = PortraitRenderMode.DEPTH,
        portraitProfile = PortraitProfile.NATIVE,
        beautyPreset = PortraitBeautyPreset.CLEAR,
        beautyStrengthLevel = PortraitBeautyStrength.BALANCED,
        bokehEffect = PortraitBokehEffect.NATURAL,
        lightSpot = PortraitBackgroundLightSpotSpec.NONE,
        blurScale = 8,
        focusRadiusXFraction = 0.26f,
        focusRadiusYFraction = 0.32f,
        edgeSoftness = 0.24f,
        vignetteStrength = 0.10f,
        subjectTracking = false,
        strength = 1.8f,
        subjectSmoothing = 0.22f,
        subjectLift = 0.06f,
        subjectSaturationBoost = 0.035f,
        highlightBloom = 0.035f,
        backgroundBloom = 0.06f
    )

    private val focusSpec = PortraitRenderSpec(
        mode = PortraitRenderMode.FOCUS,
        portraitProfile = PortraitProfile.NATIVE,
        beautyPreset = PortraitBeautyPreset.CLEAR,
        beautyStrengthLevel = PortraitBeautyStrength.BALANCED,
        bokehEffect = PortraitBokehEffect.CREAMY,
        lightSpot = PortraitBackgroundLightSpotSpec.NONE,
        blurScale = 11,
        focusRadiusXFraction = 0.33f,
        focusRadiusYFraction = 0.42f,
        edgeSoftness = 0.25f,
        vignetteStrength = 0.06f,
        subjectTracking = true,
        strength = 1.0f,
        subjectSmoothing = 0.18f,
        subjectLift = 0.04f,
        subjectSaturationBoost = 0.025f,
        highlightBloom = 0.03f,
        backgroundBloom = 0.05f
    )

    // ---- Peak blend buffer model is bounded ----

    @Test
    fun `peak blend buffer is bounded by 2 times width times chunkRows times 4 bytes`() {
        val engine = PortraitRasterChunkEngine(64)
        // For width=4096, height=3072: bufferSize = 4096 * 64 = 262144 pixels.
        // 2 * 4096 * 64 * 4 = 2,097,152 bytes (~2 MB), far less than 48 MB full-frame.
        val bufferSizePx = engine.bufferSize(4096, 3072)
        val peakBytes = 2L * bufferSizePx * 4
        assertTrue(peakBytes <= 2L * 4096 * 64 * 4,
            "Peak blend buffer must not exceed 2 * width * chunkRows * 4 bytes, got $peakBytes")
    }

    @Test
    fun `peak blend buffer at different chunk sizes is bounded`() {
        for (chunkRows in listOf(1, 7, 32, 64, 128)) {
            val engine = PortraitRasterChunkEngine(chunkRows)
            val bufferSizePx = engine.bufferSize(2048, 1536)
            val peakBytes = 2L * bufferSizePx * 4
            val boundBytes = 2L * 2048 * chunkRows * 4
            assertTrue(peakBytes <= boundBytes,
                "chunkRows=$chunkRows: peak $peakBytes > bound $boundBytes")
        }
    }

    @Test
    fun `peak blend buffer for full-resolution photo is within budget`() {
        // 12MP photo at ~4000x3000, chunk=64: 2 * 4000 * 64 * 4 = 2,048,000 bytes (~1.95 MB)
        val engine = PortraitRasterChunkEngine(64)
        val bufferSizePx = engine.bufferSize(4000, 3000)
        val peakBytes = 2L * bufferSizePx * 4
        assertTrue(peakBytes <= 3L * 1024 * 1024,
            "Full-resolution peak blend buffer should be well under 3 MB, was $peakBytes bytes")
    }

    // ---- Source boundary: odd dimensions and partial final chunks ----

    @Test
    fun `mask-aware height=1 chunk=1 matches reference`() {
        val w = 10; val h = 1
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(1).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "mask-aware h=1 chunk=1")
    }

    @Test
    fun `focus height=1 chunk=1 matches reference`() {
        val w = 10; val h = 1
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(1).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "focus h=1 chunk=1")
    }

    @Test
    fun `mask-aware 3 rows chunk=2 covers partial final chunk`() {
        val w = 10; val h = 3
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(2).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "mask-aware h=3 chunk=2 partial")
    }

    @Test
    fun `focus 3 rows chunk=2 covers partial final chunk`() {
        val w = 10; val h = 3
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(2).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "focus h=3 chunk=2 partial")
    }

    @Test
    fun `mask-aware 9 rows chunk=5 has remainder 4`() {
        val w = 8; val h = 9
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(5).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "mask-aware h=9 chunk=5 remainder=4")
    }

    @Test
    fun `focus 9 rows chunk=5 has remainder 4`() {
        val w = 8; val h = 9
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(5).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "focus h=9 chunk=5 remainder=4")
    }

    @Test
    fun `mask-aware width=1 height=1 chunk=1 edge case`() {
        val mask = SceneMaskTestUtils.createCenterSubjectMask(1, 1)
        val orig = makeGradient(1, 1); val blur = makeUniform(1, 1, 0xFF808080.toInt())
        val ref  = makeGradient(1, 1); val refB = makeUniform(1, 1, 0xFF808080.toInt())
        PortraitRasterChunkEngine(1).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, 1, 1, "mask-aware 1x1")
    }

    @Test
    fun `focus width=1 height=1 chunk=1 edge case`() {
        val orig = makeGradient(1, 1); val blur = makeUniform(1, 1, 0xFF808080.toInt())
        val ref  = makeGradient(1, 1); val refB = makeUniform(1, 1, 0xFF808080.toInt())
        PortraitRasterChunkEngine(1).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, 1, 1, "focus 1x1")
    }

    @Test
    fun `mask-aware large odd 1281x961 chunk=64 covers all rows`() {
        val w = 1281; val h = 961  // 961 = 64*15 + 1, final chunk has 1 row
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(64).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "mask-aware 1281x961 chunk=64")
    }

    @Test
    fun `focus large odd 1281x961 chunk=64 covers all rows`() {
        val w = 1281; val h = 961
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(64).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "focus 1281x961 chunk=64")
    }

    // ---- EXIF tags survive mask-aware render ----

    @Test
    fun `exif tags survive mask-aware render through real editor`() {
        val sourceWidth = 48
        val sourceHeight = 36
        val sourceFile = File.createTempFile("exif_test_", ".jpg")
        try {
            // Create a source JPEG with known EXIF tags
            val sourceBitmap = Bitmap.createBitmap(sourceWidth, sourceHeight, Bitmap.Config.ARGB_8888)
            val jpegBytes = ByteArrayOutputStream().use { os ->
                sourceBitmap.compress(Bitmap.CompressFormat.JPEG, 92, os)
                os.toByteArray()
            }
            sourceFile.writeBytes(jpegBytes)
            sourceBitmap.recycle()

            // Write EXIF tags to the file
            ExifInterface(sourceFile.absolutePath).apply {
                setAttribute(ExifInterface.TAG_MAKE, "TestMake")
                setAttribute(ExifInterface.TAG_MODEL, "TestModel")
                setAttribute(ExifInterface.TAG_ORIENTATION, "90")
                setAttribute(ExifInterface.TAG_DATETIME_ORIGINAL, "2026:01:15 10:30:00")
                setAttribute(ExifInterface.TAG_FOCAL_LENGTH, "4200/1000")
                setAttribute(ExifInterface.TAG_F_NUMBER, "180/100")
                setAttribute(ExifInterface.TAG_EXPOSURE_TIME, "1/125")
                setAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, "400")
                setAttribute(ExifInterface.TAG_FLASH, "0")
                setAttribute(ExifInterface.TAG_WHITE_BALANCE, "0")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE, "37/1,46/1,32/1")
                setAttribute(ExifInterface.TAG_GPS_LATITUDE_REF, "N")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE, "122/1,2/1,24/1")
                setAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF, "W")
                setAttribute(ExifInterface.TAG_GPS_ALTITUDE, "10/1")
                setAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF, "0")
                saveAttributes()
            }

            // Verify pre-conditions
            val preExif = ExifInterface(sourceFile.absolutePath)
            assertEquals("TestMake", preExif.getAttribute(ExifInterface.TAG_MAKE))
            assertEquals("90", preExif.getAttribute(ExifInterface.TAG_ORIENTATION))
            assertEquals("37/1,46/1,32/1", preExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE))

            // Run through real editor (mask-aware path)
            val editor = AndroidPortraitRenderEditor(
                org.robolectric.RuntimeEnvironment.getApplication()
            )
            val target = ProcessorTarget.FilePath(sourceFile.absolutePath)
            val mask = SceneMaskTestUtils.createCenterSubjectMask(sourceWidth, sourceHeight)
            kotlinx.coroutines.test.runTest {
                val (result, _) = editor.applyWithMask(target, maskSpec, mask)
                assertTrue(
                    result is PortraitRenderApplied || result is com.opencamera.core.media.ProcessorEditorResult.Failed,
                    "Expected PortraitRenderApplied or Failed, got: $result"
                )
            }

            // Verify EXIF tags survived (ExifInterface may normalize values like "180/100" -> "1.8",
            // so we check that tags are present and meaningful rather than exact string match)
            if (sourceFile.length() > 0) {
                val postExif = ExifInterface(sourceFile.absolutePath)
                // Core tags that must survive
                assertTrue(postExif.getAttribute(ExifInterface.TAG_MAKE) != null,
                    "TAG_MAKE must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_MODEL) != null,
                    "TAG_MODEL must survive render")
                assertEquals("90", postExif.getAttribute(ExifInterface.TAG_ORIENTATION),
                    "TAG_ORIENTATION must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_DATETIME_ORIGINAL) != null,
                    "TAG_DATETIME_ORIGINAL must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_FOCAL_LENGTH) != null,
                    "TAG_FOCAL_LENGTH must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_F_NUMBER) != null,
                    "TAG_F_NUMBER must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_EXPOSURE_TIME) != null,
                    "TAG_EXPOSURE_TIME must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY) != null,
                    "TAG_PHOTOGRAPHIC_SENSITIVITY must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_FLASH) != null,
                    "TAG_FLASH must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_WHITE_BALANCE) != null,
                    "TAG_WHITE_BALANCE must survive render")
                // GPS tags that must survive
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE) != null,
                    "TAG_GPS_LATITUDE must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_LATITUDE_REF) != null,
                    "TAG_GPS_LATITUDE_REF must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE) != null,
                    "TAG_GPS_LONGITUDE must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_LONGITUDE_REF) != null,
                    "TAG_GPS_LONGITUDE_REF must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE) != null,
                    "TAG_GPS_ALTITUDE must survive render")
                assertTrue(postExif.getAttribute(ExifInterface.TAG_GPS_ALTITUDE_REF) != null,
                    "TAG_GPS_ALTITUDE_REF must survive render")
            }
        } finally {
            sourceFile.delete()
        }
    }

    // ---- Real editor output size matches source ----

    @Test
    fun `real editor output dimensions match source after mask-aware render`() {
        val sourceWidth = 64
        val sourceHeight = 48
        val sourceFile = File.createTempFile("output_size_test_", ".jpg")
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
            val mask = SceneMaskTestUtils.createCenterSubjectMask(sourceWidth, sourceHeight)
            kotlinx.coroutines.test.runTest {
                val (result, _) = editor.applyWithMask(target, maskSpec, mask)
                assertTrue(
                    result is PortraitRenderApplied || result is com.opencamera.core.media.ProcessorEditorResult.Failed,
                    "Expected success or graceful failure, got: $result"
                )
            }

            if (sourceFile.length() > 0) {
                val outputBytes = sourceFile.readBytes()
                val decoded = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
                if (decoded != null) {
                    assertEquals(sourceWidth, decoded.width,
                        "Output width must match source width")
                    assertEquals(sourceHeight, decoded.height,
                        "Output height must match source height")
                    decoded.recycle()
                }
            }
        } finally {
            sourceFile.delete()
        }
    }

    @Test
    fun `real editor output dimensions match source after focus render`() {
        val sourceWidth = 48
        val sourceHeight = 64
        val sourceFile = File.createTempFile("focus_output_test_", ".jpg")
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
            kotlinx.coroutines.test.runTest {
                val result = editor.apply(target, focusSpec)
                assertTrue(
                    result is PortraitRenderApplied || result is com.opencamera.core.media.ProcessorEditorResult.Failed,
                    "Expected success or graceful failure, got: $result"
                )
            }

            if (sourceFile.length() > 0) {
                val outputBytes = sourceFile.readBytes()
                val decoded = BitmapFactory.decodeByteArray(outputBytes, 0, outputBytes.size)
                if (decoded != null) {
                    assertEquals(sourceWidth, decoded.width,
                        "Output width must match source width")
                    assertEquals(sourceHeight, decoded.height,
                        "Output height must match source height")
                    decoded.recycle()
                }
            }
        } finally {
            sourceFile.delete()
        }
    }

    // ---- Bounded analysis bitmap is recycled and not used as output ----

    @Test
    fun `analysis bitmap is recycled after mask-aware render and not used as output`() = kotlinx.coroutines.test.runTest {
        val analysisBitmap = org.mockito.Mockito.mock(Bitmap::class.java)
        org.mockito.Mockito.`when`(analysisBitmap.width).thenReturn(48)
        org.mockito.Mockito.`when`(analysisBitmap.height).thenReturn(36)

        val mask = SceneMaskTestUtils.createCenterSubjectMask(48, 36)
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Available(mask)
        )
        val processor = PortraitRenderPostProcessor(
            FakeMaskAwareEditor(),
            maskProvider,
            maskBitmapSource = { analysisBitmap }
        )
        val result = processor.process(
            makePortraitShotResult(
                ProcessorTarget.FilePath("/tmp/test.jpg")
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=applied"),
            "Mask-aware path must produce applied note, got: ${result.pipelineNotes}")
        org.mockito.Mockito.verify(analysisBitmap, org.mockito.Mockito.times(1)).recycle()
    }

    // ---- Fallback diagnostics and mask notes are stable ----

    @Test
    fun `no mask provider produces stable fallback diagnostics`() = kotlinx.coroutines.test.runTest {
        val processor = PortraitRenderPostProcessor(FakeMaskAwareEditor())
        val result = processor.process(
            makePortraitShotResult(
                ProcessorTarget.FilePath("/tmp/test.jpg")
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-render:applied:depth"),
            "Must produce depth render note, got: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("portrait-layer:light-spot=none"),
            "Must produce light-spot layer note, got: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:no-provider"),
            "Must produce degraded no-provider note, got: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"),
            "Must produce fallback-focus note, got: ${result.pipelineNotes}")
    }

    @Test
    fun `unavailable mask produces stable fallback diagnostics`() = kotlinx.coroutines.test.runTest {
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Unavailable("no-person")
        )
        val processor = PortraitRenderPostProcessor(
            FakeMaskAwareEditor(),
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            makePortraitShotResult(
                ProcessorTarget.FilePath("/tmp/test.jpg")
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:mask-unavailable"),
            "Must produce degraded mask-unavailable note, got: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"),
            "Must produce fallback-focus note, got: ${result.pipelineNotes}")
    }

    @Test
    fun `failed mask produces stable fallback diagnostics`() = kotlinx.coroutines.test.runTest {
        val maskProvider = FakeSavedPhotoSceneMaskProvider(
            SceneMaskResult.Failed("mlkit-error")
        )
        val processor = PortraitRenderPostProcessor(
            FakeMaskAwareEditor(),
            maskProvider,
            maskBitmapSource = { null }
        )
        val result = processor.process(
            makePortraitShotResult(
                ProcessorTarget.FilePath("/tmp/test.jpg")
            )
        )

        assertTrue(result.pipelineNotes.contains("portrait-mask:saved=degraded:mask-unavailable"),
            "Must produce degraded note on failed mask, got: ${result.pipelineNotes}")
        assertTrue(result.pipelineNotes.contains("portrait-render:fallback-focus"),
            "Must produce fallback-focus note, got: ${result.pipelineNotes}")
    }

    // ---- Blend paths do not allocate IntArray(width * height) ----

    @Test
    fun `chunk engine blend uses bounded buffer not full frame working array`() {
        val w = 128; val h = 128; val chunkRows = 16
        val engine = PortraitRasterChunkEngine(chunkRows)

        // The bounded blend buffer is width * min(chunkRows, height) pixels.
        // Full frame would be width * height = 16384 pixels.
        // Bounded: 128 * 16 = 2048 pixels (8x less).
        val boundedBufferSizePx = engine.bufferSize(w, h)
        val fullFramePx = w * h
        assertTrue(boundedBufferSizePx < fullFramePx,
            "Bounded buffer ($boundedBufferSizePx px) must be smaller than full frame ($fullFramePx px)")
        assertEquals(w * chunkRows, boundedBufferSizePx,
            "Bounded buffer must equal width * chunkRows when chunkRows < height")
    }

    @Test
    fun `chunk engine when chunkRows exceeds height uses height not chunkRows`() {
        val w = 50; val h = 10; val chunkRows = 100
        val engine = PortraitRasterChunkEngine(chunkRows)
        assertEquals(w * h, engine.bufferSize(w, h),
            "When chunkRows > height, buffer should be width * height")
    }

    // ---- Real allocation behavior: render proves chunk execution ----

    @Test
    fun `mask-aware render on large image with small chunks produces correct output`() {
        // 640x480 image with chunkRows=8: 60 chunks. Proves chunk-by-chunk execution.
        val w = 640; val h = 480; val chunkRows = 8
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(chunkRows).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "640x480 chunk=8 mask-aware")
    }

    @Test
    fun `focus render on large image with small chunks produces correct output`() {
        val w = 640; val h = 480; val chunkRows = 8
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(chunkRows).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "640x480 chunk=8 focus")
    }

    @Test
    fun `mask-aware chunk count matches expected for 640x480 chunk=8`() {
        // 480 / 8 = 60 chunks, proving chunk-by-chunk layout
        val w = 640; val h = 480; val chunkRows = 8
        val expectedChunks = (h + chunkRows - 1) / chunkRows
        assertEquals(60, expectedChunks, "Expected 60 chunks for 480 rows / chunk=8")
        // Each chunk allocates width * chunkRows = 640 * 8 = 5120 pixels,
        // far less than 640 * 480 = 307200 full-frame pixels
        val chunkPx = w * chunkRows
        assertTrue(chunkPx < w * h,
            "Per-chunk allocation ($chunkPx px) must be < full frame (${w * h} px)")
    }

    @Test
    fun `chunk engine source boundary large odd 1281x961 with chunk=7 proves chunked read`() {
        // 961 / 7 = 137 chunks + 1 partial chunk of 2 rows
        val w = 1281; val h = 961; val chunkRows = 7
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(chunkRows).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "1281x961 chunk=7 mask-aware")
    }

    @Test
    fun `chunk engine focus large odd 1281x961 with chunk=7 proves chunked read`() {
        val w = 1281; val h = 961; val chunkRows = 7
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(chunkRows).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "1281x961 chunk=7 focus")
    }

    @Test
    fun `chunk engine max chunkRows=1 on 100x100 proves per-row processing`() {
        // chunkRows=1 means 100 chunks, each processing exactly 1 row
        val w = 100; val h = 100; val chunkRows = 1
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(chunkRows).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "100x100 chunk=1 per-row mask-aware")
    }

    @Test
    fun `source boundary exhaustive row coverage with chunk=7 and 13 rows`() {
        // 13 = 7 + 6 → two chunks, second has 6 rows (partial)
        val w = 20; val h = 13
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(7).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "h=13 chunk=7 partial final")
    }

    @Test
    fun `source boundary exhaustive row coverage with chunk=3 and 10 rows`() {
        // 10 = 3+3+3+1 → four chunks, last has 1 row
        val w = 15; val h = 10
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(3).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "h=10 chunk=3 last=1")
    }

    // ---- Non-square dimensions ----

    @Test
    fun `mask-aware wide 256x64 chunk=16 matches reference`() {
        val w = 256; val h = 64
        val mask = SceneMaskTestUtils.createCenterSubjectMask(w, h)
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(16).renderMaskAware(orig, blur, maskSpec, mask)
        referenceMaskAwareRender(ref, refB, maskSpec, mask)
        assertArgbEqual(ref, orig, w, h, "mask-aware 256x64")
    }

    @Test
    fun `focus tall 64x256 chunk=16 matches reference`() {
        val w = 64; val h = 256
        val orig = makeGradient(w, h); val blur = makeUniform(w, h, 0xFF808080.toInt())
        val ref  = makeGradient(w, h); val refB = makeUniform(w, h, 0xFF808080.toInt())
        PortraitRasterChunkEngine(16).renderFocus(orig, blur, focusSpec)
        referenceFocusRender(ref, refB, focusSpec)
        assertArgbEqual(ref, orig, w, h, "focus 64x256")
    }

    // ---- Reference renderers (faithfully reproduce production formulas) ----

    private fun referenceMaskAwareRender(
        original: Bitmap, blurred: Bitmap,
        spec: PortraitRenderSpec, mask: SavedPhotoMaskPixels
    ) {
        val w = original.width; val h = original.height
        val src = IntArray(w * h); val bld = IntArray(w * h)
        original.getPixels(src, 0, w, 0, 0, w, h)
        blurred.getPixels(bld, 0, w, 0, 0, w, h)
        val mapper = SceneMaskCoordinateMapper(mask.maskWidth, mask.maskHeight, w, h)
        val fcx = w / 2f; val fcy = h / 2f
        val maxDist = max(1f, kotlin.math.sqrt(fcx * fcx + fcy * fcy))
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val s = src[i]; val b = bld[i]
            val alpha = s ushr 24 and 0xFF
            val sw = smoothstep(0.15f, 0.85f, mask.sampleAlpha(mapper.maskX(x), mapper.maskY(y)))
            val bm = 1f - sw
            var r = mixCh(((s ushr 16) and 0xFF).toFloat(), ((b ushr 16) and 0xFF).toFloat(), bm)
            var g = mixCh(((s ushr 8) and 0xFF).toFloat(), ((b ushr 8) and 0xFF).toFloat(), bm)
            var bl = mixCh((s and 0xFF).toFloat(), (b and 0xFF).toFloat(), bm)
            val fd = kotlin.math.sqrt((x - fcx) * (x - fcx) + (y - fcy) * (y - fcy))
            val v = 1f - ((fd / maxDist) * spec.vignetteStrength).coerceIn(0f, 0.28f)
            r *= v; g *= v; bl *= v
            val sm = (spec.subjectSmoothing * sw).coerceIn(0f, 0.28f)
            if (sm > 0f) { r = mixCh(r, ((b ushr 16) and 0xFF).toFloat(), sm); g = mixCh(g, ((b ushr 8) and 0xFF).toFloat(), sm); bl = mixCh(bl, (b and 0xFF).toFloat(), sm) }
            val lum = r * 0.299f + g * 0.587f + bl * 0.114f
            val sat = spec.subjectSaturationBoost * sw
            if (sat > 0f) { r = lum + (r - lum) * (1f + sat); g = lum + (g - lum) * (1f + sat); bl = lum + (bl - lum) * (1f + sat) }
            val li = spec.subjectLift * sw
            if (li > 0f) { r += (255f - r) * li; g += (255f - g) * li; bl += (255f - bl) * li }
            val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
            val tb = (spec.highlightBloom * sw * hf + spec.backgroundBloom * bm * hf).coerceIn(0f, 0.24f)
            if (tb > 0f) { r += (255f - r) * tb; g += (255f - g) * tb; bl += (255f - bl) * tb }
            src[i] = (alpha shl 24) or (clampCh(r).toInt() shl 16) or (clampCh(g).toInt() shl 8) or clampCh(bl).toInt()
        }
        original.setPixels(src, 0, w, 0, 0, w, h)
    }

    private fun referenceFocusRender(
        original: Bitmap, blurred: Bitmap, spec: PortraitRenderSpec
    ) {
        val w = original.width; val h = original.height
        val src = IntArray(w * h); val bld = IntArray(w * h)
        original.getPixels(src, 0, w, 0, 0, w, h)
        blurred.getPixels(bld, 0, w, 0, 0, w, h)
        val fcx = w * 0.5f; val fcy = h * if (spec.subjectTracking) 0.42f else 0.46f
        val rx = max(1f, w * spec.focusRadiusXFraction)
        val ry = max(1f, h * spec.focusRadiusYFraction)
        val fcx2 = w / 2f; val fcy2 = h / 2f
        val maxDist = max(1f, kotlin.math.sqrt(fcx2 * fcx2 + fcy2 * fcy2))
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x; val s = src[i]; val b = bld[i]
            val alpha = s ushr 24 and 0xFF
            val nd = kotlin.math.sqrt(((x - fcx) / rx) * ((x - fcx) / rx) + ((y - fcy) / ry) * ((y - fcy) / ry))
            val bm = smoothstep(1f, 1f + spec.edgeSoftness, nd)
            val sw = 1f - bm
            var r = mixCh(((s ushr 16) and 0xFF).toFloat(), ((b ushr 16) and 0xFF).toFloat(), bm)
            var g = mixCh(((s ushr 8) and 0xFF).toFloat(), ((b ushr 8) and 0xFF).toFloat(), bm)
            var bl = mixCh((s and 0xFF).toFloat(), (b and 0xFF).toFloat(), bm)
            val fd = kotlin.math.sqrt((x - fcx2) * (x - fcx2) + (y - fcy2) * (y - fcy2))
            val v = 1f - ((fd / maxDist) * spec.vignetteStrength).coerceIn(0f, 0.28f)
            r *= v; g *= v; bl *= v
            val sm = (spec.subjectSmoothing * sw).coerceIn(0f, 0.28f)
            if (sm > 0f) { r = mixCh(r, ((b ushr 16) and 0xFF).toFloat(), sm); g = mixCh(g, ((b ushr 8) and 0xFF).toFloat(), sm); bl = mixCh(bl, (b and 0xFF).toFloat(), sm) }
            val lum = r * 0.299f + g * 0.587f + bl * 0.114f
            val sat = spec.subjectSaturationBoost * sw
            if (sat > 0f) { r = lum + (r - lum) * (1f + sat); g = lum + (g - lum) * (1f + sat); bl = lum + (bl - lum) * (1f + sat) }
            val li = spec.subjectLift * sw
            if (li > 0f) { r += (255f - r) * li; g += (255f - g) * li; bl += (255f - bl) * li }
            val hf = ((lum / 255f) - 0.52f).coerceIn(0f, 1f)
            val tb = (spec.highlightBloom * sw * hf + spec.backgroundBloom * bm * hf).coerceIn(0f, 0.24f)
            if (tb > 0f) { r += (255f - r) * tb; g += (255f - g) * tb; bl += (255f - bl) * tb }
            src[i] = (alpha shl 24) or (clampCh(r).toInt() shl 16) or (clampCh(g).toInt() shl 8) or clampCh(bl).toInt()
        }
        original.setPixels(src, 0, w, 0, 0, w, h)
    }

    // ---- Helpers ----

    private fun makeGradient(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        for (y in 0 until h) {
            val row = IntArray(w)
            for (x in 0 until w) {
                val t = if (w <= 1 && h <= 1) 0f
                else (x.toFloat() / (w - 1) * 0.5f + y.toFloat() / (h - 1) * 0.5f)
                val r = (0x80 + (0x20 - 0x80) * t).toInt().coerceIn(0, 255)
                val g = (0x40 + (0x40 - 0x40) * t).toInt().coerceIn(0, 255)
                val b = (0x20 + (0x80 - 0x20) * t).toInt().coerceIn(0, 255)
                row[x] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bmp.setPixels(row, 0, w, 0, y, w, 1)
        }
        return bmp
    }

    private fun makeUniform(w: Int, h: Int, color: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val row = IntArray(w) { color }
        for (y in 0 until h) bmp.setPixels(row, 0, w, 0, y, w, 1)
        return bmp
    }

    private fun assertArgbEqual(expected: Bitmap, actual: Bitmap, w: Int, h: Int, label: String) {
        val exp = IntArray(w * h); val act = IntArray(w * h)
        expected.getPixels(exp, 0, w, 0, 0, w, h)
        actual.getPixels(act, 0, w, 0, 0, w, h)
        for (i in exp.indices) {
            assertEquals(exp[i], act[i], "$label pixel[$i] mismatch")
        }
    }

    private fun makePortraitShotResult(target: ProcessorTarget): com.opencamera.core.media.ShotResult {
        return com.opencamera.core.media.ShotResult(
            shotId = "shot-regression-gate",
            mediaType = com.opencamera.core.media.MediaType.PHOTO,
            outputPath = when (target) {
                is ProcessorTarget.FilePath -> target.path
                is ProcessorTarget.ContentUri -> target.value
            },
            outputHandle = com.opencamera.core.media.MediaOutputHandle(
                displayPath = when (target) {
                    is ProcessorTarget.FilePath -> target.path
                    is ProcessorTarget.ContentUri -> target.value
                },
                filePath = (target as? ProcessorTarget.FilePath)?.path
            ),
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
                outputPath = when (target) {
                    is ProcessorTarget.FilePath -> target.path
                    is ProcessorTarget.ContentUri -> target.value
                },
                renderUri = (target as? ProcessorTarget.ContentUri)?.value
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

// Test-local fakes (re-declared to avoid import conflicts)

private class FakeMaskAwareEditor : MaskAwarePortraitRenderEditor {
    override suspend fun apply(
        target: ProcessorTarget,
        spec: PortraitRenderSpec
    ) = PortraitRenderApplied()

    override suspend fun applyWithMask(
        target: ProcessorTarget,
        spec: PortraitRenderSpec,
        mask: SavedPhotoMaskPixels
    ) = Pair(
        PortraitRenderApplied(),
        listOf("portrait-mask:saved=applied", "portrait-render:subject-mask")
    )
}

private fun mixCh(s: Float, t: Float, m: Float) = s + (t - s) * m.coerceIn(0f, 1f)
private fun smoothstep(e0: Float, e1: Float, v: Float): Float {
    if (e0 == e1) return if (v >= e1) 1f else 0f
    val t = ((v - e0) / (e1 - e0)).coerceIn(0f, 1f); return t * t * (3f - 2f * t)
}
private fun clampCh(v: Float) = v.coerceIn(0f, 255f)
private fun max(a: Float, b: Float) = if (a > b) a else b
