package com.opencamera.app.camera

import android.graphics.Bitmap
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ProcessorEditorResult
import com.opencamera.core.media.ProcessorTarget
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotResult
import com.opencamera.core.media.ThumbnailSource
import kotlinx.coroutines.test.runTest
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@RunWith(RobolectricTestRunner::class)
class PhotoAlgorithmMaskWriteTest {

    @Test
    fun `mask aware editor writes changed output bytes to file`() = runTest {
        val outputFile = File.createTempFile("mask-write-test", ".jpg")
        try {
            outputFile.writeBytes(byteArrayOf(0x01, 0x02, 0x03))
            val originalBytes = outputFile.readBytes()

            val testBitmap = Bitmap.createBitmap(8, 8, Bitmap.Config.ARGB_8888)
            val mask = SceneMaskTestUtils.createCenterSubjectMask(8, 8)
            val editor = WritingFakeMaskAwareEditor(testBitmap)

            val processor = PhotoAlgorithmPostProcessor(
                editor,
                maskProvider = FakeSavedPhotoSceneMaskProvider(
                    result = SceneMaskResult.Available(mask)
                ),
                maskBitmapSource = { testBitmap }
            )
            val result = processor.process(
                ShotResult(
                    shotId = "mask-write-shot",
                    mediaType = MediaType.PHOTO,
                    outputPath = outputFile.absolutePath,
                    outputHandle = MediaOutputHandle(
                        displayPath = outputFile.absolutePath,
                        filePath = outputFile.absolutePath
                    ),
                    saveRequest = SaveRequest.photoLibrary(
                        metadata = MediaMetadata(algorithmProfile = "photo-vivid")
                    ),
                    thumbnailSource = ThumbnailSource.SavedMedia(
                        outputPath = outputFile.absolutePath,
                        renderUri = null
                    ),
                    metadata = MediaMetadata(algorithmProfile = "photo-vivid")
                )
            )

            assertEquals(1, editor.writtenTargets.size)
            assertTrue(result.pipelineNotes.any { it.contains("scene-mask:saved=applied") })
            val outputBytes = outputFile.readBytes()
            assertTrue(
                outputBytes.isNotEmpty(),
                "Expected output file to contain encoded bytes after mask-aware write"
            )
            assertFalse(
                outputBytes.contentEquals(originalBytes),
                "Expected output file bytes to differ from original after mask-aware write"
            )
        } finally {
            outputFile.delete()
        }
    }

    private class WritingFakeMaskAwareEditor(
        private val encodeBitmap: Bitmap
    ) : MaskAwarePhotoAlgorithmEditor {
        val writtenTargets = mutableListOf<ProcessorTarget>()

        override suspend fun apply(
            target: ProcessorTarget,
            spec: PhotoAlgorithmSpec
        ): ProcessorEditorResult = PhotoAlgorithmApplied()

        override suspend fun applyWithMask(
            target: ProcessorTarget,
            bitmap: Bitmap,
            spec: PhotoAlgorithmSpec,
            mask: SavedPhotoMaskPixels
        ): Pair<ProcessorEditorResult, List<String>> {
            val output = java.io.ByteArrayOutputStream()
            encodeBitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)
            val encoded = output.toByteArray()
            when (target) {
                is ProcessorTarget.FilePath -> {
                    File(target.path).outputStream().use { it.write(encoded) }
                }
                is ProcessorTarget.ContentUri -> {}
            }
            writtenTargets += target
            return Pair(
                PhotoAlgorithmApplied(),
                listOf("scene-mask:saved=applied", "color-render:subject-protected")
            )
        }
    }
}
