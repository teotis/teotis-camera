package com.opencamera.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import kotlin.math.abs

internal class FocalLengthSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    companion object {
        /** Fraction of distance to nearest neighbor; within this, release snaps to preset. */
        internal const val SNAP_THRESHOLD_FRACTION = 0.15f
        internal const val SNAP_THRESHOLD_MAX_RATIO_DELTA = 0.08f
        /** Max px movement from ACTION_DOWN to count as a tap (quick-jump). */
        internal const val TAP_SLOP_PX = 24f
        /** Max px distance from a preset dot center to count as a tap on that dot. */
        internal const val DOT_TAP_RADIUS_PX = 32f

        internal fun formatRatio(ratio: Float): String {
            return String.format(java.util.Locale.US, "%.1fx", ratio)
        }

        /** Compact label for preset dot nodes: "1x", "2", "5", "0.6". */
        internal fun formatCompactNodeLabel(ratio: Float): String {
            val rounded = (ratio * 10).toInt()
            val isInteger = rounded % 10 == 0
            return if (isInteger) "${rounded / 10}x"
            else String.format(java.util.Locale.US, "%.1f", ratio)
        }

        /** Returns true if external setCurrentRatio should be suppressed (active drag). */
        internal fun shouldSuppressExternalUpdate(isDragging: Boolean): Boolean = isDragging

        /**
         * Pure logic: returns true if [ratio] is within [SNAP_THRESHOLD_FRACTION]
         * of the nearest preset in [sortedPresets].
         */
        internal fun shouldSnap(ratio: Float, sortedPresets: List<Float>): Boolean {
            if (sortedPresets.size < 2) return sortedPresets.isNotEmpty()
            val nearest = sortedPresets.minByOrNull { kotlin.math.abs(it - ratio) } ?: return false
            val second = sortedPresets.sortedBy { kotlin.math.abs(it - ratio) }.getOrNull(1) ?: return true
            val neighborDist = kotlin.math.abs(second - nearest)
            val threshold = minOf(neighborDist * SNAP_THRESHOLD_FRACTION, SNAP_THRESHOLD_MAX_RATIO_DELTA)
            return kotlin.math.abs(ratio - nearest) + 1e-6f < threshold
        }
    }

    var onRatioChanged: ((Float) -> Unit)? = null
    var onRatioSnapped: ((Float) -> Unit)? = null

    var isInteractive: Boolean = true

    private var presetRatios: List<Float> = emptyList()
    private var currentRatio: Float = 1f
    private var minRatio: Float = 1f
    private var maxRatio: Float = 1f
    private var actionDownX: Float = 0f

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    // Track
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_muted)
        style = Paint.Style.FILL
        alpha = 80
    }
    private val trackHeight = 2f * density

    // Active track
    private val activeTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_secondary)
        style = Paint.Style.FILL
    }

    // Dot
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_secondary)
        style = Paint.Style.FILL
    }
    private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_accent)
        style = Paint.Style.FILL
    }
    private val dotRadius = 3f * density
    private val dotActiveRadius = 4.5f * density

    // Node label (persistent labels under preset dots)
    private val nodeLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_muted)
        textSize = 10f * scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val nodeLabelMarginTop = 3f * density

    // Thumb
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_primary)
        style = Paint.Style.FILL
    }
    private val thumbRadius = 8f * density

    // Floating label
    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_text_primary)
        textSize = 13f * scaledDensity
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }
    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, R.color.oc_surface_panel)
        style = Paint.Style.FILL
    }
    private val labelPaddingH = 8f * density
    private val labelPaddingV = 4f * density
    private val labelCornerRadius = 6f * density
    private val labelArrowSize = 4f * density
    private val labelBottomMargin = 6f * density

    private var labelAlpha: Int = 0
    private var labelAnimator: ValueAnimator? = null
    internal var isDragging = false
    private var showLabel = false

    /** Current ratio value, exposed for testing. */
    internal val currentRatioValue: Float get() = currentRatio

    // Layout constants
    private val verticalOffset = thumbRadius + labelBottomMargin + 13f * scaledDensity + labelPaddingV * 2

    private val trackTop get() = height / 2f + verticalOffset / 2 - thumbRadius
    private val trackLeft get() = thumbRadius + paddingLeft.toFloat()
    private val trackRight get() = width - thumbRadius - paddingRight.toFloat()
    private val trackWidth get() = (trackRight - trackLeft).coerceAtLeast(0f)

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val nodeLabelHeight = nodeLabelPaint.textSize + nodeLabelMarginTop
        val desiredHeight = (verticalOffset + thumbRadius * 2 + 8f * density + nodeLabelHeight).toInt()
        val height = resolveSize(desiredHeight, heightMeasureSpec)
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), height)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (presetRatios.isEmpty() || trackWidth <= 0) return

        val cy = trackTop

        // Draw track background
        val trackRect = RectF(trackLeft, cy - trackHeight / 2, trackRight, cy + trackHeight / 2)
        canvas.drawRoundRect(trackRect, trackHeight / 2, trackHeight / 2, trackPaint)

        // Draw active track (from min to current)
        val thumbX = ratioToX(currentRatio)
        if (thumbX > trackLeft + 1) {
            val activeRect = RectF(trackLeft, cy - trackHeight / 2, thumbX, cy + trackHeight / 2)
            canvas.drawRoundRect(activeRect, trackHeight / 2, trackHeight / 2, activeTrackPaint)
        }

        // Draw preset dots with node labels
        for (ratio in presetRatios) {
            val x = ratioToX(ratio)
            val isActive = abs(ratio - currentRatio) < 0.05f
            val r = if (isActive) dotActiveRadius else dotRadius
            val paint = if (isActive) dotActivePaint else dotPaint
            canvas.drawCircle(x, cy, r, paint)

            // Persistent node label below dot
            val label = formatCompactNodeLabel(ratio)
            val labelY = cy + r + nodeLabelMarginTop + nodeLabelPaint.textSize
            if (isActive) {
                nodeLabelPaint.color = ContextCompat.getColor(context, R.color.oc_accent)
            } else {
                nodeLabelPaint.color = ContextCompat.getColor(context, R.color.oc_text_muted)
            }
            canvas.drawText(label, x, labelY, nodeLabelPaint)
        }

        // Draw thumb
        canvas.drawCircle(thumbX, cy, thumbRadius, thumbPaint)

        // Draw floating label
        if (showLabel && labelAlpha > 0) {
            drawFloatingLabel(canvas, thumbX, cy)
        }
    }

    private fun drawFloatingLabel(canvas: Canvas, thumbX: Float, cy: Float) {
        val labelText = formatRatio(currentRatio)
        val textWidth = labelPaint.measureText(labelText)
        val labelW = textWidth + labelPaddingH * 2
        val labelH = labelPaint.textSize + labelPaddingV * 2

        val labelLeft = (thumbX - labelW / 2).coerceIn(
            paddingLeft.toFloat(),
            (width - paddingRight - labelW).coerceAtLeast(paddingLeft.toFloat())
        )
        val labelTop = cy - thumbRadius - labelBottomMargin - labelH - labelArrowSize

        labelBgPaint.alpha = labelAlpha
        labelPaint.alpha = labelAlpha

        // Label background
        val labelRect = RectF(labelLeft, labelTop, labelLeft + labelW, labelTop + labelH)
        canvas.drawRoundRect(labelRect, labelCornerRadius, labelCornerRadius, labelBgPaint)

        // Arrow triangle
        val arrowX = thumbX.coerceIn(labelLeft + labelCornerRadius, labelLeft + labelW - labelCornerRadius)
        val arrowPath = Path().apply {
            moveTo(arrowX - labelArrowSize, labelTop + labelH)
            lineTo(arrowX, labelTop + labelH + labelArrowSize)
            lineTo(arrowX + labelArrowSize, labelTop + labelH)
            close()
        }
        canvas.drawPath(arrowPath, labelBgPaint)

        // Label text
        val textX = labelLeft + labelW / 2
        val textY = labelTop + labelH / 2 - (labelPaint.descent() + labelPaint.ascent()) / 2
        canvas.drawText(labelText, textX, textY, labelPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return false
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                val x = event.x
                val y = event.y
                val cy = trackTop

                if (abs(y - cy) < thumbRadius * 2.5f) {
                    isDragging = true
                    actionDownX = x
                    showLabel = true
                    labelAlpha = 255
                    labelAnimator?.cancel()
                    updateRatioFromX(x)
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    updateRatioFromX(event.x)
                    return true
                }
                return false
            }
            MotionEvent.ACTION_UP -> {
                if (isDragging) {
                    isDragging = false
                    val upX = event.x
                    val isTap = abs(upX - actionDownX) < TAP_SLOP_PX * density
                    val presetAtTap = if (isTap) findPresetNearX(upX) else null

                    when {
                        // Tap on/near a preset dot → quick-jump snap
                        presetAtTap != null -> {
                            currentRatio = presetAtTap
                            onRatioSnapped?.invoke(presetAtTap)
                            onRatioChanged?.invoke(presetAtTap)
                        }
                        // Release within snap threshold → snap to nearest preset
                        shouldSnap(currentRatio) -> {
                            val snapped = findNearestPreset(currentRatio)!!
                            currentRatio = snapped
                            onRatioSnapped?.invoke(snapped)
                            onRatioChanged?.invoke(snapped)
                        }
                        // Otherwise keep the continuous value
                        else -> {
                            onRatioSnapped?.invoke(currentRatio)
                        }
                    }
                    invalidate()
                    startLabelFadeOut()
                    return true
                }
                return false
            }
            MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    invalidate()
                    startLabelFadeOut()
                    return true
                }
                return false
            }
        }
        return super.onTouchEvent(event)
    }

    fun setPresetRatios(ratios: List<Float>) {
        presetRatios = ratios.sorted()
        if (presetRatios.isNotEmpty()) {
            minRatio = presetRatios.first()
            maxRatio = presetRatios.last()
        }
        invalidate()
    }

    fun setCurrentRatio(ratio: Float) {
        if (isDragging) return
        val clamped = ratio.coerceIn(minRatio, maxRatio)
        if (abs(clamped - currentRatio) < 0.005f) return
        currentRatio = clamped
        invalidate()
    }

    fun setSliderVisible(visible: Boolean) {
        visibility = if (visible) VISIBLE else GONE
    }

    private fun updateRatioFromX(x: Float) {
        if (trackWidth <= 0) return
        val fraction = ((x - trackLeft) / trackWidth).coerceIn(0f, 1f)
        currentRatio = minRatio + fraction * (maxRatio - minRatio)
        onRatioChanged?.invoke(currentRatio)
        invalidate()
    }

    private fun ratioToX(ratio: Float): Float {
        if (maxRatio <= minRatio) return trackLeft
        val fraction = ((ratio - minRatio) / (maxRatio - minRatio)).coerceIn(0f, 1f)
        return trackLeft + fraction * trackWidth
    }

    private fun xToRatio(x: Float): Float {
        if (trackWidth <= 0) return minRatio
        val fraction = ((x - trackLeft) / trackWidth).coerceIn(0f, 1f)
        return minRatio + fraction * (maxRatio - minRatio)
    }

    private fun findNearestPreset(ratio: Float): Float? {
        if (presetRatios.isEmpty()) return null
        return presetRatios.minByOrNull { abs(it - ratio) }
    }

    /** Returns true if [ratio] is within [SNAP_THRESHOLD_FRACTION] of the nearest preset. */
    private fun shouldSnap(ratio: Float): Boolean = Companion.shouldSnap(ratio, presetRatios)

    /** If ACTION_DOWN+UP is a tap and lands near a preset dot, return that preset. */
    private fun findPresetNearX(x: Float): Float? {
        val ratio = xToRatio(x)
        // Check pixel distance from each preset dot center
        for (p in presetRatios) {
            val dotX = ratioToX(p)
            if (abs(x - dotX) < DOT_TAP_RADIUS_PX * density) return p
        }
        return null
    }

    private fun formatRatio(ratio: Float): String = Companion.formatRatio(ratio)

    private fun startLabelFadeOut() {
        labelAnimator?.cancel()
        labelAnimator = ValueAnimator.ofInt(255, 0).apply {
            duration = 1000
            startDelay = 300
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                labelAlpha = anim.animatedValue as Int
                showLabel = labelAlpha > 0
                invalidate()
            }
            start()
        }
    }
}
