package com.opencamera.app

import android.content.Context
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.opencamera.core.settings.PersistedSettingsAction

internal class SettingsPanelRenderer(
    private val context: Context,
    private val views: SettingsPanelViews,
    private val onApplySettingsAction: (PersistedSettingsAction) -> Unit = {},
    private val onOpenWatermarkDetail: (String) -> Unit = {}
) {
    fun renderPage(model: SessionSettingsPageRenderModel) {
        views.headline.text = model.headline
        views.supportingText.text = model.supportingText
        views.heroSummary.text = model.heroSummary
        views.heroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.commonSummary.text = model.commonSection.summary
        views.commonSummary.isVisible = model.commonSection.summary.isNotEmpty()
        views.photoSummary.text = model.photoSection.summary
        views.photoSummary.isVisible = model.photoSection.summary.isNotEmpty()
        views.videoSummary.text = model.videoSection.summary
        views.videoSummary.isVisible = model.videoSection.summary.isNotEmpty()
        views.catalogFooter.text = model.catalogFooter
        views.catalogFooter.isVisible = model.catalogFooter.isNotEmpty()
        views.editingHint.text = model.editingHint
        renderControl(views.gridMode, model.commonSection.gridMode, model.editingEnabled)
        renderControl(views.shutterSound, model.commonSection.shutterSound, model.editingEnabled)
        renderControl(views.selfieMirror, model.commonSection.selfieMirror, model.editingEnabled)
        renderControl(views.photoFilter, model.photoSection.defaultFilter, model.editingEnabled)
        views.photoPortraitLab.text = model.photoSection.portraitLab.buttonLabel
        views.photoPortraitLab.isEnabled = model.editingEnabled &&
            model.photoSection.portraitLab.availability != SettingsControlAvailability.UNSUPPORTED
        views.photoWatermark.text = model.photoSection.watermarkTemplate.buttonLabel
        views.photoWatermark.isEnabled = model.editingEnabled &&
            model.photoSection.watermarkTemplate.availability != SettingsControlAvailability.UNSUPPORTED
        renderControl(views.photoLive, model.photoSection.livePhoto, model.editingEnabled)
        renderControl(views.photoLiveSaveFormat, model.photoSection.liveSaveFormat, model.editingEnabled)
        renderControl(views.photoTimer, model.photoSection.countdown, model.editingEnabled)
        renderControl(views.videoResolution, model.videoSection.resolution, model.editingEnabled)
        renderControl(views.videoFrameRate, model.videoSection.frameRate, model.editingEnabled)
        renderControl(views.videoDynamicFps, model.videoSection.dynamicFps, model.editingEnabled)
        renderControl(views.videoAudio, model.videoSection.audioProfile, model.editingEnabled)
        renderControl(views.videoFilter, model.videoSection.defaultFilter, model.editingEnabled)
        views.resetDefaults.isVisible = model.hasSettingsUserAdjustments
    }

    fun renderTabs(selectedSettingsTab: SettingsTab) {
        views.tabCommon.isEnabled = selectedSettingsTab != SettingsTab.COMMON
        views.tabPhoto.isEnabled = selectedSettingsTab != SettingsTab.PHOTO
        views.tabVideo.isEnabled = selectedSettingsTab != SettingsTab.VIDEO
        views.tabCommon.alpha = if (selectedSettingsTab == SettingsTab.COMMON) 1f else 0.84f
        views.tabPhoto.alpha = if (selectedSettingsTab == SettingsTab.PHOTO) 1f else 0.84f
        views.tabVideo.alpha = if (selectedSettingsTab == SettingsTab.VIDEO) 1f else 0.84f
        views.commonSection.isVisible = selectedSettingsTab == SettingsTab.COMMON
        views.photoSection.isVisible = selectedSettingsTab == SettingsTab.PHOTO
        views.videoSection.isVisible = selectedSettingsTab == SettingsTab.VIDEO
    }

    fun renderPortraitLabPage(model: PortraitLabPageRenderModel) {
        views.portraitHeadline.text = model.headline
        views.portraitSupportingText.text = model.supportingText
        views.portraitHeroSummary.text = model.heroSummary
        views.portraitHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.portraitEditingHint.text = model.editingHint
        views.portraitFooter.text = model.footer
        renderControl(views.portraitProfile, model.profileControl, model.editingEnabled)
        renderControl(views.portraitBeautyPreset, model.beautyPresetControl, model.editingEnabled)
        renderControl(views.portraitBeautyStrength, model.beautyStrengthControl, model.editingEnabled)
        renderControl(views.portraitBokehEffect, model.bokehEffectControl, model.editingEnabled)
        views.portraitDepthStrengthSeekBar.progress = model.depthStrength
        views.portraitDepthStrengthSeekBar.isEnabled = model.editingEnabled && model.updateDepthStrengthAction != null
        views.portraitDepthStrengthValue.text = model.depthStrengthLabel
    }

    fun renderWatermarkSelectorPage(model: WatermarkLabSelectorRenderModel) {
        views.watermarkSelectorHeadline.text = model.headline
        views.watermarkSelectorSupportingText.text = model.supportingText
        views.watermarkSelectorHeroSummary.text = model.heroSummary
        views.watermarkSelectorHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.watermarkSelectorEditingHint.text = model.editingHint
        views.watermarkSelectorFooter.text = model.footer
        views.watermarkSelectorList.removeAllViews()
        val dp14 = 14.dp
        val dp8 = 8.dp
        val dp6 = 6.dp
        val dp10 = 10.dp
        model.items.forEach { item ->
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundResource(R.drawable.bg_settings_card)
                alpha = if (item.isSelected) 1f else 0.92f
                setPadding(dp14, dp14, dp14, dp14)
            }
            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = if (views.watermarkSelectorList.childCount == 0) 0 else dp8
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
                setPadding(0, dp6, 0, 0)
            }
            card.addView(supporting)
            item.useAction?.let { action ->
                val useButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = context.getString(R.string.button_use_this_template)
                    isAllCaps = false
                    isEnabled = model.editingEnabled
                    setOnClickListener { onApplySettingsAction(action) }
                }
                val useParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp10
                }
                card.addView(useButton, useParams)
            }
            item.editButtonLabel?.let { label ->
                val editButton = Button(
                    context,
                    null,
                    0,
                    R.style.Widget_OpenCamera_CompactButton
                ).apply {
                    text = label
                    isAllCaps = false
                    isEnabled = model.editingEnabled
                    setOnClickListener { onOpenWatermarkDetail(item.templateId) }
                }
                val editParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = dp10
                }
                card.addView(editButton, editParams)
            }
            views.watermarkSelectorList.addView(card, params)
        }
    }

    fun renderWatermarkDetailPage(model: WatermarkLabDetailRenderModel) {
        views.watermarkDetailHeadline.text = model.headline
        views.watermarkDetailSupportingText.text = model.supportingText
        views.watermarkDetailHeroSummary.text = model.heroSummary
        views.watermarkDetailHeroSummary.isVisible = model.heroSummary.isNotEmpty()
        views.watermarkDetailEditingHint.text = model.editingHint
        views.watermarkDetailFooter.text = model.footer
        renderControl(views.watermarkPlacement, model.placementControl, model.editingEnabled)
        renderControl(views.watermarkTextScale, model.textScaleControl, model.editingEnabled)
        renderControl(views.watermarkTextOpacity, model.textOpacityControl, model.editingEnabled)
        model.frameBackgroundControl?.let { control ->
            views.watermarkFrameBackground.isVisible = true
            renderControl(views.watermarkFrameBackground, control, model.editingEnabled)
        } ?: run {
            views.watermarkFrameBackground.isVisible = false
        }
    }

    private fun renderControl(
        button: Button,
        model: SettingsControlRenderModel,
        editingEnabled: Boolean
    ) {
        button.text = model.buttonLabel
        button.isEnabled = editingEnabled && model.isInteractive
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}
