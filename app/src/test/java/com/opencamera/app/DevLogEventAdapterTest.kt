package com.opencamera.app

import androidx.recyclerview.widget.DiffUtil
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DevLogEventAdapterTest {

    private val diffCallback = object : DiffUtil.ItemCallback<DevLogEventItem>() {
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

    @Test
    fun `DiffUtil detects same item`() {
        val old = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> defaultMode=PHOTO")
        val new = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> defaultMode=PHOTO")
        assertTrue(diffCallback.areItemsTheSame(old, new))
        assertTrue(diffCallback.areContentsTheSame(old, new))
    }

    @Test
    fun `DiffUtil detects changed content for same item`() {
        val old = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> old")
        val new = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> new")
        assertTrue(diffCallback.areItemsTheSame(old, new))
        assertFalse(diffCallback.areContentsTheSame(old, new))
    }

    @Test
    fun `DiffUtil detects different items by sequence`() {
        val old = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> defaultMode=PHOTO")
        val new = TraceEventItem(2, "[00:00:00.002] [session] 2. session.booted -> mode=PHOTO")
        assertFalse(diffCallback.areItemsTheSame(old, new))
    }

    @Test
    fun `DiffUtil detects removed items`() {
        val old = TraceEventItem(5, "[00:00:00.005] [preview] 5. preview.error -> camera error")
        val new = TraceEventItem(6, "[00:00:00.006] [preview] 6. zoom.switch.blocked -> countdown=3")
        assertFalse(diffCallback.areItemsTheSame(old, new))
    }

    @Test
    fun `DiffUtil differentiates section header from trace event`() {
        val header = SectionHeaderItem("--- Link Timing ---")
        val trace = TraceEventItem(1, "[00:00:00.001] [session] 1. session.created -> ok")
        assertFalse(diffCallback.areItemsTheSame(header, trace))
    }

    @Test
    fun `DiffUtil same section header is identical`() {
        val old = SectionHeaderItem("--- Pipeline Notes ---")
        val new = SectionHeaderItem("--- Pipeline Notes ---")
        assertTrue(diffCallback.areItemsTheSame(old, new))
        assertTrue(diffCallback.areContentsTheSame(old, new))
    }

    @Test
    fun `DiffUtil different section headers are different items`() {
        val old = SectionHeaderItem("--- Link Timing ---")
        val new = SectionHeaderItem("--- Pipeline Notes ---")
        assertFalse(diffCallback.areItemsTheSame(old, new))
    }

    @Test
    fun `DiffUtil link event items compared by displayText`() {
        val old = LinkEventItem("[Link] flow=preview-startup stage=bind status=COMPLETED")
        val new = LinkEventItem("[Link] flow=preview-startup stage=bind status=COMPLETED")
        assertTrue(diffCallback.areItemsTheSame(old, new))
        assertTrue(diffCallback.areContentsTheSame(old, new))
    }

    @Test
    fun `DiffUtil link event different displayText are different`() {
        val old = LinkEventItem("[Link] flow=preview-startup stage=bind")
        val new = LinkEventItem("[Link] flow=preview-startup stage=first-frame")
        assertFalse(diffCallback.areItemsTheSame(old, new))
        assertFalse(diffCallback.areContentsTheSame(old, new))
    }
}
