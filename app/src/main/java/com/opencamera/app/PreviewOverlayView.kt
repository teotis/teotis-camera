package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.FrameGuidelineSpec
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.settings.CompositionGridMode
import com.opencamera.core.settings.WatermarkTextPlacement
import kotlin.math.min

class PreviewOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val density = resources.displayMetrics.density
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(156, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 1.25f * density
    }

    private val gridEmphasisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(88, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.4f * density
    }

    private val countdownBubblePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(166, 5, 7, 10)
        style = Paint.Style.FILL
    }

    private val countdownTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            46f,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        setShadowLayer(18f, 0f, 6f, Color.argb(190, 0, 0, 0))
    }

    private val filterOverlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val vignettePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val frameGuidelinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val watermarkHintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private val frameScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(116, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val frameLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            12f,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }

    private var vignetteGradient: android.graphics.RadialGradient? = null
    private var vignetteOverlayRect: RectF? = null
    private var lastVignetteKey: Float = -1f

    private var renderModel = PreviewOverlayRenderModel(
        gridMode = CompositionGridMode.OFF,
        isGridVisible = false,
        countdownLabel = null,
        isCountdownVisible = false
    )

    init {
        isClickable = false
        isFocusable = false
        importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO
        visibility = GONE
    }

    internal fun render(model: PreviewOverlayRenderModel) {
        if (renderModel == model) {
            return
        }
        renderModel = model
        visibility = if (model.isVisible) VISIBLE else GONE
        prepareVignetteCache(model)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        prepareVignetteCache(renderModel)
    }

    private fun prepareVignetteCache(model: PreviewOverlayRenderModel) {
        val overlay = model.effectModel?.filterOverlay
        if (overlay == null || overlay.vignetteStrength <= 0f || width <= 0 || height <= 0) {
            vignetteGradient = null
            vignetteOverlayRect = null
            lastVignetteKey = -1f
            return
        }
        val cx = width / 2f
        val cy = height / 2f
        val radius = min(width, height) * 0.7f
        val vignetteKey = overlay.vignetteStrength
        if (vignetteKey != lastVignetteKey || vignetteGradient == null) {
            vignetteGradient = android.graphics.RadialGradient(
                cx, cy, radius,
                intArrayOf(Color.TRANSPARENT, Color.argb((overlay.vignetteStrength * 180).toInt(), 0, 0, 0)),
                floatArrayOf(0.4f, 1f),
                android.graphics.Shader.TileMode.CLAMP
            )
            lastVignetteKey = vignetteKey
        }
        vignetteOverlayRect = RectF(0f, 0f, width.toFloat(), height.toFloat())
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (renderModel.isGridVisible) {
            drawGrid(canvas, renderModel.gridMode)
        }
        renderModel.effectModel?.filterOverlay?.let { drawFilterOverlay(canvas, it) }
        renderModel.effectModel?.frameGuideline?.let { drawFrameGuideline(canvas, it) }
        renderModel.frame?.let { drawPreviewFrame(canvas, it) }
        renderModel.effectModel?.watermarkHint?.let { drawWatermarkHint(canvas, it) }
        if (renderModel.isCountdownVisible) {
            drawCountdown(canvas, renderModel.countdownLabel.orEmpty())
        }
    }

    private fun drawGrid(
        canvas: Canvas,
        gridMode: CompositionGridMode
    ) {
        when (gridMode) {
            CompositionGridMode.OFF -> Unit
            CompositionGridMode.RULE_OF_THIRDS -> {
                drawGridLines(canvas, listOf(1f / 3f, 2f / 3f))
            }
            CompositionGridMode.GOLDEN_RATIO -> {
                drawGridLines(canvas, listOf(0.38196602f, 0.61803395f))
            }
        }
    }

    private fun drawGridLines(
        canvas: Canvas,
        fractions: List<Float>
    ) {
        val widthValue = width.toFloat()
        val heightValue = height.toFloat()
        fractions.forEach { fraction ->
            val x = widthValue * fraction
            val y = heightValue * fraction
            canvas.drawLine(x, 0f, x, heightValue, gridPaint)
            canvas.drawLine(0f, y, widthValue, y, gridPaint)
        }

        val safeInset = 18f * density
        canvas.drawRect(
            RectF(
                safeInset,
                safeInset,
                widthValue - safeInset,
                heightValue - safeInset
            ),
            gridEmphasisPaint
        )
    }

    private fun drawCountdown(
        canvas: Canvas,
        countdownLabel: String
    ) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = min(width, height) * 0.14f
        canvas.drawCircle(centerX, centerY, radius, countdownBubblePaint)
        val baseline = centerY - (countdownTextPaint.ascent() + countdownTextPaint.descent()) / 2f
        canvas.drawText(countdownLabel, centerX, baseline, countdownTextPaint)
    }

    private fun drawFilterOverlay(canvas: Canvas, spec: FilterOverlaySpec) {
        if (spec.tintAlpha <= 0f) return
        filterOverlayPaint.color = spec.tintColor
        filterOverlayPaint.alpha = (spec.tintAlpha * 255).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), filterOverlayPaint)

        val gradient = vignetteGradient
        val rect = vignetteOverlayRect
        if (spec.vignetteStrength > 0f && gradient != null && rect != null) {
            vignettePaint.shader = gradient
            canvas.drawRect(rect, vignettePaint)
            vignettePaint.shader = null
        }
    }

    private fun drawFrameGuideline(canvas: Canvas, spec: FrameGuidelineSpec) {
        frameGuidelinePaint.color = spec.borderColor
        frameGuidelinePaint.alpha = (spec.borderAlpha * 255).toInt().coerceIn(0, 255)
        val ratio = spec.ratio.width.toFloat() / spec.ratio.height.toFloat()
        val viewRatio = width.toFloat() / height.toFloat()
        val rect = if (ratio > viewRatio) {
            val w = width.toFloat()
            val h = w / ratio
            val top = (height.toFloat() - h) / 2f
            RectF(0f, top, w, top + h)
        } else {
            val h = height.toFloat()
            val w = h * ratio
            val left = (width.toFloat() - w) / 2f
            RectF(left, 0f, left + w, h)
        }
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawWatermarkHint(canvas: Canvas, spec: WatermarkHintSpec) {
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val padding = 16f * density
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT -> {
                x = padding
                y = padding + watermarkHintPaint.textSize
            }
            WatermarkTextPlacement.TOP_RIGHT -> {
                x = width.toFloat() - padding
                y = padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = padding
                y = height.toFloat() - padding
            }
            WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = width.toFloat() - padding
                y = height.toFloat() - padding
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = width / 2f
                y = height.toFloat() - padding
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
    }

    private fun drawPreviewFrame(canvas: Canvas, frame: PreviewFrameRenderModel) {
        val rect = computePreviewFrameRect(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frame.ratio.width,
            ratioHeight = frame.ratio.height,
            horizontalPaddingPx = 12f * density
        )
        if (frame.dimOutsideFrame) {
            val outsidePath = android.graphics.Path().apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
                addRect(rect, android.graphics.Path.Direction.CW)
            }
            canvas.drawPath(outsidePath, frameScrimPaint)
        }
        canvas.drawRect(rect, frameGuidelinePaint)
        canvas.drawText(frame.label, rect.left + 10f * density, rect.top + 20f * density, frameLabelPaint)
    }
}

internal fun computePreviewFrameRect(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int,
    ratioHeight: Int,
    horizontalPaddingPx: Float = 0f,
    topInsetPx: Float = 0f,
    bottomInsetPx: Float = 0f
): RectF {
    val availableLeft = horizontalPaddingPx
    val availableTop = topInsetPx
    val availableRight = viewWidth - horizontalPaddingPx
    val availableBottom = viewHeight - bottomInsetPx
    val availableWidth = (availableRight - availableLeft).coerceAtLeast(1f)
    val availableHeight = (availableBottom - availableTop).coerceAtLeast(1f)
    val targetRatio = ratioWidth.toFloat() / ratioHeight.toFloat()
    val availableRatio = availableWidth / availableHeight
    return if (targetRatio > availableRatio) {
        val w = availableWidth
        val h = w / targetRatio
        val top = availableTop + (availableHeight - h) / 2f
        RectF(availableLeft, top, availableRight, top + h)
    } else {
        val h = availableHeight
        val w = h * targetRatio
        val left = availableLeft + (availableWidth - w) / 2f
        RectF(left, availableTop, left + w, availableBottom)
    }
}
