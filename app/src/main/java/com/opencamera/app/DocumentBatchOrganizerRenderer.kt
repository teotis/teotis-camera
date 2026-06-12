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
import com.opencamera.app.i18n.AppTextResolver

internal class DocumentBatchOrganizerRenderer(
    private val views: DocumentBatchOrganizerViews,
    private val onRemoveItemClick: (String) -> Unit,
    private val onMoveUpItemClick: (String) -> Unit,
    private val onMoveDownItemClick: (String) -> Unit
) {
    private var lastModel: DocumentBatchOrganizerRenderModel? = null

    fun render(model: DocumentBatchOrganizerRenderModel) {
        lastModel = model
        views.panel.isVisible = model.visible
        if (!model.visible) return

        val text = AppTextResolver(views.title.context)
        views.title.text = model.title
        views.count.text = model.countText

        renderItems(model, text)
    }

    private fun renderItems(model: DocumentBatchOrganizerRenderModel, appText: AppTextResolver) {
        views.itemList.removeAllViews()
        val context = views.itemList.context

        model.items.forEach { item ->
            val itemView = createItemView(context, item, appText)
            views.itemList.addView(itemView)
        }
    }

    private fun createItemView(
        context: Context,
        item: DocumentBatchOrganizerItemRenderModel,
        appText: AppTextResolver
    ): View {
        val rowPadding = 4.dp(context)
        val itemMargin = 3.dp(context)
        val thumbnailSize = 30.dp(context)
        val buttonHeight = 24.dp(context)

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(rowPadding, rowPadding, rowPadding, rowPadding)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = itemMargin
            }
            setBackgroundResource(R.drawable.bg_settings_card)
        }

        // Page number
        val pageLabel = TextView(context).apply {
            text = "${item.pageNumber}"
            textSize = 11f
            setTextColor(0xCCFFFFFF.toInt())
            layoutParams = LinearLayout.LayoutParams(
                20.dp(context),
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row.addView(pageLabel)

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
        row.addView(imageView)

        // Crop status label or spacer
        if (item.cropStatusLabel != null) {
            val statusLabel = TextView(context).apply {
                text = item.cropStatusLabel
                textSize = 9f
                setTextColor(0x99FFFFFF.toInt())
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    1f
                ).apply {
                    marginStart = 4.dp(context)
                }
            }
            row.addView(statusLabel)
        } else {
            val spacer = View(context).apply {
                layoutParams = LinearLayout.LayoutParams(0, 0, 1f)
            }
            row.addView(spacer)
        }

        // Action buttons container
        val actions = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Move up button (compact arrow)
        val moveUpButton = createTextAction(context, "▲", buttonHeight).apply {
            isEnabled = item.canMoveUp
            alpha = if (item.canMoveUp) 1f else 0.3f
            setOnClickListener { onMoveUpItemClick(item.itemId) }
        }
        actions.addView(moveUpButton)

        // Move down button (compact arrow)
        val moveDownButton = createTextAction(context, "▼", buttonHeight).apply {
            isEnabled = item.canMoveDown
            alpha = if (item.canMoveDown) 1f else 0.3f
            setOnClickListener { onMoveDownItemClick(item.itemId) }
        }
        actions.addView(moveDownButton)

        // Remove button
        val removeButton = createTextAction(context, "✕", buttonHeight).apply {
            setOnClickListener { onRemoveItemClick(item.itemId) }
        }
        actions.addView(removeButton)

        row.addView(actions)

        return row
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
