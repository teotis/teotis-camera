package com.opencamera.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import kotlin.math.min

/**
 * Horizontal scrolling rail of [StylePresetCardTileView] instances.
 * Positioned above the mode track in the bottom cockpit.
 */
internal class StylePresetCardRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : HorizontalScrollView(context, attrs, defStyleAttr) {

    private val cardContainer = android.widget.LinearLayout(context).apply {
        orientation = android.widget.LinearLayout.HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
    }

    private val hPadPx = StylePresetCardDimensions.RAIL_HORIZONTAL_PADDING_DP.dp
    private val vPadPx = StylePresetCardDimensions.RAIL_VERTICAL_PADDING_DP.dp
    private val spacingPx = StylePresetCardDimensions.ITEM_SPACING_DP.dp

    private var onCardSelected: ((StylePresetCardRenderModel) -> Unit)? = null
    private var cardModels: List<StylePresetCardRenderModel> = emptyList()

    private val dividerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(40, 255, 255, 255)
        strokeWidth = 1f
    }

    init {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        setPadding(hPadPx, vPadPx, hPadPx, vPadPx)
        addView(cardContainer, LayoutParams(
            LayoutParams.WRAP_CONTENT,
            LayoutParams.WRAP_CONTENT
        ))
    }

    fun setOnCardSelectedListener(listener: (StylePresetCardRenderModel) -> Unit) {
        onCardSelected = listener
    }

    fun renderCards(
        cards: List<StylePresetCardRenderModel>,
        onApplyStyle: (com.opencamera.core.settings.PersistedSettingsAction) -> Unit
    ) {
        cardModels = cards
        cardContainer.removeAllViews()

        val cardWidthPx = StylePresetCardDimensions.CARD_WIDTH_DP.dp
        val cardHeightPx = StylePresetCardDimensions.CARD_HEIGHT_DP.dp

        cards.forEachIndexed { index, card ->
            val tile = StylePresetCardTileView(context).apply {
                layoutParams = android.widget.LinearLayout.LayoutParams(
                    cardWidthPx,
                    cardHeightPx
                ).apply {
                    if (index > 0) marginStart = spacingPx
                }
                bind(card)
                setOnClickListener {
                    if (card.isEnabled && card.applyAction != null) {
                        onApplyStyle(card.applyAction)
                    }
                }
            }
            cardContainer.addView(tile)
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cardModels.size > 1) {
            val cardWidthPx = StylePresetCardDimensions.CARD_WIDTH_DP.dp
            val cardHeightPx = StylePresetCardDimensions.CARD_HEIGHT_DP.dp
            val centerY = (height / 2f)
            val halfH = (cardHeightPx * 0.25f)

            for (i in 0 until cardModels.size - 1) {
                val rightEdge = hPadPx + (i + 1) * cardWidthPx + i * spacingPx
                val x = (rightEdge + spacingPx / 2f).toFloat()
                canvas.drawRect(
                    x, centerY - halfH,
                    x + 1f, centerY + halfH,
                    dividerPaint
                )
            }
        }
    }

    private val Int.dp: Int
        get() = (this * context.resources.displayMetrics.density).toInt()

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val parentWidth = MeasureSpec.getSize(widthMeasureSpec)
        val desiredHeight = StylePresetCardDimensions.CARD_HEIGHT_DP.dp +
            vPadPx * 2 +
            4 * context.resources.displayMetrics.density.toInt()
        val heightMode = MeasureSpec.getMode(heightMeasureSpec)
        val heightSize = if (heightMode == MeasureSpec.UNSPECIFIED) {
            desiredHeight
        } else {
            min(desiredHeight, MeasureSpec.getSize(heightMeasureSpec))
        }
        setMeasuredDimension(parentWidth, heightSize)
    }
}
