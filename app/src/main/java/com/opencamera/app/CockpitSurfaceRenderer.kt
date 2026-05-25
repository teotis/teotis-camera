package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.opencamera.core.session.SessionState

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
    private val preview: PreviewViews,
    private val callbacks: CockpitCallbacks,
    private val isModeTrackScrolling: () -> Boolean = { false }
) {
    var controlRotationDegrees: Float = 0f

    private val shutterVisualDrawable = ShutterVisualDrawable()
    private var shutterDrawableAttached = false

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

    fun renderZoomCapsules(controls: SessionControlsRenderModel) {
        bottomCockpit.zoomScroll.isVisible = controls.isZoomCapsuleRowVisible
        if (!controls.isZoomCapsuleRowVisible) return
        val existingCount = bottomCockpit.zoomRow.childCount
        val targetCount = controls.zoomCapsules.size
        for (i in existingCount - 1 downTo targetCount) {
            bottomCockpit.zoomRow.removeViewAt(i)
        }
        controls.zoomCapsules.forEachIndexed { index, capsule ->
            val chip: TextView = if (index < bottomCockpit.zoomRow.childCount) {
                bottomCockpit.zoomRow.getChildAt(index) as TextView
            } else {
                TextView(context).apply {
                    textSize = context.resources.getDimension(R.dimen.text_size_zoom_chip) / context.resources.displayMetrics.density
                    minWidth = context.resources.getDimension(R.dimen.zoom_chip_min_width).toInt()
                    minHeight = context.resources.getDimension(R.dimen.zoom_chip_min_height).toInt()
                    gravity = Gravity.CENTER
                    typeface = Typeface.DEFAULT
                    setPadding(
                        context.resources.getDimension(R.dimen.zoom_chip_padding_h).toInt(),
                        context.resources.getDimension(R.dimen.zoom_chip_padding_v).toInt(),
                        context.resources.getDimension(R.dimen.zoom_chip_padding_h).toInt(),
                        context.resources.getDimension(R.dimen.zoom_chip_padding_v).toInt()
                    )
                }.also { newChip ->
                    val params = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply {
                        marginStart = if (index == 0) 0 else 4.dp
                    }
                    bottomCockpit.zoomRow.addView(newChip, params)
                }
            }
            chip.text = capsule.label
            if (capsule.isActive) {
                chip.setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
                chip.setBackgroundResource(R.drawable.bg_zoom_chip_active)
            } else {
                chip.setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                chip.setBackgroundResource(R.drawable.bg_zoom_chip)
            }
            chip.setOnClickListener {
                callbacks.onZoomRatioSelected(capsule.ratio)
            }
            chip.rotation = controlRotationDegrees
        }
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
            quickPanel.brightnessSlider.progress = brightness.steps - brightness.minSteps
            quickPanel.brightnessSlider.isEnabled = brightness.isInteractive
            quickPanel.brightnessValueText.text = brightness.value
            quickPanel.brightnessValueText.alpha = if (brightness.disabledReason != null) 0.4f else 1f
        } else {
            quickPanel.brightnessSlider.visibility = View.GONE
            quickPanel.brightnessValueText.visibility = View.GONE
        }

        quickPanel.frameRatio.text = "${sheet.frameRatioRow.title} ${sheet.frameRatioRow.value}"
        quickPanel.frameRatio.isEnabled = sheet.frameRatioEnabled

        quickPanel.livePhoto.text = quickRowLabel(sheet.liveRow)
        quickPanel.livePhoto.isEnabled = sheet.liveRow.isEnabled
        if (sheet.liveRow.isSelected) {
            quickPanel.livePhoto.alpha = 1f
            quickPanel.livePhoto.setBackgroundResource(R.drawable.bg_quick_chip_selected)
        } else {
            quickPanel.livePhoto.alpha = if (sheet.liveRow.isEnabled) 0.85f else 0.4f
            quickPanel.livePhoto.setBackgroundResource(R.drawable.bg_quick_chip)
        }

        quickPanel.timer.text = quickRowLabel(sheet.timerRow)
        quickPanel.timer.isEnabled = sheet.timerRow.isEnabled
        quickPanel.timer.alpha = if (sheet.timerRow.isEnabled) 1f else 0.4f
    }

    private var lastAutoScrolledActiveMode: com.opencamera.core.mode.ModeId? = null

    fun renderModeTrack(model: ModeTrackRenderModel) {
        val buttonMap = mapOf(
            com.opencamera.core.mode.ModeId.PHOTO to modeTrack.photo,
            com.opencamera.core.mode.ModeId.HUMANISTIC to modeTrack.humanistic,
            com.opencamera.core.mode.ModeId.VIDEO to modeTrack.video,
            com.opencamera.core.mode.ModeId.DOCUMENT to modeTrack.document
        )
        modeTrack.night.visibility = View.GONE
        modeTrack.portrait.visibility = View.GONE
        modeTrack.pro.visibility = View.GONE
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
