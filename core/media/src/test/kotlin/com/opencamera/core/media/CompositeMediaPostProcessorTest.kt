package com.opencamera.core.media

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CompositeMediaPostProcessorTest {

    @Test
    fun `throwing processor between two successful processors preserves chain`() = runTest {
        val first = NoteAppendingProcessor("first:applied")
        val throwing = ThrowingProcessor(RuntimeException("boom"))
        val third = NoteAppendingProcessor("third:applied")
        val composite = CompositeMediaPostProcessor(listOf(first, throwing, third))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.contains("first:applied"))
        assertTrue(result.pipelineNotes.any { it.contains("postprocess:failed:ThrowingProcessor") })
        assertTrue(result.pipelineNotes.contains("third:applied"))
    }

    @Test
    fun `throwing processor preserves output path and handle`() = runTest {
        val throwing = ThrowingProcessor(OutOfMemoryError("bitmap too large"))
        val composite = CompositeMediaPostProcessor(listOf(throwing))

        val input = baseResult()
        val result = composite.process(input)

        assertEquals(input.outputPath, result.outputPath)
        assertEquals(input.outputHandle, result.outputHandle)
        assertEquals(input.thumbnailSource, result.thumbnailSource)
    }

    @Test
    fun `all processors succeed adds all notes`() = runTest {
        val first = NoteAppendingProcessor("a:done")
        val second = NoteAppendingProcessor("b:done")
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())

        assertTrue(result.pipelineNotes.contains("a:done"))
        assertTrue(result.pipelineNotes.contains("b:done"))
        assertEquals(2, result.pipelineNotes.size)
    }

    @Test
    fun `empty processor list returns original result`() = runTest {
        val composite = CompositeMediaPostProcessor(emptyList())
        val input = baseResult()
        val result = composite.process(input)
        assertEquals(input, result)
    }

    @Test
    fun `onProcessorTimed callback receives each processor name and elapsed ms`() = runTest {
        val timings = mutableListOf<Pair<String, Long>>()
        val first = NoteAppendingProcessor("a:done")
        val second = NoteAppendingProcessor("b:done")
        val composite = CompositeMediaPostProcessor(
            processors = listOf(first, second),
            onProcessorTimed = { name, elapsedMs -> timings.add(name to elapsedMs) }
        )

        composite.process(baseResult())

        assertEquals(2, timings.size)
        assertEquals("NoteAppendingProcessor", timings[0].first)
        assertTrue(timings[0].second >= 0)
        assertEquals("NoteAppendingProcessor", timings[1].first)
    }

    @Test
    fun `multiple throwing processors all record failure notes`() = runTest {
        val first = ThrowingProcessor(RuntimeException("first-crash"))
        val second = ThrowingProcessor(IllegalStateException("second-crash"))
        val composite = CompositeMediaPostProcessor(listOf(first, second))

        val result = composite.process(baseResult())

        assertEquals(2, result.pipelineNotes.size)
        assertTrue(result.pipelineNotes.all { it.startsWith("postprocess:failed:") })
    }

    private fun baseResult(): ShotResult {
        return ShotResult(
            shotId = "test-shot",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/test-photo.jpg",
            outputHandle = MediaOutputHandle(
                displayPath = "/tmp/test-photo.jpg",
                filePath = "/tmp/test-photo.jpg"
            ),
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.SavedMedia(
                outputPath = "/tmp/test-photo.jpg"
            ),
            metadata = MediaMetadata()
        )
    }

    private class NoteAppendingProcessor(private val note: String) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            return result.addPipelineNotes(note)
        }
    }

    private class ThrowingProcessor(private val error: Throwable) : MediaPostProcessor {
        override suspend fun process(result: ShotResult): ShotResult {
            throw error
        }
    }
}
