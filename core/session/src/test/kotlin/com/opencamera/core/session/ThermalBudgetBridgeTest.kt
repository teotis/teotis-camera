package com.opencamera.core.session

import com.opencamera.core.media.CameraPerformanceClass
import com.opencamera.core.media.CameraResourceBudget
import com.opencamera.core.media.CameraThermalState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ThermalBudgetBridgeTest {

    // ── Android thermal status mapping ────────────────────────────────────────

    @Test
    fun `android thermal none maps to normal camera state`() {
        assertEquals(CameraThermalState.NORMAL, androidThermalStatusToCameraState(0))
    }

    @Test
    fun `android thermal moderate maps to warm camera state`() {
        assertEquals(CameraThermalState.WARM, androidThermalStatusToCameraState(2))
    }

    @Test
    fun `android thermal severe maps to hot camera state`() {
        assertEquals(CameraThermalState.HOT, androidThermalStatusToCameraState(3))
    }

    @Test
    fun `android thermal critical maps to critical camera state`() {
        assertEquals(CameraThermalState.CRITICAL, androidThermalStatusToCameraState(4))
    }

    @Test
    fun `android thermal emergency maps to critical camera state`() {
        assertEquals(CameraThermalState.CRITICAL, androidThermalStatusToCameraState(5))
    }

    // ── Budget derivation ─────────────────────────────────────────────────────

    @Test
    fun `derive budget with high performance and normal thermal returns full defaults`() {
        val budget = deriveBudget(CameraPerformanceClass.HIGH, CameraThermalState.NORMAL)
        assertEquals(15, budget.maxAnalysisFps)
        assertEquals(12, budget.maxProtectedFrames)
        assertEquals(3, budget.maxConcurrentAlgorithmJobs)
    }

    @Test
    fun `derive budget with low performance returns reduced analysis fps`() {
        val budget = deriveBudget(CameraPerformanceClass.LOW, CameraThermalState.NORMAL)
        assertEquals(8, budget.maxAnalysisFps)
        assertEquals(4, budget.maxProtectedFrames)
        assertEquals(1, budget.maxConcurrentAlgorithmJobs)
    }

    @Test
    fun `derive budget with hot thermal reduces protected frames`() {
        val budget = deriveBudget(CameraPerformanceClass.MID, CameraThermalState.HOT)
        assertTrue(budget.maxProtectedFrames < 8)
        assertEquals(5, budget.maxAnalysisFps) // MID=10, HOT multiplier=0.5
    }

    @Test
    fun `derive budget with critical thermal minimizes concurrent jobs`() {
        val budget = deriveBudget(CameraPerformanceClass.HIGH, CameraThermalState.CRITICAL)
        assertEquals(0, budget.maxAnalysisFps)
        assertTrue(budget.maxConcurrentAlgorithmJobs <= 2)
    }

    // ── Resource diagnostics snapshot ─────────────────────────────────────────

    @Test
    fun `resource diagnostics snapshot includes thermal and class tags`() {
        val budget = CameraResourceBudget(
            thermalState = CameraThermalState.WARM,
            performanceClass = CameraPerformanceClass.MID
        )
        val snapshot = buildResourceDiagnosticsSnapshot(budget)
        assertTrue(snapshot.pipelineNotes.contains("resource:class=mid"))
        assertTrue(snapshot.pipelineNotes.contains("resource:thermal=warm"))
        assertTrue(snapshot.pipelineNotes.contains("resource:analysis-fps=15"))
    }

    @Test
    fun `resource diagnostics snapshot marks algorithm queue busy when at capacity`() {
        val budget = CameraResourceBudget(maxConcurrentAlgorithmJobs = 2)
        val snapshot = buildResourceDiagnosticsSnapshot(budget, activeAlgorithmJobs = 2)
        assertTrue(snapshot.pipelineNotes.contains("resource:algorithm-queue=busy"))
    }

    @Test
    fun `resource diagnostics snapshot does not mark busy when under capacity`() {
        val budget = CameraResourceBudget(maxConcurrentAlgorithmJobs = 2)
        val snapshot = buildResourceDiagnosticsSnapshot(budget, activeAlgorithmJobs = 1)
        assertFalse(snapshot.pipelineNotes.any { it.contains("algorithm-queue=busy") })
    }

    @Test
    fun `resource diagnostics snapshot includes feature degradations in notes`() {
        val budget = CameraResourceBudget()
        val snapshot = buildResourceDiagnosticsSnapshot(
            budget,
            featureDegradations = mapOf("live" to "degraded:max-frames")
        )
        assertTrue(snapshot.pipelineNotes.contains("resource:live=degraded:max-frames"))
    }
}
