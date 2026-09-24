package com.opencamera.app

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.view.isVisible
import com.opencamera.core.settings.PersistedSettingsAction
import kotlin.math.roundToInt

/**
 * Compact photographic film rail with two high-value controls:
 * hold to compare with the original and one persistent style-strength slider.
 */
internal class StylePresetCardRailView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val cardContainer = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }
    private val cardScroll = HorizontalScrollView(context).apply {
        isHorizontalScrollBarEnabled = false
        clipToPadding = false
        overScrollMode = OVER_SCROLL_NEVER
        addView(cardContainer, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }
    private val compareButton = Button(context).apply {
        isAllCaps = false
        text = context.getString(R.string.button_hold_original)
        textSize = 13f
        setTextColor(Color.argb(245, 248, 250, 252))
        minHeight = 0
        minWidth = 0
        setPadding(16.dp, 0, 16.dp, 0)
        background = roundedStrokeBackground()
        contentDescription = context.getString(R.string.button_hold_original)
    }
    private val strengthLabel = TextView(context).apply {
        text = context.getString(R.string.label_style_strength)
        textSize = 13f
        setTextColor(Color.argb(235, 248, 250, 252))
        gravity = Gravity.CENTER_VERTICAL
    }
    private val strengthValue = TextView(context).apply {
        textSize = 13f
        setTextColor(Color.argb(235, 248, 250, 252))
        gravity = Gravity.CENTER
        minWidth = 34.dp
    }
    private val strengthSlider = SeekBar(context).apply {
        max = 100
        minHeight = 0
        contentDescription = context.getString(R.string.label_style_strength)
    }

    private var strengthChanged: ((Float) -> Unit)? = null
    private var strengthPreviewChanged: ((Float?) -> Unit)? = null
    private var originalComparisonChanged: ((Boolean) -> Unit)? = null
    private var isRenderingStrength = false
    private var isComparingOriginal = false
    private var isUserAdjustingStrength = false
    private var pendingStrengthPreview: Runnable? = null
    private var renderedPreviewBitmap: Bitmap? = null
    private val strengthRow = buildStrengthRow()

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        clipChildren = false
        clipToPadding = false
        setPadding(0, 6.dp, 0, 4.dp)
        background = GradientDrawable().apply {
            setColor(Color.argb(242, 7, 9, 12))
        }

        addView(
            compareButton,
            LayoutParams(LayoutParams.WRAP_CONTENT, StylePresetCardDimensions.RAIL_HEADER_HEIGHT_DP.dp)
        )
        addView(
            cardScroll,
            LayoutParams(LayoutParams.MATCH_PARENT, StylePresetCardDimensions.CARD_HEIGHT_DP.dp).apply {
                topMargin = 4.dp
            }
        )
        addView(strengthRow)

        compareButton.setOnTouchListener { _, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (!isComparingOriginal && isEnabled) {
                        isComparingOriginal = true
                        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                        originalComparisonChanged?.invoke(true)
                        compareButton.isPressed = true
                    }
                    true
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    releaseOriginalComparison()
                    true
                }

                else -> true
            }
        }
        compareButton.setOnClickListener {
            if (!isComparingOriginal && compareButton.isEnabled) {
                isComparingOriginal = true
                originalComparisonChanged?.invoke(true)
                postDelayed({ releaseOriginalComparison() }, ACCESSIBILITY_ORIGINAL_PREVIEW_MILLIS)
            }
        }

        strengthSlider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                strengthValue.text = progress.toString()
                if (fromUser) scheduleStrengthPreview(progress / 100f)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                isUserAdjustingStrength = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                val strength = strengthSlider.progress / 100f
                flushStrengthPreview(strength)
                isUserAdjustingStrength = false
                if (!isRenderingStrength) {
                    strengthChanged?.invoke(strength)
                }
            }
        })
    }

    fun render(
        model: StylePresetRailRenderModel,
        previewBitmap: Bitmap?,
        onApplyStyle: (PersistedSettingsAction) -> Unit,
        onStrengthPreviewChanged: (Float?) -> Unit,
        onStrengthChanged: (Float) -> Unit,
        onOriginalComparisonChanged: (Boolean) -> Unit
    ) {
        strengthChanged = onStrengthChanged
        strengthPreviewChanged = onStrengthPreviewChanged
        originalComparisonChanged = onOriginalComparisonChanged
        val hasPreview = previewBitmap != null && !previewBitmap.isRecycled
        isEnabled = model.isEnabled
        compareButton.isEnabled = model.isEnabled && hasPreview
        strengthSlider.isEnabled = model.isEnabled && hasPreview
        cardScroll.isVisible = hasPreview
        strengthRow.isVisible = hasPreview
        compareButton.text = context.getString(
            if (hasPreview) R.string.button_hold_original else R.string.status_style_preview_preparing
        )
        compareButton.contentDescription = compareButton.text

        if (!hasPreview) {
            releaseTransientStylePreview()
            cardContainer.removeAllViews()
            return
        }

        if (!isComparingOriginal && !isUserAdjustingStrength) {
            isRenderingStrength = true
            strengthSlider.progress = (model.styleStrength * 100f).roundToInt().coerceIn(0, 100)
            strengthValue.text = strengthSlider.progress.toString()
            isRenderingStrength = false
            renderCards(model.cards, previewBitmap, onApplyStyle)
        }
    }

    fun renderCards(
        cards: List<StylePresetCardRenderModel>,
        previewBitmap: Bitmap?,
        onApplyStyle: (PersistedSettingsAction) -> Unit
    ) {
        cardContainer.removeAllViews()
        replaceRenderedPreviewBitmap(previewBitmap)
        cardScroll.setPadding(
            StylePresetCardDimensions.RAIL_HORIZONTAL_PADDING_DP.dp,
            0,
            StylePresetCardDimensions.RAIL_HORIZONTAL_PADDING_DP.dp,
            0
        )

        val cardWidth = StylePresetCardDimensions.CARD_WIDTH_DP.dp
        val cardHeight = StylePresetCardDimensions.CARD_HEIGHT_DP.dp
        val spacing = StylePresetCardDimensions.ITEM_SPACING_DP.dp
        var selectedIndex = -1

        cards.forEachIndexed { index, card ->
            if (card.isSelected) selectedIndex = index
            val tile = StylePresetCardTileView(context).apply {
                layoutParams = LayoutParams(cardWidth, cardHeight).apply {
                    if (index > 0) marginStart = spacing
                }
                bind(card, previewBitmap)
                setOnClickListener {
                    if (!card.isEnabled) return@setOnClickListener
                    if (card.isSelected) {
                        animatePressFeedback(this)
                    } else if (card.applyAction != null) {
                        setPendingSelected(true)
                        animateSelection(this)
                        onApplyStyle(card.applyAction)
                    }
                }
            }
            cardContainer.addView(tile)
        }

        if (selectedIndex >= 0) {
            post {
                val selectedCenter = StylePresetCardDimensions.RAIL_HORIZONTAL_PADDING_DP.dp +
                    selectedIndex * (cardWidth + spacing) + cardWidth / 2
                cardScroll.smoothScrollTo((selectedCenter - width / 2).coerceAtLeast(0), 0)
            }
        }
    }

    fun renderCards(
        cards: List<StylePresetCardRenderModel>,
        onApplyStyle: (PersistedSettingsAction) -> Unit
    ) = renderCards(cards, previewBitmap = null, onApplyStyle = onApplyStyle)

    fun releaseOriginalComparison() {
        if (!isComparingOriginal) return
        isComparingOriginal = false
        compareButton.isPressed = false
        originalComparisonChanged?.invoke(false)
    }

    fun releaseTransientStylePreview() {
        releaseOriginalComparison()
        pendingStrengthPreview?.let(::removeCallbacks)
        pendingStrengthPreview = null
        isUserAdjustingStrength = false
        strengthPreviewChanged?.invoke(null)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, visibility)
        if (visibility != VISIBLE) releaseTransientStylePreview()
    }

    override fun onDetachedFromWindow() {
        releaseTransientStylePreview()
        releaseRenderedPreviewBitmap()
        super.onDetachedFromWindow()
    }

    private fun buildStrengthRow(): View {
        return LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(20.dp, 0, 16.dp, 0)
            addView(strengthLabel, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            addView(
                strengthSlider,
                LayoutParams(0, LayoutParams.MATCH_PARENT, 1f).apply {
                    marginStart = 8.dp
                    marginEnd = 6.dp
                }
            )
            addView(strengthValue, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT))
            layoutParams = LayoutParams(
                LayoutParams.MATCH_PARENT,
                StylePresetCardDimensions.RAIL_STRENGTH_HEIGHT_DP.dp
            )
        }
    }

    private fun roundedStrokeBackground(): GradientDrawable = GradientDrawable().apply {
        cornerRadius = 20f * resources.displayMetrics.density
        setColor(Color.argb(70, 25, 28, 32))
        setStroke(resources.displayMetrics.density.roundToInt().coerceAtLeast(1), Color.argb(100, 255, 255, 255))
    }

    private fun animateSelection(tile: StylePresetCardTileView) {
        tile.scaleX = 0.96f
        tile.scaleY = 0.96f
        ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 180L
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                val scale = 0.96f + 0.04f * fraction
                tile.scaleX = scale
                tile.scaleY = scale
            }
            start()
        }
    }

    private fun animatePressFeedback(tile: StylePresetCardTileView) {
        tile.animate()
            .scaleX(0.98f)
            .scaleY(0.98f)
            .setDuration(80L)
            .withEndAction {
                tile.animate().scaleX(1f).scaleY(1f).setDuration(80L).start()
            }
            .start()
    }

    private fun scheduleStrengthPreview(strength: Float) {
        pendingStrengthPreview?.let(::removeCallbacks)
        pendingStrengthPreview = Runnable {
            strengthPreviewChanged?.invoke(strength)
            pendingStrengthPreview = null
        }.also { postDelayed(it, STYLE_PREVIEW_DEBOUNCE_MILLIS) }
    }

    private fun flushStrengthPreview(strength: Float) {
        pendingStrengthPreview?.let(::removeCallbacks)
        pendingStrengthPreview = null
        strengthPreviewChanged?.invoke(strength)
    }

    private fun replaceRenderedPreviewBitmap(bitmap: Bitmap?) {
        if (renderedPreviewBitmap === bitmap) return
        releaseRenderedPreviewBitmap()
        renderedPreviewBitmap = bitmap
    }

    private fun releaseRenderedPreviewBitmap() {
        renderedPreviewBitmap?.takeIf { !it.isRecycled }?.recycle()
        renderedPreviewBitmap = null
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).roundToInt()

    private companion object {
        const val STYLE_PREVIEW_DEBOUNCE_MILLIS = 48L
        const val ACCESSIBILITY_ORIGINAL_PREVIEW_MILLIS = 1_000L
    }
}
