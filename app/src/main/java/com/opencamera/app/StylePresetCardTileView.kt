package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import com.opencamera.core.settings.PreviewTier
import com.opencamera.core.settings.PreviewWarmth
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
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(248, 248, 250, 252) // oc_text_primary
        textSize = 11f * context.resources.displayMetrics.scaledDensity
        typeface = Typeface.DEFAULT_BOLD
        textAlign = Paint.Align.CENTER
    }
    private val moodPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(203, 203, 213, 225) // oc_text_secondary
        textSize = 9f * context.resources.displayMetrics.scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private var currentModel: StylePresetCardRenderModel? = null
    private var tileCornerRadius: Float = 8f * context.resources.displayMetrics.density

    fun bind(model: StylePresetCardRenderModel) {
        currentModel = model

        titlePaint.textSize = 11f * context.resources.displayMetrics.scaledDensity
        moodPaint.textSize = 9f * context.resources.displayMetrics.scaledDensity

        alpha = if (model.isEnabled) 1f else 0.5f

        computePreviewColors(model.preview)
        previewGradient = null

        invalidate()
    }

    private fun computePreviewColors(preview: StylePresetPreview) {
        val (topColor, bottomColor) = when {
            preview.monochromeLevel >= 0.65f -> {
                val level = (180 - preview.monochromeLevel * 100).toInt().coerceIn(80, 200)
                Pair(Color.rgb(level, level, level), Color.rgb(level / 2, level / 2, level / 2))
            }
            preview.monochromeLevel > 0.3f -> {
                val level = (220 - preview.monochromeLevel * 80).toInt().coerceIn(140, 230)
                Pair(Color.rgb(level, level, level), Color.rgb(level / 3, level / 3, level / 3))
            }
            else -> {
                val warmthBias = when (preview.warmthDirection) {
                    PreviewWarmth.WARM -> 0.12f
                    PreviewWarmth.COOL -> -0.08f
                    PreviewWarmth.NEUTRAL -> 0f
                }
                val contrastBias = when (preview.contrastTier) {
                    PreviewTier.HIGH -> 0.15f
                    PreviewTier.LOW -> -0.1f
                    PreviewTier.NEUTRAL -> 0f
                }
                val brightnessBias = when (preview.brightnessTier) {
                    PreviewTier.HIGH -> 0.12f
                    PreviewTier.LOW -> -0.08f
                    PreviewTier.NEUTRAL -> 0f
                }
                val base = 120
                val topR = (base + 50 + contrastBias * 60 + warmthBias * 40).toInt().coerceIn(60, 240)
                val topG = (base + 40 + contrastBias * 40 - warmthBias * 10).toInt().coerceIn(60, 230)
                val topB = (base + 30 - warmthBias * 40 - contrastBias * 10).toInt().coerceIn(60, 220)
                val btmR = (base - 30 + brightnessBias * 50 + warmthBias * 30).toInt().coerceIn(30, 180)
                val btmG = (base - 40 + brightnessBias * 30 - warmthBias * 15).toInt().coerceIn(30, 170)
                val btmB = (base - 30 - warmthBias * 30 - brightnessBias * 10).toInt().coerceIn(30, 160)
                Pair(Color.rgb(topR, topG, topB), Color.rgb(btmR, btmG, btmB))
            }
        }
        previewColors = intArrayOf(topColor, bottomColor)
        previewFractions = floatArrayOf(0f, 1f)
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
        val titleText = currentModel?.title?.take(8) ?: ""
        canvas.drawText(titleText, w / 2f, titleY, titlePaint)

        val moodY = titleY + 13f * resources.displayMetrics.scaledDensity
        val moodText = currentModel?.moodLabel?.take(10) ?: ""
        if (moodText.isNotEmpty()) {
            canvas.drawText(moodText, w / 2f, moodY, moodPaint)
        }

        val paint = if (currentModel?.isSelected == true) selectionPaint else deselectionPaint
        canvas.drawRoundRect(0f, 0f, w, h, tileCornerRadius, tileCornerRadius, paint)
    }
}
