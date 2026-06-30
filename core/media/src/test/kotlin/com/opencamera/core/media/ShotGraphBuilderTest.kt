package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ShotGraphBuilderTest {

    private val baseRequest = ShotRequest(
        shotId = "test-shot",
        shotKind = ShotKind.STILL_CAPTURE,
        mediaType = MediaType.PHOTO,
        saveRequest = SaveRequest.photoLibrary(),
        thumbnailPolicy = ThumbnailPolicy.NONE,
        postProcessSpec = PostProcessSpec(),
        captureProfile = CaptureProfile()
    )

    // ── primaryCaptureNodeId ───────────────────────────────────────────

    @Test
    fun `STILL_CAPTURE primaryCaptureNodeId is shotId colon primary`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(shotKind = ShotKind.STILL_CAPTURE))
        val primary = graph.primaryStillNode()
        assertNotNull(primary)
        assertEquals("test-shot:primary", primary.id)
    }

    @Test
    fun `MULTI_FRAME_CAPTURE primaryCaptureNodeId is shotId colon primary`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(shotKind = ShotKind.MULTI_FRAME_CAPTURE))
        val primary = graph.primaryStillNode()
        assertNotNull(primary)
        assertEquals("test-shot:primary", primary.id)
    }

    @Test
    fun `LIVE_PHOTO primaryCaptureNodeId is shotId colon still`() {
        val graph = ShotGraphBuilder.build(
            baseRequest.copy(
                shotKind = ShotKind.LIVE_PHOTO,
                livePhotoSpec = LivePhotoCaptureSpec()
            )
        )
        val primary = graph.primaryStillNode()
        assertNotNull(primary)
        assertEquals("test-shot:still", primary.id)
    }

    @Test
    fun `VIDEO_RECORDING primaryCaptureNodeId is shotId colon video`() {
        val request = baseRequest.copy(
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary()
        )
        val graph = ShotGraphBuilder.build(request)
        val primary = graph.primaryVideoNode()
        assertNotNull(primary)
        assertEquals("test-shot:video", primary.id)
    }

    // ── algorithm nodes reference primaryCaptureNodeId ─────────────────

    @Test
    fun `filter algorithm uses primaryCaptureNodeId as input`() {
        val request = baseRequest.copy(
            postProcessSpec = PostProcessSpec(algorithmProfile = "photo-vivid")
        )
        val graph = ShotGraphBuilder.build(request)
        val filterNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.FILTER_RENDER }
        assertNotNull(filterNode)
        assertEquals(listOf("test-shot:primary"), filterNode.inputs)
    }

    @Test
    fun `watermark algorithm uses primaryCaptureNodeId as input`() {
        val request = baseRequest.copy(
            postProcessSpec = PostProcessSpec(watermarkText = "test watermark")
        )
        val graph = ShotGraphBuilder.build(request)
        val watermarkNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.WATERMARK_RENDER }
        assertNotNull(watermarkNode)
        assertEquals(listOf("test-shot:primary"), watermarkNode.inputs)
    }

    @Test
    fun `watermark algorithm is present for explicit non default template without text`() {
        val request = baseRequest.copy(
            saveRequest = SaveRequest.photoLibrary(
                metadata = MediaMetadata(
                    customTags = mapOf("watermarkTemplate" to "van-gogh-starry")
                )
            )
        )
        val graph = ShotGraphBuilder.build(request)
        val watermarkNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.WATERMARK_RENDER }
        assertNotNull(watermarkNode)
        assertEquals(listOf("test-shot:primary"), watermarkNode.inputs)
    }

    @Test
    fun `thumbnail algorithm uses primaryCaptureNodeId as input`() {
        val request = baseRequest.copy(thumbnailPolicy = ThumbnailPolicy.KEEP_PREVIEW_FRAME)
        val graph = ShotGraphBuilder.build(request)
        val thumbnailNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.THUMBNAIL_SELECT }
        assertNotNull(thumbnailNode)
        assertEquals(listOf("test-shot:primary"), thumbnailNode.inputs)
    }

    // ── node counts per ShotKind ──────────────────────────────────────

    @Test
    fun `STILL_CAPTURE has one capture node and one output node`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(shotKind = ShotKind.STILL_CAPTURE))
        assertEquals(1, graph.captureNodes.size)
        assertEquals(1, graph.outputNodes.size)
    }

    @Test
    fun `MULTI_FRAME_CAPTURE has two capture nodes and requires merge algorithm`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(shotKind = ShotKind.MULTI_FRAME_CAPTURE))
        assertEquals(2, graph.captureNodes.size)
        assertTrue(graph.requiresAlgorithm(AlgorithmType.MULTI_FRAME_MERGE))
    }

    @Test
    fun `MULTI_FRAME_CAPTURE merge algorithm inputs are temp-frames node`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(shotKind = ShotKind.MULTI_FRAME_CAPTURE))
        val mergeNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.MULTI_FRAME_MERGE }
        assertNotNull(mergeNode)
        assertEquals(listOf("test-shot:temp-frames"), mergeNode.inputs)
        assertEquals("test-shot:primary", mergeNode.output)
    }

    @Test
    fun `MULTI_FRAME_CAPTURE with focus stack spec requires focus stack fusion algorithm`() {
        val request = baseRequest.copy(
            shotKind = ShotKind.MULTI_FRAME_CAPTURE,
            captureProfile = CaptureProfile(
                frameCount = 2,
                focusStackSpec = FocusStackCaptureSpec.guidedNearFar()
            )
        )

        val graph = ShotGraphBuilder.build(request)
        val focusStackNode = graph.algorithmNodes.firstOrNull { it.type == AlgorithmType.FOCUS_STACK_FUSION }

        assertNotNull(focusStackNode)
        assertEquals(listOf("test-shot:temp-frames"), focusStackNode.inputs)
        assertEquals("test-shot:primary", focusStackNode.output)
        assertTrue(graph.validateConsistency(ShotKind.MULTI_FRAME_CAPTURE).isEmpty())
    }

    @Test
    fun `LIVE_PHOTO has three capture nodes and requires live assemble algorithm`() {
        val graph = ShotGraphBuilder.build(
            baseRequest.copy(
                shotKind = ShotKind.LIVE_PHOTO,
                livePhotoSpec = LivePhotoCaptureSpec()
            )
        )
        val roles = graph.captureNodes.map { it.role }
        assertTrue(roles.contains(CaptureNodeRole.PRIMARY_STILL))
        assertTrue(roles.contains(CaptureNodeRole.MOTION_SEGMENT))
        assertTrue(graph.requiresAlgorithm(AlgorithmType.LIVE_ASSEMBLE) ||
            graph.algorithmNodes.any { it.type == AlgorithmType.LIVE_ASSEMBLE })
    }

    @Test
    fun `LIVE_PHOTO has three output nodes`() {
        val graph = ShotGraphBuilder.build(
            baseRequest.copy(
                shotKind = ShotKind.LIVE_PHOTO,
                livePhotoSpec = LivePhotoCaptureSpec()
            )
        )
        assertEquals(3, graph.outputNodes.size)
    }

    @Test
    fun `VIDEO_RECORDING has one capture node and one output node`() {
        val request = baseRequest.copy(
            shotKind = ShotKind.VIDEO_RECORDING,
            mediaType = MediaType.VIDEO,
            saveRequest = SaveRequest.videoLibrary()
        )
        val graph = ShotGraphBuilder.build(request)
        assertEquals(1, graph.captureNodes.size)
        assertEquals(1, graph.outputNodes.size)
        assertEquals(CaptureNodeRole.PRIMARY_VIDEO, graph.captureNodes.first().role)
    }

    // ── no algorithm nodes when no postProcessSpec or thumbnail ────────

    @Test
    fun `no algorithm nodes for bare STILL_CAPTURE with no thumbnail`() {
        val graph = ShotGraphBuilder.build(baseRequest.copy(thumbnailPolicy = ThumbnailPolicy.NONE))
        assertTrue(graph.algorithmNodes.isEmpty())
    }
}
