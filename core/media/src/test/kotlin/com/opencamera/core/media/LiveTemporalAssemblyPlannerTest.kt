package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LiveTemporalAssemblyPlannerTest {

    private val defaultSpec = LivePhotoCaptureSpec(
        motionDurationMillis = 1_500,
        motionMimeType = "video/mp4",
        sidecarMimeType = "application/vnd.opencamera.live+json"
    )

    @Test
    fun `preview ring buffer with sufficient depth produces complete plan with pre-shutter frames`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.PREVIEW_RING_BUFFER,
                ringBufferDepthMillis = 2_000
            )
        )

        assertEquals(LiveBundleStatus.COMPLETE, plan.expectedBundleStatus)
        assertEquals(LiveMotionSource.PREVIEW_RING_BUFFER, plan.temporalWindow.source)
        assertTrue(plan.temporalWindow.preShutterMillis > 0)
        assertTrue(plan.temporalWindow.frameCount > 0)
        assertEquals(SidecarPlacement.APP_PRIVATE, plan.sidecarPlacement)
        assertTrue(plan.notes.any { it.contains("preview-ring-buffer") })
    }

    @Test
    fun `post shutter frames only produces degraded plan with zero pre-shutter`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.POST_SHUTTER_FRAMES,
                postShutterBudgetMillis = 800
            )
        )

        assertEquals(LiveBundleStatus.DEGRADED_MOTION, plan.expectedBundleStatus)
        assertEquals(0, plan.temporalWindow.preShutterMillis)
        assertEquals(800, plan.temporalWindow.postShutterMillis)
        assertEquals(LiveMotionSource.POST_SHUTTER_FRAMES, plan.temporalWindow.source)
        assertTrue(plan.notes.any { it.contains("post-shutter-frames") })
    }

    @Test
    fun `metadata only source produces still-only fallback plan`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.METADATA_ONLY
            )
        )

        assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, plan.expectedBundleStatus)
        assertEquals(0, plan.temporalWindow.preShutterMillis)
        assertEquals(0, plan.temporalWindow.postShutterMillis)
        assertEquals(0, plan.temporalWindow.frameCount)
        assertEquals(LiveMotionSource.METADATA_ONLY, plan.temporalWindow.source)
        assertTrue(plan.notes.any { it.contains("metadata-only") })
    }

    @Test
    fun `ring buffer depth smaller than requested duration scales pre-shutter proportionally`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.PREVIEW_RING_BUFFER,
                ringBufferDepthMillis = 500
            )
        )

        assertEquals(LiveBundleStatus.COMPLETE, plan.expectedBundleStatus)
        assertEquals(500, plan.temporalWindow.preShutterMillis)
        assertEquals(1_000, plan.temporalWindow.postShutterMillis)
    }

    @Test
    fun `zero budget with ring buffer source produces metadata-only fallback`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.PREVIEW_RING_BUFFER,
                ringBufferDepthMillis = 0
            )
        )

        assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, plan.expectedBundleStatus)
    }

    @Test
    fun `video recorder segment produces degraded plan with diagnostic note`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.VIDEO_RECORDER_SEGMENT,
                postShutterBudgetMillis = 1_000
            )
        )

        assertEquals(LiveBundleStatus.DEGRADED_MOTION, plan.expectedBundleStatus)
        assertEquals(LiveMotionSource.VIDEO_RECORDER_SEGMENT, plan.temporalWindow.source)
        assertTrue(plan.notes.any { it.contains("video-recorder-segment") })
    }

    @Test
    fun `zero post shutter budget with post shutter source produces metadata-only fallback`() {
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = defaultSpec,
                availableSource = LiveMotionSource.POST_SHUTTER_FRAMES,
                postShutterBudgetMillis = 0
            )
        )

        assertEquals(LiveBundleStatus.STILL_ONLY_FALLBACK, plan.expectedBundleStatus)
    }

    @Test
    fun `requested duration is preserved in temporal window`() {
        val spec = LivePhotoCaptureSpec(motionDurationMillis = 2_000)
        val plan = planLiveTemporalAssembly(
            LiveTemporalPlannerInput(
                captureSpec = spec,
                availableSource = LiveMotionSource.PREVIEW_RING_BUFFER,
                ringBufferDepthMillis = 3_000
            )
        )

        assertEquals(2_000, plan.temporalWindow.requestedDurationMillis)
        assertEquals(1_600, plan.temporalWindow.preShutterMillis) // 2000 * 0.8
        assertEquals(400, plan.temporalWindow.postShutterMillis)
    }
}
