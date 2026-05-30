package com.opencamera.feature.fullclear

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.device.LensFacing
import com.opencamera.core.mode.CameraModePlugin
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeIntent
import com.opencamera.core.mode.ModeSessionEvent
import com.opencamera.core.mode.ModeSignal
import com.opencamera.core.mode.ModeSnapshot
import com.opencamera.core.mode.ModeState
import com.opencamera.core.mode.ModeUiSpec
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class FullClearModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.FULL_CLEAR

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean = true

    override fun create(context: ModeContext): ModeController {
        return FullClearModeController(context)
    }
}

private class FullClearModeController(
    private val context: ModeContext
) : ModeController {
    private val mutableSnapshot = MutableStateFlow(
        ModeSnapshot(
            id = ModeId.FULL_CLEAR,
            uiSpec = ModeUiSpec(
                title = "全清",
                shutterLabel = "拍摄"
            ),
            state = ModeState(
                headline = "全清模式已激活",
                detail = "全清拍摄管线就绪。"
            )
        )
    )

    override val id: ModeId = ModeId.FULL_CLEAR
    override val snapshot: StateFlow<ModeSnapshot> = mutableSnapshot.asStateFlow()

    override fun deviceGraph(): DeviceGraphSpec = DeviceGraphSpec.stillCapture()

    override suspend fun onDeviceCapabilitiesChanged(deviceCapabilities: DeviceCapabilities) = Unit
    override suspend fun onLensFacingChanged(lensFacing: LensFacing) = Unit
    override suspend fun onStillCaptureResolutionChanged(
        stillCaptureResolutionPreset: StillCaptureResolutionPreset
    ) = Unit
    override suspend fun onStillCaptureQualityChanged(
        stillCaptureQuality: StillCaptureQualityPreference
    ) = Unit

    override suspend fun onEnter() {
        context.eventSink("fullclear.enter")
        mutableSnapshot.value = mutableSnapshot.value.copy(
            state = mutableSnapshot.value.state.copy(
                headline = "全清模式已激活"
            )
        )
    }

    override suspend fun onExit() {
        context.eventSink("fullclear.exit")
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal = when (intent) {
        ModeIntent.ShutterPressed -> ModeSignal.SubmitCapture(CaptureStrategy.SingleFrame())
        else -> ModeSignal.None
    }
    override suspend fun onSessionEvent(event: ModeSessionEvent) = Unit
}
