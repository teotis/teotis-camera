package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.opencamera.core.effect.PreviewStop
import com.opencamera.core.effect.applyFilterRenderSpecToStops
import com.opencamera.core.settings.FilterRenderSpec
import com.opencamera.core.settings.StylePresetPreview

/**
 * Renders a single style preset card tile with a deterministic visual preview
 * derived from [StylePresetPreview] data.
 *
 * The tile contains:
 * - Top: gradient preview area reflecting contrast, brightness, warmth, monochrome
 * - Bottom: title and mood label
 * - Selected: accent ring around the card
 */
internal class StylePresetCardTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val previewRect = RectF()
    private var previewGradient: LinearGradient? = null
    private var previewColors: IntArray = intArrayOf(Color.DKGRAY, Color.LTGRAY)
    private var previewFractions: FloatArray = floatArrayOf(0f, 1f)

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.argb(200, 85, 214, 190) // oc_accent
    }
    private val deselectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(50, 255, 255, 255)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 255, 255)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(248, 248, 250, 252) // oc_text_primary
        textSize = 11f * context.resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val moodPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(203, 203, 213, 225) // oc_text_secondary
        textSize = 9f * context.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private var currentModel: StylePresetCardRenderModel? = null
    private var tileCornerRadius: Float = 8f * context.resources.displayMetrics.density
    private var pendingSelected: Boolean = false

    fun bind(model: StylePresetCardRenderModel) {
        currentModel = model
        pendingSelected = false

        titlePaint.textSize = 11f * context.resources.displayMetrics.scaledDensity
        moodPaint.textSize = 9f * context.resources.displayMetrics.scaledDensity

        alpha = if (model.isEnabled) 1f else 0.5f

        computePreviewColors(model.preview, model.spec)
        previewGradient = null

        invalidate()
    }

    /**
     * Immediately reflect selection visually before the render model arrives,
     * giving the user frame-accurate click feedback.
     */
    fun setPendingSelected(selected: Boolean) {
        if (pendingSelected == selected) return
        pendingSelected = selected
        invalidate()
    }

    /**
     * Scene reference stops: sky blue → neutral gray → warm highlight → dark shadow.
     * These 4 fixed stops simulate a photographic scene whose colors are
     * then transformed by the filter's [FilterRenderSpec] to show the
     * characteristic tonal/color shift of the style preset.
     */
    private val sceneStops = listOf(
        PreviewStop(Color.rgb(135, 180, 220), 0f),
        PreviewStop(Color.rgb(160, 160, 165), 0.35f),
        PreviewStop(Color.rgb(220, 190, 155), 0.65f),
        PreviewStop(Color.rgb(50, 45, 55), 1f)
    )

    private fun computePreviewColors(
        preview: StylePresetPreview,
        spec: FilterRenderSpec?
    ) {
        val transformed = if (spec != null) {
            applyFilterRenderSpecToStops(spec, sceneStops)
        } else {
            sceneStops
        }

        // Monochrome override: for high monochrome level, force grayscale gradient
        if (preview.monochromeLevel >= 0.65f) {
            val hiLevel = (180 - preview.monochromeLevel * 100).toInt().coerceIn(80, 200)
            val loLevel = hiLevel / 2
            previewColors = intArrayOf(
                Color.rgb(hiLevel, hiLevel, hiLevel),
                Color.rgb(hiLevel, hiLevel, hiLevel),
                Color.rgb(loLevel, loLevel, loLevel),
                Color.rgb(loLevel, loLevel, loLevel)
            )
            previewFractions = floatArrayOf(0f, 0.4f, 0.7f, 1f)
        } else {
            previewColors = IntArray(transformed.size) { transformed[it].color }
            previewFractions = FloatArray(transformed.size) { transformed[it].fraction }
        }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            val previewHeight = (h * 0.6f)
            previewRect.set(0f, 0f, w.toFloat(), previewHeight)
            previewGradient = LinearGradient(
                0f, 0f, 0f, previewHeight,
                previewColors,
                previewFractions,
                Shader.TileMode.CLAMP
            )
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRoundRect(0f, 0f, w, h, tileCornerRadius, tileCornerRadius, bgPaint)

        previewGradient?.let { gradient ->
            previewPaint.shader = gradient
            canvas.drawRoundRect(previewRect, tileCornerRadius, tileCornerRadius, previewPaint)
        }

        val previewBottom = previewRect.bottom
        val titleY = previewBottom + 13f * resources.displayMetrics.scaledDensity
        val titleRaw = currentModel?.title ?: ""
        val availableWidth = w - 2f * 4f * resources.displayMetrics.density
        val titleText = if (titleRaw.isNotEmpty()) {
            TextUtils.ellipsize(titleRaw, titlePaint, availableWidth, TextUtils.TruncateAt.END)
                .toString()
        } else ""

        canvas.drawText(titleText, w / 2f, titleY, titlePaint)

        val moodY = titleY + 13f * resources.displayMetrics.scaledDensity
        val moodRaw = currentModel?.moodLabel ?: ""
        val moodText = if (moodRaw.isNotEmpty()) {
            TextUtils.ellipsize(moodRaw, moodPaint, availableWidth, TextUtils.TruncateAt.END)
                .toString()
        } else ""
        if (moodText.isNotEmpty()) {
            canvas.drawText(moodText, w / 2f, moodY, moodPaint)
        }

        val paint = if (pendingSelected || currentModel?.isSelected == true) selectionPaint else deselectionPaint
        canvas.drawRoundRect(0f, 0f, w, h, tileCornerRadius, tileCornerRadius, paint)
    }
}
