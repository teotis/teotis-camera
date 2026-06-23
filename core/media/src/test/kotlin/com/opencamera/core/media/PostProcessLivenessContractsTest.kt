package com.opencamera.core.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostProcessLivenessContractsTest {

    // ── PostProcessLivenessDeadline ───────────────────────────────────────────

    @Test
    fun `from factory with positive budget`() {
        val d = PostProcessLivenessDeadline.from(start = 1000L, budgetMs = 3000L)
        assertEquals(1000L, d.startedAtElapsedMillis)
        assertEquals(4000L, d.deadlineElapsedMillis)
        assertEquals(3000L, d.budgetMillis)
        assertEquals("", d.shotId) // caller expected to attach shotId
    }

    @Test
    fun `from factory with zero budget throws`() {
        assertThrows<IllegalArgumentException> {
            PostProcessLivenessDeadline.from(start = 100L, budgetMs = 0L)
        }
    }

    @Test
    fun `from factory with negative budget throws`() {
        assertThrows<IllegalArgumentException> {
            PostProcessLivenessDeadline.from(start = 100L, budgetMs = -1L)
        }
    }

    @Test
    fun `from factory with negative start throws`() {
        assertThrows<IllegalArgumentException> {
            PostProcessLivenessDeadline.from(start = -1L, budgetMs = 1000L)
        }
    }

    @Test
    fun `forShot attaches shotId`() {
        val d = PostProcessLivenessDeadline.forShot(
            shotId = "test-shot-1",
            start = 5000L,
            budgetMs = 8000L
        )
        assertEquals("test-shot-1", d.shotId)
        assertEquals(5000L, d.startedAtElapsedMillis)
        assertEquals(13000L, d.deadlineElapsedMillis)
        assertEquals(8000L, d.budgetMillis)
    }

    @Test
    fun `isExpired before deadline returns false`() {
        val d = PostProcessLivenessDeadline.from(start = 0L, budgetMs = 5000L)
        assertFalse(d.isExpired(4999L))
        assertFalse(d.isExpired(0L))
    }

    @Test
    fun `isExpired exactly at deadline returns false`() {
        // Strictly past — exactly at deadline is NOT expired
        val d = PostProcessLivenessDeadline.from(start = 0L, budgetMs = 5000L)
        assertFalse(d.isExpired(5000L))
    }

    @Test
    fun `isExpired past deadline returns true`() {
        val d = PostProcessLivenessDeadline.from(start = 0L, budgetMs = 5000L)
        assertTrue(d.isExpired(5001L))
        assertTrue(d.isExpired(10000L))
    }

    @Test
    fun `default budget constant is positive and reasonable`() {
        assertTrue(PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS > 0L)
        // Must be >= typical AlgorithmJobSpec timeout; 8s is a conservative default
        assertTrue(PostProcessLivenessDeadline.DEFAULT_BUDGET_MILLIS >= 3000L)
    }

    @Test
    fun `copy preserves deadline math`() {
        val d = PostProcessLivenessDeadline.from(start = 1000L, budgetMs = 3000L)
            .copy(shotId = "attached")
        assertEquals("attached", d.shotId)
        assertEquals(4000L, d.deadlineElapsedMillis)
        assertEquals(3000L, d.budgetMillis)
    }

    // ── ShotConfigSnapshot ────────────────────────────────────────────────────

    @Test
    fun `snapshot copy preserves fields`() {
        val snap = ShotConfigSnapshot(
            watermarkTemplateId = "classic-overlay",
            frameRatio = FrameRatio.RATIO_4_3,
            colorRecipeId = "vivid",
            isDocumentMode = true
        )
        assertEquals("classic-overlay", snap.watermarkTemplateId)
        assertEquals(FrameRatio.RATIO_4_3, snap.frameRatio)
        assertEquals("vivid", snap.colorRecipeId)
        assertTrue(snap.isDocumentMode)
    }

    @Test
    fun `snapshot defaults are unset`() {
        // Kotlin data class — primitives must be explicit
        val snap = ShotConfigSnapshot(
            watermarkTemplateId = null,
            frameRatio = FrameRatio.RATIO_16_9,
            colorRecipeId = null,
            isDocumentMode = false
        )
        assertNull(snap.watermarkTemplateId)
        assertNull(snap.colorRecipeId)
        assertFalse(snap.isDocumentMode)
    }

    @Test
    fun `snapshot equality is structural`() {
        val a = ShotConfigSnapshot("t1", FrameRatio.RATIO_1_1, "c1", false)
        val b = ShotConfigSnapshot("t1", FrameRatio.RATIO_1_1, "c1", false)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    // ── ShotRequestLivenessEnvelope ───────────────────────────────────────────

    @Test
    fun `envelope defaults are null`() {
        val req = ShotRequest(
            shotId = "test",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val env = ShotRequestLivenessEnvelope(request = req)
        assertNotNull(env.request)
        assertNull(env.configSnapshot)
        assertNull(env.liveness)
    }

    @Test
    fun `envelope carries snapshot and liveness when set`() {
        val req = ShotRequest(
            shotId = "test",
            shotKind = ShotKind.STILL_CAPTURE,
            mediaType = MediaType.PHOTO,
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA,
            postProcessSpec = PostProcessSpec(),
            captureProfile = CaptureProfile()
        )
        val snap = ShotConfigSnapshot("wm", FrameRatio.RATIO_4_3, "recipe", false)
        val liv = PostProcessLivenessDeadline.forShot("test-shot", 0L, 5000L)
        val env = ShotRequestLivenessEnvelope(
            request = req,
            configSnapshot = snap,
            liveness = liv
        )
        assertEquals(snap, env.configSnapshot)
        assertEquals(liv, env.liveness)
    }

    // ── PostProcessRequest (in MediaPostProcessorContracts) ───────────────────

    @Test
    fun `media PostProcessRequest defaults are null`() {
        val result = ShotResult(
            shotId = "test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/test.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata()
        )
        val ppr = PostProcessRequest(result = result)
        assertEquals(result, ppr.result)
        assertNull(ppr.configSnapshot)
        assertNull(ppr.liveness)
    }

    @Test
    fun `media PostProcessRequest carries snapshot and liveness`() {
        val result = ShotResult(
            shotId = "test",
            mediaType = MediaType.PHOTO,
            outputPath = "/tmp/test.jpg",
            saveRequest = SaveRequest.photoLibrary(),
            thumbnailSource = ThumbnailSource.None,
            metadata = MediaMetadata()
        )
        val snap = ShotConfigSnapshot("wm", FrameRatio.RATIO_4_3, "recipe", false)
        val liv = PostProcessLivenessDeadline.forShot("test", 0L, 5000L)
        val ppr = PostProcessRequest(result = result, configSnapshot = snap, liveness = liv)
        assertEquals(snap, ppr.configSnapshot)
        assertEquals(liv, ppr.liveness)
    }
}

/**
 * Minimal inline replacement for kotlin.test.assertThrows to avoid requiring
 * a JDK version that ships it in the standard library.
 */
private inline fun <reified T : Throwable> assertThrows(block: () -> Unit) {
    try {
        block()
    } catch (e: Throwable) {
        if (e is T) return
        throw AssertionError("Expected exception of type ${T::class.simpleName} but got ${e::class.simpleName}: ${e.message}", e)
    }
    throw AssertionError("Expected exception of type ${T::class.simpleName} but no exception was thrown")
}
