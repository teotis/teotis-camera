package com.opencamera.app.camera

import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.opencamera.core.device.DeviceRuntimeIssue
import com.opencamera.core.device.DeviceRuntimeIssueKind
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.flattenMerge
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.emptyFlow

interface RuntimeIssueMonitor {
    val runtimeIssues: Flow<DeviceRuntimeIssue>

    fun onPreviewHostAttached()

    fun onPreviewHostDetached()

    fun onPreviewBindingStarted(
        reason: String,
        isRecovery: Boolean
    ) = Unit

    fun onPreviewFirstFrameAvailable(firstFrameLatencyMillis: Long) = Unit

    fun onPreviewStopped(reason: String) = Unit
}

object NoOpRuntimeIssueMonitor : RuntimeIssueMonitor {
    override val runtimeIssues: Flow<DeviceRuntimeIssue> = emptyFlow()

    override fun onPreviewHostAttached() = Unit

    override fun onPreviewHostDetached() = Unit
}

@OptIn(ExperimentalCoroutinesApi::class)
class CompositeRuntimeIssueMonitor(
    private vararg val monitors: RuntimeIssueMonitor
) : RuntimeIssueMonitor {
    override val runtimeIssues: Flow<DeviceRuntimeIssue> = if (monitors.isEmpty()) {
        emptyFlow()
    } else {
        monitors
            .map(RuntimeIssueMonitor::runtimeIssues)
            .asFlow()
            .flattenMerge(monitors.size)
    }

    override fun onPreviewHostAttached() {
        monitors.forEach(RuntimeIssueMonitor::onPreviewHostAttached)
    }

    override fun onPreviewHostDetached() {
        monitors.forEach(RuntimeIssueMonitor::onPreviewHostDetached)
    }

    override fun onPreviewBindingStarted(
        reason: String,
        isRecovery: Boolean
    ) {
        monitors.forEach { it.onPreviewBindingStarted(reason, isRecovery) }
    }

    override fun onPreviewFirstFrameAvailable(firstFrameLatencyMillis: Long) {
        monitors.forEach { it.onPreviewFirstFrameAvailable(firstFrameLatencyMillis) }
    }

    override fun onPreviewStopped(reason: String) {
        monitors.forEach { it.onPreviewStopped(reason) }
    }
}

class AndroidThermalRuntimeIssueMonitor internal constructor(
    private val backend: ThermalStatusBackend
) : RuntimeIssueMonitor {
    constructor(context: Context) : this(
        backend = AndroidPowerManagerThermalStatusBackend(context)
    )

    private val mutableRuntimeIssues = MutableSharedFlow<DeviceRuntimeIssue>(extraBufferCapacity = 4)
    private var isObserving = false
    private var lastThermalStatus: Int? = null

    override val runtimeIssues: Flow<DeviceRuntimeIssue> = mutableRuntimeIssues.asSharedFlow()

    override fun onPreviewHostAttached() {
        if (!backend.isSupported || isObserving) {
            return
        }
        isObserving = true
        backend.start(::handleThermalStatusChanged)
        backend.currentStatus()?.let(::handleThermalStatusChanged)
    }

    override fun onPreviewHostDetached() {
        if (!isObserving) {
            return
        }
        backend.stop()
        isObserving = false
        lastThermalStatus = null
    }

    private fun handleThermalStatusChanged(status: Int) {
        if (status == lastThermalStatus) {
            return
        }
        lastThermalStatus = status
        thermalRuntimeIssueFor(status)?.let(mutableRuntimeIssues::tryEmit)
    }
}

internal interface ThermalStatusBackend {
    val isSupported: Boolean

    fun currentStatus(): Int?

    fun start(onStatusChanged: (Int) -> Unit)

    fun stop()
}

private class AndroidPowerManagerThermalStatusBackend(
    context: Context
) : ThermalStatusBackend {
    private val powerManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        context.getSystemService(PowerManager::class.java)
    } else {
        null
    }
    private var listener: PowerManager.OnThermalStatusChangedListener? = null

    override val isSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && powerManager != null

    override fun currentStatus(): Int? {
        if (!isSupported) {
            return null
        }
        return powerManager?.currentThermalStatus
    }

    override fun start(onStatusChanged: (Int) -> Unit) {
        if (!isSupported || listener != null) {
            return
        }
        val thermalListener = PowerManager.OnThermalStatusChangedListener { status ->
            onStatusChanged(status)
        }
        listener = thermalListener
        powerManager?.addThermalStatusListener(thermalListener)
    }

    override fun stop() {
        val thermalListener = listener ?: return
        powerManager?.removeThermalStatusListener(thermalListener)
        listener = null
    }
}

internal fun thermalRuntimeIssueFor(status: Int): DeviceRuntimeIssue? {
    val reason = when (status) {
        PowerManager.THERMAL_STATUS_SEVERE -> "thermal status severe"
        PowerManager.THERMAL_STATUS_CRITICAL -> "thermal status critical"
        PowerManager.THERMAL_STATUS_EMERGENCY -> "thermal status emergency"
        PowerManager.THERMAL_STATUS_SHUTDOWN -> "thermal status shutdown"
        else -> null
    } ?: return null

    return DeviceRuntimeIssue(
        kind = DeviceRuntimeIssueKind.THERMAL_CRITICAL,
        reason = reason,
        isRecoverable = false
    )
}
