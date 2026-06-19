package com.opencamera.core.media

import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

data class FrameSelectionWindow(
    val shutterTimestampNanos: Long,
    val preShutterMillis: Long,
    val postShutterMillis: Long
)

data class SelectedFrameSet(
    val frames: List<FrameDescriptor>,
    val preShutterCount: Int,
    val postShutterCount: Int,
    val coveredPreShutterMillis: Long,
    val coveredPostShutterMillis: Long,
    val diagnostics: List<String>
)

class FrameRingBuffer(private val policy: FrameBufferPolicy) {

    private val frames = mutableListOf<FrameDescriptor>()
    private val lock = ReentrantLock()

    fun append(descriptor: FrameDescriptor) = lock.withLock {
        frames.add(descriptor)
        frames.sortBy { it.timestampNanos }
        evict()
    }

    fun snapshot(): List<FrameDescriptor> = lock.withLock {
        frames.toList()
    }

    fun select(window: FrameSelectionWindow): SelectedFrameSet = lock.withLock {
        val preShutterNanos = window.preShutterMillis * 1_000_000
        val postShutterNanos = window.postShutterMillis * 1_000_000
        val startNanos = window.shutterTimestampNanos - preShutterNanos
        val endNanos = window.shutterTimestampNanos + postShutterNanos

        val selected = frames.filter { frame ->
            frame.timestampNanos in startNanos..endNanos
        }

        val preShutterCount = selected.count { it.timestampNanos <= window.shutterTimestampNanos }
        val postShutterCount = selected.count { it.timestampNanos > window.shutterTimestampNanos }

        val coveredPreShutterMillis = if (selected.isNotEmpty()) {
            val earliest = selected.first().timestampNanos
            ((window.shutterTimestampNanos - earliest) / 1_000_000).coerceAtLeast(0)
        } else 0

        val coveredPostShutterMillis = if (selected.isNotEmpty()) {
            val latest = selected.last().timestampNanos
            ((latest - window.shutterTimestampNanos) / 1_000_000).coerceAtLeast(0)
        } else 0

        val diagnostics = buildList {
            add("frame-buffer:selected=${selected.size}")
            add("frame-buffer:window=-${window.preShutterMillis}ms,+${window.postShutterMillis}ms")
            if (selected.isEmpty()) {
                add("frame-buffer:degraded=no-frames-near-shutter")
            }
        }

        SelectedFrameSet(
            frames = selected,
            preShutterCount = preShutterCount,
            postShutterCount = postShutterCount,
            coveredPreShutterMillis = coveredPreShutterMillis,
            coveredPostShutterMillis = coveredPostShutterMillis,
            diagnostics = diagnostics
        )
    }

    fun clear() = lock.withLock {
        frames.clear()
    }

    private fun evict() {
        // Evict by max frames
        while (frames.size > policy.maxFrames) {
            frames.removeAt(0)
        }
        // Evict by retention window
        if (frames.isNotEmpty()) {
            val newestTimestamp = frames.last().timestampNanos
            val retentionNanos = policy.retentionWindowMillis * 1_000_000
            val cutoff = newestTimestamp - retentionNanos
            frames.removeAll { it.timestampNanos < cutoff }
        }
    }
}
