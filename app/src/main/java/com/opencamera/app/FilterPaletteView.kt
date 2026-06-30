package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

private const val PALETTE_GRID_COLUMNS = 17
private const val PALETTE_GRID_ROWS = 9
private const val PALETTE_DOT_SNAP_FRACTION = 0.4f
private const val PALETTE_ORIGIN_SNAP_FRACTION = 1f

internal data class PaletteAxes(
    val colorAxis: Float,
    val toneAxis: Float
)

internal data class PalettePoint(
    val xFraction: Float,
    val yFraction: Float
)

private data class PalettePixelPoint(
    val x: Float,
    val y: Float
)

internal fun paletteAxesFromPoint(
    x: Float,
    y: Float,
    width: Int,
    height: Int
): PaletteAxes {
    if (width <= 0 || height <= 0) return PaletteAxes(0f, 0f)
    val clampedX = x.coerceIn(0f, width.toFloat())
    val clampedY = y.coerceIn(0f, height.toFloat())
    val snappedPoint = snappedPalettePoint(clampedX, clampedY, width, height)
    val mappedX = snappedPoint?.x ?: clampedX
    val mappedY = snappedPoint?.y ?: clampedY
    return PaletteAxes(
        colorAxis = (mappedX / width.toFloat() * 2f - 1f).coerceIn(-1f, 1f),
        toneAxis = (1f - mappedY / height.toFloat() * 2f).coerceIn(-1f, 1f)
    )
}

internal fun palettePointFromAxes(
    colorAxis: Float,
    toneAxis: Float
): PalettePoint {
    return PalettePoint(
        xFraction = ((colorAxis.coerceIn(-1f, 1f) + 1f) / 2f).coerceIn(0f, 1f),
        yFraction = ((1f - toneAxis.coerceIn(-1f, 1f)) / 2f).coerceIn(0f, 1f)
    )
}

private fun snappedPalettePoint(
    x: Float,
    y: Float,
    width: Int,
    height: Int
): PalettePixelPoint? {
    val xStep = width / (PALETTE_GRID_COLUMNS + 1f)
    val yStep = height / (PALETTE_GRID_ROWS + 1f)
    val centerX = ((PALETTE_GRID_COLUMNS + 1) / 2f) * xStep
    val centerY = ((PALETTE_GRID_ROWS + 1) / 2f) * yStep

    if (
        abs(x - centerX) <= xStep * PALETTE_ORIGIN_SNAP_FRACTION &&
        abs(y - centerY) <= yStep * PALETTE_ORIGIN_SNAP_FRACTION
    ) {
        return PalettePixelPoint(centerX, centerY)
    }

    val nearestColumn = (x / xStep).roundToInt().coerceIn(1, PALETTE_GRID_COLUMNS)
    val nearestRow = (y / yStep).roundToInt().coerceIn(1, PALETTE_GRID_ROWS)
    val dotX = nearestColumn * xStep
    val dotY = nearestRow * yStep
    return if (
        abs(x - dotX) <= xStep * PALETTE_DOT_SNAP_FRACTION &&
        abs(y - dotY) <= yStep * PALETTE_DOT_SNAP_FRACTION
    ) {
        PalettePixelPoint(dotX, dotY)
    } else {
        null
    }
}

class FilterPaletteView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val palettePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tonePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val centerGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(34, 255, 255, 255)
    }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(120, 255, 255, 255)
    }
    private val axisPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.25f
        color = Color.argb(86, 255, 255, 255)
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = Color.argb(110, 255, 255, 255)
    }
    private val reticlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = Color.WHITE
    }
    private val reticleFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(80, 255, 255, 255)
    }
    private val paletteRect = RectF()
    private var colorGradient: LinearGradient? = null
    private var toneGradient: LinearGradient? = null
    private var reticleX: Float = 0.5f
    private var reticleY: Float = 0.5f
    private var onPaletteTouch: ((Float, Float) -> Unit)? = null

    fun setOnPaletteTouchListener(listener: (colorAxis: Float, toneAxis: Float) -> Unit) {
        onPaletteTouch = listener
    }

    fun updateReticle(colorAxis: Float, toneAxis: Float) {
        val point = palettePointFromAxes(colorAxis, toneAxis)
        reticleX = point.xFraction
        reticleY = point.yFraction
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            colorGradient = LinearGradient(
                0f,
                0f,
                w.toFloat(),
                0f,
                intArrayOf(
                    Color.rgb(20, 138, 196),
                    Color.rgb(88, 185, 178),
                    Color.rgb(226, 216, 151),
                    Color.rgb(237, 139, 82),
                    Color.rgb(231, 88, 95)
                ),
                floatArrayOf(0f, 0.24f, 0.5f, 0.76f, 1f),
                Shader.TileMode.CLAMP
            )
            toneGradient = LinearGradient(
                0f,
                0f,
                0f,
                h.toFloat(),
                intArrayOf(
                    Color.argb(138, 255, 255, 255),
                    Color.argb(0, 255, 255, 255),
                    Color.argb(128, 0, 0, 0)
                ),
                floatArrayOf(0f, 0.48f, 1f),
                Shader.TileMode.CLAMP
            )
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        paletteRect.set(0f, 0f, width.toFloat(), height.toFloat())

        palettePaint.shader = colorGradient
        canvas.drawRoundRect(paletteRect, 18f, 18f, palettePaint)

        tonePaint.shader = toneGradient
        canvas.drawRoundRect(paletteRect, 18f, 18f, tonePaint)

        drawPaletteDotGrid(canvas)
        drawPaletteAxes(canvas)

        val cx = reticleX * width
        val cy = reticleY * height
        val reticleRadius = (min(width, height) * 0.055f)
            .coerceIn(16f, 26f)
        canvas.drawCircle(cx, cy, reticleRadius * 1.55f, centerGlowPaint)
        canvas.drawCircle(cx, cy, reticleRadius, reticleFillPaint)
        canvas.drawCircle(cx, cy, reticleRadius, reticlePaint)
        canvas.drawLine(cx - reticleRadius - 4f, cy, cx + reticleRadius + 4f, cy, reticlePaint)
        canvas.drawLine(cx, cy - reticleRadius - 4f, cx, cy + reticleRadius + 4f, reticlePaint)
        canvas.drawRoundRect(paletteRect, 18f, 18f, borderPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val axes = paletteAxesFromPoint(event.x, event.y, width, height)
        val point = palettePointFromAxes(axes.colorAxis, axes.toneAxis)
        reticleX = point.xFraction
        reticleY = point.yFraction
        onPaletteTouch?.invoke(axes.colorAxis, axes.toneAxis)
        invalidate()
        return true
    }

    private fun drawPaletteDotGrid(canvas: Canvas) {
        val columns = PALETTE_GRID_COLUMNS
        val rows = PALETTE_GRID_ROWS
        val xStep = width / (columns + 1f)
        val yStep = height / (rows + 1f)
        val radius = (min(width, height) * 0.0065f)
            .coerceIn(1.6f, 3.2f)
        for (row in 1..rows) {
            for (column in 1..columns) {
                val distanceFromCenter = kotlin.math.abs(column - (columns + 1) / 2f) / columns +
                    kotlin.math.abs(row - (rows + 1) / 2f) / rows
                dotPaint.alpha = (150 - distanceFromCenter * 70).toInt().coerceIn(72, 150)
                canvas.drawCircle(column * xStep, row * yStep, radius, dotPaint)
            }
        }
    }

    private fun drawPaletteAxes(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        canvas.drawLine(centerX, 0f, centerX, height.toFloat(), axisPaint)
        canvas.drawLine(0f, centerY, width.toFloat(), centerY, axisPaint)
    }
}
