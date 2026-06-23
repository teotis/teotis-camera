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

    // --- CloseDocumentBatchOrganizer: explicit dismiss from organizer ---

    @Test
    fun `CloseDocumentBatchOrganizer closes from DocumentBatchOrganizer`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.DocumentBatchOrganizer)
        val result = nextState(initial, CockpitPanelCommand.CloseDocumentBatchOrganizer)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertTrue(result.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `ToggleDocumentBatchOrganizer stays closed after explicit dismiss`() {
        val initial = CockpitPanelUiState(isDocumentBatchOrganizerDismissed = true)
        val result = nextState(initial, CockpitPanelCommand.ToggleDocumentBatchOrganizer)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertTrue(result.isDocumentBatchOrganizerDismissed)
    }

    @Test
    fun `DocumentBatchCaptureTriggered allows organizer after next shutter action`() {
        val initial = CockpitPanelUiState(isDocumentBatchOrganizerDismissed = true)
        val armed = nextState(initial, CockpitPanelCommand.DocumentBatchCaptureTriggered)
        val result = nextState(armed, CockpitPanelCommand.ToggleDocumentBatchOrganizer)

        assertFalse(armed.isDocumentBatchOrganizerDismissed)
        assertEquals(CockpitPanelRoute.BatchOverview, result.route)
    }

    @Test
    fun `CloseDocumentBatchOrganizer closes from None (no-op safe)`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.CloseDocumentBatchOrganizer)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `CloseDocumentBatchOrganizer from Settings stays Settings`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.Settings())
        val result = nextState(initial, CockpitPanelCommand.CloseDocumentBatchOrganizer)

        assertEquals(CockpitPanelRoute.Settings(), result.route)
    }

    // --- StyleStrip routing tests ---

    @Test
    fun `ToggleStyleStrip opens from None`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleStrip)

        assertEquals(CockpitPanelRoute.StyleStrip, result.route)
    }

    @Test
    fun `ToggleStyleStrip closes from StyleStrip`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleStrip)
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleStrip)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `ToggleStyleStrip closes when switching from another panel`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleStrip)

        assertEquals(CockpitPanelRoute.StyleStrip, result.route)
    }

    @Test
    fun `AndroidBack closes StyleStrip`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleStrip)
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `DismissAll closes StyleStrip`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleStrip)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    // --- CheckInStylePanel routing tests ---

    @Test
    fun `ToggleCheckInStylePanel opens from None`() {
        val initial = CockpitPanelUiState()
        val result = nextState(initial, CockpitPanelCommand.ToggleCheckInStylePanel)

        assertEquals(CockpitPanelRoute.CheckInStylePanel, result.route)
        assertNull(result.selectedFilterLabFamilyOverride)
    }

    @Test
    fun `ToggleCheckInStylePanel closes from CheckInStylePanel`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.ToggleCheckInStylePanel)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `ToggleCheckInStylePanel replaces StyleLab`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        val result = nextState(initial, CockpitPanelCommand.ToggleCheckInStylePanel)

        assertEquals(CockpitPanelRoute.CheckInStylePanel, result.route)
    }

    @Test
    fun `SelectCheckInScenario is no-op for state`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.SelectCheckInScenario("clarity"))

        assertEquals(initial, result)
    }

    @Test
    fun `SelectCheckInStyle is no-op for state`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.SelectCheckInStyle("portrait-blue"))

        assertEquals(initial, result)
    }

    @Test
    fun `AndroidBack from CheckInStylePanel closes`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.AndroidBack)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `DismissAll from CheckInStylePanel closes`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
    }

    @Test
    fun `opening CheckInStylePanel while Settings is open replaces route`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.Settings())
        val result = nextState(initial, CockpitPanelCommand.ToggleCheckInStylePanel)

        assertEquals(CockpitPanelRoute.CheckInStylePanel, result.route)
    }

    @Test
    fun `mode switch from CheckInStylePanel closes panel via DismissAll`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.CheckInStylePanel)
        val result = nextState(initial, CockpitPanelCommand.DismissAll)

        assertEquals(CockpitPanelRoute.None, result.route)
        assertFalse(result.route.isAnyPanelOpen)
    }

    // --- 05-interaction-regression: Style card rail interaction tests ---

    @Test
    fun `ToggleStyleLab replaces CheckInStylePanel (mode switch re-entry)`() {
        val initial = CockpitPanelUiState(
            route = CockpitPanelRoute.CheckInStylePanel,
            selectedFilterLabFamilyOverride = FilterLabFamily.PHOTO
        )
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleLab)

        assertEquals(CockpitPanelRoute.StyleLab, result.route)
        assertTrue(result.isFilterAdjustmentVisible)
    }

    @Test
    fun `ToggleStyleStrip from StyleLab replaces route to StyleStrip`() {
        val initial = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        val result = nextState(initial, CockpitPanelCommand.ToggleStyleStrip)

        assertEquals(CockpitPanelRoute.StyleStrip, result.route)
    }

    @Test
    fun `StyleLab toggle open then AndroidBack returns to None`() {
        var state = CockpitPanelUiState()
        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.StyleLab, state.route)
        assertTrue(state.route.isAnyPanelOpen)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }

    @Test
    fun `StyleStrip toggle open then AndroidBack returns to None`() {
        var state = CockpitPanelUiState()
        state = nextState(state, CockpitPanelCommand.ToggleStyleStrip)
        assertEquals(CockpitPanelRoute.StyleStrip, state.route)
        assertTrue(state.route.isAnyPanelOpen)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }

    @Test
    fun `style preset card rail is visible only for StyleLab route`() {
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.None))
        assertTrue(shouldShowStylePresetCardRail(CockpitPanelRoute.StyleLab))
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.StyleStrip))
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.CheckInStylePanel))
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.ColorLab))
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.Settings()))
        assertFalse(shouldShowStylePresetCardRail(CockpitPanelRoute.QuickBubble))
    }

    @Test
    fun `filter lab panel is not visible for StyleLab card rail route`() {
        assertFalse(shouldShowFilterLabPanel(CockpitPanelRoute.StyleLab))
        assertTrue(shouldShowFilterLabPanel(CockpitPanelRoute.ColorLab))
        assertTrue(shouldShowFilterLabPanel(CockpitPanelRoute.CheckInStylePanel))
        assertFalse(shouldShowFilterLabPanel(CockpitPanelRoute.StyleStrip))
        assertFalse(shouldShowFilterLabPanel(CockpitPanelRoute.None))
    }

    @Test
    fun `StyleLab card rail route does not run legacy panel transition`() {
        assertFalse(shouldRunPanelTransition(CockpitPanelRoute.StyleLab))
        assertFalse(shouldRunPanelTransition(CockpitPanelRoute.StyleStrip))
        assertTrue(shouldRunPanelTransition(CockpitPanelRoute.ColorLab))
        assertTrue(shouldRunPanelTransition(CockpitPanelRoute.CheckInStylePanel))
        assertTrue(shouldRunPanelTransition(CockpitPanelRoute.DevConsole))
    }

    @Test
    fun `CheckInStylePanel toggle open then AndroidBack returns to None`() {
        var state = CockpitPanelUiState()
        state = nextState(state, CockpitPanelCommand.ToggleCheckInStylePanel)
        assertEquals(CockpitPanelRoute.CheckInStylePanel, state.route)
        assertTrue(state.route.isAnyPanelOpen)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }

    @Test
    fun `full style interaction sequence - open StyleLab then dismiss via scrim`() {
        var state = CockpitPanelUiState()

        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.StyleLab, state.route)
        assertTrue(state.isFilterAdjustmentVisible)

        // Scrim tap = DismissAll
        state = nextState(state, CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.isFilterAdjustmentVisible)
    }

    @Test
    fun `style route switching - rapid open close open preserves correct state`() {
        var state = CockpitPanelUiState()

        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.StyleLab, state.route)

        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.None, state.route)

        state = nextState(state, CockpitPanelCommand.ToggleStyleLab)
        assertEquals(CockpitPanelRoute.StyleLab, state.route)
        assertTrue(state.isFilterAdjustmentVisible)
    }

    @Test
    fun `style route does not trap mode track - mode switch closes CheckInStylePanel`() {
        // Simulates: open CheckInStylePanel, then user taps mode track which calls DismissAll
        var state = CockpitPanelUiState()
        state = nextState(state, CockpitPanelCommand.ToggleCheckInStylePanel)
        assertEquals(CockpitPanelRoute.CheckInStylePanel, state.route)

        state = nextState(state, CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }

    @Test
    fun `style route does not trap mode track - mode switch closes StyleStrip`() {
        var state = CockpitPanelUiState()
        state = nextState(state, CockpitPanelCommand.ToggleStyleStrip)
        assertEquals(CockpitPanelRoute.StyleStrip, state.route)

        state = nextState(state, CockpitPanelCommand.DismissAll)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertFalse(state.route.isAnyPanelOpen)
    }

    @Test
    fun `quick panel open from StyleLab replaces route`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleQuickBubble)
        assertEquals(CockpitPanelRoute.QuickBubble, state.route)
    }

    @Test
    fun `DevConsole open from StyleLab replaces route`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleDevConsole)
        assertEquals(CockpitPanelRoute.DevConsole, state.route)
    }

    @Test
    fun `settings open from StyleLab replaces route`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleSettingsRoot)
        assertEquals(CockpitPanelRoute.Settings(), state.route)
    }

    @Test
    fun `ColorLab open from StyleLab replaces route`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleColorLab)
        assertEquals(CockpitPanelRoute.ColorLab, state.route)
    }

    @Test
    fun `DocumentBatchOrganizer from StyleLab replaces route`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleDocumentBatchOrganizer)
        assertEquals(CockpitPanelRoute.BatchOverview, state.route)
    }

    @Test
    fun `AndroidBack from DocumentBatchOrganizer closes`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.DocumentBatchOrganizer)
        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `CloseDocumentBatchOrganizer from DocumentBatchOrganizer then AndroidBack no-op`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.DocumentBatchOrganizer)
        state = nextState(state, CockpitPanelCommand.CloseDocumentBatchOrganizer)
        assertEquals(CockpitPanelRoute.None, state.route)
        assertTrue(state.isDocumentBatchOrganizerDismissed)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `existing settings routes not affected by style changes`() {
        // Ensure Settings back chain still works correctly after style interaction
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleSettingsRoot)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), state.route)

        state = nextState(state, CockpitPanelCommand.OpenPortraitLab)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.PORTRAIT_LAB), state.route)

        state = nextState(state, CockpitPanelCommand.SettingsBack)
        assertEquals(CockpitPanelRoute.Settings(SettingsSubpage.ROOT), state.route)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `existing Color Lab routes not affected by style changes`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleColorLab)
        assertEquals(CockpitPanelRoute.ColorLab, state.route)

        state = nextState(state, CockpitPanelCommand.SelectFilterFamily(FilterLabFamily.VIDEO))
        assertEquals(FilterLabFamily.VIDEO, state.selectedFilterLabFamilyOverride)
        assertTrue(state.isFilterAdjustmentVisible)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `existing quick panel route not affected by style changes`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleQuickBubble)
        assertEquals(CockpitPanelRoute.QuickBubble, state.route)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `existing DevConsole route not affected by style changes`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleDevConsole)
        assertEquals(CockpitPanelRoute.DevConsole, state.route)

        state = nextState(state, CockpitPanelCommand.AndroidBack)
        assertEquals(CockpitPanelRoute.None, state.route)
    }

    @Test
    fun `existing document batch route not affected by style changes`() {
        var state = CockpitPanelUiState(route = CockpitPanelRoute.StyleLab)
        state = nextState(state, CockpitPanelCommand.ToggleDocumentBatchOrganizer)
        assertEquals(CockpitPanelRoute.BatchOverview, state.route)

        state = nextState(state, CockpitPanelCommand.CloseDocumentBatchOrganizer)
        assertEquals(CockpitPanelRoute.None, state.route)
    }
}
