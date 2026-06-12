package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.opencamera.core.session.SessionState
import com.opencamera.core.settings.PersistedSettingsAction

internal data class CockpitCallbacks(
    val onZoomRatioSelected: (Float) -> Unit,
    val onZoomRatioChanged: ((Float) -> Unit)? = null
)

internal class CockpitSurfaceRenderer(
    private val context: Context,
    private val topBar: TopBarViews,
    private val quickPanel: QuickPanelViews,
    private val floatingUtility: FloatingUtilityViews,
    private val bottomCockpit: BottomCockpitViews,
    private val modeTrack: ModeTrackViews,
    private val filterStrip: FilterStripViews,
    private val preview: PreviewViews,
    private val callbacks: CockpitCallbacks,
    private val isModeTrackScrolling: () -> Boolean = { false }
) {
    var controlRotationDegrees: Float = 0f
    var brightnessDragActive: Boolean = false
        private set

    private val shutterVisualDrawable = ShutterVisualDrawable()
    private var shutterDrawableAttached = false
    private val text = com.opencamera.app.i18n.AppTextResolver(context)

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    fun renderTopTitle() {
        topBar.titleText.text = context.getString(R.string.app_name)
    }

    fun renderShutter(state: SessionState, controls: SessionControlsRenderModel, isShutterEnabled: Boolean = state.modeSnapshot.state.isShutterEnabled) {
        if (!shutterDrawableAttached) {
            shutterDrawableAttached = true
            bottomCockpit.shutter.background = shutterVisualDrawable
        }
        val shutterLabel = when (state.recordingStatus) {
            com.opencamera.core.session.RecordingStatus.IDLE -> context.getString(R.string.button_photo_capture)
            com.opencamera.core.session.RecordingStatus.REQUESTING -> context.getString(R.string.button_recording_starting)
            com.opencamera.core.session.RecordingStatus.RECORDING -> context.getString(R.string.button_recording_stop)
            com.opencamera.core.session.RecordingStatus.STOPPING -> context.getString(R.string.button_recording_saving)
        }
        bottomCockpit.shutter.contentDescription = shutterLabel
        bottomCockpit.shutter.text = ""
        shutterVisualDrawable.visualState = shutterVisualState(state)
        bottomCockpit.shutter.isEnabled = isShutterEnabled
        bottomCockpit.lensFacing.text = controls.lensFacingButtonLabel
        bottomCockpit.lensFacing.isEnabled = controls.lensFacingEnabled
    }

    fun renderCaptureOutput(text: String) {
        preview.captureOutput.text = text
    }

    fun renderRecordingIndicator(indicator: RecordingIndicatorRenderModel) {
        bottomCockpit.recordingIndicator.isVisible = indicator.isVisible
        if (indicator.isVisible) {
            bottomCockpit.recordingIndicator.text = indicator.label
        }
    }

    fun renderPreviewMirror(state: SessionState) {
        preview.previewView.scaleX = if (
            state.activeDeviceGraph.preferredLensFacing == com.opencamera.core.device.LensFacing.FRONT &&
            state.settings.persisted.common.selfieMirrorEnabled
        ) {
            -1f
        } else {
            1f
        }
    }

    private var sliderInitialized = false

    fun renderFocalLengthSlider(model: FocalLengthSliderRenderModel) {
        val slider = bottomCockpit.focalLengthSlider
        slider.setSliderVisible(model.isVisible)
        if (!model.isVisible) return

        if (!sliderInitialized) {
            sliderInitialized = true
            slider.onRatioChanged = { ratio ->
                callbacks.onZoomRatioChanged?.invoke(ratio)
            }
            slider.onRatioSnapped = { ratio ->
                callbacks.onZoomRatioSelected(ratio)
            }
        }

        slider.isInteractive = model.isEnabled
        slider.alpha = if (model.isEnabled) 1f else 0.4f
        slider.contentDescription = text.zoomSliderDescription(model.currentRatio, model.disabledReason)

        slider.setPresetRatios(model.presetRatios)
        slider.setCurrentRatio(model.currentRatio)
    }

    fun onBrightnessDragStart() {
        brightnessDragActive = true
    }

    fun onBrightnessDragEnd() {
        brightnessDragActive = false
    }

    private fun quickRowLabel(row: QuickPanelRowRenderModel): String {
        val base = "${row.title} ${row.value}"
        return if (row.disabledReason != null) "$base (${row.disabledReason})" else base
    }

    fun renderQuickBubble(
        settingsPage: SessionSettingsPageRenderModel,
        sheet: QuickPanelSheetRenderModel
    ) {
        quickPanel.grid.text = quickRowLabel(sheet.gridRow)
        quickPanel.grid.isEnabled = sheet.gridRow.isEnabled
        quickPanel.grid.alpha = if (sheet.gridRow.isEnabled) 1f else 0.4f

        quickPanel.resolution.text = quickRowLabel(sheet.resolutionRow)
        quickPanel.resolution.isEnabled = sheet.resolutionRow.isEnabled
        quickPanel.resolution.alpha = if (sheet.resolutionRow.isEnabled) 1f else 0.4f

        val brightness = sheet.brightnessRow
        if (brightness.isVisible) {
            quickPanel.brightnessSlider.visibility = View.VISIBLE
            quickPanel.brightnessValueText.visibility = View.VISIBLE
            quickPanel.brightnessSlider.max = brightness.maxSteps - brightness.minSteps
            if (!brightnessDragActive) {
                quickPanel.brightnessSlider.progress = brightness.steps - brightness.minSteps
            }
            quickPanel.brightnessSlider.isEnabled = brightness.isInteractive
            quickPanel.brightnessValueText.text = brightness.value
            quickPanel.brightnessValueText.alpha = if (brightness.disabledReason != null) 0.4f else 1f
        } else {
            quickPanel.brightnessSlider.visibility = View.GONE
            quickPanel.brightnessValueText.visibility = View.GONE
        }

        quickPanel.frameRatio.text = "${sheet.frameRatioRow.title} ${sheet.frameRatioRow.value}"
        quickPanel.frameRatio.isEnabled = sheet.frameRatioEnabled

        quickPanel.watermark.text = quickRowLabel(sheet.watermarkRow)
        quickPanel.watermark.isEnabled = sheet.watermarkRow.isEnabled
        quickPanel.watermark.alpha = if (sheet.watermarkRow.isEnabled) 1f else 0.4f

        quickPanel.livePhoto.text = quickRowLabel(sheet.liveRow)
        quickPanel.livePhoto.isEnabled = sheet.liveRow.isEnabled
        quickPanel.livePhoto.alpha = if (sheet.liveRow.isEnabled) 1f else 0.4f

        quickPanel.timer.text = quickRowLabel(sheet.timerRow)
        quickPanel.timer.isEnabled = sheet.timerRow.isEnabled
        quickPanel.timer.alpha = if (sheet.timerRow.isEnabled) 1f else 0.4f

        quickPanel.resetDefaults.visibility = if (sheet.hasQuickUserAdjustments) View.VISIBLE else View.GONE
    }

    private var lastAutoScrolledActiveMode: com.opencamera.core.mode.ModeId? = null

    fun renderModeTrack(model: ModeTrackRenderModel) {
        val buttonMap = mapOf(
            com.opencamera.core.mode.ModeId.PHOTO to modeTrack.photo,
            com.opencamera.core.mode.ModeId.CHECK_IN to modeTrack.checkIn,
            com.opencamera.core.mode.ModeId.HUMANISTIC to modeTrack.humanistic,
            com.opencamera.core.mode.ModeId.VIDEO to modeTrack.video,
            com.opencamera.core.mode.ModeId.DOCUMENT to modeTrack.document
        )
        buttonMap.values.forEach { it.visibility = View.GONE }
        model.items.forEach { item ->
            val button = buttonMap[item.modeId] ?: return@forEach
            button.visibility = View.VISIBLE
            button.text = item.trackLabel
            button.isEnabled = item.isAvailable
            if (item.isActive) {
                button.setTextColor(ContextCompat.getColor(context, R.color.oc_accent))
                button.setTypeface(null, Typeface.BOLD)
                button.setBackgroundResource(R.drawable.bg_mode_track_active_chip)
                button.alpha = 1f
            } else {
                button.setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
                button.setTypeface(null, Typeface.NORMAL)
                button.background = null
                button.alpha = if (item.isAvailable) 0.78f else 0.42f
            }
        }
        val activeItem = model.items.firstOrNull { it.isActive }
        val activeModeId = activeItem?.modeId
        if (activeModeId != null && activeModeId != lastAutoScrolledActiveMode && !isModeTrackScrolling()) {
            lastAutoScrolledActiveMode = activeModeId
            modeTrack.scroll.post {
                val activeButton = buttonMap[activeModeId]
                activeButton?.let {
                    val viewWidth = modeTrack.scroll.width
                    val chipCenter = it.left + it.width / 2
                    val scrollX = (chipCenter - viewWidth / 2).coerceAtLeast(0)
                    modeTrack.scroll.smoothScrollTo(scrollX, 0)
                }
            }
        }
    }

    fun renderModeAction(model: ModeActionRenderModel) {
        val button = modeTrack.modeAction
        if (!model.isVisible) {
            button.visibility = View.GONE
            return
        }
        button.visibility = View.VISIBLE
        button.text = model.label
        if (model.isActive) {
            button.setTextColor(ContextCompat.getColor(context, R.color.oc_accent))
            button.setTypeface(null, Typeface.BOLD)
            button.setBackgroundResource(R.drawable.bg_mode_track_active_chip)
            button.alpha = 1f
        } else {
            button.setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
            button.setTypeface(null, Typeface.NORMAL)
            button.background = null
            button.alpha = 0.78f
        }
    }

    private var lastFilterStripModel: FilterStripRenderModel? = null

    fun renderFilterStrip(model: FilterStripRenderModel, onSelectFilter: (PersistedSettingsAction) -> Unit) {
        if (model == lastFilterStripModel) return
        lastFilterStripModel = model

        val chips = filterStrip.chips
        chips.removeAllViews()

        model.items.forEach { item ->
            val chip = Button(context, null, 0, R.style.Widget_OpenCamera_CompactButton).apply {
                text = item.title
                isAllCaps = false
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
                isEnabled = item.selectAction != null || item.isSelected
                alpha = if (item.isSelected) 1f else 0.78f
                setTextColor(
                    if (item.isSelected) {
                        ContextCompat.getColor(context, R.color.oc_accent)
                    } else {
                        ContextCompat.getColor(context, R.color.oc_text_primary)
                    }
                )
                if (item.isSelected) {
                    setTypeface(null, Typeface.BOLD)
                }
                setOnClickListener {
                    item.selectAction?.let(onSelectFilter)
                }
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginEnd = 8.dp
            }
            chips.addView(chip, params)
        }
    }

    fun renderLowLightNightPrompt(model: LowLightNightPromptRenderModel) {
        val button = floatingUtility.lowLightNightPrompt
        if (model.isVisible) {
            button.visibility = View.VISIBLE
            button.text = model.label
            button.contentDescription = model.contentDescription
            button.isEnabled = model.isEnabled
            button.alpha = if (model.isEnabled) 1f else 0.5f
        } else {
            button.visibility = View.GONE
        }
    }
}
