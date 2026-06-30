package com.opencamera.app

import android.content.Context
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.isVisible

internal class DocumentBatchOrganizerRenderer(
    private val views: DocumentBatchOrganizerViews,
    private val onRemoveItemClick: (String) -> Unit,
    private val onMoveUpItemClick: (String) -> Unit,
    private val onMoveDownItemClick: (String) -> Unit,
    private val onCropEditItemClick: (String) -> Unit = {},
    private val onContinueShooting: () -> Unit = {},
    private val onExport: () -> Unit = {}
) {
    private var lastModel: DocumentBatchOrganizerRenderModel? = null
    private var selectedItemId: String? = null

    fun render(model: DocumentBatchOrganizerRenderModel) {
        lastModel = model
        selectedItemId = selectedItemId
            ?.takeIf { selected -> model.items.any { it.itemId == selected } }
            ?: model.items.lastOrNull()?.itemId
        views.panel.isVisible = model.visible
        if (!model.visible) return

        views.title.text = model.title
        views.count.text = model.countText

        renderItems(model)
        renderStatus(model)
        renderFooter(model)
    }

    private fun renderItems(model: DocumentBatchOrganizerRenderModel) {
        views.itemList.removeAllViews()

        if (model.items.isEmpty()) {
            views.emptyHint.isVisible = true
            views.emptyHint.text = model.emptyHint
            return
        }

        views.emptyHint.isVisible = false
        val context = views.itemList.context

        model.items.forEach { item ->
            val itemView = createItemView(context, item, item.itemId == selectedItemId)
            views.itemList.addView(itemView)
        }
    }

    private fun renderStatus(model: DocumentBatchOrganizerRenderModel) {
        val message = model.statusMessage
        views.status.text = message
        views.status.isVisible = !message.isNullOrBlank()
    }

    private fun renderFooter(model: DocumentBatchOrganizerRenderModel) {
        views.footer.isVisible = model.isBatchOverviewMode
        views.continueShooting.isVisible = model.showContinueShooting
        views.exportButton.isVisible = model.showExport
    }

    private fun createItemView(
        context: Context,
        item: DocumentBatchOrganizerItemRenderModel,
        isSelected: Boolean
    ): View {
        val rowPadding = 4.dp(context)
        val itemMargin = 3.dp(context)
        val thumbnailSize = 80.dp(context)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            setPadding(rowPadding, rowPadding, rowPadding, rowPadding)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = itemMargin
            }
            alpha = if (isSelected) 1f else 0.78f
            setBackgroundResource(R.drawable.bg_settings_card)
            setOnClickListener {
                selectedItemId = item.itemId
                lastModel?.let(::render)
            }
        }

        // Top row: page number, thumbnail, status label, action buttons
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Page number
        val pageLabel = TextView(context).apply {
            text = "${item.pageNumber}"
            textSize = if (isSelected) 13f else 11f
            setTextColor(if (isSelected) 0xFFFFFFFF.toInt() else 0xCCFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                if (isSelected) 24.dp(context) else 20.dp(context),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        topRow.addView(pageLabel)

        // Thumbnail
        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(thumbnailSize, thumbnailSize)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x20FFFFFF.toInt())
            if (item.renderUri != null) {
                setImageURI(null)
                setImageURI(Uri.parse(item.renderUri))
            }
        }
        topRow.addView(imageView)

        // Right side: status label + action buttons (vertical)
        val rightSide = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.START
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 8.dp(context)
            }
        }

        // Crop status label
        if (item.cropStatusLabel != null) {
            val statusLabel = TextView(context).apply {
                text = item.cropStatusLabel
                textSize = 10f
                setTextColor(0x99FFFFFF.toInt())
            }
            rightSide.addView(statusLabel)
        }

        // Action buttons row.
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = 4.dp(context)
            }
        }

        // Move controls appear only on the selected row, with larger touch targets.
        if (isSelected) {
            val moveUpButton = createMoveAction(context, "▲", item.itemId, "up", item.canMoveUp) {
                onMoveUpItemClick(item.itemId)
            }
            actions.addView(moveUpButton)
            val moveDownButton = createMoveAction(context, "▼", item.itemId, "down", item.canMoveDown) {
                onMoveDownItemClick(item.itemId)
            }
            actions.addView(moveDownButton)
        }

        val removeButton = createTextAction(context, "✕", if (isSelected) 36.dp(context) else 24.dp(context)).apply {
            setOnClickListener { onRemoveItemClick(item.itemId) }
        }
        actions.addView(removeButton)

        rightSide.addView(actions)
        topRow.addView(rightSide)
        row.addView(topRow)

        // Crop edit entry (bottom of each item row)
        if (item.cropEditLabel != null) {
            val cropEditButton = Button(context).apply {
                text = item.cropEditLabel
                textSize = 10f
                setTextColor(0x99FFFFFF.toInt())
                minimumWidth = 0
                minimumHeight = 0
                setPadding(8.dp(context), 2.dp(context), 8.dp(context), 2.dp(context))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    28.dp(context)
                ).apply {
                    topMargin = 4.dp(context)
                }
                background = context.getDrawable(R.drawable.bg_panel_row)
                setOnClickListener { onCropEditItemClick(item.itemId) }
            }
            row.addView(cropEditButton)
        }

        return row
    }

    private fun createMoveAction(
        context: Context,
        label: String,
        itemId: String,
        direction: String,
        enabled: Boolean,
        onClick: () -> Unit
    ): Button {
        val height = 40.dp(context)
        val width = 44.dp(context)
        return Button(context).apply {
            text = label
            textSize = 16f
            setTextColor(0xFFFFFFFF.toInt())
            minimumWidth = 0
            minimumHeight = 0
            setPadding(6.dp(context), 0, 6.dp(context), 0)
            layoutParams = LinearLayout.LayoutParams(width, height).apply {
                marginStart = 2.dp(context)
            }
            background = context.getDrawable(R.drawable.bg_panel_row)
            tag = "move_$direction" + "_$itemId"
            isEnabled = enabled
            alpha = if (enabled) 1f else 0.35f
            setOnClickListener {
                if (isEnabled) onClick()
            }
        }
    }

    private fun createTextAction(context: Context, label: String, heightPx: Int): Button {
        return Button(context).apply {
            text = label
            textSize = 12f
            setTextColor(0xFFFFFFFF.toInt())
            minimumWidth = 0
            minimumHeight = 0
            setPadding(4.dp(context), 0, 4.dp(context), 0)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                heightPx
            ).apply {
                marginStart = 2.dp(context)
            }
            background = context.getDrawable(R.drawable.bg_panel_row)
        }
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
