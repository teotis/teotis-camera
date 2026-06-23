package com.opencamera.app

import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.DocumentWorkflowPhase
import com.opencamera.core.session.toBatchStatus
import com.opencamera.core.session.toWorkflowPhase
import kotlin.test.Test
import kotlin.test.assertEquals

class DocumentBatchContractsTest {

    // -- Allowed phase transitions --

    @Test
    fun `Shooting can transition to BatchOverview`() {
        assertTransitionAllowed(DocumentWorkflowPhase.Shooting, DocumentWorkflowPhase.BatchOverview)
    }

    @Test
    fun `BatchOverview can transition back to Shooting`() {
        assertTransitionAllowed(DocumentWorkflowPhase.BatchOverview, DocumentWorkflowPhase.Shooting)
    }

    @Test
    fun `BatchOverview can transition to CropEdit`() {
        assertTransitionAllowed(DocumentWorkflowPhase.BatchOverview, DocumentWorkflowPhase.CropEdit)
    }

    @Test
    fun `CropEdit can transition to BatchOverview`() {
        assertTransitionAllowed(DocumentWorkflowPhase.CropEdit, DocumentWorkflowPhase.BatchOverview)
    }

    @Test
    fun `CropEdit can transition to Export`() {
        assertTransitionAllowed(DocumentWorkflowPhase.CropEdit, DocumentWorkflowPhase.Export)
    }

    @Test
    fun `Export can transition to Shooting for next batch`() {
        assertTransitionAllowed(DocumentWorkflowPhase.Export, DocumentWorkflowPhase.Shooting)
    }

    // -- Disallowed transitions --

    @Test
    fun `Shooting cannot skip directly to CropEdit`() {
        assertTransitionDenied(DocumentWorkflowPhase.Shooting, DocumentWorkflowPhase.CropEdit)
    }

    @Test
    fun `Shooting cannot skip directly to Export`() {
        assertTransitionDenied(DocumentWorkflowPhase.Shooting, DocumentWorkflowPhase.Export)
    }

    @Test
    fun `Export cannot transition to BatchOverview`() {
        assertTransitionDenied(DocumentWorkflowPhase.Export, DocumentWorkflowPhase.BatchOverview)
    }

    @Test
    fun `Export cannot transition to CropEdit`() {
        assertTransitionDenied(DocumentWorkflowPhase.Export, DocumentWorkflowPhase.CropEdit)
    }

    // -- Bidirectional mapping: old DocumentBatchStatus <-> DocumentWorkflowPhase --

    @Test
    fun `INACTIVE maps to Shooting`() {
        assertEquals(DocumentWorkflowPhase.Shooting, DocumentBatchStatus.INACTIVE.toWorkflowPhase())
    }

    @Test
    fun `ACTIVE maps to Shooting`() {
        assertEquals(DocumentWorkflowPhase.Shooting, DocumentBatchStatus.ACTIVE.toWorkflowPhase())
    }

    @Test
    fun `FINISHED maps to Export`() {
        assertEquals(DocumentWorkflowPhase.Export, DocumentBatchStatus.FINISHED.toWorkflowPhase())
    }

    @Test
    fun `Shooting maps back to ACTIVE`() {
        assertEquals(DocumentBatchStatus.ACTIVE, DocumentWorkflowPhase.Shooting.toBatchStatus())
    }

    @Test
    fun `BatchOverview maps to ACTIVE`() {
        assertEquals(DocumentBatchStatus.ACTIVE, DocumentWorkflowPhase.BatchOverview.toBatchStatus())
    }

    @Test
    fun `CropEdit maps to ACTIVE`() {
        assertEquals(DocumentBatchStatus.ACTIVE, DocumentWorkflowPhase.CropEdit.toBatchStatus())
    }

    @Test
    fun `Export maps to FINISHED`() {
        assertEquals(DocumentBatchStatus.FINISHED, DocumentWorkflowPhase.Export.toBatchStatus())
    }

    @Test
    fun `round trip ACTIVE phase preserves Shooting`() {
        val phase = DocumentBatchStatus.ACTIVE.toWorkflowPhase()
        assertEquals(DocumentBatchStatus.ACTIVE, phase.toBatchStatus())
    }

    @Test
    fun `round trip FINISHED phase preserves Export`() {
        val phase = DocumentBatchStatus.FINISHED.toWorkflowPhase()
        assertEquals(DocumentBatchStatus.FINISHED, phase.toBatchStatus())
    }

    // -- helpers --

    private val allowedTransitions: Set<Pair<DocumentWorkflowPhase, DocumentWorkflowPhase>> = setOf(
        DocumentWorkflowPhase.Shooting to DocumentWorkflowPhase.BatchOverview,
        DocumentWorkflowPhase.BatchOverview to DocumentWorkflowPhase.Shooting,
        DocumentWorkflowPhase.BatchOverview to DocumentWorkflowPhase.CropEdit,
        DocumentWorkflowPhase.CropEdit to DocumentWorkflowPhase.BatchOverview,
        DocumentWorkflowPhase.CropEdit to DocumentWorkflowPhase.Export,
        DocumentWorkflowPhase.Export to DocumentWorkflowPhase.Shooting,
    )

    private fun assertTransitionAllowed(from: DocumentWorkflowPhase, to: DocumentWorkflowPhase) {
        val allowed = allowedTransitions.contains(from to to)
        assert(allowed) { "Transition from $from to $to should be allowed but is not in the allowed set" }
    }

    private fun assertTransitionDenied(from: DocumentWorkflowPhase, to: DocumentWorkflowPhase) {
        val allowed = allowedTransitions.contains(from to to)
        assert(!allowed) { "Transition from $from to $to should be denied but is in the allowed set" }
    }
}
