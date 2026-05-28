package com.opencamera.app

import android.content.Context
import androidx.core.view.isVisible

internal class DevConsoleRenderer(
    private val context: Context,
    private val views: DevConsoleViews
) {
    fun renderVisibility(activePanelRoute: CockpitPanelRoute) {
        val isDevVisible = activePanelRoute is CockpitPanelRoute.DevConsole
        val wasVisible = views.panel.isVisible
        views.panel.isVisible = isDevVisible
        views.entry.alpha = if (isDevVisible) 1f else 0.86f
        if (isDevVisible && !wasVisible) {
            (views.panel.getChildAt(0) as? androidx.core.widget.NestedScrollView)?.scrollTo(0, 0)
        }
    }

    fun render(model: DevLogRenderModel?) {
        if (model == null) return
        views.title.text = model.title
        views.summary.text = model.summaryText
        views.summary.isVisible = model.summaryText.isNotBlank()
        views.content.text = model.content
        views.tabKey.isEnabled = model.selectedTab != DevLogTab.KEY
        views.tabCore.isEnabled = model.selectedTab != DevLogTab.CORE
        views.tabError.isEnabled = model.selectedTab != DevLogTab.ERROR
        views.tabAll.isEnabled = model.selectedTab != DevLogTab.ALL
        val activeAlpha = 1f
        val inactiveAlpha = 0.84f
        views.tabKey.alpha = if (model.selectedTab == DevLogTab.KEY) activeAlpha else inactiveAlpha
        views.tabCore.alpha = if (model.selectedTab == DevLogTab.CORE) activeAlpha else inactiveAlpha
        views.tabError.alpha = if (model.selectedTab == DevLogTab.ERROR) activeAlpha else inactiveAlpha
        views.tabAll.alpha = if (model.selectedTab == DevLogTab.ALL) activeAlpha else inactiveAlpha

        val hasStorage = model.storageUsedDisplay.isNotBlank()
        views.storageInfo.isVisible = hasStorage
        if (hasStorage) {
            views.storageInfo.text = context.getString(
                R.string.dev_storage_format, model.storageUsedDisplay, model.storageCapacityDisplay
            )
        }
    }
}
