package com.opencamera.feature.fullclear

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.DeviceGraphSpec
import com.opencamera.core.effect.FrameEffect
import com.opencamera.core.media.CaptureProfile
import com.opencamera.core.media.CaptureStrategy
import com.opencamera.core.media.MediaMetadata
import com.opencamera.core.media.MediaType
import com.opencamera.core.media.PostProcessSpec
import com.opencamera.core.media.SaveRequest
import com.opencamera.core.media.ThumbnailPolicy
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

class FullClearModePlugin : CameraModePlugin {
    override val id: ModeId = ModeId.FULL_CLEAR

    override fun isSupported(deviceCapabilities: DeviceCapabilities): Boolean {
        return deviceCapabilities.supportsStillCapture
    }

    override fun create(context: ModeContext): ModeController {
        return FullClearModeController(context)
    }
}

private class FullClearModeController(
    private val context: ModeContext
) : ModeController {
    override val id: ModeId = ModeId.FULL_CLEAR

    private val _snapshot = MutableStateFlow(
        ModeSnapshot(
            id = ModeId.FULL_CLEAR,
            uiSpec = ModeUiSpec(
                title = "Full Clear",
                shutterLabel = "Capture Full Clear",
                secondaryActionLabel = null
            ),
            state = ModeState(
                headline = "V1 Full Clear ready",
                detail = "focus-bracket-capture V1 · focus-stack-fusion V1 · honest best-frame fallback"
            )
        )
    )

    override val snapshot: StateFlow<ModeSnapshot> = _snapshot

    override fun deviceGraph(): DeviceGraphSpec {
        return DeviceGraphSpec.stillCapture()
    }

    override suspend fun onEnter() {
        context.eventSink("fullclear.enter")
        _snapshot.update {
            it.copy(
                state = it.state.copy(
                    headline = "Full Clear active",
                    detail = "V1 focus bracket capture · honest degradation labels"
                )
            )
        }
    }

    override suspend fun onExit() {
        context.eventSink("fullclear.exit")
        _snapshot.update {
            it.copy(
                state = it.state.copy(
                    headline = "Full Clear inactive"
                )
            )
        }
    }

    override suspend fun handle(intent: ModeIntent): ModeSignal {
        return when (intent) {
            ModeIntent.ShutterPressed -> {
                context.eventSink("fullclear.shutter.pressed")
                val tags = context.captureAidMetadataTags().toMutableMap()
                tags["mode"] = "fullclear"
                tags["focus-bracket-capture"] = "V1"
                tags["focus-stack-fusion"] = "V1"
                tags["degradation-policy"] = "honest-best-frame"
                val strategy = CaptureStrategy.SingleFrame(
                    saveRequest = SaveRequest.photoLibrary(
                        relativePath = "FullClear"
                    ),
                    postProcessSpec = PostProcessSpec(
                        exifOverrides = mapOf("SceneCaptureType" to "Full Clear"),
                        pipelineTags = listOf(
                            "mode:fullclear",
                            "focus-bracket-capture:V1",
                            "focus-stack-fusion:V1"
                        )
                    ),
                    metadata = MediaMetadata(
                        customTags = tags
                    ),
                    captureProfile = CaptureProfile(),
                    thumbnailPolicy = ThumbnailPolicy.USE_SAVED_MEDIA
                )
                ModeSignal.SubmitCapture(strategy)
            }

            ModeIntent.SecondaryActionPressed -> ModeSignal.None
            ModeIntent.TertiaryActionPressed -> ModeSignal.None
            ModeIntent.ProActionPressed -> ModeSignal.None
            is ModeIntent.FrameRatioSelected -> ModeSignal.None
        }
    }

    override suspend fun onSessionEvent(event: ModeSessionEvent) {
        when (event) {
            is ModeSessionEvent.ShotStarted -> {
                if (event.shot.mediaType == MediaType.PHOTO) {
                    _snapshot.update {
                        it.copy(
                            state = it.state.copy(
                                headline = "Full Clear capture in progress"
                            )
                        )
                    }
                }
            }

            is ModeSessionEvent.ShotCompleted -> {
                if (event.result.mediaType == MediaType.PHOTO) {
                    _snapshot.update {
                        it.copy(
                            state = it.state.copy(
                                headline = "Full Clear capture saved"
                            )
                        )
                    }
                }
            }

            is ModeSessionEvent.ShotFailed -> {
                if (event.mediaType == MediaType.PHOTO) {
                    _snapshot.update {
                        it.copy(
                            state = it.state.copy(
                                headline = "Full Clear capture failed"
                            )
                        )
                    }
                }
            }
        }
    }
}
