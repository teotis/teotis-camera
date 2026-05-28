package com.opencamera.app.camera

import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import com.opencamera.core.session.classifyPreviewStartCategory
import com.opencamera.core.session.previewStartWatchdogMillis
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

class PreviewStartupRuntimeIssueMonitor(
    private val scope: CoroutineScope
) : RuntimeIssueMonitor {
    private val mutableRuntimeIssues = MutableSharedFlow<DeviceRuntimeIssue>(extraBufferCapacity = 4)
    private var isPreviewHostAttached = false
    private var pendingTimeoutJob: Job? = null

    override val runtimeIssues: Flow<DeviceRuntimeIssue> = mutableRuntimeIssues.asSharedFlow()

    override fun onPreviewHostAttached() {
        isPreviewHostAttached = true
    }

    override fun onPreviewHostDetached() {
        isPreviewHostAttached = false
        cancelPendingTimeout()
    }

    override fun onPreviewBindingStarted(
        reason: String,
        isRecovery: Boolean
    ) {
        if (!isPreviewHostAttached) {
            return
        }
        cancelPendingTimeout()
        val timeoutMillis = previewStartWatchdogMillis(reason)
        pendingTimeoutJob = scope.launch {
            delay(timeoutMillis)
            mutableRuntimeIssues.emit(
                DeviceRuntimeIssue(
                    kind = DeviceRuntimeIssueKind.PREVIEW_STALL,
                    reason = buildPreviewStartupTimeoutReason(
                        reason = reason,
                        timeoutMillis = timeoutMillis
                    ),
                    isRecoverable = true
                )
            )
        }
    }

    override fun onPreviewFirstFrameAvailable(firstFrameLatencyMillis: Long) {
        cancelPendingTimeout()
    }

    override fun onPreviewStopped(reason: String) {
        cancelPendingTimeout()
    }

    private fun cancelPendingTimeout() {
        pendingTimeoutJob?.cancel()
        pendingTimeoutJob = null
    }
}

internal fun buildPreviewStartupTimeoutReason(
    reason: String,
    timeoutMillis: Long
): String {
    val categoryLabel = classifyPreviewStartCategory(reason).label
    return "first frame timed out after ${timeoutMillis} ms ($categoryLabel): $reason"
}
