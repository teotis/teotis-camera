package com.opencamera.app

import com.opencamera.core.session.DocumentBatchStatus
import com.opencamera.core.session.DocumentWorkflowPhase
import com.opencamera.core.session.toBatchStatus
import com.opencamera.core.session.toWorkflowPhase
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DocumentWorkflowPhaseIntegrationTest {

    private fun router() = CockpitPanelRouter()

    @Test
    fun `complete workflow - shooting to batch overview to crop edit to export`() {
        val r = router()

        // Start: Shooting phase
        assertEquals(CockpitPanelRoute.None, r.state.route)

        // Shooting → BatchOverview
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        assertEquals(CockpitPanelRoute.BatchOverview, r.state.route)

        // BatchOverview → CropEdit
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        r.reduce(CockpitPanelCommand.NavigateToCropEdit)
        assertEquals(CockpitPanelRoute.CropEdit, r.state.route)
        assertEquals("item-1", r.state.selectedCropEditItemId)

        // CropEdit → BatchOverview (close crop edit)
        r.reduce(CockpitPanelCommand.CloseCropEdit)
        assertEquals(CockpitPanelRoute.BatchOverview, r.state.route)
        assertNull(r.state.selectedCropEditItemId)

        // BatchOverview → Export
        r.reduce(CockpitPanelCommand.NavigateToExport)
        assertEquals(CockpitPanelRoute.Export, r.state.route)
        r.reduce(CockpitPanelCommand.StartExport)
        assertTrue(r.state.exportState is ExportState.InProgress)
    }

    @Test
    fun `export flow - start progress complete return to shooting`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.StartExport)
        assertEquals(CockpitPanelRoute.Export, r.state.route)
        assertTrue(r.state.exportState is ExportState.InProgress)

        // Progress updates
        r.reduce(CockpitPanelCommand.UpdateExportProgress(currentPage = 3, totalPages = 10))
        val inProgress = r.state.exportState as ExportState.InProgress
        assertEquals(3, inProgress.currentPage)
        assertEquals(10, inProgress.totalPages)

        // Complete
        r.reduce(CockpitPanelCommand.CompleteExport)
        assertTrue(r.state.exportState is ExportState.Success)

        // Return to shooting
        r.reduce(CockpitPanelCommand.ReturnToShooting)
        assertEquals(CockpitPanelRoute.None, r.state.route)
        assertNull(r.state.exportState)
        assertTrue(r.state.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `export flow - failure and retry`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.StartExport)
        r.reduce(CockpitPanelCommand.FailExport)
        assertTrue(r.state.exportState is ExportState.Failed)

        // Retry: restart export
        r.reduce(CockpitPanelCommand.StartExport)
        assertTrue(r.state.exportState is ExportState.InProgress)
        assertEquals(CockpitPanelRoute.Export, r.state.route)
    }

    @Test
    fun `close export returns to batch overview`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.StartExport)
        assertEquals(CockpitPanelRoute.Export, r.state.route)

        r.reduce(CockpitPanelCommand.CloseExport)
        assertEquals(CockpitPanelRoute.BatchOverview, r.state.route)
        assertNull(r.state.exportState)
        assertNull(r.state.selectedCropEditItemId)
    }

    @Test
    fun `android back from export returns to batch overview`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.StartExport)
        r.reduce(CockpitPanelCommand.UpdateExportProgress(5, 10))
        assertEquals(CockpitPanelRoute.Export, r.state.route)

        r.reduce(CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.BatchOverview, r.state.route)
        assertNull(r.state.exportState)
    }

    @Test
    fun `android back from crop edit returns to batch overview`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-2"))
        r.reduce(CockpitPanelCommand.NavigateToCropEdit)
        assertEquals(CockpitPanelRoute.CropEdit, r.state.route)

        r.reduce(CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.BatchOverview, r.state.route)
        assertNull(r.state.selectedCropEditItemId)
    }

    @Test
    fun `dismiss all from any route returns to none`() {
        val r = router()
        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.StartExport)
        r.reduce(CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, r.state.route)
        assertNull(r.state.exportState)
    }

    @Test
    fun `phase to route mapping is correct`() {
        assertEquals(CockpitPanelRoute.None, workflowPhaseToRoute(DocumentWorkflowPhase.Shooting))
        assertEquals(CockpitPanelRoute.BatchOverview, workflowPhaseToRoute(DocumentWorkflowPhase.BatchOverview))
        assertEquals(CockpitPanelRoute.CropEdit, workflowPhaseToRoute(DocumentWorkflowPhase.CropEdit))
        assertEquals(CockpitPanelRoute.Export, workflowPhaseToRoute(DocumentWorkflowPhase.Export))
    }

    @Test
    fun `batch status to workflow phase mapping`() {
        assertEquals(
            DocumentWorkflowPhase.Shooting,
            DocumentBatchStatus.INACTIVE.toWorkflowPhase()
        )
        assertEquals(
            DocumentWorkflowPhase.Shooting,
            DocumentBatchStatus.ACTIVE.toWorkflowPhase()
        )
        assertEquals(
            DocumentWorkflowPhase.Export,
            DocumentBatchStatus.FINISHED.toWorkflowPhase()
        )
    }

    @Test
    fun `workflow phase to batch status mapping`() {
        assertEquals(
            DocumentBatchStatus.ACTIVE,
            DocumentWorkflowPhase.Shooting.toBatchStatus()
        )
        assertEquals(
            DocumentBatchStatus.ACTIVE,
            DocumentWorkflowPhase.BatchOverview.toBatchStatus()
        )
        assertEquals(
            DocumentBatchStatus.FINISHED,
            DocumentWorkflowPhase.Export.toBatchStatus()
        )
    }
}
