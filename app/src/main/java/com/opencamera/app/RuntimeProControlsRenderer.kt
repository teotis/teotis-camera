package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible

internal class RuntimeProControlsRenderer(
    private val context: Context,
    private val views: RuntimeProControlsViews,
    private val onApplyControl: (FeatureCatalogControlRenderModel?) -> Unit
) {
    private var lastModel: RuntimeProControlsRenderModel? = null
    private var selectedControlId: RuntimeProControlId = RuntimeProControlId.ISO

    fun render(model: RuntimeProControlsRenderModel) {
        views.overlay.isVisible = model.isVisible
        if (!model.isVisible) {
            lastModel = null
            return
        }

        val selected = model.primaryControls.firstOrNull {
            it.id == selectedControlId && it.isSelectable
        } ?: model.primaryControls.firstOrNull(RuntimeProControlSpec::isSelectable)
        selected?.let { selectedControlId = it.id }

        views.statusLeft.text = "PRO  ·  ${model.rawControl.value}"
        views.statusRight.text = buildString {
            append("ISO ")
            append(model.isoControl.value)
            append("   S ")
            append(model.shutterControl.value)
            append("   EV ")
            append(model.exposureControl.value)
        }
        val supportDetail = buildList {
            model.rawControl.supportLabel
                ?.takeIf { model.rawControl.availability != SettingsControlAvailability.SUPPORTED }
                ?.let(::add)
            selected?.supportLabel?.let(::add)
        }.distinct().joinToString(separator = " · ")
        views.statusDetail.isVisible = supportDetail.isNotEmpty()
        views.statusDetail.text = supportDetail
        views.statusDetail.setTextColor(
            ContextCompat.getColor(
                context,
                if (selected?.availability == SettingsControlAvailability.SUPPORTED) {
                    R.color.oc_text_secondary
                } else {
                    R.color.oc_text_muted
                }
            )
        )

        if (model != lastModel) {
            renderRail(model)
            lastModel = model
        } else {
            updateRailSelection(model)
        }
        if (selected == null) {
            views.scale.isVisible = false
        } else {
            views.scale.isVisible = true
            renderScale(selected)
        }
    }

    private fun renderRail(model: RuntimeProControlsRenderModel) {
        views.rail.removeAllViews()
        model.primaryControls.forEach { control ->
            views.rail.addView(
                Button(context).apply {
                    tag = control.id
                    background = ColorDrawable(android.graphics.Color.TRANSPARENT)
                    isAllCaps = false
                    includeFontPadding = false
                    minWidth = 0
                    minHeight = 0
                    gravity = Gravity.CENTER
                    setPadding(2.dp, 0, 2.dp, 0)
                    contentDescription = buildString {
                        append(control.railLabel)
                        append(' ')
                        append(control.value)
                        control.supportLabel?.let {
                            append(' ')
                            append(it)
                        }
                    }
                    isEnabled = control.isSelectable
                    if (control.isSelectable) {
                        setOnClickListener {
                            selectedControlId = control.id
                            updateRailSelection(model)
                            renderScale(control)
                        }
                    }
                },
                LinearLayout.LayoutParams(52.dp, 42.dp)
            )
        }
        updateRailSelection(model)
    }

    private fun updateRailSelection(model: RuntimeProControlsRenderModel) {
        for (index in 0 until views.rail.childCount) {
            val button = views.rail.getChildAt(index) as? Button ?: continue
            val id = button.tag as? RuntimeProControlId ?: continue
            val control = model.primaryControls.firstOrNull { it.id == id } ?: continue
            val isSelected = id == selectedControlId && control.isSelectable
            button.text = when {
                isSelected -> "• ${control.railLabel}"
                control.availability == SettingsControlAvailability.DEGRADED ->
                    "${control.railLabel}\n仅保存"
                control.availability == SettingsControlAvailability.UNSUPPORTED ->
                    "${control.railLabel}\n不支持"
                else -> control.railLabel
            }
            button.textSize = if (isSelected) 13f else if (control.isSelectable) 12f else 10f
            button.setTypeface(null, if (isSelected) Typeface.BOLD else Typeface.NORMAL)
            button.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        isSelected -> R.color.oc_accent
                        !control.isSelectable -> R.color.oc_text_muted
                        else -> R.color.oc_text_secondary
                    }
                )
            )
            button.alpha = if (control.isSelectable) 1f else 0.55f
        }
    }

    private fun renderScale(control: RuntimeProControlSpec) {
        views.scale.alpha = if (control.availability == SettingsControlAvailability.UNSUPPORTED) 0.55f else 1f
        views.scale.render(control.options) { option ->
            val action = option.action ?: return@render
            onApplyControl(
                FeatureCatalogControlRenderModel(
                    label = control.railLabel,
                    value = option.label,
                    availability = control.availability,
                    supportLabel = control.supportLabel,
                    nextAction = action
                )
            )
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}
