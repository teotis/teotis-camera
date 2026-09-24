package com.opencamera.app.procontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.content.ContextCompat
import com.opencamera.app.R
import com.opencamera.app.RuntimeProScaleOption
import kotlin.math.abs

/** A quiet, single-parameter scale that keeps most of the camera preview unobstructed. */
internal class ImmersiveProScaleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private val density = resources.displayMetrics.density
    private val scaledDensity = resources.displayMetrics.scaledDensity
    private val accent = ContextCompat.getColor(context, R.color.oc_accent)
    private val primary = ContextCompat.getColor(context, R.color.oc_text_primary)
    private val secondary = ContextCompat.getColor(context, R.color.oc_text_secondary)
    private val muted = ContextCompat.getColor(context, R.color.oc_text_muted)

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = 12f * scaledDensity
    }
    private val tickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        strokeWidth = density
        strokeCap = Paint.Cap.ROUND
    }
    private val activeTickPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = accent
        strokeWidth = 2.5f * density
        strokeCap = Paint.Cap.ROUND
    }

    private var options: List<RuntimeProScaleOption> = emptyList()
    private var onOptionSelected: ((RuntimeProScaleOption) -> Unit)? = null

    fun render(
        options: List<RuntimeProScaleOption>,
        onOptionSelected: (RuntimeProScaleOption) -> Unit
    ) {
        this.options = options
        this.onOptionSelected = onOptionSelected
        isEnabled = options.any { it.action != null }
        contentDescription = options.joinToString(separator = ", ") { option ->
            if (option.isSelected) "${option.label} selected" else option.label
        }
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (options.isEmpty()) return

        val startX = paddingLeft + 10f * density
        val endX = width - paddingRight - 10f * density
        val trackY = height - paddingBottom - 18f * density
        val labelY = trackY - 24f * density
        val positions = optionPositions(startX, endX)

        positions.forEachIndexed { index, x ->
            val option = options[index]
            labelPaint.color = when {
                option.isSelected -> primary
                option.action == null -> muted
                else -> secondary
            }
            labelPaint.isFakeBoldText = option.isSelected
            canvas.drawText(option.label, x, labelY, labelPaint)

            if (option.isSelected && option.action != null) {
                canvas.drawLine(x, trackY - 10f * density, x, trackY + 10f * density, activeTickPaint)
            } else {
                tickPaint.color = if (option.action == null) muted else secondary
                tickPaint.alpha = if (option.action == null) 90 else 170
                canvas.drawLine(x, trackY - 5f * density, x, trackY + 5f * density, tickPaint)
            }

            if (index < positions.lastIndex) {
                val nextX = positions[index + 1]
                repeat(3) { minorIndex ->
                    val fraction = (minorIndex + 1) / 4f
                    val minorX = x + (nextX - x) * fraction
                    tickPaint.color = muted
                    tickPaint.alpha = 90
                    canvas.drawLine(
                        minorX,
                        trackY - 2.5f * density,
                        minorX,
                        trackY + 2.5f * density,
                        tickPaint
                    )
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP || options.isEmpty()) return true
        val startX = paddingLeft + 10f * density
        val endX = width - paddingRight - 10f * density
        val positions = optionPositions(startX, endX)
        val index = positions.indices.minByOrNull { abs(positions[it] - event.x) } ?: return true
        val option = options[index]
        if (option.action != null && !option.isSelected) {
            performClick()
            onOptionSelected?.invoke(option)
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun optionPositions(startX: Float, endX: Float): List<Float> {
        if (options.size == 1) return listOf((startX + endX) / 2f)
        val step = (endX - startX) / (options.size - 1)
        return options.indices.map { startX + step * it }
    }
}
