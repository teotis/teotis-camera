package com.opencamera.app.camera

import android.os.PowerManager
import com.opencamera.core.device.DeviceRuntimeIssueKind
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AndroidThermalRuntimeIssueMonitorTest {
    @Test
    fun `attach registers backend and emits current severe thermal status once`() = runTest {
        val backend = FakeThermalStatusBackend(
            currentStatus = PowerManager.THERMAL_STATUS_SEVERE
        )
        val monitor = AndroidThermalRuntimeIssueMonitor(backend)
        val recordedIssues = mutableListOf<String>()
        val collectionJob = launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.runtimeIssues.collect { issue ->
                recordedIssues += "${issue.kind}:${issue.reason}:${issue.isRecoverable}"
            }
        }

        monitor.onPreviewHostAttached()
        backend.emit(PowerManager.THERMAL_STATUS_SEVERE)
        advanceUntilIdle()

        assertTrue(backend.started)
        assertEquals(
            listOf("THERMAL_CRITICAL:thermal status severe:false"),
            recordedIssues
        )
        collectionJob.cancel()
    }

    @Test
    fun `cooldown and retrigger emit distinct thermal issues`() = runTest {
        val backend = FakeThermalStatusBackend(
            currentStatus = PowerManager.THERMAL_STATUS_NONE
        )
        val monitor = AndroidThermalRuntimeIssueMonitor(backend)
        val recordedReasons = mutableListOf<String>()
        val collectionJob = launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.runtimeIssues.collect { issue ->
                recordedReasons += issue.reason
            }
        }

        monitor.onPreviewHostAttached()
        backend.emit(PowerManager.THERMAL_STATUS_CRITICAL)
        backend.emit(PowerManager.THERMAL_STATUS_MODERATE)
        backend.emit(PowerManager.THERMAL_STATUS_CRITICAL)
        advanceUntilIdle()

        assertEquals(
            listOf(
                "thermal status critical",
                "thermal status critical"
            ),
            recordedReasons
        )
        collectionJob.cancel()
    }

    @Test
    fun `detach stops observing and unsupported backend stays idle`() {
        val supportedBackend = FakeThermalStatusBackend(
            currentStatus = PowerManager.THERMAL_STATUS_CRITICAL
        )
        val supportedMonitor = AndroidThermalRuntimeIssueMonitor(supportedBackend)

        supportedMonitor.onPreviewHostAttached()
        supportedMonitor.onPreviewHostDetached()

        assertTrue(supportedBackend.started)
        assertTrue(supportedBackend.stopped)

        val unsupportedBackend = FakeThermalStatusBackend(
            isSupported = false,
            currentStatus = PowerManager.THERMAL_STATUS_CRITICAL
        )
        val unsupportedMonitor = AndroidThermalRuntimeIssueMonitor(unsupportedBackend)

        unsupportedMonitor.onPreviewHostAttached()

        assertFalse(unsupportedBackend.started)
    }

    @Test
    fun `thermal mapping marks severe states as non recoverable critical issues`() {
        val issue = thermalRuntimeIssueFor(PowerManager.THERMAL_STATUS_EMERGENCY)

        assertEquals(DeviceRuntimeIssueKind.THERMAL_CRITICAL, issue?.kind)
        assertEquals("thermal status emergency", issue?.reason)
        assertFalse(issue?.isRecoverable ?: true)
        assertEquals(null, thermalRuntimeIssueFor(PowerManager.THERMAL_STATUS_LIGHT))
    }

    private class FakeThermalStatusBackend(
        override val isSupported: Boolean = true,
        private var currentStatus: Int? = null
    ) : ThermalStatusBackend {
        var started = false
            private set
        var stopped = false
            private set
        private var listener: ((Int) -> Unit)? = null

        override fun currentStatus(): Int? = currentStatus

        override fun start(onStatusChanged: (Int) -> Unit) {
            started = true
            stopped = false
            listener = onStatusChanged
        }

        override fun stop() {
            stopped = true
            listener = null
        }

        fun emit(status: Int) {
            currentStatus = status
            listener?.invoke(status)
        }
    }
}
