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
    private val onOverviewRequested: () -> Unit
) {
    fun render(model: DocumentBatchRailRenderModel) {
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

        views.thumbnail.isVisible = false
        views.thumbnail.setOnClickListener { onOverviewRequested() }

        renderItems(model)
        renderMoveButtons(model)

        views.overviewButton.isVisible = model.overviewLabel.isNotEmpty()
        views.overviewButton.text = model.overviewLabel
        views.overviewButton.contentDescription = context.getString(R.string.document_batch_rail_overview_description)
        views.overviewButton.setOnClickListener { onOverviewRequested() }

        views.chip.setOnClickListener { onOverviewRequested() }
    }

    private fun renderItems(model: DocumentBatchRailRenderModel) {
        views.itemList.removeAllViews()
        views.itemList.isVisible = model.items.isNotEmpty()

        val context = views.itemList.context
        model.items.forEach { item ->
            views.itemList.addView(createPageCell(context, item))
        }
    }

    private fun createPageCell(context: Context, item: DocumentBatchRailItemRenderModel): View {
        val cell = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(4.dp(context), 4.dp(context), 4.dp(context), 4.dp(context))
            alpha = if (item.isLatest) 1f else 0.72f
            setBackgroundResource(R.drawable.bg_settings_card)
            contentDescription = context.getString(R.string.document_batch_rail_page_description, item.pageNumber)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp(context)
            }
            setOnClickListener { onOverviewRequested() }
        }

        val pageLabel = TextView(context).apply {
            text = if (item.isLatest) "* ${item.pageNumber}" else "${item.pageNumber}"
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(28.dp(context), LinearLayout.LayoutParams.WRAP_CONTENT)
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

        return cell
    }

    private fun renderMoveButtons(model: DocumentBatchRailRenderModel) {
        val focusedItem = model.items.firstOrNull { it.isLatest } ?: model.items.lastOrNull()

        views.moveUpButton.isVisible = model.items.size > 1
        views.moveUpButton.text = model.moveUpLabel
        views.moveUpButton.isEnabled = focusedItem?.canMoveUp == true
        views.moveUpButton.alpha = if (views.moveUpButton.isEnabled) 1f else 0.32f
        views.moveUpButton.setOnClickListener {
            focusedItem?.let {
                onMoveUpItemClick(it.itemId)
            }
        }

        views.moveDownButton.isVisible = model.items.size > 1
        views.moveDownButton.text = model.moveDownLabel
        views.moveDownButton.isEnabled = focusedItem?.canMoveDown == true
        views.moveDownButton.alpha = if (views.moveDownButton.isEnabled) 1f else 0.32f
        views.moveDownButton.setOnClickListener {
            focusedItem?.let {
                onMoveDownItemClick(it.itemId)
            }
        }
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
