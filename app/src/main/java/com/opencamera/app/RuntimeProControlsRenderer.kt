package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.text.TextUtils
import android.widget.Button
import android.widget.LinearLayout
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import java.util.Locale

internal class RuntimeProControlsRenderer(
    private val context: Context,
    private val views: RuntimeProControlsViews,
    private val onApplyControl: (FeatureCatalogControlRenderModel?) -> Unit
) {
    private var lastModel: RuntimeProControlsRenderModel? = null

    fun render(model: RuntimeProControlsRenderModel) {
        if (!model.isVisible) {
            views.scroll.isVisible = false
            lastModel = null
            return
        }

        views.scroll.isVisible = true
        if (model == lastModel) return
        lastModel = model

        views.chips.removeAllViews()
        model.controls.forEach { control ->
            val isAutoValue = control.value.isAutoValue()
            val chip = Button(context, null, 0, R.style.Widget_OpenCamera_TopActionChip).apply {
                text = control.compactLabel()
                contentDescription = control.accessibilityLabel()
                background = ContextCompat.getDrawable(context, R.drawable.bg_pro_glass_chip)
                backgroundTintList = null
                isAllCaps = false
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                minWidth = context.resources.getDimensionPixelSize(R.dimen.pro_glass_chip_min_width)
                minHeight = 44.dp
                includeFontPadding = false
                setPadding(10.dp, 4.dp, 10.dp, 4.dp)
                isEnabled = control.isInteractive
                alpha = when {
                    control.isInteractive -> 1f
                    control.availability == SettingsControlAvailability.DEGRADED -> 0.7f
                    else -> 0.48f
                }
                setTextColor(
                    when {
                        control.isInteractive && !isAutoValue -> ContextCompat.getColor(context, R.color.oc_accent)
                        control.isInteractive -> ContextCompat.getColor(context, R.color.oc_text_primary)
                        control.availability == SettingsControlAvailability.DEGRADED ->
                            ContextCompat.getColor(context, R.color.oc_text_secondary)
                        else -> ContextCompat.getColor(context, R.color.oc_text_muted)
                    }
                )
                setTypeface(null, if (isAutoValue) Typeface.NORMAL else Typeface.BOLD)
                setOnClickListener { onApplyControl(control) }
            }
            views.chips.addView(
                chip,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = 8.dp
                }
            )
        }
    }

    private val RuntimeProControlsRenderModel.controls: List<FeatureCatalogControlRenderModel>
        get() = listOf(
            rawControl,
            isoControl,
            shutterControl,
            exposureControl,
            focusControl,
            apertureControl,
            whiteBalanceControl
        )

    private fun FeatureCatalogControlRenderModel.compactLabel(): String {
        return buildString {
            append(label)
            append('\n')
            append(value)
        }
    }

    private fun FeatureCatalogControlRenderModel.accessibilityLabel(): String {
        return buildString {
            append(label)
            append(' ')
            append(value)
            if (availabilityLabel.isNotEmpty()) {
                append(' ')
                append(availabilityLabel)
            }
            supportLabel?.let {
                append(' ')
                append(it)
            }
        }
    }

    private fun String.isAutoValue(): Boolean {
        return lowercase(Locale.ROOT) == "auto" || this == context.getString(R.string.label_auto)
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}
