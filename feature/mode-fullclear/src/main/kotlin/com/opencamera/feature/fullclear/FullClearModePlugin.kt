package com.opencamera.feature.fullclear

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FullClearModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.FULL_CLEAR

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean = true

    override fun create(context: ModeContext): ModeController = FullClearController()
}

private class FullClearController : ModeController {
    override val id: ModeId = ModeId.FULL_CLEAR

    private val mutableSnapshot = MutableStateFlow(
        ModeSnapshot(
            id = ModeId.FULL_CLEAR,
            uiSpec = ModeUiSpec(title = "Full Clear", shutterLabel = "Clear"),
            state = ModeState(headline = "Full Clear", detail = "Ready")
        )
    )

    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = DeviceGraphSpec.stillCapture()

    override suspend fun onEnter() {}
    override suspend fun onExit() {}
    override suspend fun handle(intent: ModeIntent): ModeSignal = ModeSignal.None
}
