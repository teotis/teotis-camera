package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals

class ProductionSessionTraceTest {

    @Test
    fun `production trace retains deep history but bounds the live list`() {
        val trace = createProductionSessionTrace()

        repeat(2_000) { trace.record("event.$it", "detail") }

        assertEquals(2_000, trace.snapshot().size)
        assertEquals(2_000, trace.snapshotForDisplay(PRODUCTION_TRACE_DISPLAY_WINDOW).size)
        assertEquals(10_000, PRODUCTION_TRACE_MEMORY_RETENTION)
        assertEquals(5_000, PRODUCTION_TRACE_DISPLAY_WINDOW)
    }
}
