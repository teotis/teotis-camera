package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ResourceBudgetContractsTest {

    @Test
    fun `camera thermal state has four values`() {
        assertEquals(4, CameraThermalState.entries.size)
    }

    @Test
    fun `camera performance class has four values`() {
        assertEquals(4, CameraPerformanceClass.entries.size)
    }

    @Test
    fun `default resource budget uses 256mb memory`() {
        val budget = CameraResourceBudget()
        assertEquals(256L * 1024 * 1024, budget.memoryBytes)
    }

    @Test
    fun `default resource budget uses 15 max analysis fps`() {
        val budget = CameraResourceBudget()
        assertEquals(15, budget.maxAnalysisFps)
    }

    @Test
    fun `camera work admission carries effective budget`() {
        val budget = CameraResourceBudget(thermalState = CameraThermalState.HOT)
        val admission = CameraWorkAdmission(
            admitted = true,
            degraded = true,
            reason = "thermal hot",
            effectiveBudget = budget,
            featureAdjustments = mapOf("live-photo" to "reduced-duration")
        )
        assertTrue(admission.admitted)
        assertTrue(admission.degraded)
        assertEquals(budget, admission.effectiveBudget)
        assertEquals("reduced-duration", admission.featureAdjustments["live-photo"])
    }

    @Test
    fun `algorithm job class has four values`() {
        assertEquals(4, AlgorithmJobClass.entries.size)
    }

    @Test
    fun `algorithm job result completed wraps algorithm result`() {
        val inner = AlgorithmResult.Skipped(reason = "test", notes = listOf("note"))
        val result = AlgorithmJobResult.Completed(inner)
        assertEquals(inner, result.result)
    }

    @Test
    fun `algorithm job result timed out carries recoverable flag`() {
        val recoverable = AlgorithmJobResult.TimedOut(reason = "timeout", recoverable = true)
        val nonRecoverable = AlgorithmJobResult.TimedOut(reason = "timeout", recoverable = false)
        assertTrue(recoverable.recoverable)
        assertFalse(nonRecoverable.recoverable)
    }

    @Test
    fun `algorithm job result rejected carries reason`() {
        val result = AlgorithmJobResult.Rejected(reason = "budget exceeded")
        assertEquals("budget exceeded", result.reason)
    }

    @Test
    fun `thermal state tag values match lowercase enum names`() {
        assertEquals("normal", CameraThermalState.NORMAL.tagValue)
        assertEquals("warm", CameraThermalState.WARM.tagValue)
        assertEquals("hot", CameraThermalState.HOT.tagValue)
        assertEquals("critical", CameraThermalState.CRITICAL.tagValue)
    }

    @Test
    fun `performance class tag values match lowercase enum names`() {
        assertEquals("low", CameraPerformanceClass.LOW.tagValue)
        assertEquals("mid", CameraPerformanceClass.MID.tagValue)
        assertEquals("high", CameraPerformanceClass.HIGH.tagValue)
        assertEquals("unknown", CameraPerformanceClass.UNKNOWN.tagValue)
    }
}
