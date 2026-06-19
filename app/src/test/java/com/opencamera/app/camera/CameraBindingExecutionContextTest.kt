package com.opencamera.app.camera

import java.util.concurrent.Executors
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraBindingExecutionContextTest {

    @Test
    fun `binding work switches from caller dispatcher to configured dispatcher`() = runBlocking {
        val callerDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "camera-binding-caller")
        }.asCoroutineDispatcher()
        val bindingDispatcher = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "camera-binding-main")
        }.asCoroutineDispatcher()

        try {
            val executionContext = CameraBindingExecutionContext(bindingDispatcher)

            val executionThread = withContext(callerDispatcher) {
                executionContext.run {
                    Thread.currentThread().name
                }
            }

            assertTrue(executionThread.startsWith("camera-binding-main"))
        } finally {
            callerDispatcher.close()
            bindingDispatcher.close()
        }
    }
}
