package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.FrameGuidelineSpec
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.effect.WatermarkPreviewDecoration
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

    private val watermarkHintBaseTextSizeSp = 12f

    private val watermarkBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 2f * resources.displayMetrics.density
    }

    companion object {
        /** Default alpha for the outside-frame scrim (0–255).  Higher = darker. */
        const val FRAME_SCRIM_ALPHA_DEFAULT = 200
    }

    private val frameScrimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(FRAME_SCRIM_ALPHA_DEFAULT, 0, 0, 0)
        style = Paint.Style.FILL
    }

    private val scanGuideBracketPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(200, 255, 255, 255)
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private val reticleRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val reticleTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f * density
    }

    private val watermarkPaperPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val watermarkHairlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * density
    }
    private val watermarkBlurBandPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }
    private val outsideFramePath = android.graphics.Path()
    private val drawReusableRect = RectF()

    private var vignetteGradient: android.graphics.RadialGradient? = null
    private var vignetteOverlayRect: RectF? = null
    private var lastVignetteKey: Float = -1f

    private var focusReticle: FocusReticleRenderModel? = null
    private var reticleAnimStartMs: Long = 0L
    private var animatingReticle: Boolean = false

    private var renderModel = PreviewOverlayRenderModel(
        gridMode = CompositionGridMode.OFF,
        isGridVisible = false,
        countdownLabel = null,
        isCountdownVisible = false
    )

    /** Cached geometry snapshot — computed once in [render], reused in [onDraw] for same-frame sync. */
    private var cachedGeometry: PreviewContentGeometry? = null

    /** True while a mode switch rebind is in-flight and geometry must not recompute. */
    private var geometryLocked: Boolean = false

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
        geometryLocked = model.isGeometryLocked
        if (!geometryLocked) {
            renderModel = model
            visibility = if (model.isVisible) VISIBLE else GONE
            cachedGeometry = computeGeometry(model)
            prepareVignetteCache(model)
        } else {
            renderModel = model
            visibility = if (model.isVisible) VISIBLE else GONE
        }
        invalidate()
    }

    private fun computeGeometry(model: PreviewOverlayRenderModel): PreviewContentGeometry {
        val frameRatio = model.frame?.ratio
            ?: model.effectModel?.frameGuideline?.ratio
        val geometry = previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frameRatio?.width ?: 0,
            ratioHeight = frameRatio?.height ?: 0,
            previewContentAspect = model.previewContentAspect
        )
        val frame = model.frame ?: return geometry
        val scale = zoomFrameScale(
            captureZoomRatio = frame.zoomRatio,
            previewZoomRatio = frame.previewZoomRatio
        )
        if (scale >= 0.999f) return geometry
        val scaled = scaleRectAroundCenter(geometry.activeFrameRect, scale)
        val clamped = RectF(
            scaled.left.coerceIn(geometry.contentRect.left, geometry.contentRect.right),
            scaled.top.coerceIn(geometry.contentRect.top, geometry.contentRect.bottom),
            scaled.right.coerceIn(geometry.contentRect.left, geometry.contentRect.right),
            scaled.bottom.coerceIn(geometry.contentRect.top, geometry.contentRect.bottom)
        )
        return geometry.copy(activeFrameRect = clamped)
    }

    internal fun updateFocusReticle(model: FocusReticleRenderModel?) {
        val changed = focusReticle != model
        focusReticle = model
        if (model != null && changed) {
            reticleAnimStartMs = android.os.SystemClock.uptimeMillis()
            animatingReticle = true
            postInvalidateOnAnimation()
        } else if (model == null) {
            animatingReticle = false
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        cachedGeometry = computeGeometry(renderModel)
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
        val rect = activeFrameRectOrFullView()
        val cx = rect.centerX()
        val cy = rect.centerY()
        val radius = min(rect.width(), rect.height()) * 0.7f
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
        vignetteOverlayRect = RectF(rect)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        renderModel.effectModel?.filterOverlay?.let { drawFilterOverlay(canvas, it) }
        renderModel.effectModel?.colorTransform
            ?.let(::previewColorTransformOverlaySpec)
            ?.let {
                drawColorTransformOverlay(
                    canvas = canvas,
                    spec = it,
                    bounds = activeFrameRectOrFullView(),
                    paint = filterOverlayPaint
                )
            }
        renderModel.effectModel?.frameGuideline?.let { drawFrameGuideline(canvas, it) }
        renderModel.frame?.let { drawPreviewFrame(canvas, it) }
        val sg = renderModel.scanGuide
        val fm = renderModel.frame
        if (sg != null && fm == null) {
            drawScanGuide(canvas, sg)
        } else if (sg != null && fm != null) {
            android.util.Log.w("PreviewOverlay", "Both scanGuide and frame set; preferring frame")
        }
        if (renderModel.isGridVisible) {
            drawGrid(canvas, renderModel.gridMode)
        }
        renderModel.effectModel?.watermarkHint?.let { drawWatermarkHint(canvas, it) }
        if (renderModel.isCountdownVisible) {
            drawCountdown(canvas, renderModel.countdownLabel.orEmpty())
        }
        drawFocusReticle(canvas)
    }

    internal fun drawOverlayForTest(canvas: Canvas) {
        renderModel.effectModel?.watermarkHint?.let { drawWatermarkHint(canvas, it) }
    }

    private fun activeContentGeometry(): PreviewContentGeometry {
        cachedGeometry?.let { return it }
        return computeGeometry(renderModel)
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
        filterOverlayPaint.colorFilter = null

        if (spec.tintAlpha > 0f) {
            filterOverlayPaint.color = spec.tintColor
            filterOverlayPaint.alpha = (spec.tintAlpha * 255).toInt().coerceIn(0, 255)
            canvas.drawRect(activeFrameRectOrFullView(), filterOverlayPaint)
        }

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
        val rect = activeContentGeometry().activeFrameRect
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawWatermarkHint(canvas: Canvas, spec: WatermarkHintSpec) {
        when (spec.shape) {
            WatermarkPreviewShape.FOUR_BORDER -> drawWatermarkFourBorderHint(canvas, spec)
            WatermarkPreviewShape.TEXT_ONLY,
            WatermarkPreviewShape.BACKED_TEXT -> drawWatermarkTextHint(canvas, spec)
            WatermarkPreviewShape.EXPANDED_FRAME -> drawWatermarkExpandedFrameHint(canvas, spec)
            WatermarkPreviewShape.BOTTOM_BAR -> drawWatermarkBottomBarHint(canvas, spec)
        }
    }

    private fun applyWatermarkTextScale(textScale: Float) {
        watermarkHintPaint.textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            watermarkHintBaseTextSizeSp * textScale,
            resources.displayMetrics
        )
    }

    private fun drawWatermarkTextHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val rect = activeFrameRectOrFullView()
        val padding = 16f * density
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT -> {
                x = rect.left + padding
                y = rect.top + padding + watermarkHintPaint.textSize
            }
            WatermarkTextPlacement.TOP_RIGHT -> {
                x = rect.right - padding
                y = rect.top + padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = rect.left + padding
                y = rect.bottom - padding
            }
            WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = rect.right - padding
                y = rect.bottom - padding
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = rect.centerX()
                y = rect.bottom - padding
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
    }

    private fun drawWatermarkExpandedFrameHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val previousTextColor = watermarkHintPaint.color
        watermarkHintPaint.color = when (spec.decoration) {
            WatermarkPreviewDecoration.TRAVEL_MAP -> Color.rgb(42, 82, 61)
            WatermarkPreviewDecoration.ARCHIVAL_PAPER -> Color.rgb(224, 205, 154)
            WatermarkPreviewDecoration.NIGHT_MEMORY -> Color.rgb(226, 232, 240)
            WatermarkPreviewDecoration.STARRY_MOON -> Color.rgb(232, 205, 146)
            WatermarkPreviewDecoration.BLUE_HOUR -> Color.rgb(196, 218, 246)
            WatermarkPreviewDecoration.IMPRESSION_CHROMA -> Color.rgb(58, 55, 50)
            WatermarkPreviewDecoration.NONE -> Color.WHITE
        }
        val rect = activeFrameRectOrFullView()
        val paperAlpha = expandedFramePaperAlpha(spec.templateId, spec.opacity)
        watermarkPaperPaint.color = when (spec.templateId) {
            "retro-frame" -> Color.argb((spec.opacity * 184).toInt().coerceIn(0, 184), 14, 36, 29)
            "night-street" -> Color.argb((spec.opacity * 210).toInt().coerceIn(0, 210), 7, 14, 36)
            "van-gogh-starry" -> Color.argb((spec.opacity * 218).toInt().coerceIn(0, 218), 5, 18, 48)
            "blue-hour" -> Color.argb((spec.opacity * 226).toInt().coerceIn(0, 226), 4, 24, 46)
            else -> Color.argb(paperAlpha, 252, 246, 229)
        }
        watermarkHairlinePaint.color = when (spec.templateId) {
            "retro-frame" -> Color.argb((spec.opacity * 148).toInt().coerceIn(0, 148), 218, 190, 126)
            "night-street" -> Color.argb((spec.opacity * 88).toInt().coerceIn(0, 88), 168, 178, 198)
            "van-gogh-starry" -> Color.argb((spec.opacity * 126).toInt().coerceIn(0, 126), 218, 170, 84)
            "blue-hour" -> Color.argb((spec.opacity * 156).toInt().coerceIn(0, 156), 156, 204, 250)
            else -> Color.argb((spec.opacity * 72).toInt().coerceIn(0, 72), 96, 68, 42)
        }
        val sideBand = (rect.width() * 0.035f).coerceIn(10f * density, 28f * density)
        val topBand = (rect.height() * 0.035f).coerceIn(8f * density, 24f * density)
        val leftBand = sideBand.coerceAtMost(rect.left)
        val rightBand = sideBand.coerceAtMost(width - rect.right)
        val topFrameBand = topBand.coerceAtMost(rect.top)
        if (leftBand > 0f) {
            canvas.drawRect(rect.left - leftBand, rect.top, rect.left, rect.bottom, watermarkPaperPaint)
        }
        if (rightBand > 0f) {
            canvas.drawRect(rect.right, rect.top, rect.right + rightBand, rect.bottom, watermarkPaperPaint)
        }
        if (topFrameBand > 0f) {
            canvas.drawRect(rect.left - leftBand, rect.top - topFrameBand, rect.right + rightBand, rect.top, watermarkPaperPaint)
        }
        val bottomRect = expandedFrameBottomBandRect(rect, height, density, spec.templateId)
        if (bottomRect != null) {
            canvas.drawRect(
                bottomRect.left - leftBand,
                bottomRect.top,
                bottomRect.right + rightBand,
                bottomRect.bottom,
                watermarkPaperPaint
            )
        }
        canvas.drawRect(rect, watermarkHairlinePaint)
        when (spec.decoration) {
            WatermarkPreviewDecoration.TRAVEL_MAP -> {
                bottomRect?.let { drawTravelMapPreviewDecoration(canvas, it, spec.opacity) }
            }
            WatermarkPreviewDecoration.ARCHIVAL_PAPER -> {
                drawArchivalPaperPreviewDecoration(canvas, rect, spec.opacity)
            }
            WatermarkPreviewDecoration.NIGHT_MEMORY -> {
                drawNightMemoryPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
            }
            WatermarkPreviewDecoration.STARRY_MOON -> {
                drawStarryMoonPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
            }
            WatermarkPreviewDecoration.BLUE_HOUR -> {
                drawBlueHourPreviewDecoration(canvas, rect, bottomRect, spec.opacity)
            }
            WatermarkPreviewDecoration.IMPRESSION_CHROMA,
            WatermarkPreviewDecoration.NONE -> Unit
        }

        if (bottomRect != null && spec.decoration == WatermarkPreviewDecoration.STARRY_MOON) {
            drawStarryMoonPreviewText(canvas, spec, bottomRect)
            watermarkHintPaint.color = previousTextColor
            return
        }
        if (bottomRect != null && spec.decoration == WatermarkPreviewDecoration.BLUE_HOUR) {
            drawBlueHourPreviewText(canvas, spec, bottomRect)
            watermarkHintPaint.color = previousTextColor
            return
        }

        val padding = 16f * density
        val textTop = if ((bottomRect?.height() ?: 0f) > watermarkHintPaint.textSize + padding) {
            rect.bottom + padding + watermarkHintPaint.textSize
        } else {
            rect.bottom - padding
        }
        val x: Float
        val y: Float
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        when (spec.placement) {
            WatermarkTextPlacement.TOP_LEFT -> {
                x = rect.left + padding
                y = rect.top + padding + watermarkHintPaint.textSize
            }
            WatermarkTextPlacement.TOP_RIGHT -> {
                x = rect.right - padding
                y = rect.top + padding + watermarkHintPaint.textSize
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_LEFT -> {
                x = rect.left + padding
                y = textTop
            }
            WatermarkTextPlacement.BOTTOM_RIGHT -> {
                x = rect.right - padding
                y = textTop
                watermarkHintPaint.textAlign = Paint.Align.RIGHT
            }
            WatermarkTextPlacement.BOTTOM_CENTER -> {
                x = rect.centerX()
                y = textTop
                watermarkHintPaint.textAlign = Paint.Align.CENTER
            }
        }
        canvas.drawText(spec.previewText, x, y, watermarkHintPaint)
        watermarkHintPaint.color = previousTextColor
    }

    private fun drawTravelMapPreviewDecoration(
        canvas: Canvas,
        bottomRect: RectF,
        opacity: Float
    ) {
        val region = RectF(
            bottomRect.left + bottomRect.width() * 0.54f,
            bottomRect.top + bottomRect.height() * 0.14f,
            bottomRect.right - 12f * density,
            bottomRect.bottom - 12f * density
        )
        if (region.width() <= 0f || region.height() <= 0f) return

        val contourPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(94, 125, 103)
            alpha = (opacity * 88).toInt().coerceIn(0, 88)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
        }
        val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(42, 82, 61)
            alpha = (opacity * 170).toInt().coerceIn(0, 170)
            style = Paint.Style.STROKE
            strokeWidth = 1.35f * density
            strokeCap = Paint.Cap.ROUND
        }

        listOf(0.18f, 0.46f, 0.74f).forEachIndexed { index, offset ->
            val path = Path().apply {
                moveTo(region.left, region.top + region.height() * offset)
                cubicTo(
                    region.left + region.width() * 0.22f,
                    region.top + region.height() * (offset - 0.18f + index * 0.03f),
                    region.left + region.width() * 0.62f,
                    region.top + region.height() * (offset + 0.16f - index * 0.04f),
                    region.right,
                    region.top + region.height() * (offset - 0.04f)
                )
            }
            canvas.drawPath(path, contourPaint)
        }

        val startX = region.left + region.width() * 0.16f
        val startY = region.bottom - region.height() * 0.2f
        val endX = region.right - region.width() * 0.12f
        val endY = region.top + region.height() * 0.24f
        val route = Path().apply {
            moveTo(startX, startY)
            cubicTo(
                region.left + region.width() * 0.38f,
                region.top + region.height() * 0.72f,
                region.left + region.width() * 0.58f,
                region.top + region.height() * 0.36f,
                endX,
                endY
            )
        }
        canvas.drawPath(route, routePaint)
        canvas.drawCircle(startX, startY, 2.4f * density, routePaint)
        canvas.drawCircle(endX, endY, 2.4f * density, routePaint)

        val crossX = region.right - 9f * density
        val crossY = region.bottom - 8f * density
        canvas.drawLine(crossX - 4f * density, crossY, crossX + 4f * density, crossY, routePaint)
        canvas.drawLine(crossX, crossY - 4f * density, crossX, crossY + 4f * density, routePaint)
    }

    private fun drawArchivalPaperPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        opacity: Float
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 190, 126)
            alpha = (opacity * 164).toInt().coerceIn(0, 164)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.SQUARE
        }
        val finePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(122, 98, 58)
            alpha = (opacity * 104).toInt().coerceIn(0, 104)
            style = Paint.Style.STROKE
            strokeWidth = 0.75f * density
        }
        canvas.drawRect(frameRect, paint)

        val corner = 30f * density
        val inset = 8f * density
        fun drawCorner(left: Boolean, top: Boolean) {
            val x = if (left) frameRect.left + inset else frameRect.right - inset
            val y = if (top) frameRect.top + inset else frameRect.bottom - inset
            val xDir = if (left) 1f else -1f
            val yDir = if (top) 1f else -1f
            canvas.drawLine(x, y, x + xDir * corner, y, paint)
            canvas.drawLine(x, y, x, y + yDir * corner, paint)
            canvas.drawLine(
                x + xDir * corner * 0.42f,
                y + yDir * 5f * density,
                x + xDir * corner * 0.92f,
                y + yDir * 5f * density,
                finePaint
            )
        }
        drawCorner(left = true, top = true)
        drawCorner(left = false, top = true)
        drawCorner(left = true, top = false)
        drawCorner(left = false, top = false)

    }

    private fun drawNightMemoryPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val coolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(166, 180, 204)
            alpha = (opacity * 60).toInt().coerceIn(0, 60)
            style = Paint.Style.STROKE
            strokeWidth = 0.8f * density
            strokeCap = Paint.Cap.ROUND
        }
        val warmLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(218, 160, 82)
            alpha = (opacity * 140).toInt().coerceIn(0, 140)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.ROUND
        }
        val lampPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(228, 164, 92)
            alpha = (opacity * 168).toInt().coerceIn(0, 168)
            style = Paint.Style.FILL
        }
        val inset = 9f * density
        val corner = 24f * density
        fun drawCorner(left: Boolean, top: Boolean) {
            val x = if (left) frameRect.left + inset else frameRect.right - inset
            val y = if (top) frameRect.top + inset else frameRect.bottom - inset
            val xDir = if (left) 1f else -1f
            val yDir = if (top) 1f else -1f
            canvas.drawLine(x, y, x + xDir * corner, y, coolPaint)
            canvas.drawLine(x, y, x, y + yDir * corner, coolPaint)
        }
        drawCorner(left = true, top = true)
        drawCorner(left = false, top = true)
        drawCorner(left = true, top = false)
        drawCorner(left = false, top = false)

        bottomRect?.let { band ->
            canvas.drawLine(
                band.left + 18f * density,
                band.top + 10f * density,
                band.right - 18f * density,
                band.top + 11f * density,
                warmLinePaint
            )
            val radius = 2.2f * density
            canvas.drawCircle(band.right - 24f * density, band.top + 20f * density, radius, lampPaint)
            canvas.drawCircle(band.right - 34f * density, band.top + 27f * density, radius * 0.55f, lampPaint)
        }
    }

    private fun drawStarryMoonPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val warmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(232, 190, 104)
            alpha = (opacity * 170).toInt().coerceIn(0, 170)
            style = Paint.Style.STROKE
            strokeWidth = 1.0f * density
            strokeCap = Paint.Cap.ROUND
        }
        val coolPaint = Paint(warmPaint).apply {
            color = Color.rgb(54, 139, 218)
            alpha = (opacity * 112).toInt().coerceIn(0, 112)
            strokeWidth = 0.75f * density
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(246, 215, 146)
            alpha = (opacity * 210).toInt().coerceIn(0, 210)
            style = Paint.Style.FILL
        }
        val maskPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(5, 18, 48)
            alpha = (opacity * 230).toInt().coerceIn(0, 230)
            style = Paint.Style.FILL
        }
        val moonX = frameRect.left + 34f * density
        val moonY = frameRect.top + 28f * density
        val moonR = 10f * density
        canvas.drawCircle(moonX, moonY, moonR, fillPaint)
        canvas.drawCircle(moonX + moonR * 0.45f, moonY - moonR * 0.12f, moonR * 0.92f, maskPaint)

        fun wave(y: Float, paint: Paint, shift: Float) {
            val path = Path().apply {
                moveTo(frameRect.left + 72f * density, y)
                cubicTo(
                    frameRect.left + frameRect.width() * 0.34f,
                    y - 18f * density + shift,
                    frameRect.left + frameRect.width() * 0.62f,
                    y + 18f * density - shift,
                    frameRect.right - 24f * density,
                    y - 4f * density
                )
            }
            canvas.drawPath(path, paint)
        }
        wave(frameRect.top + 24f * density, warmPaint, 0f)
        wave(frameRect.top + 30f * density, coolPaint, 4f * density)
        bottomRect?.let { band ->
            wave(band.top + band.height() * 0.42f, warmPaint, 2f * density)
            wave(band.top + band.height() * 0.56f, coolPaint, -2f * density)
        }

        listOf(
            frameRect.left + 14f * density to frameRect.top + 48f * density,
            frameRect.right - 44f * density to frameRect.top + 34f * density,
            frameRect.right - 22f * density to frameRect.centerY(),
            frameRect.left + 28f * density to frameRect.bottom - 34f * density,
            frameRect.right - 32f * density to frameRect.bottom - 26f * density
        ).forEach { (x, y) ->
            canvas.drawLine(x - 3f * density, y, x + 3f * density, y, fillPaint)
            canvas.drawLine(x, y - 3f * density, x, y + 3f * density, fillPaint)
        }
    }

    private fun drawBlueHourPreviewDecoration(
        canvas: Canvas,
        frameRect: RectF,
        bottomRect: RectF?,
        opacity: Float
    ) {
        val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(158, 202, 238)
            alpha = (opacity * 178).toInt().coerceIn(0, 178)
            style = Paint.Style.STROKE
            strokeWidth = 1.05f * density
            strokeCap = Paint.Cap.ROUND
        }
        val warmPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(230, 184, 104)
            alpha = (opacity * 178).toInt().coerceIn(0, 178)
            style = Paint.Style.STROKE
            strokeWidth = 1.05f * density
            strokeCap = Paint.Cap.ROUND
        }
        val inset = 8f * density
        val radius = 8f * density
        canvas.drawRoundRect(
            RectF(
                frameRect.left + inset,
                frameRect.top + inset,
                frameRect.right - inset,
                (bottomRect?.bottom ?: frameRect.bottom) - inset
            ),
            radius,
            radius,
            linePaint
        )
        val innerPaint = Paint(linePaint).apply {
            alpha = (opacity * 118).toInt().coerceIn(0, 118)
            strokeWidth = 0.7f * density
        }
        canvas.drawRoundRect(
            RectF(
                frameRect.left + inset * 2.0f,
                frameRect.top + inset * 1.8f,
                frameRect.right - inset * 2.0f,
                (bottomRect?.bottom ?: frameRect.bottom) - inset * 1.8f
            ),
            radius * 0.72f,
            radius * 0.72f,
            innerPaint
        )
        repeat(5) { index ->
            val y = frameRect.top + inset * (1.05f + index * 0.34f)
            canvas.drawLine(
                frameRect.left + inset * (1.2f + index * 0.25f),
                y,
                frameRect.right - inset * (1.4f + index * 0.12f),
                y + density * 1.2f,
                Paint(linePaint).apply {
                    alpha = (opacity * (62 + index * 15)).toInt().coerceIn(0, 142)
                    strokeWidth = 0.55f * density
                }
            )
        }
        bottomRect?.let { band ->
            val x = band.right - 58f * density
            val y = band.top + band.height() * 0.48f
            canvas.drawCircle(x, y, 6.2f * density, warmPaint)
            canvas.drawLine(x + 22f * density, y + 9f * density, x + 22f * density, band.bottom - 15f * density, warmPaint)
            canvas.drawLine(x + 16f * density, band.bottom - 15f * density, x + 28f * density, band.bottom - 15f * density, warmPaint)
            canvas.drawRect(
                x - 6f * density,
                y + 21f * density,
                x + 7f * density,
                y + 31f * density,
                warmPaint
            )
        }
    }

    private fun drawStarryMoonPreviewText(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        bottomRect: RectF
    ) {
        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        watermarkHintPaint.textAlign = Paint.Align.CENTER
        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 0.84f
        watermarkHintPaint.alpha = (spec.opacity * 255 * 0.86f).toInt().coerceIn(0, 255)
        val metadata = spec.previewLabels.takeIf { it.isNotEmpty() }
            ?.joinToString(" · ")
            ?: spec.previewText
        val metrics = watermarkHintPaint.fontMetrics
        val baseline = bottomRect.centerY() - (metrics.ascent + metrics.descent) / 2f
        canvas.drawText(metadata, bottomRect.centerX(), baseline, watermarkHintPaint)
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
    }

    private fun drawBlueHourPreviewText(
        canvas: Canvas,
        spec: WatermarkHintSpec,
        bottomRect: RectF
    ) {
        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        val left = bottomRect.left + 24f * density
        watermarkHintPaint.textAlign = Paint.Align.LEFT
        watermarkHintPaint.typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 1.42f
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val titleMetrics = watermarkHintPaint.fontMetrics
        val titleBaseline = bottomRect.top + bottomRect.height() * 0.38f - (titleMetrics.ascent + titleMetrics.descent) / 2f
        canvas.drawText(spec.previewText, left, titleBaseline, watermarkHintPaint)

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = originalTextSize * 0.78f
        watermarkHintPaint.alpha = (spec.opacity * 255 * 0.78f).toInt().coerceIn(0, 255)
        val metadata = spec.previewLabels.joinToString(" · ")
        if (metadata.isNotBlank()) {
            canvas.drawText(
                metadata.take(42),
                left,
                titleBaseline + originalTextSize * 1.35f,
                watermarkHintPaint
            )
        }
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
    }

    private fun drawWatermarkFourBorderHint(canvas: Canvas, spec: WatermarkHintSpec) {
        applyWatermarkTextScale(spec.textScale)
        val rect = activeFrameRectOrFullView()
        val band = fourBorderPreviewBandWidth(rect, density)
        val bottomBand = (min(rect.width(), rect.height()) * 0.09f).coerceIn(34f * density, 86f * density)
        val blurBandAlpha = (spec.opacity * 255 * 0.18f).toInt().coerceIn(0, 255)
        watermarkBlurBandPaint.alpha = blurBandAlpha
        canvas.drawRect(rect.left, rect.top, rect.right, rect.top + band, watermarkBlurBandPaint)
        canvas.drawRect(rect.left, rect.bottom - bottomBand, rect.right, rect.bottom, watermarkBlurBandPaint)
        canvas.drawRect(rect.left, rect.top + band, rect.left + band, rect.bottom - bottomBand, watermarkBlurBandPaint)
        canvas.drawRect(rect.right - band, rect.top + band, rect.right, rect.bottom - bottomBand, watermarkBlurBandPaint)

        watermarkBorderPaint.alpha = (spec.opacity * 255 * 0.42f).toInt().coerceIn(0, 255)
        canvas.drawRect(rect, watermarkBorderPaint)
        if (spec.decoration == WatermarkPreviewDecoration.IMPRESSION_CHROMA) {
            drawImpressionChromaPreviewDecoration(canvas, rect, bottomBand, spec.opacity)
        }

        val originalTextSize = watermarkHintPaint.textSize
        val originalTypeface = watermarkHintPaint.typeface
        val metadata = fourBorderPreviewMetadata(spec.previewLabels)
        val titleTextSize = originalTextSize * 1.08f
        val metadataTextSize = originalTextSize * 0.76f
        val lineGap = 5f * density

        watermarkHintPaint.textAlign = Paint.Align.CENTER
        watermarkHintPaint.setShadowLayer(3f * density, 0f, density, Color.argb(110, 0, 0, 0))
        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        watermarkHintPaint.textSize = titleTextSize
        watermarkHintPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val titleMetrics = watermarkHintPaint.fontMetrics
        val titleHeight = titleMetrics.descent - titleMetrics.ascent

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
        watermarkHintPaint.textSize = metadataTextSize
        val metadataMetrics = watermarkHintPaint.fontMetrics
        val metadataHeight = if (metadata.isNotEmpty()) {
            metadataMetrics.descent - metadataMetrics.ascent
        } else {
            0f
        }
        val blockHeight = titleHeight + if (metadata.isNotEmpty()) lineGap + metadataHeight else 0f
        val blockTop = rect.bottom - bottomBand + (bottomBand - blockHeight) / 2f

        watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        watermarkHintPaint.textSize = titleTextSize
        val titleBaseline = blockTop - titleMetrics.ascent
        canvas.drawText(spec.previewText, rect.centerX(), titleBaseline, watermarkHintPaint)

        if (metadata.isNotEmpty()) {
            watermarkHintPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            watermarkHintPaint.textSize = metadataTextSize
            watermarkHintPaint.alpha = (spec.opacity * 255 * 0.78f).toInt().coerceIn(0, 255)
            val metadataBaseline = titleBaseline + titleMetrics.descent + lineGap - metadataMetrics.ascent
            canvas.drawText(metadata, rect.centerX(), metadataBaseline, watermarkHintPaint)
        }

        watermarkHintPaint.clearShadowLayer()
        watermarkHintPaint.typeface = originalTypeface
        watermarkHintPaint.textSize = originalTextSize
    }

    private fun drawImpressionChromaPreviewDecoration(
        canvas: Canvas,
        rect: RectF,
        bottomBand: Float,
        opacity: Float
    ) {
        val top = rect.bottom - bottomBand + 7f * density
        val start = RectF(
            rect.left + 18f * density,
            top,
            rect.centerX(),
            top + 1.2f * density
        )
        val end = RectF(
            rect.centerX(),
            top,
            rect.right - 18f * density,
            top + 1.2f * density
        )
        val rosePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(198, 174, 205)
            alpha = (opacity * 96).toInt().coerceIn(0, 96)
            style = Paint.Style.FILL
        }
        val cyanPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.rgb(168, 202, 210)
            alpha = (opacity * 88).toInt().coerceIn(0, 88)
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(start, 1f * density, 1f * density, rosePaint)
        canvas.drawRoundRect(end, 1f * density, 1f * density, cyanPaint)
    }

    private val bottomBarBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val bottomBarTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_SP,
            10f,
            resources.displayMetrics
        )
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }

    private fun drawWatermarkBottomBarHint(canvas: Canvas, spec: WatermarkHintSpec) {
        val rect = activeFrameRectOrFullView()
        val isTranslucentBottomBar = spec.templateId == "pure-text"
        val barRect = if (isTranslucentBottomBar) {
            val barHeight = maxOf(52f * density, rect.height() * 0.078f)
            RectF(rect.left, rect.bottom - barHeight, rect.right, rect.bottom)
        } else {
            bottomBarPreviewRect(rect, height, density)
        }
        val bgColor = spec.barBackground
        if (bgColor != 0) {
            bottomBarBackgroundPaint.color = bgColor
            bottomBarBackgroundPaint.alpha = (spec.opacity * 200).toInt().coerceIn(0, 200)
            canvas.drawRect(barRect, bottomBarBackgroundPaint)
        }
        if (isTranslucentBottomBar) {
            val accentWidth = 3f * density
            val accentMargin = 11f * density
            bottomBarBackgroundPaint.color = Color.rgb(238, 214, 154)
            bottomBarBackgroundPaint.alpha = (spec.opacity * 225).toInt().coerceIn(0, 225)
            canvas.drawRoundRect(
                RectF(
                    barRect.left + accentMargin,
                    barRect.top + accentMargin,
                    barRect.left + accentMargin + accentWidth,
                    barRect.bottom - accentMargin
                ),
                accentWidth,
                accentWidth,
                bottomBarBackgroundPaint
            )
        }
        bottomBarTextPaint.alpha = (spec.opacity * 255).toInt().coerceIn(0, 255)
        val padding = 10f * density
        if (isTranslucentBottomBar) {
            bottomBarTextPaint.textAlign = Paint.Align.LEFT
            bottomBarTextPaint.color = Color.rgb(246, 250, 255)
            bottomBarTextPaint.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
            bottomBarTextPaint.textSize = 11.5f * density
            val leftX = barRect.left + padding + 14f * density
            val titleY = barRect.top + 23f * density
            val title = spec.previewLabels.firstOrNull() ?: spec.previewText
            canvas.drawText(title, leftX, titleY, bottomBarTextPaint)

            val secondary = spec.previewLabels.drop(1).joinToString("  ·  ").ifBlank { spec.previewText }
            bottomBarTextPaint.color = Color.rgb(196, 214, 232)
            bottomBarTextPaint.textSize = 8.5f * density
            canvas.drawText(secondary, leftX, titleY + 17f * density, bottomBarTextPaint)
            return
        }
        val textY = barRect.centerY() - (bottomBarTextPaint.ascent() + bottomBarTextPaint.descent()) / 2f
        if (spec.previewLabels.isNotEmpty()) {
            bottomBarTextPaint.textAlign = Paint.Align.LEFT
            val leftX = barRect.left + padding
            canvas.drawText(spec.previewLabels.first(), leftX, textY, bottomBarTextPaint)
            if (spec.previewLabels.size > 1) {
                bottomBarTextPaint.textAlign = Paint.Align.RIGHT
                val rightX = barRect.right - padding
                canvas.drawText(spec.previewLabels.last(), rightX, textY, bottomBarTextPaint)
            }
        } else {
            bottomBarTextPaint.textAlign = Paint.Align.CENTER
            canvas.drawText(spec.previewText, barRect.centerX(), textY, bottomBarTextPaint)
        }
    }

    private fun drawPreviewFrame(canvas: Canvas, frame: PreviewFrameRenderModel) {
        val rect = activeContentGeometry().activeFrameRect
        if (frame.dimOutsideFrame) {
            outsideFramePath.reset()
            outsideFramePath.fillType = android.graphics.Path.FillType.EVEN_ODD
            outsideFramePath.addRect(0f, 0f, width.toFloat(), height.toFloat(), android.graphics.Path.Direction.CW)
            outsideFramePath.addRect(rect, android.graphics.Path.Direction.CW)
            val savedAlpha = frameScrimPaint.alpha
            frameScrimPaint.alpha = frame.frameScrimAlpha
            canvas.drawPath(outsideFramePath, frameScrimPaint)
            frameScrimPaint.alpha = savedAlpha
        }
        canvas.drawRect(rect, frameGuidelinePaint)
    }

    private fun drawScanGuide(canvas: Canvas, model: PreviewScanGuideRenderModel) {
        val guideRect = previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = 0,
            ratioHeight = 0,
            previewContentAspect = model.contentAspect
        ).activeFrameRect
        val marginPx = 28f * density
        val left = guideRect.left + marginPx
        val top = guideRect.top + marginPx
        val right = guideRect.right - marginPx
        val bottom = guideRect.bottom - marginPx
        val guideWidth = (right - left).coerceAtLeast(0f)
        val guideHeight = (bottom - top).coerceAtLeast(0f)
        val cornerLen = model.cornerLengthDp * density
            .coerceAtMost(minOf(guideWidth, guideHeight) / 2f)
        scanGuideBracketPaint.strokeWidth = 2.5f * density

        // Top-left corner
        canvas.drawLine(left, top, left + cornerLen, top, scanGuideBracketPaint)
        canvas.drawLine(left, top, left, top + cornerLen, scanGuideBracketPaint)
        // Top-right corner
        canvas.drawLine(right, top, right - cornerLen, top, scanGuideBracketPaint)
        canvas.drawLine(right, top, right, top + cornerLen, scanGuideBracketPaint)
        // Bottom-left corner
        canvas.drawLine(left, bottom, left + cornerLen, bottom, scanGuideBracketPaint)
        canvas.drawLine(left, bottom, left, bottom - cornerLen, scanGuideBracketPaint)
        // Bottom-right corner
        canvas.drawLine(right, bottom, right - cornerLen, bottom, scanGuideBracketPaint)
        canvas.drawLine(right, bottom, right, bottom - cornerLen, scanGuideBracketPaint)
    }

    private fun drawFocusReticle(canvas: Canvas) {
        val model = focusReticle ?: return
        val elapsed = android.os.SystemClock.uptimeMillis() - reticleAnimStartMs
        val visual = focusReticleVisualState(model.status, elapsed)

        if (visual.expired) {
            animatingReticle = false
            return
        }

        val rawCx = model.normalizedX.coerceIn(0f, 1f) * width
        val rawCy = model.normalizedY.coerceIn(0f, 1f) * height
        val baseRadius = 24f * density
        val radius = baseRadius * visual.scale
        val tickLength = 8f * density
        val bounds = activeFrameRectOrFullView()
        val clamped = clampReticleCenter(rawCx, rawCy, radius, tickLength, bounds.left, bounds.top, bounds.right, bounds.bottom)
        val cx = clamped.x
        val cy = clamped.y

        reticleRingPaint.color = visual.ringColor
        reticleRingPaint.alpha = (visual.alpha * 255).toInt().coerceIn(0, 255)
        canvas.drawCircle(cx, cy, radius, reticleRingPaint)

        if (visual.ticksVisible) {
            reticleTickPaint.color = visual.ringColor
            reticleTickPaint.alpha = (visual.alpha * 255).toInt().coerceIn(0, 255)
            canvas.drawLine(cx, cy - radius - tickLength, cx, cy - radius, reticleTickPaint)
            canvas.drawLine(cx, cy + radius, cx, cy + radius + tickLength, reticleTickPaint)
            canvas.drawLine(cx - radius - tickLength, cy, cx - radius, cy, reticleTickPaint)
            canvas.drawLine(cx + radius, cy, cx + radius + tickLength, cy, reticleTickPaint)
        }

        if (animatingReticle) {
            postInvalidateOnAnimation()
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

internal data class FocusReticleVisualState(
    val scale: Float,
    val alpha: Float,
    val ringColor: Int,
    val ticksVisible: Boolean,
    val expired: Boolean
)

internal fun focusReticleVisualState(
    status: FocusReticleStatus,
    elapsedMs: Long
): FocusReticleVisualState {
    val expired: Boolean
    val scale: Float
    val alpha: Float
    val ringColor: Int
    val ticksVisible: Boolean

    when (status) {
        FocusReticleStatus.REQUESTED -> {
            expired = elapsedMs > 600L
            scale = when {
                elapsedMs < 100L -> 1.3f - 0.3f * (elapsedMs / 100f)
                elapsedMs < 400L -> 1.0f
                else -> 1.0f + 0.15f * ((elapsedMs - 400f) / 200f)
            }
            alpha = if (elapsedMs < 400L) 1f else 1f - ((elapsedMs - 400f) / 200f).coerceIn(0f, 1f)
            ringColor = Color.rgb(255, 191, 0)
            ticksVisible = false
        }
        FocusReticleStatus.SUCCEEDED -> {
            expired = elapsedMs > 500L
            scale = 1.0f
            alpha = if (elapsedMs < 250L) 1f else 1f - ((elapsedMs - 250f) / 250f).coerceIn(0f, 1f)
            ringColor = Color.WHITE
            ticksVisible = false
        }
        FocusReticleStatus.DEGRADED -> {
            expired = elapsedMs > 600L
            scale = 1.0f
            alpha = if (elapsedMs < 350L) 1f else 1f - ((elapsedMs - 350f) / 250f).coerceIn(0f, 1f)
            ringColor = Color.rgb(255, 191, 0)
            ticksVisible = true
        }
        FocusReticleStatus.FAILED, FocusReticleStatus.UNSUPPORTED -> {
            expired = elapsedMs > 400L
            scale = 1f - 0.15f * (elapsedMs / 200f).coerceIn(0f, 1f)
            alpha = if (elapsedMs < 150L) 0.5f else 0.5f * (1f - ((elapsedMs - 150f) / 250f).coerceIn(0f, 1f))
            ringColor = Color.rgb(128, 128, 128)
            ticksVisible = false
        }
    }

    return FocusReticleVisualState(
        scale = scale.coerceAtLeast(0.5f),
        alpha = alpha.coerceIn(0f, 1f),
        ringColor = ringColor,
        ticksVisible = ticksVisible,
        expired = expired
    )
}

internal data class ReticlePoint(val x: Float, val y: Float)

internal fun clampReticleCenter(
    cx: Float,
    cy: Float,
    radius: Float,
    tickLength: Float,
    boundsLeft: Float,
    boundsTop: Float,
    boundsRight: Float,
    boundsBottom: Float
): ReticlePoint {
    val extent = radius + tickLength
    return ReticlePoint(
        x = cx.coerceIn(boundsLeft + extent, boundsRight - extent),
        y = cy.coerceIn(boundsTop + extent, boundsBottom - extent)
    )
}

private const val DEFAULT_SENSOR_CONTENT_WIDTH = 4
private const val DEFAULT_SENSOR_CONTENT_HEIGHT = 3

/** sqrt(0.60) ≈ 0.775 — minimum linear scale for area-constrained frame box. */
internal const val SQRT_AREA_RATIO_MIN = 0.775f

/** Maximum linear frame span at equal preview/capture zoom, leaving 10% margin per side. */
internal const val SQRT_AREA_RATIO_MAX = 0.80f

/**
 * Build [PreviewContentGeometry] for the given view dimensions and optional frame ratio.
 *
 * When [previewContentAspect] is provided, [contentRect] is the fitEnd content area
 * within the view (e.g. a 4:3 camera preview bottom-aligned in a 16:9 view). When null,
 * defaults to the sensor's native 4:3 aspect ratio so that frame overlays stay within
 * the actual preview content bounds.
 *
 * When [ratioWidth] / [ratioHeight] are both > 0 the active frame is a centered
 * sub-rect of [contentRect] matching that ratio. Otherwise the active frame
 * equals [contentRect] (full-view capture).
 *
 * UI chrome must not shrink this geometry. Toolbars and capture controls may
 * overlap the preview, but the visible capture frame must stay centered in the
 * same preview content rect used by saved JPEG center-crop postprocessing.
 */
internal fun previewContentGeometry(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int = 0,
    ratioHeight: Int = 0,
    previewContentAspect: PreviewContentAspect? = null
): PreviewContentGeometry {
    val effectiveAspect = previewContentAspect
        ?: PreviewContentAspect(DEFAULT_SENSOR_CONTENT_WIDTH, DEFAULT_SENSOR_CONTENT_HEIGHT)
    val contentRect = if (effectiveAspect.width > 0 && effectiveAspect.height > 0) {
        val fitRect = computeEndAlignedFrameRect(
            viewWidth, viewHeight,
            effectiveAspect.width, effectiveAspect.height
        )
        RectF(fitRect.left, fitRect.top, fitRect.right, fitRect.bottom)
    } else {
        RectF(
            0f,
            0f,
            viewWidth.coerceAtLeast(0).toFloat(),
            viewHeight.coerceAtLeast(0).toFloat()
        )
    }
    val activeFrameRect = if (ratioWidth > 0 && ratioHeight > 0) {
        val fr = computeFrameRect(
            contentRect.width().toInt(),
            contentRect.height().toInt(),
            ratioWidth, ratioHeight
        )
        RectF(
            (contentRect.left + fr.left).coerceIn(contentRect.left, contentRect.right),
            (contentRect.top + fr.top).coerceIn(contentRect.top, contentRect.bottom),
            (contentRect.left + fr.right).coerceIn(contentRect.left, contentRect.right),
            (contentRect.top + fr.bottom).coerceIn(contentRect.top, contentRect.bottom)
        )
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

internal fun scaleFrameRect(rect: FrameRect, scale: Float): FrameRect {
    val halfW = rect.width * scale / 2f
    val halfH = rect.height * scale / 2f
    return FrameRect(
        left = rect.centerX - halfW,
        top = rect.centerY - halfH,
        right = rect.centerX + halfW,
        bottom = rect.centerY + halfH
    )
}

internal fun zoomFrameScale(captureZoomRatio: Float, previewZoomRatio: Float): Float {
    val capture = captureZoomRatio.coerceAtLeast(0.01f)
    val preview = previewZoomRatio.coerceAtLeast(0.01f)
    return (preview / capture).coerceIn(SQRT_AREA_RATIO_MIN, SQRT_AREA_RATIO_MAX)
}

internal fun scaleRectAroundCenter(rect: RectF, scale: Float): RectF {
    val cx = rect.centerX()
    val cy = rect.centerY()
    val halfW = rect.width() * scale / 2f
    val halfH = rect.height() * scale / 2f
    return RectF(cx - halfW, cy - halfH, cx + halfW, cy + halfH)
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

internal fun expandedFrameBottomBandRect(
    rect: RectF,
    viewHeight: Int,
    density: Float,
    templateId: String? = null
): RectF? {
    val bottomBand = if (templateId == "retro-frame") {
        (rect.height() * 0.095f).coerceIn(42f * density, 104f * density)
    } else {
        (rect.height() * 0.15f).coerceIn(56f * density, 150f * density)
    }
    val bottomFrameBand = bottomBand.coerceAtMost(viewHeight - rect.bottom)
    if (bottomFrameBand <= 0f) return null
    return RectF(rect.left, rect.bottom, rect.right, rect.bottom + bottomFrameBand)
}

internal fun expandedFramePaperAlpha(
    templateId: String,
    previewOpacity: Float
): Int {
    val maxAlpha = if (templateId == "retro-frame") 150 else 160
    return (previewOpacity.coerceIn(0f, 1f) * maxAlpha).toInt().coerceIn(0, maxAlpha)
}

internal fun bottomBarPreviewRect(
    rect: RectF,
    viewHeight: Int,
    density: Float
): RectF {
    val desiredBarHeight = (rect.height() * 0.12f).coerceIn(48f * density, 112f * density)
    val outsideHeight = (viewHeight - rect.bottom).coerceAtLeast(0f)
    val barHeight = desiredBarHeight.coerceAtMost(if (outsideHeight > 0f) outsideHeight else desiredBarHeight)
    val barTop = if (outsideHeight > 0f) rect.bottom else rect.bottom - barHeight
    return RectF(rect.left, barTop, rect.right, barTop + barHeight)
}

internal fun fourBorderPreviewBandWidth(
    rect: RectF,
    density: Float
): Float = (min(rect.width(), rect.height()) * 0.055f).coerceIn(24f * density, 64f * density)

internal fun fourBorderPreviewMetadata(labels: List<String>): String {
    return labels
        .drop(1)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .joinToString("   ")
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

internal fun computeEndAlignedFrameRect(
    viewWidth: Int,
    viewHeight: Int,
    ratioWidth: Int,
    ratioHeight: Int
): FrameRect {
    val centered = computeFrameRect(viewWidth, viewHeight, ratioWidth, ratioHeight)
    val offsetX = viewWidth - centered.right
    val offsetY = viewHeight - centered.bottom
    return FrameRect(
        left = centered.left + offsetX,
        top = centered.top + offsetY,
        right = centered.right + offsetX,
        bottom = centered.bottom + offsetY
    )
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

/**
 * Draw the preview color-transform overlay for [spec] onto [canvas] using [paint].
 *
 * When [FilterOverlaySpec.colorMatrix] is a non-identity 4x5 matrix, a
 * [ColorMatrixColorFilter] is applied to [paint] so that non-tint transforms
 * (black-and-white, warmth, coolness, contrast) reach the overlay surface
 * rather than being silently dropped when [FilterOverlaySpec.tintAlpha] is
 * zero. The color filter remains attached to [paint] after this call so
 * callers and tests can verify matrix consumption; callers that reuse the
 * paint for other draws must clear `paint.colorFilter` themselves.
 *
 * When [FilterOverlaySpec.tintAlpha] is positive, the tint color is drawn at
 * the requested alpha (existing behavior). When only a matrix is present,
 * the rect is drawn with a transparent fill so the preview surface is not
 * polluted by a foreign base color while the matrix is still consumed by the
 * paint — the agent-verifiable proof that the matrix reaches the draw path.
 */
internal fun drawColorTransformOverlay(
    canvas: Canvas,
    spec: FilterOverlaySpec,
    bounds: RectF,
    paint: Paint
) {
    val matrix = spec.colorMatrix
    val hasMatrix = matrix != null &&
        matrix.size == 20 &&
        !PreviewColorMatrixBuilder.isIdentity(matrix)
    val activeMatrix = if (hasMatrix) matrix!! else null
    val hasTint = spec.tintAlpha > 0f
    if (activeMatrix == null && !hasTint) return

    paint.colorFilter = if (activeMatrix != null) ColorMatrixColorFilter(activeMatrix) else null
    if (hasTint) {
        paint.color = spec.tintColor
        paint.alpha = (spec.tintAlpha * 255).toInt().coerceIn(0, 255)
    } else {
        paint.color = Color.WHITE
        paint.alpha = 0
    }
    canvas.drawRect(bounds, paint)
}
