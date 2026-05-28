package com.opencamera.core.session

import com.opencamera.core.device.PreviewMeteringPoint
import com.opencamera.core.device.PreviewMeteringResult
import com.opencamera.core.device.PreviewMeteringResultStatus
import com.opencamera.core.media.ThumbnailSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [PreviewSessionMutations].
 *
 * Verifies the interface compiles against the expected types and
 * that a stub implementation records calls for later assertion.
 */
class PreviewSessionMutationsTest {

    private class RecordingMutations : PreviewSessionMutations {
        val calls = mutableListOf<String>()

        override fun updatePreviewBlocked(reason: String) {
            calls.add("blocked:$reason")
        }

        override fun updatePreviewStarting(reason: String, isRecovery: Boolean) {
            calls.add("starting:$reason,recovery=$isRecovery")
        }

        override fun updatePreviewActive(firstFrameLatencyMillis: Long) {
            calls.add("active:${firstFrameLatencyMillis}")
        }

        override fun updatePreviewError(reason: String, action: String) {
            calls.add("error:$reason,action=$action")
        }

        override fun updatePreviewStopped(reason: String) {
            calls.add("stopped:$reason")
        }

        override fun updatePreviewThumbnail(source: ThumbnailSource, generation: Int) {
            calls.add("thumbnail:gen=$generation")
        }

        override fun updateCaptureFeedback(shotId: String, outputPath: String) {
            calls.add("feedback:$shotId,$outputPath")
        }

        override fun updatePreviewMeteringRequested(requestId: String, point: PreviewMeteringPoint) {
            calls.add("meteringRequested:$requestId,x=${point.normalizedX},y=${point.normalizedY}")
        }

        override fun updatePreviewMeteringCompleted(result: PreviewMeteringResult) {
            calls.add("meteringCompleted:${result.requestId},status=${result.status}")
        }

        override fun clearPreviewMeteringFeedback(requestId: String) {
            calls.add("clearMetering:$requestId")
        }

        override fun updatePreviewHostAttached(lastAction: String) {
            calls.add("hostAttached:$lastAction")
        }

        override fun updatePreviewHostDetached(reason: String, hasPermission: Boolean) {
            calls.add("hostDetached:$reason,hasPermission=$hasPermission")
        }

        override fun updatePreviewSurfaceLost(reason: String) {
            calls.add("surfaceLost:$reason")
        }

        override fun updatePreviewRuntimeError(detail: String, action: String) {
            calls.add("runtimeError:$detail,action=$action")
        }

        override fun updatePreviewMetrics(metrics: PreviewMetrics) {
            calls.add("metrics:bind=${metrics.bindCount},recovery=${metrics.recoveryCount}")
        }
    }

    @Test
    fun `updatePreviewBlocked records reason`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewBlocked("permission missing")
        assertEquals(listOf("blocked:permission missing"), mutations.calls)
    }

    @Test
    fun `updatePreviewStarting records reason and recovery flag`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewStarting("bind camera", isRecovery = false)
        assertEquals(listOf("starting:bind camera,recovery=false"), mutations.calls)
    }

    @Test
    fun `updatePreviewStarting with recovery flag`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewStarting("recover after error", isRecovery = true)
        assertEquals(listOf("starting:recover after error,recovery=true"), mutations.calls)
    }

    @Test
    fun `updatePreviewActive records latency`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewActive(150L)
        assertEquals(listOf("active:150"), mutations.calls)
    }

    @Test
    fun `updatePreviewError records reason and action`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewError("camera disconnected", "attempt recovery")
        assertEquals(listOf("error:camera disconnected,action=attempt recovery"), mutations.calls)
    }

    @Test
    fun `updatePreviewStopped records reason`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewStopped("user switched mode")
        assertEquals(listOf("stopped:user switched mode"), mutations.calls)
    }

    @Test
    fun `updatePreviewThumbnail records generation`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewThumbnail(ThumbnailSource.PreviewSnapshot("/tmp/preview.jpg"), 3)
        assertEquals(listOf("thumbnail:gen=3"), mutations.calls)
    }

    @Test
    fun `updatePreviewThumbnail accepts all ThumbnailSource variants`() {
        val mutations = RecordingMutations()

        mutations.updatePreviewThumbnail(ThumbnailSource.None, 0)
        mutations.updatePreviewThumbnail(ThumbnailSource.Pending, 1)
        mutations.updatePreviewThumbnail(ThumbnailSource.PreviewSnapshot("/a.jpg"), 2)
        mutations.updatePreviewThumbnail(ThumbnailSource.SavedMedia("/b.jpg"), 3)

        assertEquals(4, mutations.calls.size)
    }

    @Test
    fun `updateCaptureFeedback records shot and path`() {
        val mutations = RecordingMutations()
        mutations.updateCaptureFeedback("shot-1", "/tmp/capture.jpg")
        assertEquals(listOf("feedback:shot-1,/tmp/capture.jpg"), mutations.calls)
    }

    @Test
    fun `updatePreviewMeteringRequested records request and point`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewMeteringRequested(
            "meter-1",
            PreviewMeteringPoint(0.5f, 0.4f)
        )
        assertEquals(listOf("meteringRequested:meter-1,x=0.5,y=0.4"), mutations.calls)
    }

    @Test
    fun `updatePreviewMeteringCompleted records result`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewMeteringCompleted(
            PreviewMeteringResult(
                requestId = "meter-1",
                point = PreviewMeteringPoint(0.5f, 0.4f),
                status = PreviewMeteringResultStatus.SUCCEEDED
            )
        )
        assertEquals(
            listOf("meteringCompleted:meter-1,status=${PreviewMeteringResultStatus.SUCCEEDED}"),
            mutations.calls
        )
    }

    @Test
    fun `updatePreviewHostAttached records lastAction`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewHostAttached("Preview host attached")
        assertEquals(listOf("hostAttached:Preview host attached"), mutations.calls)
    }

    @Test
    fun `updatePreviewHostDetached records reason and permission`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewHostDetached("rotation", hasPermission = true)
        assertEquals(listOf("hostDetached:rotation,hasPermission=true"), mutations.calls)
    }

    @Test
    fun `updatePreviewSurfaceLost records reason`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewSurfaceLost("surface destroyed")
        assertEquals(listOf("surfaceLost:surface destroyed"), mutations.calls)
    }

    @Test
    fun `updatePreviewRuntimeError records detail and action`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewRuntimeError("camera disconnected", "Preview runtime issue, attempting recovery")
        assertEquals(listOf("runtimeError:camera disconnected,action=Preview runtime issue, attempting recovery"), mutations.calls)
    }

    @Test
    fun `updatePreviewMetrics records metrics`() {
        val mutations = RecordingMutations()
        mutations.updatePreviewMetrics(PreviewMetrics(bindCount = 3, recoveryCount = 1))
        assertEquals(listOf("metrics:bind=3,recovery=1"), mutations.calls)
    }

    @Test
    fun `all fifteen methods are callable through interface`() {
        val mutations: PreviewSessionMutations = RecordingMutations()

        mutations.updatePreviewBlocked("r")
        mutations.updatePreviewStarting("r", false)
        mutations.updatePreviewActive(0L)
        mutations.updatePreviewError("r", "a")
        mutations.updatePreviewStopped("r")
        mutations.updatePreviewThumbnail(ThumbnailSource.None, 0)
        mutations.updateCaptureFeedback("s", "o")
        mutations.updatePreviewMeteringRequested("id", PreviewMeteringPoint(0f, 0f))
        mutations.updatePreviewMeteringCompleted(
            PreviewMeteringResult("id", PreviewMeteringPoint(0f, 0f), PreviewMeteringResultStatus.FAILED)
        )
        mutations.clearPreviewMeteringFeedback("id")
        mutations.updatePreviewHostAttached("attached")
        mutations.updatePreviewHostDetached("detached", true)
        mutations.updatePreviewSurfaceLost("lost")
        mutations.updatePreviewRuntimeError("error", "action")
        mutations.updatePreviewMetrics(PreviewMetrics())

        assertEquals(15, (mutations as RecordingMutations).calls.size)
    }
}
