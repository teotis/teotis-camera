package com.opencamera.app.camera

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PreviewStartupRuntimeIssueMonitorTest {
    @Test
    fun `bind without first frame emits recoverable preview stall issue after timeout`() = runTest {
        val monitorScope = TestScope(StandardTestDispatcher(testScheduler))
        val monitor = PreviewStartupRuntimeIssueMonitor(monitorScope)
        val recordedIssues = mutableListOf<String>()
        val collectionJob = monitorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.runtimeIssues.collect { issue ->
                recordedIssues += "${issue.kind}:${issue.reason}:${issue.isRecoverable}"
            }
        }

        monitor.onPreviewHostAttached()
        monitor.onPreviewBindingStarted(
            reason = "session boot",
            isRecovery = false
        )
        monitorScope.advanceTimeBy(299)
        monitorScope.runCurrent()
        assertTrue(recordedIssues.isEmpty())

        monitorScope.advanceTimeBy(1)
        monitorScope.advanceUntilIdle()

        assertEquals(
            listOf(
                "PREVIEW_STALL:first frame timed out after 300 ms (Cold start): session boot:true"
            ),
            recordedIssues
        )
        collectionJob.cancel()
    }

    @Test
    fun `first frame before timeout cancels pending stall issue`() = runTest {
        val monitorScope = TestScope(StandardTestDispatcher(testScheduler))
        val monitor = PreviewStartupRuntimeIssueMonitor(monitorScope)
        val recordedReasons = mutableListOf<String>()
        val collectionJob = monitorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.runtimeIssues.collect { issue ->
                recordedReasons += issue.reason
            }
        }

        monitor.onPreviewHostAttached()
        monitor.onPreviewBindingStarted(
            reason = "recover after provider failure: provider restarted",
            isRecovery = true
        )
        monitorScope.advanceTimeBy(200)
        monitorScope.runCurrent()
        monitor.onPreviewFirstFrameAvailable(180)
        monitorScope.advanceTimeBy(500)
        monitorScope.advanceUntilIdle()

        assertTrue(recordedReasons.isEmpty())
        collectionJob.cancel()
    }

    @Test
    fun `preview host detach clears pending timeout`() = runTest {
        val monitorScope = TestScope(StandardTestDispatcher(testScheduler))
        val monitor = PreviewStartupRuntimeIssueMonitor(monitorScope)
        val recordedReasons = mutableListOf<String>()
        val collectionJob = monitorScope.launch(start = CoroutineStart.UNDISPATCHED) {
            monitor.runtimeIssues.collect { issue ->
                recordedReasons += issue.reason
            }
        }

        monitor.onPreviewHostAttached()
        monitor.onPreviewBindingStarted(
            reason = "session settings updated",
            isRecovery = false
        )
        monitorScope.advanceTimeBy(100)
        monitorScope.runCurrent()
        monitor.onPreviewHostDetached()
        monitorScope.advanceTimeBy(500)
        monitorScope.advanceUntilIdle()

        assertTrue(recordedReasons.isEmpty())
        collectionJob.cancel()
    }
}
