package com.opencamera.app

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

internal class DocumentBatchRailRenderer(
    private val views: DocumentBatchRailViews,
    private val onRemoveItemClick: (String) -> Unit,
    private val onMoveUpItemClick: (String) -> Unit,
    private val onMoveDownItemClick: (String) -> Unit,
    private val onExportRequested: () -> Unit
) {
    private var selectedItemId: String? = null
    private var lastModel: DocumentBatchRailRenderModel? = null

    fun render(model: DocumentBatchRailRenderModel) {
        lastModel = model
        selectedItemId = selectedItemId
            ?.takeIf { selected -> model.items.any { it.itemId == selected } }
            ?: model.latestItemId
            ?: model.items.lastOrNull()?.itemId
        views.rail.isVisible = model.visible
        if (!model.visible) return

        if (model.isSlimShooting) {
            renderSlim(model)
        }
    }

    private fun renderSlim(model: DocumentBatchRailRenderModel) {
        val context = views.chip.context

        views.chip.isVisible = true
        views.chip.text = model.countText
        views.chip.isEnabled = model.overviewLabel.isNotEmpty()
        views.chip.setOnClickListener(null)

        views.thumbnail.isVisible = false
        views.thumbnail.setOnClickListener(null)

        renderItems(model)
        renderMoveButtons()

        views.overviewButton.isVisible = model.overviewLabel.isNotEmpty()
        views.overviewButton.text = model.overviewLabel
        views.overviewButton.contentDescription = context.getString(R.string.document_batch_rail_overview_description)
        views.overviewButton.setOnClickListener { onExportRequested() }
    }

    private fun renderItems(model: DocumentBatchRailRenderModel) {
        views.itemList.removeAllViews()
        views.itemScroll.isVisible = model.items.isNotEmpty()
        views.itemScroll.isNestedScrollingEnabled = true
        views.itemScroll.isVerticalScrollBarEnabled = model.items.size > 2
        views.itemScroll.overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS

        val context = views.itemList.context
        model.items.forEach { item ->
            views.itemList.addView(createPageCell(context, item, item.itemId == selectedItemId))
        }
    }

    private fun createPageCell(
        context: Context,
        item: DocumentBatchRailItemRenderModel,
        isSelected: Boolean
    ): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            alpha = if (isSelected) 1f else 0.72f
            this.isSelected = isSelected
            setBackgroundResource(R.drawable.bg_settings_card)
            contentDescription = context.getString(R.string.document_batch_rail_page_description, item.pageNumber)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp(context)
            }
            setOnClickListener {
                selectedItemId = item.itemId
                lastModel?.let(::render)
            }
        }

        val pageLabel = TextView(context).apply {
            text = if (isSelected) "* ${item.pageNumber}" else "${item.pageNumber}"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(20.dp(context), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        cell.addView(pageLabel)

        val thumbnail = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(42.dp(context), 48.dp(context)).apply {
                marginStart = 4.dp(context)
            }
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x24FFFFFF)
            if (item.renderUri != null) {
                setImageURI(null)
                setImageURI(Uri.parse(item.renderUri))
            }
        }
        cell.addView(thumbnail)

        if (isSelected) {
            val actions = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 6.dp(context)
                }
            }
            actions.addView(createMoveAction(context, "▲", item.itemId, "up", item.canMoveUp) {
                onMoveUpItemClick(item.itemId)
            })
            actions.addView(createMoveAction(context, "▼", item.itemId, "down", item.canMoveDown) {
                onMoveDownItemClick(item.itemId)
            })
            cell.addView(actions)
        }

        return cell
    }

    private fun createMoveAction(
        context: Context,
        label: String,
        itemId: String,
        direction: String,
        enabled: Boolean,
        onClick: () -> Unit
    ): TextView {
        return TextView(context).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
            setBackgroundResource(R.drawable.bg_panel_row)
            setPadding(6.dp(context), 4.dp(context), 6.dp(context), 4.dp(context))
            tag = "move_$direction" + "_$itemId"
            layoutParams = LinearLayout.LayoutParams(36.dp(context), 34.dp(context)).apply {
                topMargin = 2.dp(context)
            }
            setOnClickListener {
                if (isEnabled) onClick()
            }
        }
    }

    private fun renderMoveButtons() {
        views.moveUpButton.isVisible = false
        views.moveUpButton.setOnClickListener(null)
        views.moveDownButton.isVisible = false
        views.moveDownButton.setOnClickListener(null)
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
