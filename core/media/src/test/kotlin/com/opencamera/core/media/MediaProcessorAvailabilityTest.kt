/**
 * 覆盖行为:
 * - ALL_AVAILABLE 所有字段为 true
 * - NONE_AVAILABLE 所有字段为 false
 * - 自定义构造各字段正确
 * - 默认构造所有字段为 true
 *
 * 不适合单测的行为: 无
 */
package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MediaProcessorAvailabilityTest {

    @Test
    fun `ALL_AVAILABLE has all fields true`() {
        val a = MediaProcessorAvailability.ALL_AVAILABLE
        assertTrue(a.filterRenderAvailable)
        assertTrue(a.watermarkRenderAvailable)
        assertTrue(a.multiFrameMergeAvailable)
        assertTrue(a.portraitRenderAvailable)
        assertTrue(a.documentProcessorAvailable)
        assertTrue(a.temporalMediaAssemblerAvailable)
    }

    @Test
    fun `NONE_AVAILABLE has all fields false`() {
        val a = MediaProcessorAvailability.NONE_AVAILABLE
        assertFalse(a.filterRenderAvailable)
        assertFalse(a.watermarkRenderAvailable)
        assertFalse(a.multiFrameMergeAvailable)
        assertFalse(a.portraitRenderAvailable)
        assertFalse(a.documentProcessorAvailable)
        assertFalse(a.temporalMediaAssemblerAvailable)
    }

    @Test
    fun `default constructor has all fields true`() {
        val a = MediaProcessorAvailability()
        assertTrue(a.filterRenderAvailable)
        assertTrue(a.watermarkRenderAvailable)
        assertTrue(a.multiFrameMergeAvailable)
        assertTrue(a.portraitRenderAvailable)
        assertTrue(a.documentProcessorAvailable)
        assertTrue(a.temporalMediaAssemblerAvailable)
    }

    @Test
    fun `custom construction sets individual fields correctly`() {
        val a = MediaProcessorAvailability(
            filterRenderAvailable = true,
            watermarkRenderAvailable = false,
            multiFrameMergeAvailable = true,
            portraitRenderAvailable = false,
            documentProcessorAvailable = true,
            temporalMediaAssemblerAvailable = false
        )
        assertTrue(a.filterRenderAvailable)
        assertFalse(a.watermarkRenderAvailable)
        assertTrue(a.multiFrameMergeAvailable)
        assertFalse(a.portraitRenderAvailable)
        assertTrue(a.documentProcessorAvailable)
        assertFalse(a.temporalMediaAssemblerAvailable)
    }

    @Test
    fun `data class equality works`() {
        val a = MediaProcessorAvailability()
        val b = MediaProcessorAvailability()
        assertEquals(a, b)
    }

    @Test
    fun `data class copy works`() {
        val original = MediaProcessorAvailability.ALL_AVAILABLE
        val modified = original.copy(filterRenderAvailable = false)
        assertFalse(modified.filterRenderAvailable)
        assertTrue(modified.watermarkRenderAvailable)
    }
}
