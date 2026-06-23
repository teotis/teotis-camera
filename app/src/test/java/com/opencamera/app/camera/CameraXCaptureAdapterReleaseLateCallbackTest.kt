package com.opencamera.app.camera

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CameraXCaptureAdapterReleaseLateCallbackTest {

    @Test
    fun `launch into cancelled scope does not execute body`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        job.cancel()

        val executed = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        scope.launch {
            executed.set(true)
            latch.countDown()
        }

        assertFalse(
            latch.await(2, TimeUnit.SECONDS),
            "Launch into cancelled scope should not complete"
        )
        assertFalse(executed.get())
    }

    @Test
    fun `scope cancel is idempotent - double cancel does not throw`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)

        scope.launch { /* work */ }
        Thread.sleep(5)

        job.cancel()
        job.cancel()
        assertTrue(job.isCancelled)
    }

    @Test
    fun `isActive guard prevents late callback execution after scope cancel`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val sideEffect = AtomicBoolean(false)
        val bodyStarted = CountDownLatch(1)
        val mayProceed = CountDownLatch(1)

        scope.launch {
            bodyStarted.countDown()
            mayProceed.await()
            // Guard pattern used in CameraXCaptureAdapter
            if (!isActive) return@launch
            sideEffect.set(true)
        }

        assertTrue(bodyStarted.await(2, TimeUnit.SECONDS))
        job.cancel()
        mayProceed.countDown()

        Thread.sleep(50)

        assertFalse(
            sideEffect.get(),
            "isActive guard should prevent side effects after scope cancellation"
        )
    }

    @Test
    fun `isActive guard allows execution before scope cancel`() {
        val job = SupervisorJob()
        val scope = CoroutineScope(job + Dispatchers.Default)
        val sideEffect = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        scope.launch {
            if (!isActive) return@launch
            sideEffect.set(true)
            latch.countDown()
        }

        assertTrue(latch.await(2, TimeUnit.SECONDS))
        assertTrue(sideEffect.get(), "Side effect should run when scope is active")
        job.cancel()
    }

    @Test
    fun `supervisorJob cancellation does not propagate to sibling scopes`() {
        val job1 = SupervisorJob()
        val job2 = SupervisorJob()
        val scope2 = CoroutineScope(job2 + Dispatchers.Default)

        val latch = CountDownLatch(1)
        scope2.launch {
            latch.countDown()
        }

        job1.cancel()

        assertTrue(
            latch.await(2, TimeUnit.SECONDS),
            "Unrelated scope's coroutine should still complete"
        )
        job2.cancel()
    }
}
