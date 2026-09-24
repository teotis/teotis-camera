package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import com.opencamera.core.media.AlgorithmJobClass
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FocusStackCaptureSpec
import com.opencamera.core.media.FocusStackFrameRole
import com.opencamera.core.media.FrameBundle
import com.opencamera.core.media.FrameBundleFrame
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PixelReference
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import java.io.ByteArrayInputStream
import java.io.File
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidFocusStackFusionProcessorTest {
    private val appContext: Context get() = RuntimeEnvironment.getApplication()

    @Test
    fun `full clear fusion is capture critical so the selected effect cannot silently time out`() {
        assertEquals(
            AlgorithmJobClass.CAPTURE_CRITICAL,
            AndroidFocusStackFusionProcessor(appContext).jobClass(
                baseResult(
                    outputPath = "unused.jpg",
                    frameBundle = FrameBundle(shotId = "focus-shot", frames = emptyList())
                )
            )
        )
    }

    @Test
    fun `applies local contrast fusion from near and far focus frames on Android`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeSplitFocusBitmap(near, sharpLeft = true, sharpRight = false)
            writeSplitFocusBitmap(far, sharpLeft = false, sharpRight = true)
            val output = File(tempDir, "out.jpg")

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = output.absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )
            val fused = BitmapFactory.decodeFile(output.absolutePath)

            assertNotNull(fused, "fused output must decode")
            try {
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:strategy=android-local-contrast" })
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:roles=near,far" })
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:memory=striped-rgb565" })
                assertTrue(horizontalContrast(fused, x = 10, y = 20) > 60)
                assertTrue(horizontalContrast(fused, x = 70, y = 20) > 60)
            } finally {
                fused.recycle()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `aligns a shifted near frame before selecting sharp regions`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-alignment-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeShiftedSplitFocusBitmap(
                file = near,
                sharpLeft = true,
                sharpRight = false,
                shiftX = 6,
                shiftY = 4
            )
            writeShiftedSplitFocusBitmap(
                file = far,
                sharpLeft = false,
                sharpRight = true,
                shiftX = 0,
                shiftY = 0
            )
            val output = File(tempDir, "out.jpg")

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = output.absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )
            val fused = BitmapFactory.decodeFile(output.absolutePath)

            assertNotNull(fused, "aligned fused output must decode")
            try {
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
                val alignment = processed.pipelineNotes
                    .firstOrNull { it.startsWith("focus-stack:alignment=translation:") }
                assertNotNull(
                    alignment,
                    "real focus stacks must expose registration diagnostics: ${processed.pipelineNotes}"
                )
                val (dx, dy) = alignment.removePrefix("focus-stack:alignment=translation:")
                    .split(',')
                    .map(String::toInt)
                assertTrue(
                    abs(dx - 6) <= 2 && abs(dy - 4) <= 1,
                    "registration must recover the known 6x4 shift within JPEG tolerance: $alignment"
                )
                assertTrue(horizontalContrast(fused, x = 18, y = 24) > 45)
                assertTrue(horizontalContrast(fused, x = 76, y = 24) > 45)
            } finally {
                fused.recycle()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `skips with explicit reason when far focus frame is missing`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-skip-")
        try {
            val near = File(tempDir, "near.jpg")
            writeSplitFocusBitmap(near, sharpLeft = true, sharpRight = false)

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = File(tempDir, "out.jpg").absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            )
                        )
                    )
                )
            )

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:skipped=missing-near-far" })
            assertFalse(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `marks alignment unverified for textureless frames instead of claiming registration`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-low-texture-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeSolidBitmap(near, Color.rgb(126, 126, 126))
            writeSolidBitmap(far, Color.rgb(132, 132, 132))

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = File(tempDir, "out.jpg").absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment=unverified:low-texture" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment-fallback=translation:0,0" })
            assertFalse(processed.pipelineNotes.any { it.startsWith("focus-stack:alignment=translation:") })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `marks alignment unverified when the best translation reaches search boundary`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-boundary-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeShiftedSplitFocusBitmap(near, true, false, shiftX = 14, shiftY = 0)
            writeShiftedSplitFocusBitmap(far, false, true, shiftX = 0, shiftY = 0)

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = File(tempDir, "out.jpg").absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment=unverified:search-boundary" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment-fallback=translation:0,0" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `marks repeating texture alignment ambiguous instead of claiming a translation`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-ambiguous-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writePeriodicBitmap(near)
            writePeriodicBitmap(far)

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = File(tempDir, "out.jpg").absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment=unverified:ambiguous" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:alignment-fallback=translation:0,0" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `moving foreground falls back to far anchor instead of producing fusion ghosting`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-motion-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeHandDepthBitmap(
                file = near,
                foregroundOffsetX = 0,
                foregroundSharp = true,
                backgroundSharp = false
            )
            writeHandDepthBitmap(
                file = far,
                foregroundOffsetX = 12,
                foregroundSharp = false,
                backgroundSharp = true
            )
            val output = File(tempDir, "out.jpg")

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = output.absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )
            val fused = BitmapFactory.decodeFile(output.absolutePath)

            assertNotNull(fused, "fused output must decode")
            try {
                assertTrue(
                    processed.pipelineNotes.any { it == "focus-stack:skipped=foreground-motion" },
                    "moving foreground must reject local fusion, got notes: ${processed.pipelineNotes}"
                )
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:fallback=far-anchor" })
                assertFalse(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
                assertTrue(
                    horizontalContrast(fused, x = 50, y = 34) < 30,
                    "fallback must not blend the displaced sharp near-hand into the far anchor"
                )
                assertTrue(
                    horizontalContrast(fused, x = 96, y = 34) > 45,
                    "far anchor background should remain sharp"
                )
            } finally {
                fused.recycle()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `small grayscale foreground motion also vetoes fusion`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-gray-motion-")
        try {
            val near = File(tempDir, "near.jpg")
            val far = File(tempDir, "far.jpg")
            writeGrayscaleMotionBitmap(near, foregroundOffsetX = 0, backgroundSharp = false)
            writeGrayscaleMotionBitmap(far, foregroundOffsetX = 8, backgroundSharp = true)
            val output = File(tempDir, "out.jpg")

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = output.absolutePath,
                    frameBundle = FrameBundle(
                        shotId = "focus-gray-motion",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                pixelReference = PixelReference.File(far.absolutePath),
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )

            assertTrue(processed.pipelineNotes.any { it == "focus-stack:skipped=foreground-motion" })
            assertTrue(processed.pipelineNotes.any { it == "focus-stack:fallback=far-anchor" })
            assertFalse(processed.pipelineNotes.any { it == "focus-stack:applied=true" })
        } finally {
            tempDir.deleteRecursively()
        }
    }

    @Test
    fun `far anchor frame missing on filesystem falls back to outputHandle contentUri`() = runTest {
        val tempDir = createTempDir(prefix = "android-focus-stack-uri-")
        try {
            val near = File(tempDir, "near.jpg")
            writeSplitFocusBitmap(near, sharpLeft = true, sharpRight = false)
            val farAnchor = File(tempDir, "far-anchor.jpg")
            writeSplitFocusBitmap(farAnchor, sharpLeft = false, sharpRight = true)

            // Model the real-device failure: the FAR anchor frame's pixelReference is a
            // relative display path that does not resolve on the filesystem, while the
            // real bytes live behind a content:// URI in the outputHandle. We register
            // an input stream (the original FAR pixels) and an output stream (where the
            // fusion result will be written back) on the ShadowContentResolver.
            val farContentUri = Uri.parse("content://media/external/images/media/9999991")
            val farBytes = farAnchor.readBytes()
            val shadowResolver = Shadows.shadowOf(appContext.contentResolver)
            shadowResolver.registerInputStream(farContentUri, ByteArrayInputStream(farBytes))
            val outputSink = java.io.ByteArrayOutputStream()
            shadowResolver.registerOutputStream(farContentUri, outputSink)

            val processed = AndroidFocusStackFusionProcessor(appContext).process(
                baseResult(
                    outputPath = "Pictures/OpenCamera/Check-in/OpenCamera_CHECKIN_FAKE.jpg",
                    outputHandle = MediaOutputHandle(
                        displayPath = "Pictures/OpenCamera/Check-in/OpenCamera_CHECKIN_FAKE.jpg",
                        contentUri = farContentUri.toString()
                    ),
                    frameBundle = FrameBundle(
                        shotId = "focus-shot",
                        frames = listOf(
                            FrameBundleFrame(
                                frameIndex = 0,
                                pixelReference = PixelReference.File(near.absolutePath),
                                focusStackRole = FocusStackFrameRole.NEAR
                            ),
                            FrameBundleFrame(
                                frameIndex = 1,
                                // Relative display path that does NOT exist on disk,
                                // forcing the fallback to outputHandle.contentUri.
                                pixelReference = PixelReference.File("Pictures/OpenCamera/Check-in/fake-far.jpg"),
                                frameRole = FrameRole.FUSION_ANCHOR,
                                focusStackRole = FocusStackFrameRole.FAR
                            )
                        )
                    )
                )
            )
            val fused = BitmapFactory.decodeStream(
                ByteArrayInputStream(outputSink.toByteArray())
            )
            assertNotNull(fused, "fused output must decode even when far file is missing")
            try {
                assertTrue(
                    processed.pipelineNotes.any { it == "focus-stack:applied=true" },
                    "expected applied=true, got notes: ${processed.pipelineNotes}"
                )
                assertTrue(processed.pipelineNotes.any { it == "focus-stack:far-source=content-uri-fallback" })
                assertTrue(horizontalContrast(fused, x = 10, y = 20) > 60)
                assertTrue(horizontalContrast(fused, x = 70, y = 20) > 60)
            } finally {
                fused.recycle()
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun baseResult(
        outputPath: String,
        frameBundle: FrameBundle,
        outputHandle: MediaOutputHandle = MediaOutputHandle(
            displayPath = outputPath,
            filePath = outputPath
        )
    ): ShotResult {
        return ShotResult(
            shotId = "focus-shot",
            mediaType = MediaType.PHOTO,
            outputPath = outputPath,
            outputHandle = outputHandle,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            captureProfile = CaptureProfile(
                frameCount = frameBundle.frameCount,
                focusStackSpec = FocusStackCaptureSpec.automaticNearFar()
            ),
            metadata = MediaMetadata(),
            frameBundle = frameBundle,
            intermediateOutputPaths = frameBundle.frames.mapNotNull {
                (it.pixelReference as? PixelReference.File)?.path
            }
        )
    }

    private fun writeSplitFocusBitmap(
        file: File,
        sharpLeft: Boolean,
        sharpRight: Boolean,
        width: Int = 96,
        height: Int = 64
    ) {
        writeShiftedSplitFocusBitmap(
            file,
            sharpLeft,
            sharpRight,
            0,
            0,
            width,
            height,
            withRegistrationStructure = false
        )
    }

    private fun writeShiftedSplitFocusBitmap(
        file: File,
        sharpLeft: Boolean,
        sharpRight: Boolean,
        shiftX: Int,
        shiftY: Int,
        width: Int = 96,
        height: Int = 64,
        withRegistrationStructure: Boolean = true
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val sourceX = x - shiftX
                    val sourceY = y - shiftY
                    val inScene = sourceX in 0 until width && sourceY in 0 until height
                    val leftHalf = sourceX < width / 2
                    val sharp = if (leftHalf) sharpLeft else sharpRight
                    val value = when {
                        !inScene -> 96
                        !withRegistrationStructure -> if (sharp) {
                            if (sourceX % 2 == 0) 24 else 232
                        } else {
                            128
                        }
                        else -> {
                            val base = 58 + sourceX + sourceY / 2 +
                                (if (sourceX in 15..29 && sourceY in 10..48) 52 else 0) -
                                (if (sourceX in 57..84 && sourceY in 18..31) 44 else 0)
                            val detail = if (sharp) {
                                if ((sourceX + sourceY) % 2 == 0) -28 else 28
                            } else {
                                if ((sourceX / 4 + sourceY / 4) % 2 == 0) -4 else 4
                            }
                            (base + detail).coerceIn(0, 255)
                        }
                    }
                    bitmap.setPixel(x, y, Color.rgb(value, value, value))
                }
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeSolidBitmap(file: File, color: Int, width: Int = 96, height: Int = 64) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            bitmap.eraseColor(color)
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writePeriodicBitmap(file: File, width: Int = 96, height: Int = 64) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val value = if ((x / 4) % 2 == 0) 52 else 204
                    bitmap.setPixel(x, y, Color.rgb(value, value, value))
                }
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeShiftedForegroundBitmap(
        file: File,
        foregroundOffsetX: Int,
        width: Int = 112,
        height: Int = 80
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val isForeground =
                        x in (30 + foregroundOffsetX) until (72 + foregroundOffsetX) &&
                            y in 24 until 62
                    val color = if (isForeground) {
                        val stripe = if ((x + y) % 5 == 0) 16 else 0
                        Color.rgb(204 - stripe, 134 - stripe / 2, 92 - stripe / 3)
                    } else {
                        val value = 146 + ((x + y) % 7)
                        Color.rgb(value, value, value)
                    }
                    bitmap.setPixel(x, y, color)
                }
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeHandDepthBitmap(
        file: File,
        foregroundOffsetX: Int,
        foregroundSharp: Boolean,
        backgroundSharp: Boolean,
        width: Int = 128,
        height: Int = 96
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val isForeground =
                        x in (24 + foregroundOffsetX) until (72 + foregroundOffsetX) &&
                            y in 20 until 78
                    val color = if (isForeground) {
                        if (foregroundSharp && x % 2 == 0) {
                            Color.rgb(214, 132, 84)
                        } else if (foregroundSharp) {
                            Color.rgb(82, 48, 34)
                        } else {
                            Color.rgb(154, 92, 62)
                        }
                    } else {
                        val value = if (backgroundSharp) {
                            if (x % 2 == 0) 28 else 228
                        } else {
                            136
                        }
                        Color.rgb(value, value, value)
                    }
                    bitmap.setPixel(x, y, color)
                }
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun writeGrayscaleMotionBitmap(
        file: File,
        foregroundOffsetX: Int,
        backgroundSharp: Boolean,
        width: Int = 128,
        height: Int = 96
    ) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val isForeground =
                        x in (34 + foregroundOffsetX) until (50 + foregroundOffsetX) &&
                            y in 34 until 54
                    val value = if (isForeground) {
                        52
                    } else if (backgroundSharp) {
                        if (x % 2 == 0) 28 else 228
                    } else {
                        136
                    }
                    bitmap.setPixel(x, y, Color.rgb(value, value, value))
                }
            }
            file.parentFile?.mkdirs()
            file.outputStream().use { out ->
                check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out))
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun horizontalContrast(bitmap: Bitmap, x: Int, y: Int): Int {
        return abs(luma(bitmap.getPixel(x, y)) - luma(bitmap.getPixel(x + 1, y)))
    }

    private fun luma(rgb: Int): Int {
        val r = Color.red(rgb)
        val g = Color.green(rgb)
        val b = Color.blue(rgb)
        return ((r * 299) + (g * 587) + (b * 114)) / 1000
    }
}
