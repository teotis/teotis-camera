package com.opencamera.core.session

import com.opencamera.core.media.FrameRatio
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessLivenessDeadline
import com.opencamera.core.media.ShotConfigSnapshot
import com.opencamera.core.mode.ModeId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PostProcessLivenessContractsTest {

    // ── PostProcessLivenessEvent: DeadlineExpired ─────────────────────────────

    @Test
    fun `DeadlineExpired carries all fields`() {
        val event = PostProcessLivenessEvent.DeadlineExpired(
            shotId = "shot-001",
            mediaType = MediaType.PHOTO,
            mode = ModeId.PHOTO,
            stage = PostProcessLivenessStage.MEDIA_POST_PROCESS,
            reason = "budget exhausted",
            elapsedSinceShutterMs = 4500L,
            elapsedSincePostprocessStartMs = 8500L,
            budgetMillis = 8000L
        )
        assertEquals("shot-001", event.shotId)
        assertEquals(MediaType.PHOTO, event.mediaType)
        assertEquals(ModeId.PHOTO, event.mode)
        assertEquals(PostProcessLivenessStage.MEDIA_POST_PROCESS, event.stage)
        assertEquals("budget exhausted", event.reason)
        assertEquals(4500L, event.elapsedSinceShutterMs)
        assertEquals(8500L, event.elapsedSincePostprocessStartMs)
        assertEquals(8000L, event.budgetMillis)
    }

    // ── PostProcessLivenessEvent: PipelineFailed ──────────────────────────────

    @Test
    fun `PipelineFailed carries all fields`() {
        val event = PostProcessLivenessEvent.PipelineFailed(
            shotId = "shot-002",
            mediaType = MediaType.PHOTO,
            mode = ModeId.DOCUMENT,
            stage = PostProcessLivenessStage.MEDIA_SAVE,
            reason = "disk full",
            elapsedSinceShutterMs = 3200L,
            elapsedSincePostprocessStartMs = 1500L
        )
        assertEquals("shot-002", event.shotId)
        assertEquals(MediaType.PHOTO, event.mediaType)
        assertEquals(ModeId.DOCUMENT, event.mode)
        assertEquals("disk full", event.reason)
    }

    // ── PostProcessLivenessEvent: ForceReleasedFromDocumentBatch ──────────────

    @Test
    fun `ForceReleasedFromDocumentBatch carries all fields`() {
        val event = PostProcessLivenessEvent.ForceReleasedFromDocumentBatch(
            shotId = "shot-003",
            mediaType = MediaType.PHOTO,
            mode = ModeId.DOCUMENT,
            stage = PostProcessLivenessStage.DOCUMENT_BATCH,
            reason = "batch timer expired",
            elapsedSinceShutterMs = 10000L,
            elapsedSincePostprocessStartMs = 7200L,
            itemId = "item-abc"
        )
        assertEquals("shot-003", event.shotId)
        assertEquals("item-abc", event.itemId)
    }

    @Test
    fun `ForceReleasedFromDocumentBatch allows null itemId`() {
        val event = PostProcessLivenessEvent.ForceReleasedFromDocumentBatch(
            shotId = "shot-004",
            mediaType = MediaType.PHOTO,
            mode = ModeId.DOCUMENT,
            stage = PostProcessLivenessStage.DOCUMENT_BATCH,
            reason = "orphaned item",
            elapsedSinceShutterMs = 9000L,
            elapsedSincePostprocessStartMs = 6000L,
            itemId = null
        )
        assertNull(event.itemId)
    }

    // ── toDiagnosticString ────────────────────────────────────────────────────

    @Test
    fun `diagnostic string contains key labels`() {
        val event = PostProcessLivenessEvent.DeadlineExpired(
            shotId = "s1",
            mediaType = MediaType.PHOTO,
            mode = ModeId.PHOTO,
            stage = PostProcessLivenessStage.THUMBNAIL,
            reason = "timeout",
            elapsedSinceShutterMs = 5000L,
            elapsedSincePostprocessStartMs = 2000L,
            budgetMillis = 3000L
        )
        val s = event.toDiagnosticString()
        assertTrue(s.contains("deadline-expired"), "variant label missing: $s")
        assertTrue(s.contains("shotId=s1"), "shotId missing: $s")
        assertTrue(s.contains("budgetMillis=3000"), "budgetMillis missing: $s")
    }

    @Test
    fun `diagnostic string for PipelineFailed is greppable`() {
        val event = PostProcessLivenessEvent.PipelineFailed(
            shotId = "s2",
            mediaType = MediaType.PHOTO,
            mode = ModeId.PHOTO,
            stage = PostProcessLivenessStage.MEDIA_POST_PROCESS,
            reason = "crash",
            elapsedSinceShutterMs = 1000L,
            elapsedSincePostprocessStartMs = 500L
        )
        val s = event.toDiagnosticString()
        assertTrue(s.contains("pipeline-failed"), "variant label missing: $s")
        assertTrue(s.contains("stage=MEDIA_POST_PROCESS"), "stage missing: $s")
        // PipelineFailed variant adds no extra field — verify clean format
        assertFalse(s.contains("budgetMillis="), "budgetMillis should not appear: $s")
    }

    @Test
    fun `diagnostic string for ForceReleasedFromDocumentBatch is greppable`() {
        val event = PostProcessLivenessEvent.ForceReleasedFromDocumentBatch(
            shotId = "s3",
            mediaType = MediaType.PHOTO,
            mode = ModeId.DOCUMENT,
            stage = PostProcessLivenessStage.DOCUMENT_BATCH,
            reason = "timer",
            elapsedSinceShutterMs = 8000L,
            elapsedSincePostprocessStartMs = 4000L,
            itemId = "item-x"
        )
        val s = event.toDiagnosticString()
        assertTrue(s.contains("force-released-from-document-batch"), "variant label missing: $s")
        assertTrue(s.contains("itemId=item-x"), "itemId missing: $s")
    }

    @Test
    fun `diagnostic string covers null itemId`() {
        val event = PostProcessLivenessEvent.ForceReleasedFromDocumentBatch(
            shotId = "s4",
            mediaType = MediaType.PHOTO,
            mode = ModeId.DOCUMENT,
            stage = PostProcessLivenessStage.DOCUMENT_BATCH,
            reason = "orphan",
            elapsedSinceShutterMs = 7000L,
            elapsedSincePostprocessStartMs = 3000L,
            itemId = null
        )
        val s = event.toDiagnosticString()
        assertTrue(s.contains("itemId="), "itemId= should always appear: $s")
    }

    // ── PendingPostprocessLivenessAttachment ──────────────────────────────────

    @Test
    fun `attachment defaults are null`() {
        val att = PendingPostprocessLivenessAttachment(
            configSnapshot = null,
            liveness = null
        )
        assertNull(att.configSnapshot)
        assertNull(att.liveness)
    }

    @Test
    fun `attachment carries values when set`() {
        val snap = ShotConfigSnapshot("wm", FrameRatio.RATIO_4_3, "recipe", false)
        val liv = PostProcessLivenessDeadline.forShot("s1", 0L, 5000L)
        val att = PendingPostprocessLivenessAttachment(
            configSnapshot = snap,
            liveness = liv
        )
        assertEquals(snap, att.configSnapshot)
        assertEquals(liv, att.liveness)
    }

    // ── PendingPostprocessUiState backward compatibility ──────────────────────

    @Test
    fun `PendingPostprocessUiState defaults to null livenessAttachment`() {
        val state = PendingPostprocessUiState(
            shotId = "s1",
            mediaType = MediaType.PHOTO,
            message = "Saving photo..."
        )
        assertEquals("s1", state.shotId)
        assertNull(state.livenessAttachment)
        assertTrue(state.warnBeforeExit) // default unchanged
    }
}
