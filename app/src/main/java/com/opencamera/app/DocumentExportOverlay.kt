package com.opencamera.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

@SuppressLint("ViewConstructor")
internal class DocumentExportOverlay(
    context: Context,
    private val onCancel: () -> Unit = {},
    private val onReturn: () -> Unit = {},
    private val onRetry: () -> Unit = {}
) : FrameLayout(context) {

    private val titleView: TextView
    private val progressView: TextView
    private val retryButton: Button
    private val returnButton: Button
    private val cancelButton: Button

    init {
        isVisible = false
        setBackgroundColor(0xCC000000.toInt())

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT
            )
        }

        titleView = TextView(context).apply {
            setTextColor(Color.WHITE)
            textSize = 18f
            gravity = Gravity.CENTER
        }
        content.addView(titleView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        progressView = TextView(context).apply {
            setTextColor(0x99FFFFFF.toInt())
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 32)
        }
        content.addView(progressView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        retryButton = Button(context).apply {
            textSize = 14f
            minimumWidth = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFFFF9800.toInt())
            isVisible = false
        }
        buttonRow.addView(retryButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = 12 })

        returnButton = Button(context).apply {
            textSize = 14f
            minimumWidth = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
            isVisible = false
        }
        buttonRow.addView(returnButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        content.addView(buttonRow)

        cancelButton = Button(context).apply {
            textSize = 14f
            minimumWidth = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            setTextColor(0xFFCCCCCC.toInt())
            setBackgroundColor(0x33FFFFFF)
        }
        content.addView(cancelButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = 24 })

        addView(content, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        cancelButton.setOnClickListener { onCancel() }
        returnButton.setOnClickListener { onReturn() }
        retryButton.setOnClickListener { onRetry() }
    }

    fun render(model: DocumentExportRenderModel) {
        isVisible = model.visible
        if (!model.visible) return

        titleView.text = model.titleText
        progressView.text = model.progressText
        progressView.isVisible = model.progressText.isNotEmpty()

        cancelButton.isVisible = model.showReturnButton.not() && model.showRetryButton.not()
        returnButton.isVisible = model.showReturnButton
        returnButton.text = model.returnLabel
        retryButton.isVisible = model.showRetryButton
        retryButton.text = model.retryLabel
    }
}
