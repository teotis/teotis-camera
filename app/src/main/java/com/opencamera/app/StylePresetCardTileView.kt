package com.opencamera.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextPaint
import android.text.TextUtils
import android.util.AttributeSet
import android.view.View
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.settings.FilterRenderSpec
import kotlin.math.max

/**
 * Photographic style tile. A shared live-preview snapshot is cropped into every
 * tile, then transformed with the candidate style's color matrix so differences
 * are judged against the same scene. The rail keeps tiles hidden until that
 * snapshot exists, rather than replacing photography with an abstract gradient.
 */
internal class StylePresetCardTileView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val previewPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val previewRect = RectF()
    private var previewBitmap: Bitmap? = null

    private val selectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
        color = Color.argb(245, 248, 250, 252)
    }
    private val deselectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = resources.displayMetrics.density
        color = Color.argb(54, 255, 255, 255)
    }
    private val selectedDotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.rgb(85, 214, 190)
    }
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(230, 13, 15, 18)
    }
    private val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(248, 248, 250, 252)
        textSize = 12f * resources.displayMetrics.scaledDensity
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        textAlign = Paint.Align.CENTER
    }

    private var currentModel: StylePresetCardRenderModel? = null
    private var currentSpec: FilterRenderSpec? = null
    private val tileCornerRadius = 7f * resources.displayMetrics.density
    private var pendingSelected: Boolean = false

    fun bind(model: StylePresetCardRenderModel, bitmap: Bitmap? = null) {
        currentModel = model
        currentSpec = model.spec
        pendingSelected = false
        alpha = if (model.isEnabled) 1f else 0.5f
        previewBitmap = bitmap
        rebuildPreviewShader()
        invalidate()
    }

    fun setPendingSelected(selected: Boolean) {
        if (pendingSelected == selected) return
        pendingSelected = selected
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val previewHeight = minOf(
            StylePresetCardDimensions.PREVIEW_HEIGHT_DP * resources.displayMetrics.density,
            h.toFloat()
        )
        previewRect.set(0f, 0f, w.toFloat(), previewHeight)
        rebuildPreviewShader()
    }

    private fun rebuildPreviewShader() {
        if (previewRect.width() <= 0f || previewRect.height() <= 0f) return
        val bitmap = previewBitmap
        previewPaint.shader = if (bitmap != null && !bitmap.isRecycled) {
            BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
                val scale = max(
                    previewRect.width() / bitmap.width.toFloat(),
                    previewRect.height() / bitmap.height.toFloat()
                )
                val dx = (previewRect.width() - bitmap.width * scale) / 2f
                val dy = (previewRect.height() - bitmap.height * scale) / 2f
                shader.setLocalMatrix(Matrix().apply {
                    setScale(scale, scale)
                    postTranslate(dx, dy)
                })
            }
        } else null
        previewPaint.color = Color.rgb(28, 31, 36)
        previewPaint.colorFilter = PreviewColorMatrixBuilder.buildMatrix(currentSpec)?.let {
            ColorMatrixColorFilter(it)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        canvas.drawRoundRect(0f, 0f, w, h, tileCornerRadius, tileCornerRadius, bgPaint)
        canvas.drawRoundRect(previewRect, tileCornerRadius, tileCornerRadius, previewPaint)

        val titleRaw = currentModel?.title.orEmpty()
        val availableWidth = w - 8f * resources.displayMetrics.density
        val title = TextUtils.ellipsize(
            titleRaw,
            titlePaint,
            availableWidth,
            TextUtils.TruncateAt.END
        ).toString()
        val titleY = previewRect.bottom + 18f * resources.displayMetrics.scaledDensity
        canvas.drawText(title, w / 2f, titleY, titlePaint)

        val selected = pendingSelected || currentModel?.isSelected == true
        canvas.drawRoundRect(
            1f,
            1f,
            w - 1f,
            previewRect.bottom - 1f,
            tileCornerRadius,
            tileCornerRadius,
            if (selected) selectionPaint else deselectionPaint
        )
        if (selected) {
            val radius = 4f * resources.displayMetrics.density
            canvas.drawCircle(
                previewRect.right - 10f * resources.displayMetrics.density,
                previewRect.bottom - 10f * resources.displayMetrics.density,
                radius,
                selectedDotPaint
            )
        }
    }
}
