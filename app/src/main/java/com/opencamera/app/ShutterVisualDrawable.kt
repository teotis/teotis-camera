package com.opencamera.app

import android.animation.ValueAnimator
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.view.animation.DecelerateInterpolator
import kotlin.math.min

internal enum class ShutterVisualState {
    PHOTO_READY,
    PHOTO_PRESSED,
    COUNTDOWN,
    SAVING,
    VIDEO_REQUESTING,
    VIDEO_RECORDING,
    VIDEO_STOPPING,
    BLOCKED,
    FAILURE_OR_DEGRADED
}

internal class ShutterVisualDrawable : Drawable() {

    companion object {
        private val SHUTTER_RING_GRAY = Color.rgb(224, 224, 224)
        private val SHUTTER_FILL_GRAY = Color.rgb(208, 208, 208)
    }

    var visualState: ShutterVisualState = ShutterVisualState.PHOTO_READY
        set(value) {
            if (field == value) return
            field = value
            onStateChanged()
        }

    var countdownProgress: Float = 0f
        set(value) {
            field = value.coerceIn(0f, 1f)
            invalidateSelf()
        }

    var savingRotation: Float = 0f
        set(value) {
            field = value
            invalidateSelf()
        }

    private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val progressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val countdownTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private var ringAlpha = 255
    private var fillAlpha = 255
    private var progressAlpha = 0
    private var innerScale = 1f

    private var pressAnimator: ValueAnimator? = null
    private var savingAnimator: ValueAnimator? = null

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        val cx = bounds.exactCenterX()
        val cy = bounds.exactCenterY()
        val maxRadius = min(bounds.width(), bounds.height()) / 2f
        val density = maxRadius / 36f

        val ringStroke = 3.5f * density
        val ringRadius = maxRadius - ringStroke
        val innerRadius = maxRadius * 0.55f * innerScale

        ringPaint.strokeWidth = ringStroke
        progressPaint.strokeWidth = ringStroke

        when (visualState) {
            ShutterVisualState.PHOTO_READY -> {
                ringPaint.color = SHUTTER_RING_GRAY
                ringPaint.alpha = ringAlpha
                fillPaint.color = SHUTTER_FILL_GRAY
                fillPaint.alpha = fillAlpha
                drawPhotoReady(canvas, cx, cy, ringRadius, innerRadius)
            }
            ShutterVisualState.PHOTO_PRESSED -> {
                ringPaint.color = SHUTTER_RING_GRAY
                ringPaint.alpha = ringAlpha
                fillPaint.color = SHUTTER_FILL_GRAY
                fillPaint.alpha = (fillAlpha * 0.6f).toInt()
                drawPhotoReady(canvas, cx, cy, ringRadius, innerRadius * 0.85f)
            }
            ShutterVisualState.COUNTDOWN -> {
                ringPaint.color = SHUTTER_RING_GRAY
                ringPaint.alpha = ringAlpha
                fillPaint.color = Color.argb(85, 208, 208, 208)
                fillPaint.alpha = fillAlpha
                progressPaint.color = Color.rgb(133, 214, 190)
                progressPaint.alpha = progressAlpha
                drawCountdown(canvas, cx, cy, ringRadius, innerRadius)
            }
            ShutterVisualState.SAVING -> {
                ringPaint.color = Color.argb(133, 200, 200, 200)
                ringPaint.alpha = ringAlpha
                drawSaving(canvas, cx, cy, ringRadius, ringStroke)
            }
            ShutterVisualState.VIDEO_REQUESTING -> {
                ringPaint.color = Color.rgb(220, 60, 60)
                ringPaint.alpha = ringAlpha
                fillPaint.color = Color.rgb(180, 40, 40)
                fillPaint.alpha = (fillAlpha * 0.5f).toInt()
                drawPhotoReady(canvas, cx, cy, ringRadius, innerRadius)
            }
            ShutterVisualState.VIDEO_RECORDING -> {
                ringPaint.color = Color.rgb(220, 60, 60)
                ringPaint.alpha = ringAlpha
                fillPaint.color = Color.rgb(220, 60, 60)
                fillPaint.alpha = fillAlpha
                val stopSize = maxRadius * 0.35f
                val stopCorner = 4f * density
                val stopRect = RectF(
                    cx - stopSize, cy - stopSize,
                    cx + stopSize, cy + stopSize
                )
                canvas.drawRoundRect(stopRect, stopCorner, stopCorner, fillPaint)
                canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            }
            ShutterVisualState.VIDEO_STOPPING -> {
                ringPaint.color = Color.argb(133, 160, 60, 60)
                ringPaint.alpha = ringAlpha
                fillPaint.color = Color.argb(120, 180, 80, 80)
                fillPaint.alpha = fillAlpha
                val stopSize = maxRadius * 0.35f
                val stopRect = RectF(
                    cx - stopSize, cy - stopSize,
                    cx + stopSize, cy + stopSize
                )
                canvas.drawRoundRect(stopRect, 4f * density, 4f * density, fillPaint)
                canvas.drawCircle(cx, cy, ringRadius, ringPaint)
            }
            ShutterVisualState.BLOCKED -> {
                ringPaint.color = Color.argb(100, 200, 200, 200)
                ringPaint.alpha = ringAlpha
                fillPaint.color = Color.argb(51, 133, 214, 190)
                fillPaint.alpha = fillAlpha
                drawPhotoReady(canvas, cx, cy, ringRadius, innerRadius)
            }
            ShutterVisualState.FAILURE_OR_DEGRADED -> {
                ringPaint.color = Color.argb(160, 255, 120, 80)
                ringPaint.alpha = ringAlpha
                drawFailure(canvas, cx, cy, ringRadius, ringStroke)
            }
        }
    }

    private fun drawPhotoReady(canvas: Canvas, cx: Float, cy: Float, ringRadius: Float, innerRadius: Float) {
        canvas.drawCircle(cx, cy, innerRadius, fillPaint)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
    }

    private fun drawCountdown(canvas: Canvas, cx: Float, cy: Float, ringRadius: Float, innerRadius: Float) {
        canvas.drawCircle(cx, cy, innerRadius, fillPaint)
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        if (countdownProgress > 0f && progressAlpha > 0) {
            val sweepAngle = countdownProgress * 360f
            val oval = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
            canvas.drawArc(oval, -90f, sweepAngle, false, progressPaint)
        }
    }

    private fun drawSaving(canvas: Canvas, cx: Float, cy: Float, ringRadius: Float, ringStroke: Float) {
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        if (progressAlpha > 0) {
            progressPaint.color = Color.argb(progressAlpha, 133, 214, 190)
            val oval = RectF(cx - ringRadius, cy - ringRadius, cx + ringRadius, cy + ringRadius)
            val startAngle = savingRotation
            canvas.drawArc(oval, startAngle, 90f, false, progressPaint)
        }
    }

    private fun drawFailure(canvas: Canvas, cx: Float, cy: Float, ringRadius: Float, ringStroke: Float) {
        canvas.drawCircle(cx, cy, ringRadius, ringPaint)
        val offset = ringRadius * 0.3f
        val failPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(ringAlpha, 255, 120, 80)
            strokeWidth = ringStroke
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - offset, cy - offset, cx + offset, cy + offset, failPaint)
        canvas.drawLine(cx + offset, cy - offset, cx - offset, cy + offset, failPaint)
    }

    private fun onStateChanged() {
        pressAnimator?.cancel()
        savingAnimator?.cancel()

        ringAlpha = 255
        fillAlpha = 255
        progressAlpha = 255
        innerScale = 1f

        when (visualState) {
            ShutterVisualState.PHOTO_PRESSED -> {
                pressAnimator = ValueAnimator.ofFloat(1f, 0.85f).apply {
                    duration = 80L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        innerScale = it.animatedValue as Float
                        invalidateSelf()
                    }
                    start()
                }
            }
            ShutterVisualState.SAVING -> {
                val rotateAnim = ValueAnimator.ofFloat(0f, 360f).apply {
                    duration = 1200L
                    repeatCount = ValueAnimator.INFINITE
                    interpolator = null
                    addUpdateListener {
                        savingRotation = it.animatedValue as Float
                    }
                    start()
                }
                savingAnimator = rotateAnim
            }
            ShutterVisualState.FAILURE_OR_DEGRADED -> {
                pressAnimator = ValueAnimator.ofFloat(0f, 4f).apply {
                    duration = 300L
                    interpolator = DecelerateInterpolator()
                    addUpdateListener {
                        val shakeOffset = (it.animatedValue as Float)
                        val offset = if ((shakeOffset.toInt() / 2) % 2 == 0) shakeOffset else -shakeOffset
                        val b = bounds
                        setBounds(b.left + offset.toInt(), b.top, b.right + offset.toInt(), b.bottom)
                        invalidateSelf()
                    }
                    start()
                }
            }
            else -> {}
        }
        invalidateSelf()
    }

    override fun setAlpha(alpha: Int) {
        ringAlpha = alpha
        fillAlpha = alpha
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {}

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }
}
