package com.opencamera.app.procontrols

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.LinearLayout
import androidx.core.content.ContextCompat

/** Listener interface for ProControlPickerView discrete option changes. */
interface OnOptionChangeListener {
    fun onOptionChange(value: String?)
    fun onAutoToggle()
    fun onReset()
}

class ProControlPickerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    var optionListener: OnOptionChangeListener? = null

    private val density = context.resources.displayMetrics.density
    private val scaledDensity = context.resources.displayMetrics.scaledDensity

    private val labelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_secondary)
        textSize = 13f * scaledDensity
        isFakeBoldText = true
    }
    private val optionBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF3A3F4B.toInt()
        style = Paint.Style.FILL
    }
    private val optionSelectedBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF55D6BE.toInt()
        style = Paint.Style.FILL
        alpha = 40
    }
    private val optionTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
        textSize = 12f * scaledDensity
        textAlign = Paint.Align.CENTER
    }
    private val optionSelectedTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_accent)
        textSize = 12f * scaledDensity
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val optionDisabledPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFF222222.toInt()
        style = Paint.Style.FILL
        alpha = 120
    }
    private val optionDisabledTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ContextCompat.getColor(context, com.opencamera.app.R.color.oc_text_muted)
        textSize = 12f * scaledDensity
        textAlign = Paint.Align.CENTER
        alpha = 120
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

    private val optH = 28f * density
    private val optW = 60f * density
    private val optRadius = 6f * density
    private val optMargin = 8f * density

    private val btnH = 26f * density
    private val btnRadius = 6f * density

    private var label: String = ""
    private var options: List<String> = emptyList()
    private var currentOption: String? = null
    private var initialOption: String? = null
    internal var availability: Availability = Availability.SUPPORTED
    private var isAuto: Boolean = true

    private val autoButtonArea = RectF()
    private val resetButtonArea = RectF()
    private val optionAreas = mutableListOf<RectF>()

    fun configure(
        label: String,
        options: List<String>,
        initial: String? = null,
    ) {
        this.label = label
        this.options = options
        this.initialOption = initial
        if (initial != null) {
            isAuto = false
            currentOption = initial
        } else {
            isAuto = true
            currentOption = null
        }
        recalcAreas()
        invalidate()
    }

    fun setAvailability(avail: Availability) {
        availability = avail
        invalidate()
    }

    fun setCurrentOption(option: String?) {
        currentOption = option
        isAuto = option == null
        invalidate()
    }

    fun setOnOptionChangeListener(listener: OnOptionChangeListener?) {
        this.optionListener = listener
    }

    private fun recalcAreas() {
        optionAreas.clear()
        var x = paddingLeft.toFloat()
        for (index in options.indices) {
            @Suppress("UNUSED_VARIABLE")
            val opt = options[index]
            optionAreas.add(RectF(x, 0f, x + optW, optH))
            x += optW + optMargin
        }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val controlRowH = 32f * density
        val gap = 8f * density
        val totalH = (controlRowH + gap + optH).toInt()
        setMeasuredDimension(MeasureSpec.getSize(widthSpec), resolveSize(totalH, heightSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()

        // Label
        canvas.drawText(label, paddingLeft.toFloat(), 20f * scaledDensity, labelPaint)

        // Option row
        val gap = 8f * density
        val optRowTop = (32f * density + gap).toFloat()

        if (availability == Availability.UNSUPPORTED) {
            for (i in options.indices) {
                val r = optionAreas.getOrNull(i) ?: continue
                canvas.drawRoundRect(r.left, optRowTop, r.right, optRowTop + optH, optRadius, optRadius, optionDisabledPaint)
                val t = options[i]
                canvas.drawText(t, r.left + optW / 2f, optRowTop + optH / 2f - (optionDisabledTextPaint.descent() + optionDisabledTextPaint.ascent()) / 2f, optionDisabledTextPaint)
            }
        } else {
            for (i in options.indices) {
                val r = optionAreas.getOrNull(i) ?: continue
                val isSelected = options[i] == currentOption && !isAuto
                val bg = if (isSelected) optionSelectedBgPaint else optionBgPaint
                val tp = if (isSelected) optionSelectedTextPaint else optionTextPaint
                canvas.drawRoundRect(r.left, optRowTop, r.right, optRowTop + optH, optRadius, optRadius, bg)
                val t = options[i]
                canvas.drawText(t, r.left + optW / 2f, optRowTop + optH / 2f - (tp.descent() + tp.ascent()) / 2f, tp)
            }
        }

        // Auto button (right-aligned)
        updateButtonAreas(w)

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
        canvas.drawRoundRect(autoButtonArea, btnRadius, btnRadius, autoBtnPaint)
        val autoTextY = autoButtonArea.centerY() - (autoBtnTextPaint.descent() + autoBtnTextPaint.ascent()) / 2f
        canvas.drawText("Auto", autoButtonArea.centerX(), autoTextY, autoBtnTextPaint)

        canvas.drawRoundRect(resetButtonArea, btnRadius, btnRadius, resetBtnPaint)
        val resetTextY = resetButtonArea.centerY() - (resetBtnTextPaint.descent() + resetBtnTextPaint.ascent()) / 2f
        canvas.drawText("重置", resetButtonArea.centerX(), resetTextY, resetBtnTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (availability == Availability.UNSUPPORTED) return false
        if (event.action == MotionEvent.ACTION_DOWN) {
            updateButtonAreas(width.toFloat())
            val gap = 8f * density
            val optRowTop = (32f * density + gap).toFloat()
            val localY = event.y

            // Option buttons
            if (localY >= optRowTop && localY <= optRowTop + optH) {
                for (i in options.indices) {
                    val r = optionAreas.getOrNull(i) ?: continue
                    if (event.x >= r.left && event.x <= r.right) {
                        currentOption = options[i]
                        isAuto = false
                        optionListener?.onOptionChange(options[i])
                        invalidate()
                        return true
                    }
                }
            }

            // Auto button
            if (autoButtonArea.contains(event.x, localY)) {
                isAuto = !isAuto
                if (isAuto) currentOption = null
                optionListener?.onAutoToggle()
                invalidate()
                return true
            }

            // Reset button
            if (resetButtonArea.contains(event.x, localY)) {
                currentOption = initialOption
                isAuto = initialOption == null
                optionListener?.onReset()
                invalidate()
                return true
            }
        }
        return false
    }

    private fun updateButtonAreas(widthPx: Float) {
        val autoBtnW = 60f * density
        val autoBtnR = widthPx - paddingRight
        val autoBtnL = autoBtnR - autoBtnW
        val autoBtnT = ((32f * density - btnH) / 2f)
        autoButtonArea.set(autoBtnL, autoBtnT, autoBtnR, autoBtnT + btnH)

        val resetBtnW = 48f * density
        val resetBtnR = autoBtnL - 8f * density
        val resetBtnL = resetBtnR - resetBtnW
        resetButtonArea.set(resetBtnL, autoBtnT, resetBtnR, autoBtnT + btnH)
    }
}
