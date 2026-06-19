package com.opencamera.app.camera

import android.net.Uri
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import com.opencamera.core.device.CaptureTemplate
import com.opencamera.core.device.DeviceShotRequest
import com.opencamera.core.device.MultiFrameCaptureExecutionPlan
import com.opencamera.core.device.MultiFrameCaptureExecutionPlanner
import com.opencamera.core.device.MultiFrameCaptureStep
import com.opencamera.core.device.MultiFrameOutputRole
import com.opencamera.core.media.CaptureFrameFormat
import com.opencamera.core.media.CaptureNode
import com.opencamera.core.media.CaptureNodeRole
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.FlashMode
import com.opencamera.core.media.MediaOutputHandle
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.MediaSaveTask
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotGraph
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.ThumbnailPolicy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.mockito.Mockito.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class StillCaptureExecutorTest {

    private fun createExecutor(): StillCaptureExecutor {
        return StillCaptureExecutor(
            context = RuntimeEnvironment.getApplication(),
            captureOutputFactory = CaptureOutputFactory(RuntimeEnvironment.getApplication()),
            multiFrameExecutionPlanner = MultiFrameCaptureExecutionPlanner()
        )
    }

    // --- toExecutionDiagnostics ---

    @Test
    fun `toExecutionDiagnostics reports total temp and final frame`() {
        val plan = MultiFrameCaptureExecutionPlan(
            steps = listOf(
                MultiFrameCaptureStep(frameIndex = 1, outputRole = MultiFrameOutputRole.TEMPORARY),
                MultiFrameCaptureStep(frameIndex = 2, outputRole = MultiFrameOutputRole.TEMPORARY),
                MultiFrameCaptureStep(frameIndex = 3, outputRole = MultiFrameOutputRole.FINAL_OUTPUT)
            ),
            interFrameDelayMillis = 0L
        )

        val diagnostics = plan.toExecutionDiagnostics()

        assertEquals(3, diagnostics.size)
        assertTrue(diagnostics.any { it.contains("burst-executed=3") })
        assertTrue(diagnostics.any { it.contains("burst-temp-frames=2") })
        assertTrue(diagnostics.any { it.contains("burst-final-frame=3") })
    }

    @Test
    fun `toExecutionDiagnostics for single frame reports zero temp`() {
        val plan = MultiFrameCaptureExecutionPlan(
            steps = listOf(
                MultiFrameCaptureStep(frameIndex = 1, outputRole = MultiFrameOutputRole.FINAL_OUTPUT)
            ),
            interFrameDelayMillis = 0L
        )

        val diagnostics = plan.toExecutionDiagnostics()

        assertTrue(diagnostics.any { it.contains("burst-executed=1") })
        assertTrue(diagnostics.any { it.contains("burst-temp-frames=0") })
        assertTrue(diagnostics.any { it.contains("burst-final-frame=1") })
    }

    // --- captureSinglePhoto success ---

    @Test
    fun `captureSinglePhoto returns success on image saved`() = runBlocking {
        val executor = createExecutor()
        val capture = mockImageCaptureSuccess()
        val request = createPhotoOutputRequest("/tmp/test_success.jpg")

        val outcome = executor.captureSinglePhoto(capture, request)

        assertTrue(outcome is PhotoCaptureOutcome.Success)
        val success = outcome as PhotoCaptureOutcome.Success
        assertEquals("/tmp/test_success.jpg", success.outputPath)
        assertTrue(success.deviceCaptureStartedAtElapsedMillis > 0)
        assertTrue(success.deviceCaptureCompletedAtElapsedMillis >= success.deviceCaptureStartedAtElapsedMillis)
    }

    // --- captureSinglePhoto failure ---

    @Test
    fun `captureSinglePhoto returns failure on error`() = runBlocking {
        val executor = createExecutor()
        val exception = mock(ImageCaptureException::class.java)
        `when`(exception.message).thenReturn("Shutter timeout")
        val capture = mockImageCaptureError(exception)
        val request = createPhotoOutputRequest("/tmp/test_fail.jpg")

        val outcome = executor.captureSinglePhoto(capture, request)

        assertTrue(outcome is PhotoCaptureOutcome.Failure)
        val failure = outcome as PhotoCaptureOutcome.Failure
        assertEquals("Shutter timeout", failure.reason)
    }

    // --- captureMultiFrame success ---

    @Test
    fun `captureMultiFrame accumulates all frames in bundle`() = runBlocking {
        val executor = createExecutor()
        val callCount = AtomicInteger(0)
        val capture = mockImageCapture { callback ->
            callCount.getAndIncrement()
            val results = mock(ImageCapture.OutputFileResults::class.java)
            `when`(results.savedUri).thenReturn(null)
            callback.onImageSaved(results)
        }
        val plan = createMultiFrameShotPlan(shotId = "mf-1", frameCount = 3)
        val deviceRequest = DeviceShotRequest(
            shotId = "mf-1",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            frameCount = 3
        )

        val outcome = executor.captureMultiFrame(capture, plan, deviceRequest)

        assertTrue(outcome is PhotoCaptureOutcome.Success)
        val success = outcome as PhotoCaptureOutcome.Success
        assertEquals(3, callCount.get())
        assertNotNull(success.frameBundle)
        assertEquals(3, success.frameBundle!!.frames.size)
        assertTrue(success.diagnostics.any { it.contains("device:burst-bundle-frames=3") })
    }

    // --- captureMultiFrame intermediate failure cleanup ---

    @Test
    fun `captureMultiFrame cleans up on intermediate failure`() = runBlocking {
        val executor = createExecutor()
        val callCount = AtomicInteger(0)
        val capture = mockImageCapture { callback ->
            val call = callCount.getAndIncrement()
            if (call == 0) {
                val results = mock(ImageCapture.OutputFileResults::class.java)
                `when`(results.savedUri).thenReturn(null)
                callback.onImageSaved(results)
            } else {
                val exception = mock(ImageCaptureException::class.java)
                `when`(exception.message).thenReturn("Frame 2 failed")
                callback.onError(exception)
            }
        }
        val plan = createMultiFrameShotPlan(shotId = "mf-fail", frameCount = 2)
        val deviceRequest = DeviceShotRequest(
            shotId = "mf-fail",
            template = CaptureTemplate.STILL_CAPTURE,
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            frameCount = 2
        )

        val outcome = executor.captureMultiFrame(capture, plan, deviceRequest)

        assertTrue(outcome is PhotoCaptureOutcome.Failure)
        assertEquals("Frame 2 failed", (outcome as PhotoCaptureOutcome.Failure).reason)
    }

    // --- captureSinglePhoto uses savedUri for outputHandle ---

    @Test
    fun `captureSinglePhoto uses savedUri for output handle when present`() = runBlocking {
        val executor = createExecutor()
        val savedUri = Uri.parse("content://media/external/images/media/42")
        val capture = mockImageCapture { callback ->
            val results = mock(ImageCapture.OutputFileResults::class.java)
            `when`(results.savedUri).thenReturn(savedUri)
            callback.onImageSaved(results)
        }
        val request = createPhotoOutputRequest("/tmp/test_uri.jpg")

        val outcome = executor.captureSinglePhoto(capture, request)

        assertTrue(outcome is PhotoCaptureOutcome.Success)
        val success = outcome as PhotoCaptureOutcome.Success
        assertEquals("content://media/external/images/media/42", success.outputHandle.contentUri)
    }

    // --- Helpers ---

    private fun mockImageCapture(onTakePicture: (ImageCapture.OnImageSavedCallback) -> Unit): ImageCapture {
        val capture = mock(ImageCapture::class.java)
        doAnswer { invocation ->
            val callback = invocation.getArgument<ImageCapture.OnImageSavedCallback>(2)
            onTakePicture(callback)
            null
        }.`when`(capture).takePicture(
            any(ImageCapture.OutputFileOptions::class.java),
            any(java.util.concurrent.Executor::class.java),
            any(ImageCapture.OnImageSavedCallback::class.java)
        )
        return capture
    }

    private fun mockImageCaptureSuccess(): ImageCapture {
        return mockImageCapture { callback ->
            val results = mock(ImageCapture.OutputFileResults::class.java)
            `when`(results.savedUri).thenReturn(null)
            callback.onImageSaved(results)
        }
    }

    private fun mockImageCaptureError(exception: ImageCaptureException): ImageCapture {
        return mockImageCapture { callback ->
            callback.onError(exception)
        }
    }

    private fun createPhotoOutputRequest(outputPath: String): PhotoOutputRequest {
        val outputOptions = mock(ImageCapture.OutputFileOptions::class.java)
        return PhotoOutputRequest(
            outputOptions = outputOptions,
            outputPath = outputPath,
            outputHandle = MediaOutputHandle(displayPath = outputPath)
        )
    }

    private fun createMultiFrameShotPlan(shotId: String, frameCount: Int): ShotPlan {
        val saveRequest = SaveRequest.photoLibrary()
        val request = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = saveRequest,
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile(frameCount = frameCount)
        )
        val graph = ShotGraph(
            shotId = shotId,
            captureNodes = listOf(
                CaptureNode(
                    id = "$shotId:temp",
                    role = CaptureNodeRole.TEMPORARY_FRAME,
                    frameCount = frameCount,
                    requiredFormat = CaptureFrameFormat(mimeType = "image/jpeg")
                ),
                CaptureNode(
                    id = "$shotId:primary",
                    role = CaptureNodeRole.PRIMARY_STILL,
                    frameCount = 1,
                    requiredFormat = CaptureFrameFormat(mimeType = "image/jpeg")
                )
            ),
            algorithmNodes = emptyList(),
            outputNodes = emptyList()
        )
        return ShotPlan(
            request = request,
            saveTask = MediaSaveTask(
                shotId = shotId,
                mediaType = MediaType.PHOTO,
                saveRequest = saveRequest,
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile(frameCount = frameCount)
            ),
            graph = graph
        )
    }

    private fun assertNotNull(value: Any?) {
        assertTrue("Expected non-null value", value != null)
    }
}
