package com.opencamera.app

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@SuppressLint("ViewConstructor")
internal class DocumentCropEditOverlay(
    context: Context,
    private val onConfirm: () -> Unit = {},
    private val onCancel: () -> Unit = {},
    private val onEdgeDragged: (CropEdges) -> Unit = {}
) : FrameLayout(context) {

    private var currentEdges = CropEdges.default()

    /** Returns the current crop edges, updated by drag interactions. */
    fun getCurrentEdges(): CropEdges = currentEdges

    private val scrimView = View(context).apply {
        setBackgroundColor(0xCC000000.toInt())
    }

    private val cropAreaView = CropAreaView(context).apply {
        clipChildren = false
    }

    private val imageView = ImageView(context).apply {
        scaleType = ImageView.ScaleType.FIT_CENTER
        setBackgroundColor(0xFF1A1A1A.toInt())
    }

    private val titleView = TextView(context).apply {
        setTextColor(Color.WHITE)
        textSize = 14f
        gravity = Gravity.CENTER
    }

    private val confirmButton: Button
    private val cancelButton: Button

    private var draggingPointId: Int = -1
    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private val dragThreshold = 20f

    init {
        isVisible = false
        addView(scrimView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        val contentLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(24, 48, 24, 24)
        }

        contentLayout.addView(titleView, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ))

        val imageContainer = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 12
            }
        }
        imageContainer.addView(imageView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        imageContainer.addView(cropAreaView, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        contentLayout.addView(imageContainer, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            0,
            1f
        ))

        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }

        cancelButton = Button(context).apply {
            textSize = 14f
            minimumWidth = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            setTextColor(0xFFCCCCCC.toInt())
            setBackgroundColor(0x33FFFFFF)
        }
        buttonRow.addView(cancelButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ).apply { marginEnd = 12 })

        confirmButton = Button(context).apply {
            textSize = 14f
            minimumWidth = 0
            minimumHeight = 0
            setPadding(32, 16, 32, 16)
            setTextColor(0xFFFFFFFF.toInt())
            setBackgroundColor(0xFF4CAF50.toInt())
        }
        buttonRow.addView(confirmButton, LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f
        ))

        contentLayout.addView(buttonRow)

        addView(contentLayout, LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        confirmButton.setOnClickListener { onConfirm() }
        cancelButton.setOnClickListener { onCancel() }
    }

    fun render(model: DocumentCropEditRenderModel) {
        isVisible = model.visible
        if (!model.visible) return

        titleView.text = model.titleText
        confirmButton.text = model.confirmLabel
        cancelButton.text = model.cancelLabel

        if (model.pageRenderUri != null) {
            imageView.setImageURI(null)
            imageView.setImageURI(Uri.parse(model.pageRenderUri))
        } else {
            imageView.setImageDrawable(null)
        }

        currentEdges = model.cropEdges
        post { updateCropArea() }
    }

    private fun updateCropArea() {
        val imageWidth = imageView.width.toFloat()
        val imageHeight = imageView.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return

        cropAreaView.setEdges(currentEdges, imageWidth, imageHeight)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (!isVisible) return false
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val imageWidth = imageView.width.toFloat()
            val imageHeight = imageView.height.toFloat()
            if (imageWidth <= 0f || imageHeight <= 0f) return false

            val points = cropEditControlPoints(currentEdges)
            for (point in points) {
                val px = imageView.left + point.x * imageWidth
                val py = imageView.top + point.y * imageHeight
                if (abs(ev.rawX - px) < dragThreshold && abs(ev.rawY - py) < dragThreshold) {
                    draggingPointId = point.id
                    lastTouchX = ev.rawX
                    lastTouchY = ev.rawY
                    return true
                }
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isVisible || draggingPointId < 0) return false

        val imageWidth = imageView.width.toFloat()
        val imageHeight = imageView.height.toFloat()
        if (imageWidth <= 0f || imageHeight <= 0f) return false

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                val dx = (event.rawX - lastTouchX) / imageWidth
                val dy = (event.rawY - lastTouchY) / imageHeight
                lastTouchX = event.rawX
                lastTouchY = event.rawY

                currentEdges = applyDrag(currentEdges, draggingPointId, dx, dy)
                currentEdges = CropEdges(
                    left = currentEdges.left.coerceIn(0f, currentEdges.right - 0.05f),
                    top = currentEdges.top.coerceIn(0f, currentEdges.bottom - 0.05f),
                    right = currentEdges.right.coerceIn(currentEdges.left + 0.05f, 1f),
                    bottom = currentEdges.bottom.coerceIn(currentEdges.top + 0.05f, 1f)
                )
                updateCropArea()
                onEdgeDragged(currentEdges)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                draggingPointId = -1
            }
        }
        return true
    }

    override fun performClick(): Boolean {
        return super.performClick()
    }

    private fun applyDrag(edges: CropEdges, pointId: Int, dx: Float, dy: Float): CropEdges = when (pointId) {
        0 -> edges.copy(left = (edges.left + dx).coerceAtMost(edges.right - 0.05f), top = (edges.top + dy).coerceAtMost(edges.bottom - 0.05f))
        1 -> edges.copy(top = (edges.top + dy).coerceAtMost(edges.bottom - 0.05f))
        2 -> edges.copy(right = (edges.right + dx).coerceAtLeast(edges.left + 0.05f), top = (edges.top + dy).coerceAtMost(edges.bottom - 0.05f))
        3 -> edges.copy(right = (edges.right + dx).coerceAtLeast(edges.left + 0.05f))
        4 -> edges.copy(right = (edges.right + dx).coerceAtLeast(edges.left + 0.05f), bottom = (edges.bottom + dy).coerceAtLeast(edges.top + 0.05f))
        5 -> edges.copy(bottom = (edges.bottom + dy).coerceAtLeast(edges.top + 0.05f))
        6 -> edges.copy(left = (edges.left + dx).coerceAtMost(edges.right - 0.05f), bottom = (edges.bottom + dy).coerceAtLeast(edges.top + 0.05f))
        7 -> edges.copy(left = (edges.left + dx).coerceAtMost(edges.right - 0.05f))
        else -> edges
    }

    internal class CropAreaView(context: Context) : View(context) {
        private var edges = CropEdges.default()
        private var imageWidth = 0f
        private var imageHeight = 0f

        private val dimPaint = Paint().apply {
            color = 0x99000000.toInt()
            style = Paint.Style.FILL
        }

        private val borderPaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        private val handlePaint = Paint().apply {
            color = 0xFFFFFFFF.toInt()
            style = Paint.Style.FILL
            isAntiAlias = true
        }

        fun setEdges(edges: CropEdges, imgWidth: Float, imgHeight: Float) {
            this.edges = edges
            this.imageWidth = imgWidth
            this.imageHeight = imgHeight
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            if (imageWidth <= 0f || imageHeight <= 0f) return

            val left = edges.left * imageWidth
            val top = edges.top * imageHeight
            val right = edges.right * imageWidth
            val bottom = edges.bottom * imageHeight

            // Dim outside the crop area
            canvas.drawRect(0f, 0f, imageWidth, top, dimPaint)
            canvas.drawRect(0f, bottom, imageWidth, imageHeight, dimPaint)
            canvas.drawRect(0f, top, left, bottom, dimPaint)
            canvas.drawRect(right, top, imageWidth, bottom, dimPaint)

            // Draw crop border
            canvas.drawRect(left, top, right, bottom, borderPaint)

            // Draw control point handles
            val points = cropEditControlPoints(edges)
            for (point in points) {
                val px = point.x * imageWidth
                val py = point.y * imageHeight
                canvas.drawCircle(px, py, 12f, handlePaint)
            }
        }
    }
}
