package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import java.io.File

class MultiFrameMergeAlgorithmProcessorTest {

    private fun buildMergeRequest(
        frameCount: Int,
        inputPaths: List<String>
    ): AlgorithmRequest {
        return AlgorithmRequest(
            node = AlgorithmNode(
                id = "merge-night",
                type = AlgorithmType.MULTI_FRAME_MERGE,
                inputs = inputPaths,
                output = "/tmp/merged.jpg",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = inputPaths.map { path ->
                MediaInputRef(
                    path = path,
                    handle = MediaOutputHandle(displayPath = path),
                    mimeType = "image/jpeg"
                )
            },
            metadata = MediaMetadata(
                customTags = mapOf("frameCount" to frameCount.toString())
            )
        )
    }

    @Test
    fun `multi frame merge processor returns Applied when frame count greater than one and intermediates exist`() =
        runTest {
            val tempDir = createTempDir(prefix = "merge-test-")
            val frameA = File(tempDir, "frame_a.jpg").apply { writeText("frame-a-data") }
            val frameB = File(tempDir, "frame_b.jpg").apply { writeText("frame-bb-data") }

            try {
                val processor = MultiFrameMergePlaceholderPostProcessor().toAlgorithmProcessor()
                val request = buildMergeRequest(
                    frameCount = 3,
                    inputPaths = listOf(frameA.absolutePath, frameB.absolutePath)
                )

                assertTrue(processor.canProcess(request))
                val result = processor.process(request)

                assertTrue(result is AlgorithmResult.Applied)
                assertTrue((result as AlgorithmResult.Applied).notes.contains("merge:placeholder"))
                assertTrue(result.notes.any { it.startsWith("merge:inputs=") })
                assertTrue(result.notes.contains("merge:strategy=burst-placeholder"))
            } finally {
                tempDir.deleteRecursively()
            }
        }

    @Test
    fun `multi frame merge processor returns Skipped when frame count is one`() = runTest {
        val processor = MultiFrameMergePlaceholderPostProcessor().toAlgorithmProcessor()
        val request = buildMergeRequest(frameCount = 1, inputPaths = listOf("/tmp/single.jpg"))

        assertFalse(processor.canProcess(request))
        val result = processor.process(request)

        assertTrue(result is AlgorithmResult.Skipped)
        assertEquals(
            "single-frame-or-no-intermediates",
            (result as AlgorithmResult.Skipped).reason
        )
    }

    @Test
    fun `multi frame merge processor returns Skipped when no intermediate inputs`() = runTest {
        val processor = MultiFrameMergePlaceholderPostProcessor().toAlgorithmProcessor()
        val request = buildMergeRequest(frameCount = 5, inputPaths = emptyList())

        assertFalse(processor.canProcess(request))
        val result = processor.process(request)

        assertTrue(result is AlgorithmResult.Skipped)
    }

    @Test
    fun `temp files are deleted after merge regardless of Applied result`() = runTest {
        val tempDir = createTempDir(prefix = "merge-cleanup-")
        val frameA = File(tempDir, "burst_a.jpg").apply { writeText("aaa") }
        val frameB = File(tempDir, "burst_b.jpg").apply { writeText("bbb") }

        try {
            val processor = MultiFrameMergePlaceholderPostProcessor().toAlgorithmProcessor()
            val request = buildMergeRequest(
                frameCount = 4,
                inputPaths = listOf(frameA.absolutePath, frameB.absolutePath)
            )

            val result = processor.process(request)
            assertTrue(result is AlgorithmResult.Applied)
            assertTrue(frameA.exists().not())
            assertTrue(frameB.exists().not())
        } finally {
            tempDir.deleteRecursively()
        }
    }
}
