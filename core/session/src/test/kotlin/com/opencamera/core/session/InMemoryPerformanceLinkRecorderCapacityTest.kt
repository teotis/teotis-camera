package com.opencamera.core.session

import kotlin.test.Test
import kotlin.test.assertEquals

class InMemoryPerformanceLinkRecorderCapacityTest {

    @Test
    fun `when events exceed maxEvents then oldest removed`() {
        val recorder = InMemoryPerformanceLinkRecorder(maxEvents = 50)
        repeat(100) { i ->
            recorder.recordEvent(
                PerformanceLinkEvent(
                    flow = "test",
                    stage = "stage.$i",
                    status = LinkEventStatus.COMPLETED,
                    correlationId = "id.$i",
                    startElapsedMillis = 0L,
                    endElapsedMillis = 1L,
                    durationMillis = 1L,
                    detail = null,
                    source = "test"
                )
            )
        }
        val snap = recorder.snapshot()
        assertEquals(50, snap.size)
        assertEquals("stage.50", snap.first().stage)
        assertEquals("stage.99", snap.last().stage)
    }

    @Test
    fun `snapshotForDisplay returns last N`() {
        val recorder = InMemoryPerformanceLinkRecorder(maxEvents = 1000)
        repeat(500) { i ->
            recorder.recordEvent(
                PerformanceLinkEvent(
                    flow = "test",
                    stage = "stage.$i",
                    status = LinkEventStatus.COMPLETED,
                    correlationId = "id.$i",
                    startElapsedMillis = 0L,
                    endElapsedMillis = 1L,
                    durationMillis = 1L,
                    detail = null,
                    source = "test"
                )
            )
        }
        val display = recorder.snapshotForDisplay(100)
        assertEquals(100, display.size)
        assertEquals("stage.400", display.first().stage)
        assertEquals("stage.499", display.last().stage)
    }
}
