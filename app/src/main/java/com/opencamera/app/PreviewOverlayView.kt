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
import com.opencamera.core.effect.WatermarkPreviewShape
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

    private val watermarkBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    private val frameScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(140, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val reticleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val reticleTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private var vignetteGradient: android.graphics.RadialGradient? = null
    private var vignetteOverlayRect: RectF? = null
    private var lastVignetteKey: Float = -1f

    private var focusReticle: FocusReticleRenderModel? = null

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

    internal fun updateFocusReticle(model: FocusReticleRenderModel?) {
        focusReticle = model
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
        renderModel.effectModel?.filterOverlay?.let { drawFilterOverlay(canvas, it) }
        renderModel.effectModel?.frameGuideline?.let { drawFrameGuideline(canvas, it) }
        renderModel.frame?.let { drawPreviewFrame(canvas, it) }
        if (renderModel.isGridVisible) {
            drawGrid(canvas, renderModel.gridMode)
        }
        renderModel.effectModel?.watermarkHint?.let { drawWatermarkHint(canvas, it) }
        if (renderModel.isCountdownVisible) {
            drawCountdown(canvas, renderModel.countdownLabel.orEmpty())
        }
        drawFocusReticle(canvas)
    }

    private fun activeContentGeometry(): PreviewContentGeometry {
        val frameRatio = renderModel.frame?.ratio
            ?: renderModel.effectModel?.frameGuideline?.ratio
        return previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frameRatio?.width ?: 0,
            ratioHeight = frameRatio?.height ?: 0
        )
    }

    private fun activeFrameRectOrFullView(): RectF {
        return activeContentGeometry().activeFrameRect
    }

    internal fun currentActiveFrameRectOrNull(): RectF? {
        val hasFrame = renderModel.frame?.ratio != null
            || renderModel.effectModel?.frameGuideline?.ratio != null
        if (!hasFrame) return null
        return activeContentGeometry().activeFrameRect
    }

    private fun drawGrid(
        canvas: Canvas,
        gridMode: CompositionGridMode
    ) {
        val bounds = activeFrameRectOrFullView()
        when (gridMode) {
            CompositionGridMode.OFF -> Unit
            CompositionGridMode.RULE_OF_THIRDS -> {
                drawGridLines(canvas, listOf(1f / 3f, 2f / 3f), bounds)
            }
            CompositionGridMode.GOLDEN_RATIO -> {
                drawGridLines(canvas, listOf(0.38196602f, 0.61803395f), bounds)
            }
        }
    }

    private fun drawGridLines(
        canvas: Canvas,
        fractions: List<Float>,
        bounds: RectF
    ) {
        val segments = gridLinePositions(
            bounds.left, bounds.top, bounds.width(), bounds.height(), fractions
        )
        segments.forEach { seg ->
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, gridPaint)
        }
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
        val rect = previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = spec.ratio.width,
            ratioHeight = spec.ratio.height
        ).activeFrameRect
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawWatermarkHint(canvas: Canvas, spec: WatermarkHintSpec) {
        when (spec.shape) {
            WatermarkPreviewShape.FOUR_BORDER -> drawWatermarkFourBorderHint(canvas, spec)
            WatermarkPreviewShape.TEXT_ONLY,
            WatermarkPreviewShape.BACKED_TEXT,
            WatermarkPreviewShape.EXPANDED_FRAME -> drawWatermarkTextHint(canvas, spec)
        }
    }

    private fun drawWatermarkTextHint(canvas: Canvas, spec: WatermarkHintSpec) {
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

    private fun drawWatermarkFourBorderHint(canvas: Canvas, spec: WatermarkHintSpec) {
        val inset = 10f * density
        val rect = activeFrameRectOrFullView()
        val borderRect = RectF(
            rect.left + inset,
            rect.top + inset,
            rect.right - inset,
            rect.bottom - inset
        )
        watermarkBorderPaint.alpha = (spec.opacity * 255 * 0.7f).toInt().coerceIn(0, 255)
        canvas.drawRect(borderRect, watermarkBorderPaint)

        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val padding = 16f * density
        val textY = borderRect.bottom - padding
        watermarkHintPaint.textAlign = when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT,
            WatermarkTextPlacement.BOTTOM_LEFT -> Paint.Align.LEFT
            WatermarkTextPlacement.TOP_RIGHT,
            WatermarkTextPlacement.BOTTOM_RIGHT -> Paint.Align.RIGHT
            WatermarkTextPlacement.BOTTOM_CENTER -> Paint.Align.CENTER
        }
        val textX = when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT,
            WatermarkTextPlacement.BOTTOM_LEFT -> borderRect.left + padding
            WatermarkTextPlacement.TOP_RIGHT,
            WatermarkTextPlacement.BOTTOM_RIGHT -> borderRect.right - padding
            WatermarkTextPlacement.BOTTOM_CENTER -> borderRect.centerX()
        }
        canvas.drawText(spec.previewText, textX, textY, watermarkHintPaint)
    }

    private fun drawPreviewFrame(canvas: Canvas, frame: PreviewFrameRenderModel) {
        val rect = previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frame.ratio.width,
            ratioHeight = frame.ratio.height
        ).activeFrameRect
        if (frame.dimOutsideFrame) {
            val outsidePath = android.graphics.Path().apply {
                fillType = android.graphics.Path.FillType.EVEN_ODD
                addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
                addRect(rect, android.graphics.Path.Direction.CW)
            }
            canvas.drawPath(outsidePath, frameScrimPaint)
        }
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawFocusReticle(canvas: Canvas) {
        val model = focusReticle ?: return
        val cx = model.normalizedX.coerceIn(0f, 1f) * width
        val cy = model.normalizedY.coerceIn(0f, 1f) * height
        val radius = 24f * density
        val tickLength = 8f * density

        when (model.status) {
            FocusReticleStatus.REQUESTED -> {
                reticleRingPaint.color = Color.rgb(255, 191, 0)
                canvas.drawCircle(cx, cy, radius, reticleRingPaint)
            }
            FocusReticleStatus.SUCCEEDED -> {
                reticleRingPaint.color = Color.WHITE
                canvas.drawCircle(cx, cy, radius, reticleRingPaint)
            }
            FocusReticleStatus.DEGRADED -> {
                reticleRingPaint.color = Color.rgb(255, 191, 0)
                canvas.drawCircle(cx, cy, radius, reticleRingPaint)
                reticleTickPaint.color = Color.rgb(255, 191, 0)
                // Short tick marks at cardinal positions
                canvas.drawLine(cx, cy - radius - tickLength, cx, cy - radius, reticleTickPaint)
                canvas.drawLine(cx, cy + radius, cx, cy + radius + tickLength, reticleTickPaint)
                canvas.drawLine(cx - radius - tickLength, cy, cx - radius, cy, reticleTickPaint)
                canvas.drawLine(cx + radius, cy, cx + radius + tickLength, cy, reticleTickPaint)
            }
            FocusReticleStatus.FAILED, FocusReticleStatus.UNSUPPORTED -> {
                reticleRingPaint.color = Color.argb(128, 128, 128, 128)
                canvas.drawCircle(cx, cy, radius, reticleRingPaint)
            }
        }
    }
}

internal enum class PreviewDisplayOrientation { PORTRAIT, LANDSCAPE }

/**
 * Single source of truth for preview overlay geometry.
 *
 * [contentRect] describes the actual preview content area (full view for fillCenter).
 * [activeFrameRect] describes the captured frame inside [contentRect].
 *
 * All overlay components (grid, frame outline, dim region, tap-to-focus)
 * must read from this helper instead of computing independent rects.
 */
internal data class PreviewContentGeometry(
    val viewWidth: Int,
    val viewHeight: Int,
    val contentRect: RectF,
    val activeFrameRect: RectF
) {
    val frameCenterX: Float get() = activeFrameRect.centerX()
    val frameCenterY: Float get() = activeFrameRect.centerY()
    val contentCenterX: Float get() = contentRect.centerX()
    val contentCenterY: Float get() = contentRect.centerY()
}

internal enum class FocusReticleStatus {
    REQUESTED,
    SUCCEEDED,
    DEGRADED,
    FAILED,
    UNSUPPORTED
}

internal data class FocusReticleRenderModel(
    val normalizedX: Float,
    val normalizedY: Float,
    val status: FocusReticleStatus
)

/**
 * Build [PreviewContentGeometry] for the given view dimensions and optional frame ratio.
 *
 * When [ratioWidth] / [ratioHeight] are both > 0 the active frame is a centered
 * sub-rect of [contentRect] matching that ratio. Otherwise the active frame
 * equals [contentRect] (full-view capture).
 *
 * UI chrome must not shrink this geometry. Toolbars and capture controls may
 * overlap the preview, but the visible capture frame must stay centered in the
 * same full preview content rect used by saved JPEG center-crop postprocessing.
 */
internal fun previewContentGeometry(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int = 0,
    ratioHeight: Int = 0
): PreviewContentGeometry {
    val contentRect = RectF(
        0f,
        0f,
        viewWidth.coerceAtLeast(0).toFloat(),
        viewHeight.coerceAtLeast(0).toFloat()
    )
    val activeFrameRect = if (ratioWidth > 0 && ratioHeight > 0) {
        val fr = computeFrameRect(
            viewWidth, viewHeight,
            ratioWidth, ratioHeight
        )
        RectF(fr.left, fr.top, fr.right, fr.bottom)
    } else {
        RectF(contentRect)
    }
    return PreviewContentGeometry(
        viewWidth = viewWidth,
        viewHeight = viewHeight,
        contentRect = contentRect,
        activeFrameRect = activeFrameRect
    )
}

internal data class OrientedFrameRatio(
    val orientedWidth: Int,
    val orientedHeight: Int
)

internal fun orientedFrameRatio(
    ratioWidth: Int,
    ratioHeight: Int,
    orientation: PreviewDisplayOrientation
): OrientedFrameRatio {
    if (ratioWidth == ratioHeight) return OrientedFrameRatio(1, 1)
    return when (orientation) {
        PreviewDisplayOrientation.PORTRAIT -> OrientedFrameRatio(
            orientedWidth = minOf(ratioWidth, ratioHeight),
            orientedHeight = maxOf(ratioWidth, ratioHeight)
        )
        PreviewDisplayOrientation.LANDSCAPE -> OrientedFrameRatio(
            orientedWidth = maxOf(ratioWidth, ratioHeight),
            orientedHeight = minOf(ratioWidth, ratioHeight)
        )
    }
}

internal data class FrameRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
}

internal fun computeFrameRect(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int,
    ratioHeight: Int
): FrameRect {
    val availableLeft = 0f
    val availableTop = 0f
    val availableRight = viewWidth.toFloat()
    val availableBottom = viewHeight.toFloat()
    val availableWidth = (availableRight - availableLeft).coerceAtLeast(1f)
    val availableHeight = (availableBottom - availableTop).coerceAtLeast(1f)
    val orientation = if (viewWidth <= viewHeight) {
        PreviewDisplayOrientation.PORTRAIT
    } else {
        PreviewDisplayOrientation.LANDSCAPE
    }
    val oriented = orientedFrameRatio(ratioWidth, ratioHeight, orientation)
    val targetRatio = oriented.orientedWidth.toFloat() / oriented.orientedHeight.toFloat()
    val availableRatio = availableWidth / availableHeight
    return if (targetRatio > availableRatio) {
        val w = availableWidth
        val h = w / targetRatio
        val top = availableTop + (availableHeight - h) / 2f
        FrameRect(availableLeft, top, availableRight, top + h)
    } else {
        val h = availableHeight
        val w = h * targetRatio
        val left = availableLeft + (availableWidth - w) / 2f
        FrameRect(left, availableTop, left + w, availableBottom)
    }
}

internal fun computePreviewFrameRect(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int,
    ratioHeight: Int
): RectF {
    val r = computeFrameRect(viewWidth, viewHeight, ratioWidth, ratioHeight)
    return RectF(r.left, r.top, r.right, r.bottom)
}

internal data class GridLineSegment(
    val x1: Float, val y1: Float,
    val x2: Float, val y2: Float
)

internal fun gridLinePositions(
    frameLeft: Float,
    frameTop: Float,
    frameWidth: Float,
    frameHeight: Float,
    fractions: List<Float>
): List<GridLineSegment> {
    return fractions.flatMap { fraction ->
        val x = frameLeft + frameWidth * fraction
        val y = frameTop + frameHeight * fraction
        listOf(
            GridLineSegment(x, frameTop, x, frameTop + frameHeight),
            GridLineSegment(frameLeft, y, frameLeft + frameWidth, y)
        )
    }
}
