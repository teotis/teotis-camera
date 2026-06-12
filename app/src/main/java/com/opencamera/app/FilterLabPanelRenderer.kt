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
    private val appText = com.opencamera.app.i18n.AppTextResolver(context)

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

        views.resetDefaults.isVisible = model.resetStyleAction != null
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
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (item.isSelected) 1f else 0.9f
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.selectionList.childCount == 0) 0 else 6.dp
            }

            if (item.isSelected) {
                // Selected: vertical layout with compact padding
                card.orientation = LinearLayout.VERTICAL
                card.setPadding(12.dp, 10.dp, 12.dp, 10.dp)

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
                    setPadding(0, 4.dp, 0, 0)
                }
                card.addView(supporting)

                if (item.adjustButtonLabel != null) {
                    val adjustButton = Button(
                        context,
                        null,
                        0,
                        R.style.Widget_OpenCamera_CompactButton
                    ).apply {
                        text = item.adjustButtonLabel
                        isAllCaps = false
                        isEnabled = model.editingEnabled
                        setOnClickListener { onOpenAdjustment(model.adjustControl) }
                    }
                    val adjustParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        topMargin = 8.dp
                    }
                    card.addView(adjustButton, adjustParams)
                }
            } else {
                // Unselected: compact horizontal row — title + select button
                card.orientation = LinearLayout.HORIZONTAL
                card.setPadding(10.dp, 8.dp, 8.dp, 8.dp)
                card.gravity = android.view.Gravity.CENTER_VERTICAL

                val title = TextView(context).apply {
                    text = item.title
                    textSize = 14f
                    setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
                    maxLines = 1
                    ellipsize = android.text.TextUtils.TruncateAt.END
                    layoutParams = LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
                    )
                }
                card.addView(title)

                val selectButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = context.getString(R.string.button_use_this_look)
                    isAllCaps = false
                    textSize = 12f
                    isEnabled = model.editingEnabled && item.nextAction != null
                    setOnClickListener {
                        item.nextAction?.let(onSelectFilter)
                    }
                }
                val btnParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 8.dp
                }
                card.addView(selectButton, btnParams)
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

    fun renderCheckInStylePanel(
        model: CheckInStylePanelRenderModel,
        onSelectScenario: (PersistedSettingsAction) -> Unit,
        onSelectStyle: (PersistedSettingsAction) -> Unit
    ) {
        views.headline.text = model.headline
        views.supportingText.text = model.scenarioSummary
        views.heroSummary.isVisible = false
        views.currentSummary.isVisible = false
        views.editingHint.text = appText.checkInEditingHint(model.editingEnabled)
        views.footer.isVisible = false

        views.photoTab.isVisible = false
        views.humanisticTab.isVisible = false
        views.portraitTab.isVisible = false
        views.videoTab.isVisible = false
        views.sectionPaletteTitle.isVisible = false
        views.adjustmentPanel.isVisible = false
        views.resetDefaults.isVisible = false
        views.saveCustom.isVisible = false

        views.selectionList.removeAllViews()
        views.sectionFiltersTitle.text = appText.checkInSceneTitle()
        views.sectionFiltersTitle.isVisible = true
        views.selectionCard.isVisible = true

        model.scenarioCards.forEach { card ->
            val cardView = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (card.isActive) 1f else 0.9f
                setPadding(14.dp, 14.dp, 14.dp, 14.dp)
            }
            val cardParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.selectionList.childCount == 0) 0 else 8.dp
            }

            val titleRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
            }
            val title = TextView(context).apply {
                text = if (card.isActive) "● ${card.label}" else card.label
                textSize = 15f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
            }
            titleRow.addView(title)
            if (card.isDegraded && card.degradedLabel != null) {
                val badge = TextView(context).apply {
                    text = card.degradedLabel
                    textSize = 11f
                    setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                    setPadding(8.dp, 0, 0, 0)
                }
                titleRow.addView(badge)
            }
            cardView.addView(titleRow)

            val desc = TextView(context).apply {
                text = card.description
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 6.dp, 0, 0)
            }
            cardView.addView(desc)

            if (!card.isActive && card.selectAction != null) {
                val selectButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = appText.checkInSelectScene()
                    isAllCaps = false
                    isEnabled = model.editingEnabled
                    setOnClickListener { onSelectScenario(card.selectAction) }
                }
                val btnParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
                cardView.addView(selectButton, btnParams)
            }
            views.selectionList.addView(cardView, cardParams)
        }

        if (model.styleItems.isNotEmpty()) {
            val styleTitle = TextView(context).apply {
                text = appText.checkInStyleTitle()
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 16.dp, 0, 8.dp)
            }
            views.selectionList.addView(styleTitle)
        }

        model.styleItems.forEach { item ->
            val itemRow = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 4.dp
                }
                setPadding(8.dp, 8.dp, 8.dp, 8.dp)
                setBackgroundResource(R.drawable.bg_settings_card)
            }
            val label = TextView(context).apply {
                text = if (item.isSelected) "● ${item.title}" else item.title
                textSize = 14f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            itemRow.addView(label)

            if (!item.isSelected && item.selectAction != null) {
                val btn = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = appText.checkInUseStyle()
                    isAllCaps = false
                    textSize = 12f
                    isEnabled = model.editingEnabled
                    setOnClickListener { onSelectStyle(item.selectAction) }
                }
                itemRow.addView(btn)
            }
            views.selectionList.addView(itemRow)
        }

        if (model.compositionGuidance.isNotEmpty()) {
            val guidanceView = TextView(context).apply {
                text = model.compositionGuidance
                textSize = 13f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 16.dp, 0, 8.dp)
            }
            views.selectionList.addView(guidanceView)
        }

        if (model.degradationLabel != null) {
            val degradationView = TextView(context).apply {
                text = model.degradationLabel
                textSize = 12f
                setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                setPadding(0, 8.dp, 0, 0)
            }
            views.selectionList.addView(degradationView)
        }
    }
}
