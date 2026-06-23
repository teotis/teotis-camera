package com.opencamera.app

import android.content.Context
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView

internal class DevConsoleRenderer(
    private val context: Context,
    private val views: DevConsoleViews
) {
    private val adapter = DevLogEventAdapter()

    init {
        if (views.eventsRecycler.layoutManager == null) {
            views.eventsRecycler.layoutManager = LinearLayoutManager(context)
        }
        views.eventsRecycler.adapter = adapter

        views.scrollTop.setOnClickListener {
            views.scrollTop.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            views.eventsRecycler.scrollToPosition(0)
        }
        views.scrollBottom.setOnClickListener {
            views.scrollBottom.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY)
            views.eventsRecycler.scrollToPosition(adapter.itemCount - 1)
        }
    }

    fun renderVisibility(activePanelRoute: CockpitPanelRoute) {
        val isDevVisible = activePanelRoute is CockpitPanelRoute.DevConsole
        views.panel.isVisible = isDevVisible
        views.entry.alpha = if (isDevVisible) 1f else 0.86f
    }

    fun render(model: DevLogRenderModel?) {
        if (model == null) return
        views.title.text = model.title
        views.summary.text = model.summaryText
        views.summary.isVisible = model.summaryText.isNotBlank()
        adapter.submitList(model.visibleEvents)
        views.tabKey.isEnabled = true
        views.tabCore.isEnabled = true
        views.tabError.isEnabled = true
        views.tabAll.isEnabled = true
        val activeAlpha = 1f
        val inactiveAlpha = 0.84f
        views.tabKey.alpha = if (model.selectedTab == DevLogTab.KEY) activeAlpha else inactiveAlpha
        views.tabCore.alpha = if (model.selectedTab == DevLogTab.CORE) activeAlpha else inactiveAlpha
        views.tabError.alpha = if (model.selectedTab == DevLogTab.ERROR) activeAlpha else inactiveAlpha
        views.tabAll.alpha = if (model.selectedTab == DevLogTab.ALL) activeAlpha else inactiveAlpha

        val hasStorage = model.storageUsedDisplay.isNotBlank()
        views.storageInfo.isVisible = hasStorage
        if (hasStorage) {
            views.storageInfo.text = context.getString(
                R.string.dev_storage_format, model.storageUsedDisplay, model.storageCapacityDisplay
            )
        }
    }
}

private object DevLogDiffCallback : DiffUtil.ItemCallback<DevLogEventItem>() {
    override fun areItemsTheSame(oldItem: DevLogEventItem, newItem: DevLogEventItem): Boolean {
        if (oldItem.type != newItem.type) return false
        return when (oldItem) {
            is TraceEventItem -> newItem is TraceEventItem && oldItem.sequence == newItem.sequence
            is SectionHeaderItem -> newItem is SectionHeaderItem && oldItem.displayText == newItem.displayText
            is LinkEventItem -> newItem is LinkEventItem && oldItem.displayText == newItem.displayText
        }
    }

    override fun areContentsTheSame(oldItem: DevLogEventItem, newItem: DevLogEventItem): Boolean {
        return oldItem == newItem
    }
}

private class DevLogEventAdapter : ListAdapter<DevLogEventItem, RecyclerView.ViewHolder>(DevLogDiffCallback) {

    override fun getItemViewType(position: Int): Int = getItem(position).type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dev_log_event, parent, false) as TextView
        return when (viewType) {
            SectionHeaderItem.TYPE -> SectionHeaderViewHolder(view)
            LinkEventItem.TYPE -> LinkEventViewHolder(view)
            else -> TraceEventViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = getItem(position)
        when (holder) {
            is SectionHeaderViewHolder -> holder.bind(item.displayText)
            is LinkEventViewHolder -> holder.bind(item.displayText)
            is TraceEventViewHolder -> holder.bind(item.displayText)
        }
    }

    private class TraceEventViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(text: String) {
            tv.text = text
            tv.setTypeface(Typeface.MONOSPACE, Typeface.NORMAL)
            tv.setTextColor(tv.context.getColor(R.color.oc_text_secondary))
        }
    }

    private class SectionHeaderViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(text: String) {
            tv.text = text
            tv.setTypeface(Typeface.MONOSPACE, Typeface.BOLD)
            tv.setTextColor(tv.context.getColor(R.color.oc_accent))
        }
    }

    private class LinkEventViewHolder(private val tv: TextView) : RecyclerView.ViewHolder(tv) {
        fun bind(text: String) {
            tv.text = text
            tv.setTypeface(Typeface.MONOSPACE, Typeface.ITALIC)
            tv.setTextColor(tv.context.getColor(R.color.oc_text_secondary))
        }
    }
}

internal fun devConsoleBottomScrollY(viewHeight: Int, contentHeight: Int): Int {
    return (contentHeight - viewHeight).coerceAtLeast(0)
}
