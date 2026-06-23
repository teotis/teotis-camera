package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class CockpitPanelRouterBatchOverviewTest {

    private fun router(
        initial: CockpitPanelRoute = CockpitPanelRoute.None
    ) = CockpitPanelRouter(CockpitPanelUiState(route = initial))

    @Test
    fun `NavigateToBatchOverview opens batch overview and resets dismissed flag`() {
        val r = router()
        val state = r.reduce(CockpitPanelCommand.NavigateToBatchOverview)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
        assertFalse(state.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `CloseBatchOverview returns to None and keeps rail visible`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.CloseBatchOverview)

        assertIs<CockpitPanelRoute.None>(state.route)
        assertFalse(state.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `from Shooting route NavigateToBatchOverview opens batch overview`() {
        val r = router(CockpitPanelRoute.None)
        val state = r.reduce(CockpitPanelCommand.NavigateToBatchOverview)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
    }

    @Test
    fun `from BatchOverview NavigateToCropEdit transitions to CropEdit`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.NavigateToCropEdit)

        assertIs<CockpitPanelRoute.CropEdit>(state.route)
    }

    @Test
    fun `from BatchOverview NavigateToExport transitions to Export`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.NavigateToExport)

        assertIs<CockpitPanelRoute.Export>(state.route)
    }

    @Test
    fun `from BatchOverview AndroidBack returns to None`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.AndroidBack)

        assertIs<CockpitPanelRoute.None>(state.route)
    }

    @Test
    fun `from BatchOverview DismissAll closes batch overview`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.DismissAll)

        assertIs<CockpitPanelRoute.None>(state.route)
    }

    @Test
    fun `ToggleDocumentBatchOrganizer from None opens BatchOverview`() {
        val r = router()
        val state = r.reduce(CockpitPanelCommand.ToggleDocumentBatchOrganizer)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
    }

    @Test
    fun `ToggleDocumentBatchOrganizer from BatchOverview closes`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.ToggleDocumentBatchOrganizer)

        assertIs<CockpitPanelRoute.None>(state.route)
        assertTrue(state.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `CloseBatchOverview from non-BatchOverview is no-op`() {
        val r = router(CockpitPanelRoute.None)
        val state = r.reduce(CockpitPanelCommand.CloseBatchOverview)

        assertIs<CockpitPanelRoute.None>(state.route)
    }

    @Test
    fun `batch overview routing cycle Shooting-BatchOverview-Shooting`() {
        val r = router()

        // Shooting -> BatchOverview
        val overview = r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        assertIs<CockpitPanelRoute.BatchOverview>(overview.route)

        // BatchOverview -> Shooting (via CloseBatchOverview)
        val back = r.reduce(CockpitPanelCommand.CloseBatchOverview)
        assertIs<CockpitPanelRoute.None>(back.route)
        assertFalse(back.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `batch overview routing cycle Shooting-BatchOverview-CropEdit`() {
        val r = router()

        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        val cropEdit = r.reduce(CockpitPanelCommand.NavigateToCropEdit)

        assertIs<CockpitPanelRoute.CropEdit>(cropEdit.route)
    }

    @Test
    fun `batch overview routing cycle Shooting-BatchOverview-Export`() {
        val r = router()

        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        val export = r.reduce(CockpitPanelCommand.NavigateToExport)

        assertIs<CockpitPanelRoute.Export>(export.route)
    }
}
