package com.opencamera.app.camera

import com.opencamera.core.media.CaptureFrameFormat
import com.opencamera.core.media.CaptureNode
import com.opencamera.core.media.CaptureNodeRole
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.ShotGraph
import com.opencamera.core.media.ShotKind
import com.opencamera.core.media.ShotPlan
import com.opencamera.core.media.ShotRequest
import com.opencamera.core.media.MediaSaveTask
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailPolicy
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.CaptureProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoRecordingControllerTest {

    private lateinit var outcomes: MutableList<RecordingOutcome>
    private lateinit var torchChanges: MutableList<Boolean>
    private lateinit var controller: VideoRecordingController

    @Before
    fun setUp() {
        outcomes = mutableListOf()
        torchChanges = mutableListOf()
        controller = VideoRecordingController(
            isAudioPermissionGranted = { true },
            onTorchChange = { enabled -> torchChanges.add(enabled) },
            qualityTrackerStart = { },
            qualityTrackerStop = { }
        )
    }

    // --- start → finalize success ---

    @Test
    fun `start then finalize success emits FinalizeSuccess`() {
        val plan = createVideoPlan("v1")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)
        outcomes.addAll(controller.outcomes)
        controller.outcomes.clear()

        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = false, errorMessage = null, outputUri = "content://video/1"
        ))

        outcomes.addAll(controller.outcomes)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is RecordingOutcome.FinalizeSuccess)
        val success = outcomes[0] as RecordingOutcome.FinalizeSuccess
        assertEquals("v1", success.plan.request.shotId)
        assertEquals("content://video/1", success.outputUri)
        assertFalse(controller.isActive)
    }

    // --- start → finalize error ---

    @Test
    fun `start then finalize error emits FinalizeError`() {
        val plan = createVideoPlan("v2")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)
        outcomes.addAll(controller.outcomes)
        controller.outcomes.clear()

        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = true, errorMessage = "File size limit", outputUri = null
        ))

        outcomes.addAll(controller.outcomes)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is RecordingOutcome.FinalizeError)
        val error = outcomes[0] as RecordingOutcome.FinalizeError
        assertEquals("v2", error.shotId)
        assertEquals("File size limit", error.errorMessage)
        assertFalse(controller.isActive)
    }

    // --- start → stop → finalize ---

    @Test
    fun `start then stop then finalize succeeds`() {
        val plan = createVideoPlan("v3")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)
        controller.stopRecording()

        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = false, errorMessage = null, outputUri = "content://video/3"
        ))
        outcomes.addAll(controller.outcomes)

        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is RecordingOutcome.FinalizeSuccess)
    }

    // --- start → release → late finalize ---

    @Test
    fun `start then release then late finalize emits Released only`() {
        val plan = createVideoPlan("v4")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)

        controller.release()

        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = false, errorMessage = null, outputUri = "content://video/4"
        ))

        outcomes.addAll(controller.outcomes)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is RecordingOutcome.Released)
        assertEquals("v4", (outcomes[0] as RecordingOutcome.Released).shotId)
        assertFalse(controller.isActive)
    }

    @Test
    fun `start then release then late finalize with error emits Released only`() {
        val plan = createVideoPlan("v4e")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)

        controller.release()

        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = true, errorMessage = "Encoding failed", outputUri = null
        ))

        outcomes.addAll(controller.outcomes)
        assertEquals(1, outcomes.size)
        assertTrue(outcomes[0] is RecordingOutcome.Released)
    }

    // --- duplicate stop ---

    @Test(expected = IllegalStateException::class)
    fun `stop throws when no active recording`() {
        controller.stopRecording()
    }

    // --- start while already active ---

    @Test(expected = IllegalArgumentException::class)
    fun `start throws when recording already active`() {
        controller.startRecording(createVideoPlan("v5"))
        controller.startRecording(createVideoPlan("v5b"))
    }

    // --- bind/rebind while recording ---

    @Test
    fun `binding request while recording does not affect active recording`() {
        val plan = createVideoPlan("v6")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)

        assertTrue(controller.isActive)
        assertEquals("v6", controller.activePlan()?.request?.shotId)
    }

    // --- audio permission ---

    @Test
    fun `audio permission denied is reported`() {
        val denyController = VideoRecordingController(
            isAudioPermissionGranted = { false },
            onTorchChange = { }
        )
        assertFalse(denyController.hasAudioPermission())
    }

    @Test
    fun `audio permission granted is reported`() {
        assertTrue(controller.hasAudioPermission())
    }

    @Test
    fun `started event forwards configured frame rate hint to quality tracker`() {
        val frameRateHints = mutableListOf<Int?>()
        val trackingController = VideoRecordingController(
            isAudioPermissionGranted = { true },
            onTorchChange = { },
            qualityTrackerStart = { frameRateHints.add(it) }
        )

        trackingController.startRecording(createVideoPlan("fps"), expectedFrameRate = 60)
        trackingController.handleEvent(RecordingControllerEvent.Started)

        assertEquals(listOf(60), frameRateHints)
    }

    @Test
    fun `started event keeps null frame rate hint when unavailable`() {
        val frameRateHints = mutableListOf<Int?>()
        val trackingController = VideoRecordingController(
            isAudioPermissionGranted = { true },
            onTorchChange = { },
            qualityTrackerStart = { frameRateHints.add(it) }
        )

        trackingController.startRecording(createVideoPlan("fps-null"))
        trackingController.handleEvent(RecordingControllerEvent.Started)

        assertEquals(1, frameRateHints.size)
        assertNull(frameRateHints.single())
    }

    // --- torch transitions ---

    @Test
    fun `apply torch enabled calls onTorchChange true`() = runBlockingTest {
        controller.applyTorch(true)
        assertEquals(listOf(true), torchChanges)
        assertTrue(controller.isTorchEnabled)
    }

    @Test
    fun `apply torch disabled calls onTorchChange false`() = runBlockingTest {
        controller.applyTorch(true)
        controller.applyTorch(false)
        assertEquals(listOf(true, false), torchChanges)
        assertFalse(controller.isTorchEnabled)
    }

    @Test
    fun `apply torch same state does not call onTorchChange`() = runBlockingTest {
        controller.applyTorch(false)
        assertTrue(torchChanges.isEmpty())
    }

    @Test
    fun `release resets torch to disabled`() = runBlockingTest {
        controller.applyTorch(true)
        torchChanges.clear()

        controller.startRecording(createVideoPlan("v7"))
        controller.release()

        assertFalse(controller.isTorchEnabled)
    }

    @Test
    fun `finalize error resets torch state`() = runBlockingTest {
        controller.applyTorch(true)

        val plan = createVideoPlan("v8")
        controller.startRecording(plan)
        controller.handleEvent(RecordingControllerEvent.Started)
        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = true, errorMessage = "error", outputUri = null
        ))

        assertFalse(controller.isTorchEnabled)
    }

    // --- lifecycle interrupted shot tracking ---

    @Test
    fun `release marks shotId as lifecycle interrupted`() {
        val plan = createVideoPlan("v9")
        controller.startRecording(plan)
        controller.release()

        controller.outcomes.clear()

        // Late finalize for the interrupted shot should produce no outcome
        controller.handleEvent(RecordingControllerEvent.Finalized(
            hasError = false, errorMessage = null, outputUri = "content://video/9"
        ))

        assertTrue(controller.outcomes.isEmpty())
    }

    // --- Helpers ---

    private fun createVideoPlan(shotId: String): ShotPlan {
        val saveRequest = SaveRequest.videoLibrary()
        val request = ShotRequest(
            shotId = shotId,
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = saveRequest,
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val graph = ShotGraph(
            shotId = shotId,
            captureNodes = listOf(
                CaptureNode(
                    id = "$shotId:video",
                    role = CaptureNodeRole.PRIMARY_VIDEO,
                    frameCount = 1,
                    requiredFormat = CaptureFrameFormat(mimeType = "video/mp4")
                )
            ),
            algorithmNodes = emptyList(),
            outputNodes = emptyList()
        )
        return ShotPlan(
            request = request,
            saveTask = MediaSaveTask(
                shotId = shotId,
                mediaType = MediaType.VIDEO,
                saveRequest = saveRequest,
                thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
                postProcessSpec = PostProcessSpec(),
                captureProfile = CaptureProfile()
            ),
            graph = graph
        )
    }
}

/**
 * Test helper for running suspend lambdas in unit tests.
 */
private fun runBlockingTest(block: suspend () -> Unit) {
    kotlinx.coroutines.runBlocking { block() }
}
