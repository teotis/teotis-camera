package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class FakeAlgorithmProcessor(
    override val type: AlgorithmType,
    private val canProcessResult: Boolean = true,
    private val processResult: AlgorithmResult = AlgorithmResult.Applied(
        output = MediaOutputHandle(displayPath = "fake/output.jpg"),
        notes = listOf("fake:applied")
    )
) : AlgorithmProcessor {
    val invocations = mutableListOf<AlgorithmRequest>()

    override fun canProcess(request: AlgorithmRequest): Boolean = canProcessResult

    override suspend fun process(request: AlgorithmRequest): AlgorithmResult {
        invocations.add(request)
        return processResult
    }
}

class AlgorithmProcessorTest {

    private fun buildRequest(
        algorithmType: AlgorithmType = AlgorithmType.FILTER_RENDER,
        inputs: List<MediaInputRef> = listOf(
            MediaInputRef(
                path = "/tmp/input.jpg",
                handle = MediaOutputHandle(displayPath = "/tmp/input.jpg"),
                mimeType = "image/jpeg"
            )
        )
    ): AlgorithmRequest {
        return AlgorithmRequest(
            node = AlgorithmNode(
                id = "node-1",
                type = algorithmType,
                inputs = inputs.map { it.path },
                output = "output-1",
                requirement = AlgorithmRequirement.OPTIONAL,
                fallback = AlgorithmFallback.SKIP
            ),
            inputs = inputs,
            metadata = MediaMetadata(algorithmProfile = "photo-vivid")
        )
    }

    @Test
    fun `FakeAlgorithmProcessor returns Applied with notes when process succeeds`() = runTest {
        val expectedOutput = MediaOutputHandle(displayPath = "/tmp/result.jpg")
        val expectedNotes = listOf("algorithm-render:applied:photo-vivid")
        val processor = FakeAlgorithmProcessor(
            type = AlgorithmType.FILTER_RENDER,
            processResult = AlgorithmResult.Applied(
                output = expectedOutput,
                notes = expectedNotes
            )
        )
        val request = buildRequest()

        val result = processor.process(request)

        assertTrue(result is AlgorithmResult.Applied)
        val applied = result as AlgorithmResult.Applied
        assertEquals(expectedOutput, applied.output)
        assertEquals(expectedNotes, applied.notes)
        assertEquals(1, processor.invocations.size)
        assertEquals(request, processor.invocations.first())
    }

    @Test
    fun `FakeAlgorithmProcessor returns Skipped with reason when process declines`() = runTest {
        val processor = FakeAlgorithmProcessor(
            type = AlgorithmType.FILTER_RENDER,
            processResult = AlgorithmResult.Skipped(
                reason = "no-profile-specified",
                notes = emptyList()
            )
        )

        val result = processor.process(buildRequest())

        assertTrue(result is AlgorithmResult.Skipped)
        assertEquals("no-profile-specified", (result as AlgorithmResult.Skipped).reason)
    }

    @Test
    fun `FakeAlgorithmProcessor returns Failed with recoverable true`() = runTest {
        val processor = FakeAlgorithmProcessor(
            type = AlgorithmType.FILTER_RENDER,
            processResult = AlgorithmResult.Failed(
                reason = "out-of-memory",
                recoverable = true
            )
        )

        val result = processor.process(buildRequest())

        assertTrue(result is AlgorithmResult.Failed)
        assertEquals("out-of-memory", (result as AlgorithmResult.Failed).reason)
        assertTrue(result.recoverable)
    }

    @Test
    fun `FakeAlgorithmProcessor returns Failed with recoverable false`() = runTest {
        val processor = FakeAlgorithmProcessor(
            type = AlgorithmType.FILTER_RENDER,
            processResult = AlgorithmResult.Failed(
                reason = "corrupt-input",
                recoverable = false
            )
        )

        val result = processor.process(buildRequest())

        assertTrue(result is AlgorithmResult.Failed)
        assertFalse((result as AlgorithmResult.Failed).recoverable)
    }

    @Test
    fun `canProcess returns false when processor cannot handle request type`() {
        val processor = FakeAlgorithmProcessor(
            type = AlgorithmType.WATERMARK_RENDER,
            canProcessResult = false
        )
        val request = buildRequest(algorithmType = AlgorithmType.FILTER_RENDER)

        assertFalse(processor.canProcess(request))
    }

    @Test
    fun `AlgorithmRequest carries node inputs and metadata`() {
        val inputs = listOf(
            MediaInputRef(
                path = "/tmp/frame1.jpg",
                handle = MediaOutputHandle(displayPath = "/tmp/frame1.jpg"),
                mimeType = "image/jpeg"
            ),
            MediaInputRef(
                path = "/tmp/frame2.jpg",
                handle = MediaOutputHandle(displayPath = "/tmp/frame2.jpg"),
                mimeType = "image/jpeg"
            )
        )
        val metadata = MediaMetadata(
            algorithmProfile = "night-multiframe",
            customTags = mapOf("mode" to "night")
        )

        val request = AlgorithmRequest(
            node = AlgorithmNode(
                id = "merge-node",
                type = AlgorithmType.MULTI_FRAME_MERGE,
                inputs = inputs.map { it.path },
                output = "merged-output",
                requirement = AlgorithmRequirement.REQUIRED,
                fallback = AlgorithmFallback.FAIL_SHOT
            ),
            inputs = inputs,
            metadata = metadata,
            budget = AlgorithmBudget(maxDurationMillis = 60_000)
        )

        assertEquals("merge-node", request.node.id)
        assertEquals(AlgorithmType.MULTI_FRAME_MERGE, request.node.type)
        assertEquals(2, request.inputs.size)
        assertEquals("/tmp/frame1.jpg", request.inputs[0].path)
        assertEquals("night-multiframe", request.metadata.algorithmProfile)
        assertEquals("night", request.metadata.customTags["mode"])
        assertEquals(60_000L, request.budget.maxDurationMillis)
        assertEquals(AlgorithmRequirement.REQUIRED, request.node.requirement)
        assertEquals(AlgorithmFallback.FAIL_SHOT, request.node.fallback)
    }
}
