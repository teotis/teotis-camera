package com.opencamera.core.media

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class FrameRingBufferTest {

    @Test
    fun `append keeps frames sorted by timestamp`() {
        val buffer = FrameRingBuffer(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        val frame1 = makeDescriptor("f1", timestampNanos = 300)
        val frame2 = makeDescriptor("f2", timestampNanos = 100)
        val frame3 = makeDescriptor("f3", timestampNanos = 200)

        buffer.append(frame1)
        buffer.append(frame2)
        buffer.append(frame3)

        val snapshot = buffer.snapshot()
        assertEquals(3, snapshot.size)
        assertEquals(100L, snapshot[0].timestampNanos)
        assertEquals(200L, snapshot[1].timestampNanos)
        assertEquals(300L, snapshot[2].timestampNanos)
    }

    @Test
    fun `exceeding maxFrames evicts oldest frames`() {
        // LIVE_PREVIEW_DEFAULT has maxFrames = 24
        val policy = FrameBufferPolicy.LIVE_PREVIEW_DEFAULT
        val buffer = FrameRingBuffer(policy)

        // Add 26 frames (exceeds maxFrames by 2)
        for (i in 1..26) {
            buffer.append(makeDescriptor("f$i", timestampNanos = i * 100L))
        }

        val snapshot = buffer.snapshot()
        // Should keep only the 24 most recent frames
        assertEquals(policy.maxFrames, snapshot.size)
        // Oldest frames (100, 200) should be evicted
        assertEquals(300L, snapshot[0].timestampNanos)
        assertEquals(2600L, snapshot.last().timestampNanos)
    }

    @Test
    fun `exceeding retentionWindowMillis evicts stale frames`() {
        // LIVE_PREVIEW_DEFAULT has retentionWindowMillis = 2000ms = 2_000_000_000 nanos
        val policy = FrameBufferPolicy.LIVE_PREVIEW_DEFAULT
        val buffer = FrameRingBuffer(policy)

        // Add frames spanning 3000ms (exceeds 2000ms retention)
        buffer.append(makeDescriptor("f1", timestampNanos = 1_000_000_000L))  // t=1s
        buffer.append(makeDescriptor("f2", timestampNanos = 2_000_000_000L))  // t=2s
        buffer.append(makeDescriptor("f3", timestampNanos = 3_000_000_000L))  // t=3s
        buffer.append(makeDescriptor("f4", timestampNanos = 4_000_000_000L))  // t=4s

        val snapshot = buffer.snapshot()
        // Frames at t=1s should be evicted (3s - 1s = 2s > 2s retention, or 4s - 1s = 3s > 2s)
        // Frames at t=2s should be kept (4s - 2s = 2s <= 2s retention)
        assertEquals(3, snapshot.size)
        assertEquals(2_000_000_000L, snapshot[0].timestampNanos)
        assertEquals(4_000_000_000L, snapshot.last().timestampNanos)
    }

    @Test
    fun `select around shutter returns pre and post counts`() {
        val buffer = FrameRingBuffer(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        // Add frames: -1200ms, -800ms, -400ms, 0ms (shutter), +200ms, +400ms relative to shutter
        val shutterNanos = 2_000_000_000L  // 2s
        buffer.append(makeDescriptor("pre1", timestampNanos = 800_000_000L))   // -1200ms
        buffer.append(makeDescriptor("pre2", timestampNanos = 1_200_000_000L)) // -800ms
        buffer.append(makeDescriptor("pre3", timestampNanos = 1_600_000_000L)) // -400ms
        buffer.append(makeDescriptor("shutter", timestampNanos = shutterNanos)) // 0ms
        buffer.append(makeDescriptor("post1", timestampNanos = 2_200_000_000L)) // +200ms
        buffer.append(makeDescriptor("post2", timestampNanos = 2_400_000_000L)) // +400ms

        val window = FrameSelectionWindow(
            shutterTimestampNanos = shutterNanos,
            preShutterMillis = 1000,
            postShutterMillis = 500
        )

        val selected = buffer.select(window)

        // Should include frames within [-1000ms, +500ms] of shutter
        // pre2 (-800ms), pre3 (-400ms), shutter (0ms), post1 (+200ms), post2 (+400ms)
        assertEquals(5, selected.frames.size)
        assertEquals(3, selected.preShutterCount)  // pre2, pre3, shutter
        assertEquals(2, selected.postShutterCount)  // post1, post2
        // pre1 (-1200ms) is outside the window
        assertFalse(selected.frames.any { it.frameId == "pre1" })
    }

    @Test
    fun `clear empties buffer`() {
        val buffer = FrameRingBuffer(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)

        buffer.append(makeDescriptor("f1", timestampNanos = 100))
        buffer.append(makeDescriptor("f2", timestampNanos = 200))
        assertEquals(2, buffer.snapshot().size)

        buffer.clear()
        assertEquals(0, buffer.snapshot().size)
    }

    private fun makeDescriptor(
        frameId: String,
        timestampNanos: Long,
        width: Int = 640,
        height: Int = 480
    ) = FrameDescriptor(
        frameId = frameId,
        source = FrameSourceKind.PREVIEW_ANALYSIS,
        timestampNanos = timestampNanos,
        width = width,
        height = height,
        rotationDegrees = 0,
        payloadAccess = FramePayloadAccess.METADATA_ONLY,
        lensFacingTag = "BACK",
        zoomRatio = 1.0f
    )

    @Test
    fun `concurrent append select and clear do not throw or corrupt`() {
        val buffer = FrameRingBuffer(FrameBufferPolicy.LIVE_PREVIEW_DEFAULT)
        val iterations = 500
        val writerCount = 4
        val readerCount = 4
        val totalThreads = writerCount + readerCount
        val barrier = CyclicBarrier(totalThreads)
        val failed = AtomicBoolean(false)

        val latch = CountDownLatch(totalThreads)

        val writers = (0 until writerCount).map { writerIdx ->
            Thread {
                barrier.await()
                for (i in 0 until iterations) {
                    val ts = (writerIdx * iterations.toLong() + i) * 1_000_000
                    buffer.append(makeDescriptor("w${writerIdx}_$i", timestampNanos = ts))
                }
                latch.countDown()
            }
        }

        val readers = (0 until readerCount).map {
            Thread {
                barrier.await()
                repeat(iterations) {
                    try {
                        val snap = buffer.snapshot()
                        for (j in 1 until snap.size) {
                            assertTrue(
                                "snapshot not sorted: ${snap[j - 1].timestampNanos} > ${snap[j].timestampNanos}",
                                snap[j - 1].timestampNanos <= snap[j].timestampNanos
                            )
                        }

                        if (snap.isNotEmpty()) {
                            val mid = snap.size / 2
                            val window = FrameSelectionWindow(
                                shutterTimestampNanos = snap[mid].timestampNanos,
                                preShutterMillis = 10,
                                postShutterMillis = 10
                            )
                            val sel = buffer.select(window)
                            for (f in sel.frames) {
                                assertTrue(
                                    "selected frame outside window",
                                    f.timestampNanos in (snap[mid].timestampNanos - 10_000_000)..(snap[mid].timestampNanos + 10_000_000)
                                )
                            }
                        }
                    } catch (e: Exception) {
                        failed.set(true)
                        throw e
                    }
                }
                latch.countDown()
            }
        }

        (writers + readers).forEach { it.start() }
        latch.await()

        assertFalse("concurrent access caused an exception", failed.get())

        val finalSnap = buffer.snapshot()
        for (i in 1 until finalSnap.size) {
            assertTrue(
                "final snapshot not sorted",
                finalSnap[i - 1].timestampNanos <= finalSnap[i].timestampNanos
            )
        }
        assertTrue(finalSnap.size <= FrameBufferPolicy.LIVE_PREVIEW_DEFAULT.maxFrames)
    }
}
