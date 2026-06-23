package com.opencamera.app.camera

import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.PreviewConfig
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertTrue

class CameraXCaptureAdapterLifecycleTest {

    @Test
    fun `graph copy produces a new instance without mutating the original`() {
        val original = DeviceGraphSpec.stillCapture(
            enablePreviewSnapshots = true,
            zoomRatio = 1f
        )
        val snapshot = original

        val updated = original.copy(
            preview = original.preview.copy(
                zoomRatio = 2f
            )
        )

        assertNotSame(original, updated)
        assertEquals(1f, snapshot.preview.zoomRatio)
        assertEquals(2f, updated.preview.zoomRatio)
    }

    @Test
    fun `scope cancellation prevents launched coroutines from completing`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val completed = AtomicBoolean(false)

        scope.launch {
            kotlinx.coroutines.delay(5_000)
            completed.set(true)
        }

        // Give the coroutine a moment to be scheduled, then cancel
        Thread.sleep(10)
        job.cancel()

        // Wait briefly; if cancellation worked, completed stays false
        Thread.sleep(50)
        assertFalse(completed.get(), "Coroutine should not complete after scope job cancellation")
    }

    @Test
    fun `scope recreation after cancel allows new launches to succeed`() {
        val latch = CountDownLatch(1)
        var job = SupervisorJob()
        var scope = CoroutineScope(job + Dispatchers.Default)

        scope.launch {
            // First scope's work should be cancelled
            kotlinx.coroutines.delay(10_000)
        }

        Thread.sleep(10)
        job.cancel()

        // Recreate scope
        job = SupervisorJob()
        scope = CoroutineScope(job + Dispatchers.Default)

        scope.launch {
            latch.countDown()
        }

        assertTrue(
            latch.await(2, TimeUnit.SECONDS),
            "New scope's coroutine should complete after recreation"
        )
        job.cancel()
    }

    @Test
    fun `boundGraph snapshot is independent of subsequent mutations`() {
        val graph1 = DeviceGraphSpec.stillCapture(
            enablePreviewSnapshots = true,
            zoomRatio = 1f
        )
        val graph2 = graph1.copy(
            preview = graph1.preview.copy(zoomRatio = 5f)
        )

        // Simulate the snapshot pattern: boundGraph returns the captured reference
        val boundGraph: DeviceGraphSpec = graph1
        // Subsequent copy does not affect the captured snapshot
        assertTrue(graph2.preview.zoomRatio > boundGraph.preview.zoomRatio)

        assertEquals(1f, boundGraph?.preview?.zoomRatio)
    }

    @Test
    fun `release scope cancellation is idempotent`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)

        scope.launch { /* work */ }
        Thread.sleep(5)

        job.cancel()
        // Double-cancel should not throw
        job.cancel()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `callback scope restarts after release for next preview bind`() {
        val scopes = CameraXCaptureWorkScopes(
            callbackDispatcher = Dispatchers.Default,
            postProcessScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
            postProcessDispatcher = Dispatchers.Default
        )
        val firstScope = scopes.activeCallbackScope()

        scopes.cancelCallbackScope()
        val restartedScope = scopes.activeCallbackScope()

        assertTrue(firstScope.coroutineContext.job.isCancelled)
        assertFalse(restartedScope.coroutineContext.job.isCancelled)
        assertNotSame(firstScope, restartedScope)
        scopes.cancelCallbackScope()
    }

    @Test
    fun `postprocess scope survives callback release cancellation`() {
        val postProcessJob = SupervisorJob()
        val scopes = CameraXCaptureWorkScopes(
            callbackDispatcher = Dispatchers.Default,
            postProcessScope = CoroutineScope(postProcessJob + Dispatchers.Default),
            postProcessDispatcher = Dispatchers.Default
        )
        val latch = CountDownLatch(1)

        scopes.cancelCallbackScope()
        scopes.launchPostProcess {
            latch.countDown()
        }

        assertTrue(
            latch.await(2, TimeUnit.SECONDS),
            "Final-output postprocess must keep running after preview callback release"
        )
        scopes.cancelCallbackScope()
        postProcessJob.cancel()
    }
}
