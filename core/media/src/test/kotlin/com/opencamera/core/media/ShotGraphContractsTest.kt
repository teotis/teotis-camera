package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertEquals

class ShotGraphContractsTest {

    private val jpegFormat = CaptureFrameFormat(mimeType = "image/jpeg")
    private val videoFormat = CaptureFrameFormat(mimeType = "video/mp4")

    private fun captureNode(id: String, role: CaptureNodeRole, format: CaptureFrameFormat = jpegFormat) =
        CaptureNode(id = id, role = role, frameCount = 1, requiredFormat = format)

    private fun algorithmNode(id: String, type: AlgorithmType, input: String, output: String) =
        AlgorithmNode(id = id, type = type, inputs = listOf(input), output = output,
            requirement = AlgorithmRequirement.REQUIRED, fallback = AlgorithmFallback.FAIL_SHOT)

    private fun graph(
        captureNodes: List<CaptureNode> = emptyList(),
        algorithmNodes: List<AlgorithmNode> = emptyList(),
        outputNodes: List<OutputNode> = emptyList()
    ) = ShotGraph(
        shotId = "test",
        captureNodes = captureNodes,
        algorithmNodes = algorithmNodes,
        outputNodes = outputNodes
    )

    // ── STILL_CAPTURE invariants ──────────────────────────────────────

    @Test
    fun `STILL_CAPTURE valid graph produces no errors`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL)),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        assertEquals(emptyList(), g.validateConsistency(ShotKind.STILL_CAPTURE))
    }

    @Test
    fun `STILL_CAPTURE missing PRIMARY_STILL returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:video", CaptureNodeRole.PRIMARY_VIDEO, videoFormat)),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.STILL_CAPTURE)
        assertTrue(errors.any { it.contains("PRIMARY_STILL") })
    }

    @Test
    fun `STILL_CAPTURE with TEMPORARY_FRAME returns error`() {
        val g = graph(
            captureNodes = listOf(
                captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL),
                captureNode("s:temp", CaptureNodeRole.TEMPORARY_FRAME)
            ),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.MULTI_FRAME_MERGE, "s:temp", "s:primary")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.STILL_CAPTURE)
        assertTrue(errors.any { it.contains("TEMPORARY_FRAME") })
    }

    // ── MULTI_FRAME_CAPTURE invariants ────────────────────────────────

    @Test
    fun `MULTI_FRAME_CAPTURE valid graph produces no errors`() {
        val g = graph(
            captureNodes = listOf(
                captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL),
                captureNode("s:temp", CaptureNodeRole.TEMPORARY_FRAME)
            ),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.MULTI_FRAME_MERGE, "s:temp", "s:primary")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        assertEquals(emptyList(), g.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE))
    }

    @Test
    fun `MULTI_FRAME_CAPTURE missing PRIMARY_STILL returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:temp", CaptureNodeRole.TEMPORARY_FRAME)),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.MULTI_FRAME_MERGE, "s:temp", "s:primary")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE)
        assertTrue(errors.any { it.contains("PRIMARY_STILL") })
    }

    @Test
    fun `MULTI_FRAME_CAPTURE missing TEMPORARY_FRAME returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL)),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.MULTI_FRAME_MERGE, "s:temp", "s:primary")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE)
        assertTrue(errors.any { it.contains("TEMPORARY_FRAME") })
    }

    @Test
    fun `MULTI_FRAME_CAPTURE missing MULTI_FRAME_MERGE algorithm returns error`() {
        val g = graph(
            captureNodes = listOf(
                captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL),
                captureNode("s:temp", CaptureNodeRole.TEMPORARY_FRAME)
            ),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE)
        assertTrue(errors.any { it.contains("MULTI_FRAME_MERGE") })
    }

    // ── LIVE_PHOTO invariants ─────────────────────────────────────────

    @Test
    fun `LIVE_PHOTO valid graph produces no errors`() {
        val g = graph(
            captureNodes = listOf(
                captureNode("s:still", CaptureNodeRole.PRIMARY_STILL),
                captureNode("s:motion", CaptureNodeRole.MOTION_SEGMENT, videoFormat)
            ),
            algorithmNodes = listOf(
                algorithmNode("alg", AlgorithmType.LIVE_ASSEMBLE, "s:still", "s:live")
            ),
            outputNodes = listOf(
                OutputNode("out-still", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"),
                OutputNode("out-motion", MediaArtifactRole.MOTION_SEGMENT, mimeType = "video/mp4"),
                OutputNode("out-sidecar", MediaArtifactRole.LIVE_SIDECAR, mimeType = "application/json")
            )
        )
        assertEquals(emptyList(), g.validateConsistency(ShotKind.LIVE_PHOTO))
    }

    @Test
    fun `LIVE_PHOTO missing PRIMARY_STILL returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:motion", CaptureNodeRole.MOTION_SEGMENT, videoFormat)),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.LIVE_ASSEMBLE, "s:still", "s:live")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.LIVE_PHOTO)
        assertTrue(errors.any { it.contains("PRIMARY_STILL") })
    }

    @Test
    fun `LIVE_PHOTO missing MOTION_SEGMENT returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:still", CaptureNodeRole.PRIMARY_STILL)),
            algorithmNodes = listOf(algorithmNode("alg", AlgorithmType.LIVE_ASSEMBLE, "s:still", "s:live")),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.LIVE_PHOTO)
        assertTrue(errors.any { it.contains("MOTION_SEGMENT") })
    }

    @Test
    fun `LIVE_PHOTO missing LIVE_ASSEMBLE algorithm returns error`() {
        val g = graph(
            captureNodes = listOf(
                captureNode("s:still", CaptureNodeRole.PRIMARY_STILL),
                captureNode("s:motion", CaptureNodeRole.MOTION_SEGMENT, videoFormat)
            ),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.LIVE_PHOTO)
        assertTrue(errors.any { it.contains("LIVE_ASSEMBLE") })
    }

    // ── VIDEO_RECORDING invariants ────────────────────────────────────

    @Test
    fun `VIDEO_RECORDING valid graph produces no errors`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:video", CaptureNodeRole.PRIMARY_VIDEO, videoFormat)),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_VIDEO, mimeType = "video/mp4"))
        )
        assertEquals(emptyList(), g.validateConsistency(ShotKind.VIDEO_RECORDING))
    }

    @Test
    fun `VIDEO_RECORDING missing PRIMARY_VIDEO returns error`() {
        val g = graph(
            captureNodes = listOf(captureNode("s:primary", CaptureNodeRole.PRIMARY_STILL)),
            outputNodes = listOf(OutputNode("out", MediaArtifactRole.PRIMARY_STILL, mimeType = "image/jpeg"))
        )
        val errors = g.validateConsistency(ShotKind.VIDEO_RECORDING)
        assertTrue(errors.any { it.contains("PRIMARY_VIDEO") })
    }

    // ── Edge: empty graph ─────────────────────────────────────────────

    @Test
    fun `empty graph reports all missing invariants for STILL_CAPTURE`() {
        val g = graph()
        val errors = g.validateConsistency(ShotKind.STILL_CAPTURE)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("PRIMARY_STILL") })
    }

    @Test
    fun `empty graph reports all missing invariants for MULTI_FRAME_CAPTURE`() {
        val g = graph()
        val errors = g.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE)
        assertTrue(errors.isNotEmpty())
        assertTrue(errors.any { it.contains("PRIMARY_STILL") })
        assertTrue(errors.any { it.contains("TEMPORARY_FRAME") })
        assertTrue(errors.any { it.contains("MULTI_FRAME_MERGE") })
    }
}
