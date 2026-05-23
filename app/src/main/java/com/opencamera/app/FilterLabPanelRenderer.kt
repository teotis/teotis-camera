package com.opencamera.app

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.opencamera.core.settings.PersistedSettingsAction

internal class FilterLabPanelRenderer(
    private val context: Context,
    private val views: FilterLabViews,
    private val onOpenAdjustment: (FilterLabAdjustRenderModel?) -> Unit = {},
    private val onSelectFilter: (PersistedSettingsAction) -> Unit = {},
    private val isFilterAdjustmentVisible: () -> Boolean = { true }
) {
    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    fun renderPage(model: FilterLabPageRenderModel) {
        views.headline.text = model.headline
        views.supportingText.text = model.supportingText
        views.heroSummary.text = model.heroSummary
        views.heroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.currentSummary.text = model.currentFilterSummary
        views.editingHint.text = model.editingHint
        views.footer.text = model.footer

        val tabsVisible = model.showFamilyTabs
        views.photoTab.isVisible = tabsVisible
        views.humanisticTab.isVisible = tabsVisible
        views.portraitTab.isVisible = tabsVisible
        views.videoTab.isVisible = tabsVisible
        if (tabsVisible) {
            renderTab(views.photoTab, model.photoTab)
            renderTab(views.humanisticTab, model.humanisticTab)
            renderTab(views.portraitTab, model.portraitTab)
            renderTab(views.videoTab, model.videoTab)
        }

        if (model.showFilterItems) {
            renderFilterSelectionList(model, onOpenAdjustment, onSelectFilter, isFilterAdjustmentVisible())
            renderSaveCustomControl(model.saveCustomControl, model.editingEnabled)
            views.selectionCard.isVisible = true
            views.currentSummary.isVisible = true
            views.sectionFiltersTitle.isVisible = true
        } else {
            views.selectionList.removeAllViews()
            views.selectionCard.isVisible = false
            views.currentSummary.isVisible = false
            views.sectionFiltersTitle.isVisible = false
            views.saveCustom.isVisible = false
        }

        views.sectionPaletteTitle.isVisible = model.showAdjustmentPanel
        if (model.showAdjustmentPanel) {
            renderAdjustmentPanel(
                model.adjustmentPanel,
                model.editingEnabled,
                model.showAdvancedControls,
                model.showModeToggle
            )
        } else {
            views.adjustmentPanel.isVisible = false
        }
    }

    fun renderTab(button: Button, model: FilterLabTabRenderModel) {
        button.text = model.label
        button.isSingleLine = true
        button.maxLines = 1
        button.ellipsize = android.text.TextUtils.TruncateAt.END
        button.isEnabled = !model.isSelected
        button.alpha = if (model.isSelected) 1f else 0.84f
    }

    fun renderSaveCustomControl(model: FilterLabSaveCustomRenderModel, editingEnabled: Boolean) {
        views.saveCustom.text = model.buttonLabel
        views.saveCustom.isEnabled = editingEnabled && model.isEnabled
    }

    fun renderFilterSelectionList(
        model: FilterLabPageRenderModel,
        onOpenAdjustment: (FilterLabAdjustRenderModel?) -> Unit,
        onSelectFilter: (PersistedSettingsAction) -> Unit,
        isFilterAdjustmentVisible: Boolean
    ) {
        views.selectionList.removeAllViews()
        model.filterItems.forEach { item ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (item.isSelected) 1f else 0.9f
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.selectionList.childCount == 0) 0 else 8.dp
            }

            val title = TextView(context).apply {
                text = item.title
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
            }
            card.addView(title)

            val supporting = TextView(context).apply {
                text = item.supportingText
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 6.dp, 0, 0)
            }
            card.addView(supporting)

            if (item.isSelected) {
                val adjustButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = item.adjustButtonLabel
                    isAllCaps = false
                    isEnabled = model.editingEnabled && item.adjustButtonLabel != null
                    setOnClickListener { onOpenAdjustment(model.adjustControl) }
                }
                val adjustParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(adjustButton, adjustParams)
            } else {
                val selectButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = context.getString(R.string.button_use_this_look)
                    isAllCaps = false
                    isEnabled = model.editingEnabled && item.nextAction != null
                    setOnClickListener {
                        item.nextAction?.let(onSelectFilter)
                    }
                }
                val selectParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                card.addView(selectButton, selectParams)
            }
            views.selectionList.addView(card, params)
        }
    }

    fun renderAdjustmentPanel(
        model: FilterAdjustmentPanelRenderModel,
        editingEnabled: Boolean,
        showAdvancedControls: Boolean = true,
        showModeToggle: Boolean = true
    ) {
        views.adjustmentPanel.isVisible = model.isVisible
        views.modeToggle.isVisible = showModeToggle
        if (showAdvancedControls) {
            views.modeToggle.text = model.modeToggleLabel
            views.modeToggle.isEnabled = editingEnabled && model.selectedProfileId != null
        } else {
            views.modeToggle.text = context.getString(R.string.button_color_lab_reset)
            views.modeToggle.isEnabled = true
        }
        views.paletteSummary.text = listOf(
            model.selectedProfileLabel,
            model.lightPalette.summary
        ).filter { value -> value.isNotBlank() }.joinToString(separator = "\n")
        views.paletteHint.text = model.lightPalette.supportingText
        views.paletteSurface.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        views.paletteHint.isVisible = model.mode == FilterAdjustmentMode.LIGHT
        views.advancedTitle.isVisible = showAdvancedControls
        views.advancedControls.isVisible = showAdvancedControls && model.mode == FilterAdjustmentMode.ADVANCED
        views.advancedExposure.text = model.advancedControls.buttonLabel(FilterAdvancedControl.EXPOSURE)
        views.advancedSoftGlow.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SOFT_GLOW)
        views.advancedHalo.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HALO)
        views.advancedGrain.text = model.advancedControls.buttonLabel(FilterAdvancedControl.GRAIN)
        views.advancedSharpness.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHARPNESS)
        views.advancedVignette.text = model.advancedControls.buttonLabel(FilterAdvancedControl.VIGNETTE)
        views.advancedHighlights.text = model.advancedControls.buttonLabel(FilterAdvancedControl.HIGHLIGHTS)
        views.advancedShadows.text = model.advancedControls.buttonLabel(FilterAdvancedControl.SHADOWS)
        views.advancedWarmBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.WARM_BOOST)
        views.advancedCoolBoost.text = model.advancedControls.buttonLabel(FilterAdvancedControl.COOL_BOOST)
        views.advancedTemperatureShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TEMPERATURE_SHIFT)
        views.advancedTintShift.text =
            model.advancedControls.buttonLabel(FilterAdvancedControl.TINT_SHIFT)
        val advancedButtons = listOf(
            views.advancedExposure,
            views.advancedSoftGlow,
            views.advancedHalo,
            views.advancedGrain,
            views.advancedSharpness,
            views.advancedVignette,
            views.advancedHighlights,
            views.advancedShadows,
            views.advancedWarmBoost,
            views.advancedCoolBoost,
            views.advancedTemperatureShift,
            views.advancedTintShift
        )
        advancedButtons.forEach { button ->
            button.isEnabled = editingEnabled && model.selectedProfileId != null
        }
    }

    private fun List<FilterAdvancedControlRenderModel>.buttonLabel(
        control: FilterAdvancedControl
    ): String {
        return first { item -> item.control == control }.buttonLabel
    }
}
