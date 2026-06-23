package com.opencamera.core.session

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemorySessionTraceCapacityTest {

    @Test
    fun `when events exceed memoryRetention then oldest is removed and size stays at retention`() {
        val retention = 500
        val trace = InMemorySessionTrace(memoryRetention = retention)
        repeat(retention + 100) { trace.record("event.$it", "detail") }
        val snap = trace.snapshot()
        assertEquals(retention, snap.size)
        assertEquals("event.100", snap.first().name)
        assertEquals("event.${retention + 99}", snap.last().name)
    }

    @Test
    fun `removeFirst is O1 amortized`() {
        val trace = InMemorySessionTrace(memoryRetention = 50_000)
        val start = System.currentTimeMillis()
        repeat(50_000) { trace.record("event.$it", "detail") }
        val elapsed = System.currentTimeMillis() - start
        assertEquals(50_000, trace.snapshot().size)
        assertTrue(elapsed < 500, "Writing 50k events took ${elapsed}ms, expected < 500ms")
    }

    @Test
    fun `snapshotForDisplay returns last N events in order`() {
        val trace = InMemorySessionTrace(memoryRetention = 10_000)
        repeat(5000) { trace.record("event.$it", "detail") }
        val display = trace.snapshotForDisplay(1000)
        assertEquals(1000, display.size)
        assertEquals("event.4000", display.first().name)
        assertEquals("event.4999", display.last().name)
    }

    @Test
    fun `snapshotForDisplay with window larger than size returns all`() {
        val trace = InMemorySessionTrace(memoryRetention = 1000)
        repeat(100) { trace.record("event.$it", "detail") }
        val display = trace.snapshotForDisplay(1000)
        assertEquals(100, display.size)
        assertEquals("event.0", display.first().name)
        assertEquals("event.99", display.last().name)
    }

    @Test
    fun `begin and end paired events are both retained within retention`() {
        val trace = InMemorySessionTrace(memoryRetention = 100)
        val handle = trace.begin("op")
        trace.end(handle, "done")
        val snap = trace.snapshot()
        assertEquals(2, snap.size)
        assertEquals("op.started", snap[0].name)
        assertEquals("op.completed", snap[1].name)
    }

    @Test
    fun `displayWindow does not affect memory retention`() {
        val trace = InMemorySessionTrace(memoryRetention = 5000, displayWindow = 100)
        repeat(200) { trace.record("event.$it", "detail") }
        assertEquals(200, trace.snapshot().size)
        assertEquals(100, trace.snapshotForDisplay().size)
    }

    @Test
    fun `deprecated maxEvents constructor maps to memoryRetention`() {
        @Suppress("DEPRECATION")
        val trace = InMemorySessionTrace(maxEvents = 100)
        repeat(200) { trace.record("event.$it", "detail") }
        assertEquals(100, trace.snapshot().size)
        assertEquals("event.100", trace.snapshot().first().name)
    }
}
