package com.opencamera.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CockpitPanelRouterTest {

    @Test
    fun `ToggleColorLab opens from None`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.ToggleColorLab)

        assertEquals(CockpitPanelRoute.ColorLab, result.route)
        assertTrue(result.isFilterAdjustmentVisible)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `ToggleColorLab closes from ColorLab and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.ColorLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.ToggleColorLab)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `ToggleStyleLab opens from None`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleLab)

        assertEquals(CockpitPanelRoute.StyleLab, result.route)
        assertTrue(result.isFilterAdjustmentVisible)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `ToggleStyleLab closes from StyleLab and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.StyleLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleLab)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `DismissAll resets all state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
            selectedSettingsTab = SettingsTab.VIDEO,
            selectedWatermarkDetailTemplateId = "template-123",
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertEquals(SettingsTab.COMMON, result.selectedSettingsTab)
        assertNull(result.selectedWatermarkDetailTemplateId)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `SettingsBack from WATERMARK_DETAIL returns to WATERMARK_SELECTOR and clears template id`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
            selectedWatermarkDetailTemplateId = "template-123"
        )
        val result = nextState(initial, CockpitPanelCommand.SettingsBack)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR), result.route)
        assertNull(result.selectedWatermarkDetailTemplateId)
    }

    @Test
    fun `SettingsBack from PORTRAIT_LAB returns to ROOT`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB)
        )
        val result = nextState(initial, CockpitPanelCommand.SettingsBack)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), result.route)
    }

    @Test
    fun `SettingsBack from WATERMARK_SELECTOR returns to ROOT`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR)
        )
        val result = nextState(initial, CockpitPanelCommand.SettingsBack)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), result.route)
    }

    @Test
    fun `SettingsBack from ROOT stays at ROOT`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT)
        )
        val result = nextState(initial, CockpitPanelCommand.SettingsBack)

        assertEquals(initial, result)
    }

    @Test
    fun `AndroidBack from None returns unchanged state`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(initial, result)
    }

    @Test
    fun `AndroidBack from StyleLab closes and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.StyleLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `AndroidBack from ColorLab closes and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.ColorLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `AndroidBack from Settings ROOT closes settings and resets tab`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT),
            selectedSettingsTab = SettingsTab.VIDEO
        )
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertEquals(SettingsTab.COMMON, result.selectedSettingsTab)
    }

    @Test
    fun `AndroidBack from Settings WATERMARK_DETAIL returns to WATERMARK_SELECTOR`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
            selectedWatermarkDetailTemplateId = "template-123"
        )
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR), result.route)
        assertNull(result.selectedWatermarkDetailTemplateId)
    }

    @Test
    fun `OpenPortraitLab sets route to Settings PORTRAIT_LAB`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT),
            selectedSettingsTab = SettingsTab.PHOTO
        )
        val result = nextState(initial, CockpitPanelCommand.OpenPortraitLab)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB), result.route)
        assertEquals(SettingsTab.PHOTO, result.selectedSettingsTab)
    }

    @Test
    fun `OpenPortraitLab from None sets route to Settings PORTRAIT_LAB`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.OpenPortraitLab)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB), result.route)
    }

    @Test
    fun `OpenWatermarkSelector sets route to Settings WATERMARK_SELECTOR`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT),
            selectedSettingsTab = SettingsTab.PHOTO
        )
        val result = nextState(initial, CockpitPanelCommand.OpenWatermarkSelector)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR), result.route)
        assertNull(result.selectedWatermarkDetailTemplateId)
    }

    @Test
    fun `OpenWatermarkSelector clears previous template id`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
            selectedWatermarkDetailTemplateId = "old-template"
        )
        val result = nextState(initial, CockpitPanelCommand.OpenWatermarkSelector)

        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR), result.route)
        assertNull(result.selectedWatermarkDetailTemplateId)
    }

    @Test
    fun `Portrait lab back chain - PORTRAIT_LAB to ROOT then close`() {
        val atPortraitLab = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB),
            selectedSettingsTab = SettingsTab.PHOTO
        )
        val atRoot = nextState(atPortraitLab, CockpitPanelCommand.SettingsBack)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), atRoot.route)
        assertEquals(SettingsTab.PHOTO, atRoot.selectedSettingsTab)

        val closed = nextState(atRoot, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, closed.route)
        assertEquals(SettingsTab.COMMON, closed.selectedSettingsTab)
    }

    @Test
    fun `Watermark navigation chain - detail to selector to root to close`() {
        val atDetail = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_DETAIL),
            selectedWatermarkDetailTemplateId = "tpl-1"
        )
        val atSelector = nextState(atDetail, CockpitPanelCommand.SettingsBack)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.WATERMARK_SELECTOR), atSelector.route)
        assertNull(atSelector.selectedWatermarkDetailTemplateId)

        val atRoot = nextState(atSelector, CockpitPanelCommand.SettingsBack)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), atRoot.route)

        val closed = nextState(atRoot, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, closed.route)
    }

    @Test
    fun `SelectSettingsTab updates only the selected tab`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(),
            selectedSettingsTab = SettingsTab.COMMON
        )
        val result = nextState(initial, CockpitPanelCommand.SelectSettingsTab(SettingsTab.PHOTO))

        assertEquals(SettingsTab.PHOTO, result.selectedSettingsTab)
        assertEquals(initial.route, result.route)
        assertEquals(initial.selectedWatermarkDetailTemplateId, result.selectedWatermarkDetailTemplateId)
        assertEquals(initial.selectedFilterLabFamilyOverride, result.selectedFilterLabFamilyOverride)
        assertEquals(initial.isFilterAdjustmentVisible, result.isFilterAdjustmentVisible)
        assertEquals(initial.filterAdjustmentMode, result.filterAdjustmentMode)
    }

    // --- Transition continuity: one-active-route and route switching ---

    @Test
    fun `opening StyleLab while Settings is open replaces route`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT),
            selectedSettingsTab = SettingsTab.PHOTO
        )
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleLab)

        assertEquals(CockpitPanelRoute.StyleLab, result.route)
        assertTrue(result.isFilterAdjustmentVisible)
    }

    @Test
    fun `opening Settings while StyleLab is open replaces route`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.StyleLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO,
            isFilterAdjustmentVisible = true
        )
        val result = nextState(initial, CockpitPanelCommand.ToggleSettingsRoot)

        assertEquals(CockpitPanelRoute.Settings(), result.route)
    }

    @Test
    fun `opening ColorLab while StyleLab is open replaces route`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        val result = nextState(initial, CockpitPanelCommand.ToggleColorLab)

        assertEquals(CockpitPanelRoute.ColorLab, result.route)
    }

    @Test
    fun `AndroidBack from QuickBubble closes`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.QuickBubble)
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `AndroidBack from DevConsole closes`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.DevConsole)
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `quick repeated toggles maintain correct one-active-route`() {
        var state = CockpitPanelUiState()

        // Open Settings
        state = nextState(state, CockpitPanelCommand.ToggleSettingsRoot)
        assertEquals(CockpitPanelRoute.Settings(), state.route)

        // Quick toggle StyleLab (should replace Settings)
        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.StyleLab, state.route)

        // Quick toggle ColorLab (should replace StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleColorLab)
        assertEquals(CockpitPanelRoute.ColorLab, state.route)

        // DismissAll (should close everything)
        state = nextState(state, CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `repeated same toggle opens then closes`() {
        var state = CockpitPanelUiState()

        state = nextState(state, CockpitPanelCommand.ToggleQuickBubble)
        assertEquals(CockpitPanelRoute.QuickBubble, state.route)

        state = nextState(state, CockpitPanelCommand.ToggleQuickBubble)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `DismissAll from QuickBubble closes to None`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.QuickBubble)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    // --- Outside-dismiss coverage: DismissAll is the command fired by panelDismissScrim ---

    @Test
    fun `DismissAll from StyleLab closes to None and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.StyleLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.VIDEO,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `DismissAll from ColorLab closes to None and resets filter state`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.ColorLab,
            selectedFilterLabFamilyOverride = FilterLabFamily.HUMANISTIC,
            isFilterAdjustmentVisible = true,
            filterAdjustmentMode = FilterAdjustmentMode.ADVANCED
        )
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
        assertEquals(false, result.isFilterAdjustmentVisible)
        assertEquals(FilterAdjustmentMode.LIGHT, result.filterAdjustmentMode)
    }

    @Test
    fun `DismissAll from DevConsole closes to None`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.DevConsole)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `DismissAll from DocumentBatchOrganizer closes to None`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.DocumentBatchOrganizer)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `DismissAll from Settings closes to None and resets tab`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.Settings(SettingsSubpage.ROOT),
            selectedSettingsTab = SettingsTab.VIDEO
        )
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertEquals(SettingsTab.COMMON, result.selectedSettingsTab)
    }

    @Test
    fun `scrim dismiss path ToggleQuickBubble then DismissAll returns to None`() {
        var state = CockpitPanelUiState()

        state = nextState(state, CockpitPanelCommand.ToggleQuickBubble)
        assertEquals(CockpitPanelRoute.QuickBubble, state.route)
        assertTrue(state.route.isAnyPanelOpen)

        state = nextState(state, CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }
}
