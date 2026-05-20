package com.opencamera.app.camera.device

import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceCommand
import com.opencamera.core.device.DeviceEvent
import com.opencamera.core.device.DeviceGraphSpec
import kotlinx.coroutines.flow.Flow

interface CameraDeviceAdapter {
    val capabilities: DeviceCapabilities
    val events: Flow<DeviceEvent>

    fun capabilitiesFor(deviceGraph: DeviceGraphSpec): DeviceCapabilities = capabilities

    suspend fun bindUseCases(
        lifecycleOwner: LifecycleOwner,
        previewView: PreviewView,
        deviceGraph: DeviceGraphSpec
    )

    suspend fun dispatch(command: DeviceCommand)

    suspend fun release()

    fun boundGraph(): DeviceGraphSpec?
}
