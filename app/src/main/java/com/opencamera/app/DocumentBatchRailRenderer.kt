package com.opencamera.app

import android.content.Context
import android.net.Uri
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.core.view.isVisible

internal class DocumentBatchRailRenderer(
    private val views: DocumentBatchRailViews,
    private val onRemoveItemClick: (String) -> Unit
) {
    fun render(model: DocumentBatchRailRenderModel) {
        views.rail.isVisible = model.visible
        if (!model.visible) return

        views.header.text = model.countText
        views.header.isEnabled = model.organizeEnabled

        renderItems(model)
    }

    private fun renderItems(model: DocumentBatchRailRenderModel) {
        views.list.removeAllViews()
        val context = views.list.context
        val itemSizePx = 44.dp(context)
        val marginPx = 2.dp(context)

        model.items.forEach { item ->
            val itemView = createItemView(context, item, itemSizePx, marginPx)
            views.list.addView(itemView)
        }
    }

    private fun createItemView(
        context: Context,
        item: DocumentBatchRailItemRenderModel,
        sizePx: Int,
        marginPx: Int
    ): View {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = android.view.Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = marginPx
            }
        }

        val imageView = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(sizePx, sizePx)
            scaleType = ImageView.ScaleType.CENTER_CROP
            setBackgroundColor(0x20FFFFFF.toInt())
            contentDescription = context.getString(R.string.document_batch_rail_page_description, item.pageNumber)
            if (item.renderUri != null) {
                setImageURI(null)
                setImageURI(Uri.parse(item.renderUri))
            }
            setOnClickListener {
                onRemoveItemClick(item.itemId)
            }
        }
        container.addView(imageView)

        if (item.statusLabel != null) {
            val labelView = android.widget.TextView(context).apply {
                text = item.statusLabel
                textSize = 9f
                setTextColor(0xCCFFFFFF.toInt())
                gravity = android.view.Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }
            container.addView(labelView)
        }

        return container
    }

    private fun Int.dp(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }
}
