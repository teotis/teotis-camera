package com.opencamera.app

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

internal enum class DocumentBatchRailInteractionPhase {
    BROWSING,
    PAGE_SELECTED
}

internal class DocumentBatchRailRenderer(
    private val views: DocumentBatchRailViews,
    private val onRemoveItemClick: (String) -> Unit,
    private val onMoveUpItemClick: (String) -> Unit,
    private val onMoveDownItemClick: (String) -> Unit,
    private val onExportRequested: () -> Unit,
    private val onClearBatchClick: () -> Unit
) {
    private var phase: DocumentBatchRailInteractionPhase = DocumentBatchRailInteractionPhase.BROWSING
    private var selectedItemId: String? = null
    private var lastModel: DocumentBatchRailRenderModel? = null
    private var lastItemCount: Int = 0
    private var clearConfirmationArmed: Boolean = false

    fun render(model: DocumentBatchRailRenderModel) {
        lastModel = model
        views.overlay.isVisible = model.visible
        views.rail.isVisible = model.visible
        if (!model.visible) {
            // Mode route change or panel open dismisses every transient selection.
            collapseToBrowsing()
            clearConfirmationArmed = false
            lastItemCount = 0
            views.actionContainer.isVisible = false
            return
        }

        // Capture refresh: the ordered thumbnail rail stays visible while transient
        // page actions are dismissed across shot boundaries.
        if (lastItemCount > 0 && model.items.size > lastItemCount) {
            if (phase == DocumentBatchRailInteractionPhase.PAGE_SELECTED) {
                collapseToBrowsing()
            }
            clearConfirmationArmed = false
        }
        lastItemCount = model.items.size

        // If the selected page vanished, return to the always-visible browsing rail.
        if (phase == DocumentBatchRailInteractionPhase.PAGE_SELECTED) {
            if (selectedItemId == null || model.items.none { it.itemId == selectedItemId }) {
                collapseToBrowsing()
            }
        }

        if (model.isSlimShooting) {
            renderSlim(model)
        }
    }

    fun dismissTransientActions(): Boolean {
        if (phase == DocumentBatchRailInteractionPhase.BROWSING && !clearConfirmationArmed) {
            return false
        }
        collapseToBrowsing()
        clearConfirmationArmed = false
        lastModel?.let(::render)
        return true
    }

    private fun renderSlim(model: DocumentBatchRailRenderModel) {
        val context = views.chip.context
        applyRailFrame(context)
        views.rail.clipChildren = false
        views.overlay.clipChildren = false
        views.rail.isClickable = true
        views.rail.setOnClickListener {
            dismissTransientActions()
        }

        views.chip.isVisible = true
        views.chip.text = model.countText
        views.chip.isEnabled = model.overviewLabel.isNotEmpty()
        views.chip.setOnClickListener {
            // The page count is informational. It only clears transient actions;
            // the thumbnail list itself remains reachable without an expand step.
            dismissTransientActions()
        }

        renderItems(model, showActions = phase == DocumentBatchRailInteractionPhase.PAGE_SELECTED)

        renderExternalActions(context, model)
    }

    private fun applyRailFrame(context: Context) {
        val targetWidth = when (phase) {
            DocumentBatchRailInteractionPhase.BROWSING ->
                context.dimensionPixelSize(R.dimen.document_batch_rail_browsing_width)
            DocumentBatchRailInteractionPhase.PAGE_SELECTED ->
                context.dimensionPixelSize(R.dimen.document_batch_rail_selected_width)
        }
        val params = views.rail.layoutParams ?: ViewGroup.LayoutParams(
            targetWidth,
            ViewGroup.LayoutParams.MATCH_PARENT
        )
        if (params.width != targetWidth) {
            params.width = targetWidth
            views.rail.layoutParams = params
        }
        views.rail.alpha = when (phase) {
            DocumentBatchRailInteractionPhase.BROWSING -> 0.74f
            DocumentBatchRailInteractionPhase.PAGE_SELECTED -> 0.96f
        }
    }

    private fun renderExternalActions(context: Context, model: DocumentBatchRailRenderModel) {
        val selectedItem = if (phase == DocumentBatchRailInteractionPhase.PAGE_SELECTED) {
            model.items.firstOrNull { it.itemId == selectedItemId }
        } else {
            null
        }

        if (selectedItem != null) {
            clearConfirmationArmed = false
            views.clearButton.isVisible = false
            views.clearButton.setOnClickListener(null)
            configureActionContainer(context)
            configureActionButton(
                button = views.moveUpButton,
                context = context,
                label = model.moveUpLabel,
                tag = "move_up_${selectedItem.itemId}",
                enabled = selectedItem.canMoveUp,
                contentDescription = model.moveUpLabel
            ) {
                onMoveUpItemClick(selectedItem.itemId)
            }
            configureActionButton(
                button = views.moveDownButton,
                context = context,
                label = model.moveDownLabel,
                tag = "move_down_${selectedItem.itemId}",
                enabled = selectedItem.canMoveDown,
                contentDescription = model.moveDownLabel
            ) {
                onMoveDownItemClick(selectedItem.itemId)
            }
            configureActionButton(
                button = views.removeButton,
                context = context,
                label = context.getString(R.string.document_batch_remove),
                tag = "remove_${selectedItem.itemId}",
                enabled = true,
                contentDescription = selectedItem.removeContentDescription
            ) {
                onRemoveItemClick(selectedItem.itemId)
            }
            configureActionButton(
                button = views.overviewButton,
                context = context,
                label = model.overviewLabel,
                tag = "export_batch",
                enabled = model.overviewLabel.isNotEmpty(),
                contentDescription = context.getString(R.string.document_batch_rail_overview_description)
            ) {
                onExportRequested()
            }
            return
        }

        hideActionButton(views.moveUpButton)
        hideActionButton(views.moveDownButton)
        hideActionButton(views.removeButton)
        hideActionButton(views.overviewButton)
        views.actionContainer.isVisible = false
        renderBrowsingClearButton(context, model)
    }

    private fun configureActionContainer(context: Context) {
        views.actionContainer.isVisible = true
        val params = (views.actionContainer.layoutParams as? FrameLayout.LayoutParams)
            ?: FrameLayout.LayoutParams(
                context.dimensionPixelSize(R.dimen.document_batch_action_width),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        params.width = context.dimensionPixelSize(R.dimen.document_batch_action_width)
        params.height = ViewGroup.LayoutParams.WRAP_CONTENT
        params.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        params.marginStart = context.dimensionPixelSize(
            R.dimen.document_batch_action_selected_margin_start
        )
        views.actionContainer.layoutParams = params
    }

    private fun renderBrowsingClearButton(context: Context, model: DocumentBatchRailRenderModel) {
        val showClear = phase == DocumentBatchRailInteractionPhase.BROWSING && model.items.isNotEmpty()
        views.clearButton.isVisible = showClear
        if (!showClear) {
            views.clearButton.setOnClickListener(null)
            return
        }

        views.clearButton.elevation = 0f
        views.clearButton.setBackgroundResource(R.drawable.bg_document_batch_clear_chip)
        applyButtonFrame(views.clearButton, context)
        views.clearButton.tag = "clear_batch"
        views.clearButton.minHeight = 0
        views.clearButton.setPadding(4.dp(context), 0, 4.dp(context), 0)
        views.clearButton.text = if (clearConfirmationArmed) {
            context.getString(R.string.document_batch_clear_confirm)
        } else {
            context.getString(R.string.document_batch_clear)
        }
        views.clearButton.contentDescription = if (clearConfirmationArmed) {
            context.getString(R.string.document_batch_clear_confirm_content_description)
        } else {
            context.getString(R.string.document_batch_clear_content_description)
        }
        views.clearButton.setOnClickListener {
            if (clearConfirmationArmed) {
                clearConfirmationArmed = false
                onClearBatchClick()
                collapseToBrowsing()
            } else {
                clearConfirmationArmed = true
            }
            lastModel?.let(::render)
        }
    }

    private fun renderItems(model: DocumentBatchRailRenderModel, showActions: Boolean) {
        views.itemList.removeAllViews()
        views.itemScroll.isVisible = model.items.isNotEmpty()
        views.itemScroll.isClickable = true
        views.itemScroll.setOnClickListener {
            dismissTransientActions()
        }
        views.itemList.isClickable = true
        views.itemList.setOnClickListener {
            dismissTransientActions()
        }
        views.itemScroll.isNestedScrollingEnabled = true
        views.itemScroll.isVerticalScrollBarEnabled = model.items.size > 2
        views.itemScroll.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val context = views.itemList.context
        model.items.forEach { item ->
            val isSelected = showActions && item.itemId == selectedItemId
            views.itemList.addView(createPageCell(context, item, isSelected))
        }
    }

    private fun createPageCell(
        context: Context,
        item: DocumentBatchRailItemRenderModel,
        isSelected: Boolean
    ): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(5.dp(context), 5.dp(context), 5.dp(context), 5.dp(context))
            alpha = if (isSelected) 1f else 0.76f
            this.isSelected = isSelected
            setBackgroundResource(
                if (isSelected) {
                    R.drawable.bg_document_batch_page_cell_selected
                } else {
                    R.drawable.bg_document_batch_page_cell
                }
            )
            contentDescription = context.getString(R.string.document_batch_rail_page_description, item.pageNumber)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp(context)
            }
            setOnClickListener {
                handlePageCellTap(item.itemId)
            }
        }

        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val pageLabel = TextView(context).apply {
            text = "${item.pageNumber}"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(18.dp(context), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        topRow.addView(pageLabel)

        val thumbnail = ImageView(context).apply {
            val thumbnailWidth = if (isSelected) {
                context.dimensionPixelSize(R.dimen.document_batch_thumbnail_selected_width)
            } else {
                context.dimensionPixelSize(R.dimen.document_batch_thumbnail_browsing_width)
            }
            val thumbnailHeight = if (isSelected) {
                context.dimensionPixelSize(R.dimen.document_batch_thumbnail_selected_height)
            } else {
                context.dimensionPixelSize(R.dimen.document_batch_thumbnail_browsing_height)
            }
            layoutParams = LinearLayout.LayoutParams(thumbnailWidth, thumbnailHeight).apply {
                marginStart = 4.dp(context)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x24FFFFFF)
            if (item.renderUri != null) {
                setImageURI(null)
                setImageURI(Uri.parse(item.renderUri))
            }
        }
        topRow.addView(thumbnail)
        cell.addView(topRow)

        return cell
    }

    private fun handlePageCellTap(itemId: String) {
        when (phase) {
            DocumentBatchRailInteractionPhase.BROWSING -> {
                selectedItemId = itemId
                phase = DocumentBatchRailInteractionPhase.PAGE_SELECTED
                clearConfirmationArmed = false
            }
            DocumentBatchRailInteractionPhase.PAGE_SELECTED -> {
                if (selectedItemId == itemId) {
                    // Tapping the selected page removes action chrome while keeping
                    // the ordered thumbnail rail visible.
                    collapseToBrowsing()
                    clearConfirmationArmed = false
                } else {
                    // Tapping a different page moves the action affordance.
                    selectedItemId = itemId
                    clearConfirmationArmed = false
                }
            }
        }
        lastModel?.let(::render)
    }

    private fun collapseToBrowsing() {
        phase = DocumentBatchRailInteractionPhase.BROWSING
        selectedItemId = null
    }

    private fun configureActionButton(
        button: Button,
        context: Context,
        label: String,
        tag: String,
        enabled: Boolean,
        contentDescription: String,
        onClick: () -> Unit
    ) {
        button.isVisible = true
        button.text = label
        button.tag = tag
        button.contentDescription = contentDescription
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else 0.35f
        button.setBackgroundResource(R.drawable.bg_document_batch_action_chip)
        button.minHeight = 0
        button.setPadding(4.dp(context), 0, 4.dp(context), 0)
        button.elevation = 10.dp(context).toFloat()
        applyButtonFrame(button, context)
        button.setOnClickListener {
            if (button.isEnabled) onClick()
        }
    }

    private fun hideActionButton(button: Button) {
        button.isVisible = false
        button.tag = null
        button.setOnClickListener(null)
    }

    private fun applyButtonFrame(button: Button, context: Context) {
        val params = button.layoutParams ?: LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            context.dimensionPixelSize(R.dimen.document_batch_action_height)
        )
        params.width = LinearLayout.LayoutParams.MATCH_PARENT
        params.height = context.dimensionPixelSize(R.dimen.document_batch_action_height)
        if (params is LinearLayout.LayoutParams) {
            params.topMargin = 6.dp(context)
        }
        button.layoutParams = params
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

    private fun Context.dimensionPixelSize(resId: Int): Int =
        resources.getDimensionPixelSize(resId)
}
