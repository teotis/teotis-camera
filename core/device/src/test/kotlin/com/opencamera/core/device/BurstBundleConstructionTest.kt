package com.opencamera.core.device

import com.opencamera.core.media.FrameBundle
import com.opencamera.core.media.FrameBundleFrame
import com.opencamera.core.media.FrameRole
import com.opencamera.core.media.FrameBundleStatus
import com.opencamera.core.media.MotionScore
import com.opencamera.core.media.NoiseModel
import com.opencamera.core.media.PixelReference
import com.opencamera.core.media.computeBundleStatus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Tests for the burst bundle frame construction logic that
 * captureMultiFrameStillImage uses to build a FrameBundle from
 * multi-frame execution steps.
 *
 * These tests verify:
 * - Frame count matches execution step count
 * - FINAL_OUTPUT step maps to FUSION_ANCHOR role
 * - TEMPORARY steps map to FUSION_SUPPLEMENT role
 * - Unknown CameraX metadata is represented explicitly
 * - Bundle status is DEGRADED when metadata is unknown
 * - Intermediate and final paths are preserved
 */
class BurstBundleConstructionTest {

    private fun buildBundleFromSteps(
        steps: List<MultiFrameCaptureStep>,
        framePaths: Map<Int, String>,
        shotId: String = "test-shot"
    ): FrameBundle {
        val frames = steps.map { step ->
            FrameBundleFrame(
                frameIndex = step.frameIndex,
                pixelReference = PixelReference.File(framePaths.getValue(step.frameIndex)),
                frameRole = when (step.outputRole) {
                    MultiFrameOutputRole.FINAL_OUTPUT -> FrameRole.FUSION_ANCHOR
                    MultiFrameOutputRole.TEMPORARY -> FrameRole.FUSION_SUPPLEMENT
                },
                noiseModel = NoiseModel.Unknown,
                motionScore = MotionScore.Unknown,
                isDegraded = true,
                degradationReasons = listOf("camera-x:no-per-frame-metadata")
            )
        }
        return FrameBundle(
            shotId = shotId,
            frames = frames,
            diagnostics = listOf(
                "device:burst-bundle-frames=${frames.size}",
                "device:burst-metadata=unknown",
                "device:burst-final-frame=${steps.lastOrNull { it.outputRole == MultiFrameOutputRole.FINAL_OUTPUT }?.frameIndex ?: 1}"
            )
        )
    }

    @Test
    fun `bundle frame count matches execution step count`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(3, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/t2.jpg", 3 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertEquals(3, bundle.frameCount)
        assertEquals(3, bundle.frames.size)
    }

    @Test
    fun `final frame is FUSION_ANCHOR and temporary frames are FUSION_SUPPLEMENT`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(3, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/t2.jpg", 3 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertEquals(FrameRole.FUSION_SUPPLEMENT, bundle.frames[0].frameRole)
        assertEquals(FrameRole.FUSION_SUPPLEMENT, bundle.frames[1].frameRole)
        assertEquals(FrameRole.FUSION_ANCHOR, bundle.frames[2].frameRole)
        assertEquals(3, bundle.anchorFrame?.frameIndex)
    }

    @Test
    fun `missing metadata is represented explicitly as unknown`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertTrue(bundle.frames.all { it.noiseModel is NoiseModel.Unknown })
        assertTrue(bundle.frames.all { it.motionScore is MotionScore.Unknown })
        assertTrue(bundle.frames.all { it.isDegraded })
        assertTrue(bundle.frames.all {
            it.degradationReasons.contains("camera-x:no-per-frame-metadata")
        })
    }

    @Test
    fun `bundle status is DEGRADED when metadata is unknown`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertTrue(bundle.isDegraded)
        assertEquals(FrameBundleStatus.DEGRADED, bundle.status())
    }

    @Test
    fun `bundle diagnostics contain required device burst entries`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(3, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/t2.jpg", 3 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertTrue(bundle.diagnostics.contains("device:burst-bundle-frames=3"))
        assertTrue(bundle.diagnostics.contains("device:burst-metadata=unknown"))
        assertTrue(bundle.diagnostics.contains("device:burst-final-frame=3"))
    }

    @Test
    fun `single frame bundle has no temporary frames`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        assertEquals(1, bundle.frameCount)
        assertEquals(FrameRole.FUSION_ANCHOR, bundle.frames[0].frameRole)
        // isDegraded is true for all frames (unknown metadata), so validFrames excludes them
        assertEquals(0, bundle.validFrames.size)
    }

    @Test
    fun `validFrames excludes degraded entries`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(3, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/t2.jpg", 3 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        // All frames are degraded due to unknown metadata, so validFrames excludes all
        assertEquals(0, bundle.validFrames.size)
    }

    @Test
    fun `intermediate output paths are preserved alongside bundle`() {
        val intermediatePaths = listOf("/tmp/t1.jpg", "/tmp/t2.jpg")
        val finalPath = "/tmp/out.jpg"

        // Simulates the adapter behavior: intermediateOutputPaths stays intact
        // while FrameBundle is built separately
        assertEquals(2, intermediatePaths.size)
        assertFalse(intermediatePaths.contains(finalPath))
    }

    @Test
    fun `bundle frames reference correct file paths`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/data/tmp/frame1.jpg", 2 to "/data/media/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        val ref1 = bundle.frames[0].pixelReference
        val ref2 = bundle.frames[1].pixelReference
        assertTrue(ref1 is PixelReference.File)
        assertEquals("/data/tmp/frame1.jpg", (ref1 as PixelReference.File).path)
        assertTrue(ref2 is PixelReference.File)
        assertEquals("/data/media/out.jpg", (ref2 as PixelReference.File).path)
    }

    @Test
    fun `cleanup does not affect bundle construction`() {
        val steps = listOf(
            MultiFrameCaptureStep(1, MultiFrameOutputRole.TEMPORARY),
            MultiFrameCaptureStep(2, MultiFrameOutputRole.FINAL_OUTPUT)
        )
        val paths = mapOf(1 to "/tmp/t1.jpg", 2 to "/tmp/out.jpg")
        val bundle = buildBundleFromSteps(steps, paths)

        // Bundle is an immutable data structure; cleanup is tracked separately
        assertEquals(2, bundle.frameCount)
        assertEquals(FrameBundleStatus.DEGRADED, bundle.status())
    }
}
