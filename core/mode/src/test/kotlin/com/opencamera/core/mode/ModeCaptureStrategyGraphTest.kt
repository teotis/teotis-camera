package com.opencamera.core.mode

import com.opencamera.core.media.AlgorithmType
import com.opencamera.core.media.CaptureNodeRole
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.LivePhotoCaptureSpec
import com.opencamera.core.media.MediaArtifactRole
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ShotExecutor
import com.opencamera.core.media.toShotGraph
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ModeCaptureStrategyGraphTest {

    private val executor = ShotExecutor(idGenerator = { "test-shot" })

    private fun graphFor(strategy: CaptureStrategy) = executor.plan(strategy).toShotGraph()

    @Test
    fun `photo single frame produces still capture graph`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(),
                postProcessSpec = PostProcessSpec(algorithmProfile = "photo-vivid")
            )
        )

        assertEquals(1, graph.captureNodes.size)
        assertEquals(CaptureNodeRole.PRIMARY_STILL, graph.captureNodes[0].role)
        assertEquals(1, graph.captureNodes[0].frameCount)
        assertEquals(1, graph.outputNodes.size)
        assertEquals(MediaArtifactRole.PRIMARY_STILL, graph.outputNodes[0].role)
    }

    @Test
    fun `photo live photo produces still plus motion graph`() {
        val graph = graphFor(
            CaptureStrategy.LivePhoto(
                saveRequest = SaveRequest.photoLibrary(),
                livePhotoSpec = LivePhotoCaptureSpec(
                    motionDurationMillis = 1800,
                    motionMimeType = "video/mp4",
                    sidecarMimeType = "application/vnd.opencamera.live+json"
                )
            )
        )

        assertEquals(2, graph.captureNodes.size)
        assertTrue(graph.captureNodes.any { it.role == CaptureNodeRole.PRIMARY_STILL })
        assertTrue(graph.captureNodes.any { it.role == CaptureNodeRole.MOTION_SEGMENT })

        assertTrue(graph.algorithmNodes.any { it.type == AlgorithmType.LIVE_ASSEMBLE })

        assertEquals(3, graph.outputNodes.size)
        assertTrue(graph.outputNodes.any { it.role == MediaArtifactRole.PRIMARY_STILL })
        assertTrue(graph.outputNodes.any { it.role == MediaArtifactRole.MOTION_SEGMENT })
        assertTrue(graph.outputNodes.any { it.role == MediaArtifactRole.LIVE_SIDECAR })
    }

    @Test
    fun `night multi frame produces merge graph`() {
        val graph = graphFor(
            CaptureStrategy.MultiFrame(
                saveRequest = SaveRequest.photoLibrary(),
                captureProfile = CaptureProfile(frameCount = 6),
                postProcessSpec = PostProcessSpec(algorithmProfile = "night-multiframe-handheld")
            )
        )

        assertEquals(2, graph.captureNodes.size)
        val tempNode = graph.captureNodes.first { it.role == CaptureNodeRole.TEMPORARY_FRAME }
        assertEquals(6, tempNode.frameCount)
        assertTrue(graph.captureNodes.any { it.role == CaptureNodeRole.PRIMARY_STILL })

        assertTrue(graph.algorithmNodes.any { it.type == AlgorithmType.MULTI_FRAME_MERGE })

        assertEquals(1, graph.outputNodes.size)
        assertEquals(MediaArtifactRole.PRIMARY_STILL, graph.outputNodes[0].role)
    }

    @Test
    fun `night single frame fallback produces simple still graph`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(),
                postProcessSpec = PostProcessSpec(algorithmProfile = "night-fallback-balanced")
            )
        )

        assertEquals(1, graph.captureNodes.size)
        assertEquals(CaptureNodeRole.PRIMARY_STILL, graph.captureNodes[0].role)
        assertTrue(graph.algorithmNodes.none { it.type == AlgorithmType.MULTI_FRAME_MERGE })
    }

    @Test
    fun `portrait with filter produces filter algorithm node`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(),
                postProcessSpec = PostProcessSpec(algorithmProfile = "portrait-blue")
            )
        )

        assertTrue(graph.algorithmNodes.any { it.type == AlgorithmType.FILTER_RENDER })
    }

    @Test
    fun `document with algorithm profile produces filter algorithm node`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                saveRequest = SaveRequest.photoLibrary(),
                postProcessSpec = PostProcessSpec(algorithmProfile = "document-receipt-scan")
            )
        )

        assertTrue(graph.algorithmNodes.any { it.type == AlgorithmType.FILTER_RENDER })
    }

    @Test
    fun `video recording produces video graph`() {
        val graph = graphFor(
            CaptureStrategy.VideoRecording(
                saveRequest = SaveRequest.videoLibrary()
            )
        )

        assertEquals(1, graph.captureNodes.size)
        assertEquals("video/mp4", graph.captureNodes[0].requiredFormat.mimeType)
        assertEquals(1, graph.outputNodes.size)
        assertEquals(MediaArtifactRole.PRIMARY_VIDEO, graph.outputNodes[0].role)
    }

    @Test
    fun `multi frame graph has correct frame count`() {
        val frameCount = 12
        val graph = graphFor(
            CaptureStrategy.MultiFrame(
                captureProfile = CaptureProfile(frameCount = frameCount)
            )
        )

        val tempNode = graph.captureNodes.first { it.role == CaptureNodeRole.TEMPORARY_FRAME }
        assertEquals(frameCount, tempNode.frameCount)
    }

    @Test
    fun `live photo graph has motion duration from spec`() {
        val spec = LivePhotoCaptureSpec(motionDurationMillis = 2400)
        val graph = graphFor(CaptureStrategy.LivePhoto(livePhotoSpec = spec))

        assertTrue(graph.captureNodes.any { it.role == CaptureNodeRole.MOTION_SEGMENT })
        val liveAssemble = graph.algorithmNodes.first { it.type == AlgorithmType.LIVE_ASSEMBLE }
        assertEquals(AlgorithmType.LIVE_ASSEMBLE, liveAssemble.type)
    }

    @Test
    fun `watermark text produces watermark render algorithm node`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                postProcessSpec = PostProcessSpec(watermarkText = "OpenCamera Test")
            )
        )

        assertTrue(graph.algorithmNodes.any { it.type == AlgorithmType.WATERMARK_RENDER })
    }

    @Test
    fun `no watermark text produces no watermark algorithm node`() {
        val graph = graphFor(
            CaptureStrategy.SingleFrame(
                postProcessSpec = PostProcessSpec()
            )
        )

        assertTrue(graph.algorithmNodes.none { it.type == AlgorithmType.WATERMARK_RENDER })
    }
}
