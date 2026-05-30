package com.opencamera.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PerformanceLinkEventTest {

    // -- Duration math ---------------------------------------------------

    @Test
    fun `duration is difference of end and start elapsed millis`() {
        val event = PerformanceLinkEvent(
            flow = "capture",
            stage = "device",
            status = LinkEventStatus.COMPLETED,
            correlationId = "shot-1",
            startElapsedMillis = 100L,
            endElapsedMillis = 245L,
            durationMillis = 145L,
            detail = null,
            source = "test"
        )
        assertEquals(145L, event.durationMillis)
    }

    @Test
    fun `span snapshot computes duration from monotonic elapsed stamps`() {
        val snapshot = PerformanceSpanSnapshot(
            flow = "capture",
            stage = "device",
            correlationId = "shot-2",
            startElapsedMillis = 1_000_000L,
            endElapsedMillis = 1_000_150L,
            status = LinkEventStatus.COMPLETED,
            detail = null,
            source = "test"
        )
        assertEquals(150L, snapshot.durationMillis)
    }

    @Test
    fun `span snapshot clamps negative duration to zero`() {
        val snapshot = PerformanceSpanSnapshot(
            flow = "preview",
            stage = "startup",
            correlationId = "boot-1",
            startElapsedMillis = 200L,
            endElapsedMillis = 150L,
            status = LinkEventStatus.DEGRADED,
            detail = "clock anomaly",
            source = "test"
        )
        assertEquals(0L, snapshot.durationMillis)
    }

    // -- Missing endpoints -----------------------------------------------

    @Test
    fun `duration is null when end elapsed is missing`() {
        val event = PerformanceLinkEvent(
            flow = "capture",
            stage = "postprocess",
            status = LinkEventStatus.UNAVAILABLE,
            correlationId = "shot-3",
            startElapsedMillis = 300L,
            endElapsedMillis = null,
            durationMillis = null,
            detail = "postprocess did not complete",
            source = "test"
        )
        assertNull(event.durationMillis)
        assertEquals(LinkEventStatus.UNAVAILABLE, event.status)
    }

    @Test
    fun `span snapshot with null end returns null duration`() {
        val snapshot = PerformanceSpanSnapshot(
            flow = "capture",
            stage = "postprocess",
            correlationId = "shot-4",
            startElapsedMillis = 400L,
            endElapsedMillis = null,
            status = LinkEventStatus.STARTED,
            detail = null,
            source = "test"
        )
        assertNull(snapshot.durationMillis)
    }

    @Test
    fun `snapshot toEvent preserves null duration when endpoint missing`() {
        val snapshot = PerformanceSpanSnapshot(
            flow = "recording",
            stage = "stop",
            correlationId = "rec-1",
            startElapsedMillis = 500L,
            endElapsedMillis = null,
            status = LinkEventStatus.STARTED,
            detail = null,
            source = "test"
        )
        val event = snapshot.toEvent()
        assertNull(event.durationMillis)
        assertNull(event.endElapsedMillis)
        assertEquals(LinkEventStatus.STARTED, event.status)
    }

    // -- Link log formatting ---------------------------------------------

    @Test
    fun `link log line includes all required fields`() {
        val event = PerformanceLinkEvent(
            flow = "capture",
            stage = "device",
            status = LinkEventStatus.COMPLETED,
            correlationId = "shot-1",
            startElapsedMillis = 100L,
            endElapsedMillis = 245L,
            durationMillis = 145L,
            detail = "exposure=16ms",
            source = "CaptureSessionProcessor"
        )
        val line = event.toLinkLogLine()

        assertTrue(line.startsWith("link "))
        assertTrue(line.contains("flow=capture"))
        assertTrue(line.contains("stage=device"))
        assertTrue(line.contains("status=completed"))
        assertTrue(line.contains("id=shot-1"))
        assertTrue(line.contains("startElapsed=100"))
        assertTrue(line.contains("endElapsed=245"))
        assertTrue(line.contains("duration=145ms"))
        assertTrue(line.contains("detail=exposure_16ms"))
        assertTrue(line.contains("source=CaptureSessionProcessor"))
    }

    @Test
    fun `link log line escapes spaces and equals in values`() {
        val event = PerformanceLinkEvent(
            flow = "still capture",
            stage = "device=pipeline",
            status = LinkEventStatus.COMPLETED,
            correlationId = "shot two",
            startElapsedMillis = 10L,
            endElapsedMillis = 20L,
            durationMillis = 10L,
            detail = "some detail with spaces",
            source = "Test Source"
        )
        val line = event.toLinkLogLine()

        assertTrue(line.contains("flow=still_capture"))
        assertTrue(line.contains("stage=device_pipeline"))
        assertTrue(line.contains("id=shot_two"))
        assertTrue(line.contains("detail=some_detail_with_spaces"))
        assertTrue(line.contains("source=Test_Source"))
    }

    @Test
    fun `link log line omits detail when null or blank`() {
        val event = PerformanceLinkEvent(
            flow = "preview",
            stage = "firstFrame",
            status = LinkEventStatus.COMPLETED,
            correlationId = "preview-1",
            startElapsedMillis = 0L,
            endElapsedMillis = 84L,
            durationMillis = 84L,
            detail = null,
            source = "test"
        )
        val line = event.toLinkLogLine()
        assertTrue(!line.contains("detail="))
    }

    @Test
    fun `link log line omits endElapsed and duration when unavailable`() {
        val event = PerformanceLinkEvent(
            flow = "recording",
            stage = "save",
            status = LinkEventStatus.UNAVAILABLE,
            correlationId = "rec-9",
            startElapsedMillis = 5000L,
            endElapsedMillis = null,
            durationMillis = null,
            detail = null,
            source = "test"
        )
        val line = event.toLinkLogLine()
        assertTrue(!line.contains("endElapsed="))
        assertTrue(!line.contains("duration="))
    }

    @Test
    fun `link log line renders each status label`() {
        val statusLabels = mapOf(
            LinkEventStatus.STARTED to "started",
            LinkEventStatus.COMPLETED to "completed",
            LinkEventStatus.DEGRADED to "degraded",
            LinkEventStatus.FAILED to "failed",
            LinkEventStatus.CANCELLED to "cancelled",
            LinkEventStatus.UNAVAILABLE to "unavailable"
        )
        for ((status, label) in statusLabels) {
            val event = PerformanceLinkEvent(
                flow = "f", stage = "s", status = status,
                correlationId = "x", startElapsedMillis = 0L,
                endElapsedMillis = 1L, durationMillis = 1L,
                detail = null, source = "t"
            )
            assertTrue(event.toLinkLogLine().contains("status=$label"))
        }
    }

    // -- Recorder: start / complete / retrieve ---------------------------

    @Test
    fun `recorder starts and completes a span`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("capture", "device", "shot-1", source = "test")

        assertEquals("capture", span.flow)
        assertEquals("device", span.stage)
        assertEquals("shot-1", span.correlationId)
        assertEquals(LinkEventStatus.STARTED, span.status)
        assertNull(span.endElapsedMillis)

        val event = recorder.completeSpan(span)
        assertEquals(LinkEventStatus.COMPLETED, event.status)
        assertNotNull(event.endElapsedMillis)
        assertNotNull(event.durationMillis)

        val events = recorder.snapshot()
        assertEquals(1, events.size)
        assertEquals("shot-1", events[0].correlationId)
    }

    @Test
    fun `recorder completes span with custom status and detail`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("preview", "recovery", "rec-1", source = "test")
        val event = recorder.completeSpan(span, status = LinkEventStatus.DEGRADED, detail = "timeout triggered")

        assertEquals(LinkEventStatus.DEGRADED, event.status)
        assertEquals("timeout triggered", event.detail)
    }

    @Test
    fun `recorder records direct event`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val event = PerformanceLinkEvent(
            flow = "capture", stage = "postprocess",
            status = LinkEventStatus.FAILED, correlationId = "shot-99",
            startElapsedMillis = 1000L, endElapsedMillis = 1500L,
            durationMillis = 500L, detail = "encoding error", source = "direct"
        )
        recorder.recordEvent(event)
        assertEquals(1, recorder.snapshot().size)
        assertEquals("shot-99", recorder.snapshot()[0].correlationId)
    }

    @Test
    fun `recorder caps events at configured max`() {
        val recorder = InMemoryPerformanceLinkRecorder(maxEvents = 3)
        for (i in 1..5) {
            val span = recorder.startSpan("flow", "stage", "id-$i", source = "test")
            recorder.completeSpan(span)
        }
        val events = recorder.snapshot()
        assertEquals(3, events.size)
        assertEquals("id-3", events[0].correlationId)
        assertEquals("id-4", events[1].correlationId)
        assertEquals("id-5", events[2].correlationId)
    }

    @Test
    fun `recorder snapshot is independent copy`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        recorder.recordEvent(
            PerformanceLinkEvent(
                flow = "f", stage = "s", status = LinkEventStatus.COMPLETED,
                correlationId = "id-1", startElapsedMillis = 0L, endElapsedMillis = 1L,
                durationMillis = 1L, detail = null, source = "t"
            )
        )
        val snap1 = recorder.snapshot()
        recorder.recordEvent(
            PerformanceLinkEvent(
                flow = "f2", stage = "s2", status = LinkEventStatus.COMPLETED,
                correlationId = "id-2", startElapsedMillis = 2L, endElapsedMillis = 3L,
                durationMillis = 1L, detail = null, source = "t"
            )
        )
        assertEquals(1, snap1.size)
        assertEquals(2, recorder.snapshot().size)
    }

    @Test
    fun `recorder uses monotonic time for duration`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("preview", "startup", "boot-1", source = "test")

        // startElapsed should be positive
        assertTrue(span.startElapsedMillis >= 0)

        val event = recorder.completeSpan(span)

        // duration should be non-negative and tiny (same call)
        assertNotNull(event.durationMillis)
        assertTrue(event.durationMillis!! >= 0)
    }

    // -- SpanSnapshot toEvent fidelity -----------------------------------

    @Test
    fun `snapshot toEvent preserves all identity fields`() {
        val snapshot = PerformanceSpanSnapshot(
            flow = "capture",
            stage = "save",
            correlationId = "shot-10",
            startElapsedMillis = 700L,
            endElapsedMillis = 800L,
            status = LinkEventStatus.COMPLETED,
            detail = "saved to disk",
            source = "MediaSaver"
        )
        val event = snapshot.toEvent()

        assertEquals(snapshot.flow, event.flow)
        assertEquals(snapshot.stage, event.stage)
        assertEquals(snapshot.correlationId, event.correlationId)
        assertEquals(snapshot.startElapsedMillis, event.startElapsedMillis)
        assertEquals(snapshot.endElapsedMillis, event.endElapsedMillis)
        assertEquals(snapshot.durationMillis, event.durationMillis)
        assertEquals(snapshot.status, event.status)
        assertEquals(snapshot.detail, event.detail)
        assertEquals(snapshot.source, event.source)
    }

    // -- Complete span resolves end time ----------------------------------

    @Test
    fun `completeSpan sets endElapsed when status is COMPLETED`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("capture", "device", "shot-1", source = "test")
        val event = recorder.completeSpan(span, status = LinkEventStatus.COMPLETED)
        assertNotNull(event.endElapsedMillis)
        assertTrue(event.endElapsedMillis!! >= span.startElapsedMillis)
    }

    @Test
    fun `completeSpan with UNAVAILABLE status preserves null endElapsed`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("capture", "save", "shot-5", source = "test")
        val event = recorder.completeSpan(span, status = LinkEventStatus.UNAVAILABLE)
        assertNull(event.endElapsedMillis)
        assertNull(event.durationMillis)
    }

    @Test
    fun `completeSpan with FAILED status records endElapsed`() {
        val recorder = InMemoryPerformanceLinkRecorder()
        val span = recorder.startSpan("recording", "stop", "rec-1", source = "test")
        val event = recorder.completeSpan(span, status = LinkEventStatus.FAILED, detail = "io error")
        assertEquals(LinkEventStatus.FAILED, event.status)
        assertEquals("io error", event.detail)
        assertNotNull(event.endElapsedMillis)
    }
}
