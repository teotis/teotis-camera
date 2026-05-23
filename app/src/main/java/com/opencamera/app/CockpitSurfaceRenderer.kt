package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.opencamera.core.session.SessionState

internal data class CockpitCallbacks(
    val onZoomRatioSelected: (Float) -> Unit
)

internal class CockpitSurfaceRenderer(
    private val context: Context,
    private val topBar: TopBarViews,
    private val quickPanel: QuickPanelViews,
    private val bottomCockpit: BottomCockpitViews,
    private val modeTrack: ModeTrackViews,
    private val preview: PreviewViews,
    private val callbacks: CockpitCallbacks,
    private val isModeTrackScrolling: () -> Boolean = { false }
) {
    var controlRotationDegrees: Float = 0f

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    fun renderTopTitle() {
        topBar.titleText.text = context.getString(R.string.app_name)
    }

    fun renderShutter(state: SessionState, controls: SessionControlsRenderModel) {
        val shutterLabel = when (state.recordingStatus) {
            com.opencamera.core.session.RecordingStatus.IDLE -> context.getString(R.string.button_photo_capture)
            com.opencamera.core.session.RecordingStatus.REQUESTING -> context.getString(R.string.button_recording_starting)
            com.opencamera.core.session.RecordingStatus.RECORDING -> context.getString(R.string.button_recording_stop)
            com.opencamera.core.session.RecordingStatus.STOPPING -> context.getString(R.string.button_recording_saving)
        }
        bottomCockpit.shutter.contentDescription = shutterLabel
        bottomCockpit.shutter.text = ""
        if (state.recordingStatus != com.opencamera.core.session.RecordingStatus.IDLE) {
            bottomCockpit.shutter.setBackgroundResource(R.drawable.bg_shutter_recording_selector)
        } else {
            bottomCockpit.shutter.setBackgroundResource(R.drawable.bg_shutter_selector)
        }
        bottomCockpit.shutter.isEnabled = state.modeSnapshot.state.isShutterEnabled
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
        bottomCockpit.zoomRow.removeAllViews()
        controls.zoomCapsules.forEach { capsule ->
            val chip = TextView(context).apply {
                text = capsule.label
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
                if (capsule.isActive) {
                    setTextColor(ContextCompat.getColor(context, R.color.oc_text_primary))
                    setBackgroundResource(R.drawable.bg_zoom_chip_active)
                } else {
                    setTextColor(ContextCompat.getColor(context, R.color.oc_text_secondary))
                    setBackgroundResource(R.drawable.bg_zoom_chip)
                }
                setOnClickListener {
                    callbacks.onZoomRatioSelected(capsule.ratio)
                }
                rotation = controlRotationDegrees
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                marginStart = if (bottomCockpit.zoomRow.childCount == 0) 0 else 4.dp
            }
            bottomCockpit.zoomRow.addView(chip, params)
        }
    }

    fun renderQuickBubble(
        settingsPage: SessionSettingsPageRenderModel,
        sheet: QuickPanelSheetRenderModel
    ) {
        quickPanel.grid.text = "${sheet.gridRow.title} ${sheet.gridRow.value}"
        quickPanel.grid.isEnabled = sheet.gridRow.isEnabled

        quickPanel.flash.text = "${sheet.qualityRow.title} ${sheet.qualityRow.value}"
        quickPanel.flash.isEnabled = sheet.qualityRow.isEnabled

        quickPanel.frame43.isEnabled = sheet.frameRatioEnabled
        quickPanel.frame169.isEnabled = sheet.frameRatioEnabled
        quickPanel.frame11.isEnabled = sheet.frameRatioEnabled
        sheet.frameRatioOptions.forEach { option ->
            val button = when (option.ratio) {
                com.opencamera.core.media.FrameRatio.RATIO_4_3 -> quickPanel.frame43
                com.opencamera.core.media.FrameRatio.RATIO_16_9 -> quickPanel.frame169
                com.opencamera.core.media.FrameRatio.RATIO_1_1 -> quickPanel.frame11
            }
            if (option.isSelected) {
                button.alpha = 1f
                button.setBackgroundResource(R.drawable.bg_quick_chip_selected)
            } else {
                button.alpha = if (sheet.frameRatioEnabled) 0.85f else 0.4f
                button.setBackgroundResource(R.drawable.bg_quick_chip)
            }
        }

        quickPanel.livePhoto.text = "${sheet.liveRow.title} ${sheet.liveRow.value}"
        quickPanel.livePhoto.isEnabled = sheet.liveRow.isEnabled

        quickPanel.timer.text = "${sheet.timerRow.title} ${sheet.timerRow.value}"
        quickPanel.timer.isEnabled = sheet.timerRow.isEnabled
    }

    private var lastAutoScrolledActiveMode: com.opencamera.core.mode.ModeId? = null

    fun renderModeTrack(model: ModeTrackRenderModel) {
        val buttons = listOf(
            modeTrack.photo,
            modeTrack.video,
            modeTrack.document
        )
        modeTrack.night.visibility = View.GONE
        modeTrack.portrait.visibility = View.GONE
        modeTrack.pro.visibility = View.GONE
        modeTrack.humanistic.visibility = View.GONE
        model.items.forEachIndexed { index, item ->
            if (index < buttons.size) {
                val button = buttons[index]
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
        }
        buttons.drop(model.items.size).forEach { button ->
            button.visibility = View.GONE
        }
        val activeItem = model.items.firstOrNull { it.isActive }
        val activeModeId = activeItem?.modeId
        if (activeModeId != null && activeModeId != lastAutoScrolledActiveMode && !isModeTrackScrolling()) {
            lastAutoScrolledActiveMode = activeModeId
            modeTrack.scroll.post {
                val activeButton = buttons.firstOrNull { b ->
                    val idx = buttons.indexOf(b)
                    idx < model.items.size && model.items[idx].isActive
                }
                activeButton?.let {
                    val viewWidth = modeTrack.scroll.width
                    val chipCenter = it.left + it.width / 2
                    val scrollX = (chipCenter - viewWidth / 2).coerceAtLeast(0)
                    modeTrack.scroll.smoothScrollTo(scrollX, 0)
                }
            }
        }
    }
}
