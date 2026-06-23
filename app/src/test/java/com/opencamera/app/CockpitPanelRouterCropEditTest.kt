package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CockpitPanelRouterCropEditTest {

    private fun router(
        initial: CockpitPanelRoute = CockpitPanelRoute.None
    ) = CockpitPanelRouter(CockpitPanelUiState(route = initial))

    @Test
    fun `SelectCropEditItem sets selectedCropEditItemId`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        val state = r.reduce(CockpitPanelCommand.SelectCropEditItem("item-42"))

        assertEquals("item-42", state.selectedCropEditItemId)
    }

    @Test
    fun `NavigateToCropEdit from BatchOverview opens CropEdit`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.NavigateToCropEdit)

        assertIs<CockpitPanelRoute.CropEdit>(state.route)
        assertEquals("item-1", state.selectedCropEditItemId)
    }

    @Test
    fun `CloseCropEdit returns to BatchOverview and clears itemId`() {
        val r = router(CockpitPanelRoute.CropEdit)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.CloseCropEdit)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
        assertNull(state.selectedCropEditItemId)
    }

    @Test
    fun `AndroidBack from CropEdit returns to BatchOverview`() {
        val r = router(CockpitPanelRoute.CropEdit)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.AndroidBack)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
        assertNull(state.selectedCropEditItemId)
    }

    @Test
    fun `DismissAll from CropEdit clears itemId`() {
        val r = router(CockpitPanelRoute.CropEdit)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.DismissAll)

        assertIs<CockpitPanelRoute.None>(state.route)
        assertNull(state.selectedCropEditItemId)
    }

    @Test
    fun `full cycle BatchOverview - SelectItem - CropEdit - Confirm - BatchOverview`() {
        val r = router()

        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-3"))
        val cropEdit = r.reduce(CockpitPanelCommand.NavigateToCropEdit)
        assertIs<CockpitPanelRoute.CropEdit>(cropEdit.route)
        assertEquals("item-3", cropEdit.selectedCropEditItemId)

        val confirmed = r.reduce(CockpitPanelCommand.CloseCropEdit)
        assertIs<CockpitPanelRoute.BatchOverview>(confirmed.route)
        assertNull(confirmed.selectedCropEditItemId)
    }

    @Test
    fun `full cycle BatchOverview - SelectItem - CropEdit - Cancel - BatchOverview`() {
        val r = router()

        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-5"))
        r.reduce(CockpitPanelCommand.NavigateToCropEdit)
        val cancelled = r.reduce(CockpitPanelCommand.CloseCropEdit)

        assertIs<CockpitPanelRoute.BatchOverview>(cancelled.route)
        assertNull(cancelled.selectedCropEditItemId)
    }

    @Test
    fun `full cycle Shooting - BatchOverview - CropEdit - Back - BatchOverview`() {
        val r = router()

        r.reduce(CockpitPanelCommand.NavigateToBatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-2"))
        r.reduce(CockpitPanelCommand.NavigateToCropEdit)
        val backResult = r.reduce(CockpitPanelCommand.AndroidBack)

        assertIs<CockpitPanelRoute.BatchOverview>(backResult.route)
        assertNull(backResult.selectedCropEditItemId)
    }

    @Test
    fun `NavigateToBatchOverview clears cropEditItemId`() {
        val r = router(CockpitPanelRoute.CropEdit)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.NavigateToBatchOverview)

        assertIs<CockpitPanelRoute.BatchOverview>(state.route)
        assertNull(state.selectedCropEditItemId)
    }

    @Test
    fun `CloseBatchOverview clears cropEditItemId`() {
        val r = router(CockpitPanelRoute.BatchOverview)
        r.reduce(CockpitPanelCommand.SelectCropEditItem("item-1"))
        val state = r.reduce(CockpitPanelCommand.CloseBatchOverview)

        assertIs<CockpitPanelRoute.None>(state.route)
        assertNull(state.selectedCropEditItemId)
    }
}
