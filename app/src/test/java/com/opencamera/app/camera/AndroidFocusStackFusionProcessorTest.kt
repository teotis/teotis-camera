package com.opencamera.app.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class AndroidFocusStackFusionProcessorTest {
    private val appContext: Context get() = RuntimeEnvironment.getApplication()

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
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val leftHalf = x < width / 2
                    val sharp = if (leftHalf) sharpLeft else sharpRight
                    val value = if (sharp) {
                        if (x % 2 == 0) 24 else 232
                    } else {
                        128
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
