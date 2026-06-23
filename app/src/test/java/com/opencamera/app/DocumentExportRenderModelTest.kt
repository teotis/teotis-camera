package com.opencamera.app

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DocumentExportRenderModelTest {

    private val text = object : TestAppTextResolver() {
        override fun documentExportProgress(current: Int, total: Int): String = "Exporting $current/$total pages"
    }

    private fun panelState(
        route: CockpitPanelRoute = CockpitPanelRoute.None,
        exportState: ExportState? = null
    ) = CockpitPanelUiState(route = route, exportState = exportState)

    @Test
    fun `hidden when route is not Export`() {
        val model = exportRenderModel(panelState(), text)
        assertFalse(model.visible)
    }

    @Test
    fun `hidden when route is BatchOverview`() {
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.BatchOverview), text)
        assertFalse(model.visible)
    }

    @Test
    fun `visible with null export state shows title and progress`() {
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export), text)
        assertTrue(model.visible)
        assertEquals("Export Batch", model.titleText)
        assertEquals("Exporting 0/0 pages", model.progressText)
    }

    @Test
    fun `in progress shows correct progress text`() {
        val state = ExportState.InProgress(currentPage = 3, totalPages = 10)
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertTrue(model.visible)
        assertEquals("Exporting 3/10 pages", model.progressText)
        assertFalse(model.showReturnButton)
        assertFalse(model.showRetryButton)
    }

    @Test
    fun `in progress hides return and retry buttons`() {
        val state = ExportState.InProgress(currentPage = 1, totalPages = 5)
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertFalse(model.showReturnButton)
        assertFalse(model.showRetryButton)
    }

    @Test
    fun `success shows return button and hides cancel`() {
        val state = ExportState.Success(totalPages = 7)
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertTrue(model.visible)
        assertTrue(model.showReturnButton)
        assertEquals("Export success, 7 pages", model.titleText)
        assertFalse(model.showRetryButton)
    }

    @Test
    fun `success hides progress text`() {
        val state = ExportState.Success(totalPages = 3)
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertEquals("", model.progressText)
    }

    @Test
    fun `failure shows error state with retry`() {
        val state = ExportState.Failed(errorMessage = "IO error")
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertTrue(model.visible)
        assertTrue(model.isError)
        assertTrue(model.showRetryButton)
        assertTrue(model.showReturnButton)
        assertEquals("Export failed", model.titleText)
        assertEquals("IO error", model.progressText)
    }

    @Test
    fun `failure has both retry and return labels`() {
        val state = ExportState.Failed(errorMessage = "err")
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertEquals("Retry", model.retryLabel)
        assertEquals("Return to Shooting", model.returnLabel)
    }

    @Test
    fun `return label is set on in-progress state`() {
        val state = ExportState.InProgress(currentPage = 0, totalPages = 1)
        val model = exportRenderModel(panelState(route = CockpitPanelRoute.Export, exportState = state), text)
        assertEquals("Return to Shooting", model.returnLabel)
    }

    @Test
    fun `visible when route is Export even with null state`() {
        val model = exportRenderModel(
            panelState(route = CockpitPanelRoute.Export, exportState = null),
            text
        )
        assertTrue(model.visible)
    }
}
