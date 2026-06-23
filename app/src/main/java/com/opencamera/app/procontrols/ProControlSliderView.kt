package com.opencamera.app.procontrols

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln

/** Availability states for professional control widgets. */
enum class Availability { SUPPORTED, DEGRADED, UNSUPPORTED }

/** Listener interface for ProControlSliderView value changes. */
interface SliderListener {
    fun onValueChange(value: Float?)
    fun onAutoToggle()
    fun onReset()
}

class ProControlSliderView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    companion object {
        internal const val SNAP_THRESHOLD_FRACTION = 0.15f
        internal const val SNAP_THRESHOLD_MAX_DELTA = 0.08f
        private const val TAP_SLOP_PX = 24f
        private const val DOT_TAP_RADIUS_PX = 32f

        internal fun shouldSnap(value: Float, sortedPresets: List<Float>): Boolean {
            if (sortedPresets.size < 2) return sortedPresets.isNotEmpty()
            val nearest = sortedPresets.minByOrNull { abs(it - value) } ?: return false
            val second = sortedPresets.sortedBy { abs(it - value) }.getOrNull(1) ?: return true
            val neighborDist = abs(second - nearest)
            val threshold = maxOf(neighborDist * SNAP_THRESHOLD_FRACTION, SNAP_THRESHOLD_MAX_DELTA)
            return abs(value - nearest) + 1e-6f < threshold
        }

        internal fun findNearestPreset(value: Float, sortedPresets: List<Float>): Float? {
            if (sortedPresets.isEmpty()) return null
            return sortedPresets.minByOrNull { abs(it - value) }
        }

        internal fun fractionToValue(fraction: Float, minValue: Float, maxValue: Float): Float {
            if (maxValue <= minValue) return minValue
            return exp(ln(minValue) + fraction * (ln(maxValue) - ln(minValue)))
        }

        internal fun valueToFraction(value: Float, minValue: Float, maxValue: Float): Float {
            if (maxValue <= minValue) return 0f
            val logRange = ln(maxValue) - ln(minValue)
            if (logRange == 0f) return 0f
            return ((ln(value) - ln(minValue)) / logRange).coerceIn(0f, 1f)
        }

        private fun sp(context: Context, value: Int): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value.toFloat(), context.resources.displayMetrics)

        private fun dp(context: Context, value: Int): Float =
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value.toFloat(), context.resources.displayMetrics)
    }

    var listener: SliderListener? = null

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val headerHeight = 32f * density

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_secondary)
        textSize = 13f * scaledDensity
        isFakeBoldText = true
    }
    private val autoBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val autoBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val resetBtnPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3F4B.toInt()
        style = Paint.Style.FILL
    }
    private val resetBtnTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
        textSize = 11f * scaledDensity
        textAlign = Paint.Align.CENTER
    }

    private val autoButtonArea = RectF()
    private val resetButtonArea = RectF()
    private val autoButtonRect = RectF()

    internal var isAuto: Boolean = true
        private set
    private var restoreValue: Float? = null
    private var initialValue: Float? = null
    private var formatValue: (Float) -> String = { it.toInt().toString() }
    internal var availability: Availability = Availability.SUPPORTED
        private set
    internal val trackView: SliderTrack = SliderTrack(context).apply {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, (48f * density).toInt())
    }

    init {
        orientation = VERTICAL
        clipChildren = false
        setWillNotDraw(false)
        addView(trackView)
        trackView.onDragEnd = {
            val value = trackView.currentValue
            restoreValue = value
            isAuto = value == null
            listener?.onValueChange(value)
            invalidate()
        }
    }

    fun configure(
        label: String,
        min: Float,
        max: Float,
        presets: List<Float>,
        initial: Float? = null,
        format: (Float) -> String = { it.toInt().toString() }
    ) {
        formatValue = format
        restoreValue = initial
        initialValue = initial
        trackView.setup(label, min, max, presets)
        if (initial != null) {
            isAuto = false
            trackView.setCurrentValue(initial)
        } else {
            isAuto = true
        }
        invalidate()
    }

    fun setAvailability(avail: Availability) {
        availability = avail
        trackView.isEnabled = avail == Availability.SUPPORTED
        if (avail != Availability.SUPPORTED) trackView.cancelDrag()
        invalidate()
    }

    fun setCurrentValue(value: Float?) {
        if (trackView.isDragging) return
        if (value == null) {
            isAuto = true
        } else {
            isAuto = false
            restoreValue = value
            trackView.setCurrentValue(value)
        }
        invalidate()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val trackH = trackView.layoutParams.height
        val pad = 8f * density
        val desiredH = (headerHeight + pad + trackH + pad + 18f * scaledDensity + 6f * density).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(desiredH, heightSpec))
        measureChild(trackView, MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthSpec) - paddingLeft - paddingRight, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(trackH, MeasureSpec.EXACTLY))
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l
        val pad = 8f * density
        trackView.layout(paddingLeft, headerHeight.toInt() + pad.toInt(), w - paddingRight, headerHeight.toInt() + pad.toInt() + trackView.measuredHeight)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val pad = 8f * density
        val trackTop = headerHeight + pad

        // Label
        val labelText = trackView.label
        canvas.drawText(labelText, paddingLeft.toFloat(), 18f * scaledDensity + 6f * density, labelPaint)

        // Auto button (right-aligned)
        updateHeaderButtonAreas(w)

        if (isAuto) {
            autoBtnPaint.color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_accent)
            autoBtnTextPaint.color = 0xFF000000.toInt()
            autoBtnTextPaint.isFakeBoldText = true
            autoBtnTextPaint.textSize = 11f * scaledDensity
        } else {
            autoBtnPaint.color = 0xFF3A3F4B.toInt()
            autoBtnTextPaint.color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
            autoBtnTextPaint.isFakeBoldText = false
            autoBtnTextPaint.textSize = 11f * scaledDensity
        }
        canvas.drawRoundRect(autoButtonRect, 6f * density, 6f * density, autoBtnPaint)
        val autoTextBaseY = autoButtonRect.centerY() - (autoBtnTextPaint.descent() + autoBtnTextPaint.ascent()) / 2f
        canvas.drawText("Auto", autoButtonRect.centerX(), autoTextBaseY, autoBtnTextPaint)

        // Reset button (left of Auto)
        canvas.drawRoundRect(resetButtonArea, 6f * density, 6f * density, resetBtnPaint)
        val resetTextBaseY = resetButtonArea.centerY() - (resetBtnTextPaint.descent() + resetBtnTextPaint.ascent()) / 2f
        canvas.drawText("重置", resetButtonArea.centerX(), resetTextBaseY, resetBtnTextPaint)

        // Value text below track
        val valueTop = trackTop + trackView.measuredHeight + 8f * density
        val valuePaint = if (isAuto) {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
                textSize = 14f * scaledDensity
                textAlign = Paint.Align.CENTER
            }
        } else {
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_primary)
                textSize = 16f * scaledDensity
                textAlign = Paint.Align.CENTER
                isFakeBoldText = true
            }
        }
        val valueText = if (isAuto) "Auto" else trackView.currentValue?.let { formatValue(it) } ?: ""
        canvas.drawText(valueText, w / 2f, valueTop + 14f * scaledDensity, valuePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (availability != Availability.SUPPORTED) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            updateHeaderButtonAreas(width.toFloat())
            if (autoButtonArea.contains(event.x, event.y)) {
                isAuto = !isAuto
                if (isAuto) {
                    trackView.setCurrentValue(null)
                } else {
                    val v = restoreValue ?: trackView.minValue
                    trackView.setCurrentValue(v)
                    trackView.notifyDragEnd()
                }
                listener?.onAutoToggle()
                invalidate()
                return true
            }
            if (resetButtonArea.contains(event.x, event.y)) {
                val v = initialValue ?: trackView.minValue
                trackView.setCurrentValue(v)
                restoreValue = v
                isAuto = false
                trackView.notifyDragEnd()
                listener?.onReset()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun updateHeaderButtonAreas(widthPx: Float) {
        val autoBtnW = 60f * density
        val autoBtnH = 26f * density
        val autoBtnR = widthPx - paddingRight
        val autoBtnL = autoBtnR - autoBtnW
        val autoBtnT = (headerHeight - autoBtnH) / 2f
        autoButtonRect.set(autoBtnL, autoBtnT, autoBtnR, autoBtnT + autoBtnH)
        autoButtonArea.set(autoButtonRect)

        val resetBtnW = 48f * density
        val resetBtnR = autoBtnL - 8f * density
        val resetBtnL = resetBtnR - resetBtnW
        resetButtonArea.set(resetBtnL, autoBtnT, resetBtnR, autoBtnT + autoBtnH)
    }

    // -- Inner: SliderTrack --
    internal class SliderTrack(context: Context) : View(context) {

        companion object {
            private const val SNAP_FRACTION = 0.15f
            private const val SNAP_MAX_DELTA = 0.08f
            private const val TAP_SLOP = 24f
            private const val DOT_TAP_RADIUS = 32f
        }

        private val density = context.resources.displayMetrics.density
        private val scaledDensity = context.resources.displayMetrics.scaledDensity

        var onDragEnd: (() -> Unit)? = null

        internal var label: String = ""
            private set
        internal var minValue: Float = 0f
            private set
        internal var maxValue: Float = 100f
            private set
        internal var currentValue: Float? = null
            private set
        private var presets: List<Float> = emptyList()
        internal var isDragging: Boolean = false
            private set

        private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
            style = Paint.Style.FILL
            alpha = 80
        }
        private val trackH = 2f * density

        private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_secondary)
            style = Paint.Style.FILL
        }

        private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
            style = Paint.Style.FILL
        }
        private val dotActivePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_accent)
            style = Paint.Style.FILL
        }
        private val dotR = 3f * density
        private val dotActiveR = 4.5f * density

        private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_primary)
            style = Paint.Style.FILL
        }
        private val thumbR = 8f * density

        private val floatingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_primary)
            textSize = 13f * scaledDensity
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        private val floatingBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_surface_panel)
            style = Paint.Style.FILL
        }
        private val floatPadH = 8f * density
        private val floatPadV = 4f * density
        private val floatRadius = 6f * density
        private val floatBottom = 6f * density

        private val floatingReserve = thumbR + floatBottom + 13f * scaledDensity + floatPadV * 2
        private val compactH = 40f * density

        private val showLabel: Boolean get() = isDragging
        private val trackCenter: Float
            get() = if (showLabel) height / 2f + floatingReserve / 2f - thumbR
                    else ((height.toFloat() - compactH) / 2f + compactH / 2f).coerceAtLeast(thumbR)

        private val trackLeft get() = thumbR + paddingLeft.toFloat()
        private val trackRight get() = width - thumbR - paddingRight.toFloat()
        private val trackWidth get() = (trackRight - trackLeft).coerceAtLeast(0f)

        private var actionDownX = 0f
        private val labelRect = RectF()

        fun setup(label: String, min: Float, max: Float, presets: List<Float>) {
            this.label = label
            this.minValue = min
            this.maxValue = max
            this.presets = presets.sorted()
            invalidate()
        }

        fun setCurrentValue(value: Float?) {
            if (isDragging) return
            currentValue = value?.coerceIn(minValue, maxValue)
            invalidate()
        }

        fun cancelDrag() {
            isDragging = false
            invalidate()
        }

        fun notifyDragEnd() {
            onDragEnd?.invoke()
        }

        override fun onMeasure(widthSpec: Int, heightSpec: Int) {
            val expandedH = (floatingReserve + thumbR * 2 + 8f * density + compactH).toInt()
            val desiredH = if (showLabel) expandedH else compactH.toInt()
            setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(desiredH, heightSpec))
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (trackWidth <= 0) return

            val cy = trackCenter
            val tl = trackLeft
            val tw = trackWidth

            // Track background
            canvas.drawRoundRect(tl, cy - trackH / 2, tl + tw, cy + trackH / 2, trackH / 2, trackH / 2, trackPaint)

            // Active track
            currentValue?.let { v ->
                val frac = valueToFraction(v, minValue, maxValue)
                val thumbX = tl + frac * tw
                if (thumbX > tl + 1) {
                    canvas.drawRoundRect(tl, cy - trackH / 2, thumbX, cy + trackH / 2, trackH / 2, trackH / 2, activePaint)
                }
            }

            // Preset dots
            for (p in presets) {
                val frac = valueToFraction(p, minValue, maxValue)
                val x = tl + frac * tw
                val isActive = currentValue?.let { abs(it - p) < 0.05f } == true
                canvas.drawCircle(x, cy, if (isActive) dotActiveR else dotR, if (isActive) dotActivePaint else dotPaint)
            }

            // Thumb
            val thumbX = currentValue?.let { tl + valueToFraction(it, minValue, maxValue) * tw } ?: tl
            canvas.drawCircle(thumbX, cy, thumbR, thumbPaint)

            // Floating label
            if (showLabel) {
                currentValue?.let { drawFloatingLabel(canvas, thumbX, cy, it) }
            }
        }

        private fun drawFloatingLabel(canvas: Canvas, thumbX: Float, cy: Float, value: Float) {
            val text = value.toInt().toString()
            val tw = floatingPaint.measureText(text)
            val lw = tw + floatPadH * 2
            val lh = floatingPaint.textSize + floatPadV * 2

            val ll = (thumbX - lw / 2).coerceIn(paddingLeft.toFloat(), (width - paddingRight - lw).coerceAtLeast(paddingLeft.toFloat()))
            val lt = cy - thumbR - floatBottom - lh

            canvas.drawRoundRect(ll, lt, ll + lw, lt + lh, floatRadius, floatRadius, floatingBgPaint)
            canvas.drawText(text, ll + lw / 2, lt + lh / 2 - (floatingPaint.descent() + floatingPaint.ascent()) / 2, floatingPaint)
        }

        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (!isEnabled) return false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (abs(event.y - trackCenter) < thumbR * 2.5f) {
                        isDragging = true
                        actionDownX = event.x
                        parent?.requestDisallowInterceptTouchEvent(true)
                        updateValueFromX(event.x)
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (isDragging) { updateValueFromX(event.x); return true }
                    return false
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        isDragging = false
                        val v = currentValue ?: return false
                        val tapSlopPx = TAP_SLOP.toFloat() * density
                        val dotTapPx = DOT_TAP_RADIUS.toFloat() * density
                        if (abs(event.x - actionDownX) < tapSlopPx) {
                            val tl2 = trackLeft
                            val tw2 = trackWidth
                            presets.minByOrNull { abs(valueToFraction(it, minValue, maxValue) * tw2 + tl2 - event.x) }
                                ?.takeIf { abs(valueToFraction(it, minValue, maxValue) * tw2 + tl2 - event.x) < dotTapPx }
                                ?.let { currentValue = it }
                        } else if (shouldSnapPreset(v)) {
                            findNearestPreset(v, presets)?.let { currentValue = it }
                        }
                        invalidate()
                        onDragEnd?.invoke()
                        return true
                    }
                    return false
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (isDragging) { isDragging = false; invalidate(); return true }
                    return false
                }
            }
            return super.onTouchEvent(event)
        }

        private fun updateValueFromX(x: Float) {
            if (trackWidth <= 0) return
            val frac = ((x - trackLeft) / trackWidth).coerceIn(0f, 1f)
            currentValue = fractionToValue(frac, minValue, maxValue)
            invalidate()
        }

        private fun shouldSnapPreset(v: Float): Boolean {
            if (presets.size < 2) return presets.isNotEmpty()
            val nearest = presets.minByOrNull { abs(it - v) } ?: return false
            val second = presets.sortedBy { abs(it - v) }.getOrNull(1) ?: return true
            val dist = abs(second - nearest)
            return abs(v - nearest) + 1e-6f < maxOf(dist * SNAP_FRACTION, SNAP_MAX_DELTA)
        }
    }
}
