package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

class FilterPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val reticleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }
    private var paletteGradient: RadialGradient? = null
    private var reticleX: Float = 0.5f
    private var reticleY: Float = 0.5f
    private var onPaletteTouch: ((Float, Float) -> Unit)? = null

    fun setOnPaletteTouchListener(listener: (colorAxis: Float, toneAxis: Float) -> Unit) {
        onPaletteTouch = listener
    }

    fun updateReticle(colorAxis: Float, toneAxis: Float) {
        reticleX = (colorAxis + 1f) / 2f
        reticleY = (toneAxis + 1f) / 2f
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            paletteGradient = RadialGradient(
                w / 2f, h / 2f, Math.min(w, h) / 2f,
                intArrayOf(
                    Color.rgb(80, 140, 210),
                    Color.rgb(200, 200, 200),
                    Color.rgb(60, 60, 100),
                    Color.rgb(220, 180, 120),
                    Color.rgb(200, 200, 200),
                    Color.rgb(40, 60, 40)
                ),
                floatArrayOf(0f, 0.3f, 0.5f, 0.7f, 0.85f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Draw palette background with color gradient
        palettePaint.shader = paletteGradient
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), palettePaint)

        // Draw grid lines
        val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.5f
            color = Color.argb(40, 255, 255, 255)
        }
        for (i in 1..3) {
            val x = width * i / 4f
            canvas.drawLine(x, 0f, x, height.toFloat(), gridPaint)
            val y = height * i / 4f
            canvas.drawLine(0f, y, width.toFloat(), y, gridPaint)
        }

        // Draw reticle
        val cx = reticleX * width
        val cy = reticleY * height
        val reticleRadius = 24f
        canvas.drawCircle(cx, cy, reticleRadius, reticleFillPaint)
        canvas.drawCircle(cx, cy, reticleRadius, reticlePaint)
        canvas.drawLine(cx - reticleRadius - 4f, cy, cx + reticleRadius + 4f, cy, reticlePaint)
        canvas.drawLine(cx, cy - reticleRadius - 4f, cx, cy + reticleRadius + 4f, reticlePaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val colorAxis = (event.x / width * 2f - 1f).coerceIn(-1f, 1f)
        val toneAxis = (event.y / height * 2f - 1f).coerceIn(-1f, 1f)
        reticleX = (colorAxis + 1f) / 2f
        reticleY = (toneAxis + 1f) / 2f
        onPaletteTouch?.invoke(colorAxis, toneAxis)
        invalidate()
        return true
    }
}
