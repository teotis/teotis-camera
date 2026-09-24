package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import com.opencamera.core.effect.FilterOverlaySpec
import com.opencamera.core.effect.FrameGuidelineSpec
import com.opencamera.core.effect.PreviewColorMatrixBuilder
import com.opencamera.core.effect.WatermarkHintSpec
import com.opencamera.core.effect.WatermarkPreviewShape
import com.opencamera.core.settings.CompositionGridMode
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

    private val watermarkHintRenderer = PreviewWatermarkHintRenderer(
        context = context,
        density = density,
        displayMetrics = resources.displayMetrics,
        activeFrameRect = ::activeFrameRectOrFullView,
        viewWidth = { width },
        viewHeight = { height }
    )

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

    private val reticleLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        textSize = 9f * density
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val outsideFramePath = android.graphics.Path()

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
    private var cachedSurfaceTransform: PreviewSurfaceTransform? = null

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
            val geometryState = computeGeometryState(model)
            cachedGeometry = geometryState.geometry
            cachedSurfaceTransform = geometryState.surfaceTransform
            prepareVignetteCache(model)
        } else {
            renderModel = model
            visibility = if (model.isVisible) VISIBLE else GONE
        }
        invalidate()
    }

    private data class PreviewGeometryState(
        val geometry: PreviewContentGeometry,
        val surfaceTransform: PreviewSurfaceTransform?
    )

    private fun computeGeometryState(model: PreviewOverlayRenderModel): PreviewGeometryState {
        val frameRatio = model.frame?.ratio
            ?: model.effectModel?.frameGuideline?.ratio
        val baseGeometry = previewContentGeometry(
            viewWidth = width,
            viewHeight = height,
            ratioWidth = frameRatio?.width ?: 0,
            ratioHeight = frameRatio?.height ?: 0,
            previewContentAspect = model.previewContentAspect
        )
        val frame = model.frame ?: return PreviewGeometryState(baseGeometry, null)
        val watermarkHint = model.effectModel?.watermarkHint
        if (
            watermarkHint?.shape == WatermarkPreviewShape.EXPANDED_FRAME &&
            isStaticHighDesignWatermarkTemplate(watermarkHint.templateId)
        ) {
            val previewLayout = highDesignWatermarkPreviewLayout(
                basePhotoSlot = baseGeometry.activeFrameRect,
                availableBounds = baseGeometry.contentRect
            )
            val captureCropRect = scaleRectAroundCenter(
                baseGeometry.activeFrameRect,
                exactCaptureFrameScale(
                    captureZoomRatio = frame.zoomRatio,
                    previewZoomRatio = frame.previewZoomRatio
                )
            )
            val surfaceScale = previewLayout.photoSlot.width() /
                captureCropRect.width().coerceAtLeast(1f)
            return PreviewGeometryState(
                geometry = baseGeometry.copy(activeFrameRect = previewLayout.photoSlot),
                surfaceTransform = PreviewSurfaceTransform(
                    scale = surfaceScale,
                    pivotX = captureCropRect.centerX(),
                    pivotY = captureCropRect.centerY(),
                    translationX = previewLayout.photoSlot.centerX() - captureCropRect.centerX(),
                    translationY = previewLayout.photoSlot.centerY() - captureCropRect.centerY(),
                    sourceClipRect = captureCropRect
                )
            )
        }
        val scale = zoomFrameScale(
            captureZoomRatio = frame.zoomRatio,
            previewZoomRatio = frame.previewZoomRatio
        )
        if (scale >= 0.999f) return PreviewGeometryState(baseGeometry, null)
        val scaled = scaleRectAroundCenter(baseGeometry.activeFrameRect, scale)
        val clamped = RectF(
            scaled.left.coerceIn(baseGeometry.contentRect.left, baseGeometry.contentRect.right),
            scaled.top.coerceIn(baseGeometry.contentRect.top, baseGeometry.contentRect.bottom),
            scaled.right.coerceIn(baseGeometry.contentRect.left, baseGeometry.contentRect.right),
            scaled.bottom.coerceIn(baseGeometry.contentRect.top, baseGeometry.contentRect.bottom)
        )
        return PreviewGeometryState(
            geometry = baseGeometry.copy(activeFrameRect = clamped),
            surfaceTransform = null
        )
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
        val geometryState = computeGeometryState(renderModel)
        cachedGeometry = geometryState.geometry
        cachedSurfaceTransform = geometryState.surfaceTransform
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
        return computeGeometryState(renderModel).geometry
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

    internal fun currentPreviewSurfaceTransformOrNull(): PreviewSurfaceTransform? = cachedSurfaceTransform

    internal fun currentPreviewInteractionBoundsOrNull(): RectF? =
        cachedSurfaceTransform?.sourceClipRect ?: currentActiveFrameRectOrNull()

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
        watermarkHintRenderer.draw(canvas, spec)
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

        val sourcePoint = transformedPreviewPoint(
            normalizedX = model.normalizedX,
            normalizedY = model.normalizedY,
            viewWidth = width,
            viewHeight = height,
            transform = cachedSurfaceTransform,
            isMirrored = renderModel.isPreviewMirrored
        )
        val rawCx = sourcePoint.x
        val rawCy = sourcePoint.y
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

        model.lockLabel?.let { label ->
            reticleLabelPaint.color = visual.ringColor
            reticleLabelPaint.alpha = (visual.alpha * 255).toInt().coerceIn(0, 255)
            val metrics = reticleLabelPaint.fontMetrics
            val baseline = cy - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(label, cx, baseline, reticleLabelPaint)
        }

        if (animatingReticle && visual.animates) {
            postInvalidateOnAnimation()
        } else {
            animatingReticle = false
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

internal data class PreviewSurfaceTransform(
    val scale: Float,
    val pivotX: Float,
    val pivotY: Float,
    val translationX: Float,
    val translationY: Float,
    val sourceClipRect: RectF
)

internal fun transformedPreviewPoint(
    normalizedX: Float,
    normalizedY: Float,
    viewWidth: Int,
    viewHeight: Int,
    transform: PreviewSurfaceTransform?,
    isMirrored: Boolean
): ReticlePoint {
    val sourceX = normalizedX.coerceIn(0f, 1f) * viewWidth
    val sourceY = normalizedY.coerceIn(0f, 1f) * viewHeight
    if (transform == null) return ReticlePoint(sourceX, sourceY)
    val horizontalScale = if (isMirrored) -transform.scale else transform.scale
    return ReticlePoint(
        x = transform.pivotX + (sourceX - transform.pivotX) * horizontalScale +
            transform.translationX,
        y = transform.pivotY + (sourceY - transform.pivotY) * transform.scale +
            transform.translationY
    )
}

internal enum class FocusReticleStatus {
    REQUESTED,
    LOCK_REQUESTED,
    SUCCEEDED,
    LOCKED,
    LOCKED_DEGRADED,
    DEGRADED,
    FAILED,
    UNSUPPORTED
}

internal data class FocusReticleRenderModel(
    val normalizedX: Float,
    val normalizedY: Float,
    val status: FocusReticleStatus,
    val lockLabel: String? = null
)

internal data class FocusReticleVisualState(
    val scale: Float,
    val alpha: Float,
    val ringColor: Int,
    val ticksVisible: Boolean,
    val expired: Boolean,
    val animates: Boolean
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
    val animates: Boolean

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
            animates = true
        }
        FocusReticleStatus.LOCK_REQUESTED -> {
            expired = false
            scale = 1.0f
            alpha = 1.0f
            ringColor = Color.rgb(255, 191, 0)
            ticksVisible = true
            animates = false
        }
        FocusReticleStatus.SUCCEEDED -> {
            expired = elapsedMs > 500L
            scale = 1.0f
            alpha = if (elapsedMs < 250L) 1f else 1f - ((elapsedMs - 250f) / 250f).coerceIn(0f, 1f)
            ringColor = Color.WHITE
            ticksVisible = false
            animates = true
        }
        FocusReticleStatus.LOCKED -> {
            expired = false
            scale = 1.0f
            alpha = 1.0f
            ringColor = Color.WHITE
            ticksVisible = true
            animates = false
        }
        FocusReticleStatus.LOCKED_DEGRADED -> {
            expired = false
            scale = 1.0f
            alpha = 1.0f
            ringColor = Color.rgb(255, 191, 0)
            ticksVisible = true
            animates = false
        }
        FocusReticleStatus.DEGRADED -> {
            expired = elapsedMs > 600L
            scale = 1.0f
            alpha = if (elapsedMs < 350L) 1f else 1f - ((elapsedMs - 350f) / 250f).coerceIn(0f, 1f)
            ringColor = Color.rgb(255, 191, 0)
            ticksVisible = true
            animates = true
        }
        FocusReticleStatus.FAILED, FocusReticleStatus.UNSUPPORTED -> {
            expired = elapsedMs > 400L
            scale = 1f - 0.15f * (elapsedMs / 200f).coerceIn(0f, 1f)
            alpha = if (elapsedMs < 150L) 0.5f else 0.5f * (1f - ((elapsedMs - 150f) / 250f).coerceIn(0f, 1f))
            ringColor = Color.rgb(128, 128, 128)
            ticksVisible = false
            animates = true
        }
    }

    return FocusReticleVisualState(
        scale = scale.coerceAtLeast(0.5f),
        alpha = alpha.coerceIn(0f, 1f),
        ringColor = ringColor,
        ticksVisible = ticksVisible,
        expired = expired,
        animates = animates
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

/** sqrt(0.60) ≈ 0.775 — minimum linear scale for legacy simplified frame previews. */
internal const val SQRT_AREA_RATIO_MIN = 0.775f

/** Maximum legacy preview span, leaving room to show simplified watermark material. */
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

internal fun exactCaptureFrameScale(captureZoomRatio: Float, previewZoomRatio: Float): Float {
    val capture = captureZoomRatio.coerceAtLeast(0.01f)
    val preview = previewZoomRatio.coerceAtLeast(0.01f)
    return (preview / capture).coerceIn(0.01f, 1f)
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
    val bottomBand = when (templateId) {
        "retro-frame" -> (rect.height() * 0.095f).coerceIn(42f * density, 104f * density)
        "travel-polaroid" -> (rect.width() * com.opencamera.core.effect.TravelTicketPaperSpec.BOTTOM_BAND_RATIO)
            .coerceIn(82f * density, 220f * density)
        else -> (rect.height() * 0.15f).coerceIn(56f * density, 150f * density)
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
