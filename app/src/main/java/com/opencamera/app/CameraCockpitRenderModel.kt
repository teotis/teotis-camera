package com.opencamera.app

import android.view.Surface
import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.session.PreviewRatio
import com.opencamera.core.session.SessionState

internal enum class CockpitDisplayOrientation {
    PORTRAIT,
    LANDSCAPE_LEFT,
    LANDSCAPE_RIGHT
}

internal data class CockpitOrientationRenderModel(
    val orientation: CockpitDisplayOrientation,
    val controlRotationDegrees: Float
)

internal data class TopStatusRenderModel(
    val appName: String,
    val modeLabel: String,
    val statusText: String,
    val labEntryLabel: String,
    val settingsEntryLabel: String
)

internal data class RightRailEntryRenderModel(
    val route: CockpitPanelRoute,
    val label: String,
    val isActive: Boolean = false,
    val isVisible: Boolean = true
)

internal data class RightRailRenderModel(
    val entries: List<RightRailEntryRenderModel>
)

internal data class ZoomChipRenderModel(
    val label: String,
    val ratio: Float,
    val isActive: Boolean,
    val isEnabled: Boolean = true
)

internal data class ZoomStripRenderModel(
    val chips: List<ZoomChipRenderModel>,
    val isVisible: Boolean
)

internal data class RecordingIndicatorRenderModel(
    val isVisible: Boolean,
    val label: String,
    val status: com.opencamera.core.session.RecordingStatus
)

internal data class BottomCockpitRenderModel(
    val captureOutputText: String,
    val shutterLabel: String,
    val isShutterEnabled: Boolean,
    val isRecording: Boolean,
    val lensButtonLabel: String,
    val lensButtonEnabled: Boolean,
    val recordingIndicator: RecordingIndicatorRenderModel,
    val shutterVisualState: ShutterVisualState = ShutterVisualState.PHOTO_READY,
    val disabledReason: String? = null
)

internal data class PreviewRatioChipRenderModel(
    val label: String,
    val ratio: PreviewRatio,
    val isActive: Boolean
)

internal data class CameraCockpitRenderModel(
    val topStatus: TopStatusRenderModel,
    val rightRail: RightRailRenderModel,
    val zoomStrip: ZoomStripRenderModel,
    val modeTrack: ModeTrackRenderModel,
    val modeAction: ModeActionRenderModel,
    val bottomCockpit: BottomCockpitRenderModel,
    val previewRatioChip: PreviewRatioChipRenderModel,
    val activePanelRoute: CockpitPanelRoute = CockpitPanelRoute.None,
    val isStyleEntryVisible: Boolean = true,
    val orientation: CockpitOrientationRenderModel = CockpitOrientationRenderModel(
        CockpitDisplayOrientation.PORTRAIT, 0f
    )
)

internal fun cameraCockpitRenderModel(
    state: SessionState,
    text: AppTextResolver,
    strings: SessionUiStrings,
    activeRoute: CockpitPanelRoute = CockpitPanelRoute.None
): CameraCockpitRenderModel {
    val controls = sessionControlsRenderModel(state, strings, text)
    val primary = primaryStatusRenderModel(state, text)
    val modeTrack = modeTrackRenderModel(state, text)
    val captureOutput = sessionCaptureOutputText(state, strings, text)

    return CameraCockpitRenderModel(
        topStatus = TopStatusRenderModel(
            appName = "OpenCamera",
            modeLabel = primary.modeLabel,
            statusText = primary.statusText,
            labEntryLabel = text.get(R.string.button_color_lab_entry),
            settingsEntryLabel = text.get(R.string.button_settings_entry)
        ),
        rightRail = RightRailRenderModel(
            entries = listOf(
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.StyleLab,
                    label = text.get(R.string.button_palette_entry),
                    isActive = activeRoute is CockpitPanelRoute.StyleLab
                ),
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.QuickBubble,
                    label = text.get(R.string.button_quick_launcher),
                    isActive = activeRoute is CockpitPanelRoute.QuickBubble
                ),
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.DevConsole,
                    label = text.get(R.string.button_dev_entry),
                    isActive = activeRoute is CockpitPanelRoute.DevConsole
                )
            )
        ),
        zoomStrip = ZoomStripRenderModel(
            chips = controls.zoomCapsules.map { capsule ->
                ZoomChipRenderModel(
                    label = capsule.label,
                    ratio = capsule.ratio,
                    isActive = capsule.isActive
                )
            },
            isVisible = controls.isZoomCapsuleRowVisible
        ),
        modeTrack = modeTrack,
        modeAction = modeActionRenderModel(state),
        bottomCockpit = BottomCockpitRenderModel(
            captureOutputText = captureOutput,
            shutterLabel = text.get(R.string.button_shutter_short),
            isShutterEnabled = shutterDisabledReason(state, text) == null,
            isRecording = state.recordingStatus != com.opencamera.core.session.RecordingStatus.IDLE,
            lensButtonLabel = controls.lensFacingButtonLabel,
            lensButtonEnabled = controls.lensFacingEnabled,
            recordingIndicator = recordingIndicatorRenderModel(state, text),
            shutterVisualState = shutterVisualState(state),
            disabledReason = shutterDisabledReason(state, text)
        ),
        previewRatioChip = PreviewRatioChipRenderModel(
            label = state.previewRatio.label,
            ratio = state.previewRatio,
            isActive = true
        ),
        activePanelRoute = activeRoute,
        isStyleEntryVisible = styleSurfaceRole(state.activeMode) != StyleSurfaceRole.HIDDEN
    )
}

internal fun orientationRenderModel(displayRotation: Int): CockpitOrientationRenderModel {
    return when (displayRotation) {
        Surface.ROTATION_90 -> CockpitOrientationRenderModel(
            CockpitDisplayOrientation.LANDSCAPE_LEFT, 90f
        )
        Surface.ROTATION_270 -> CockpitOrientationRenderModel(
            CockpitDisplayOrientation.LANDSCAPE_RIGHT, -90f
        )
        else -> CockpitOrientationRenderModel(
            CockpitDisplayOrientation.PORTRAIT, 0f
        )
    }
}

internal fun recordingIndicatorRenderModel(
    state: SessionState,
    text: AppTextResolver
): RecordingIndicatorRenderModel {
    val status = state.recordingStatus
    val isVisible = status != com.opencamera.core.session.RecordingStatus.IDLE
    val label = when (status) {
        com.opencamera.core.session.RecordingStatus.REQUESTING -> text.get(R.string.status_recording_starting)
        com.opencamera.core.session.RecordingStatus.RECORDING ->
            formatRecordingElapsed(state.recordingElapsedMillis ?: 0L)
        com.opencamera.core.session.RecordingStatus.STOPPING -> text.get(R.string.status_recording_saving)
        com.opencamera.core.session.RecordingStatus.IDLE -> ""
    }
    return RecordingIndicatorRenderModel(
        isVisible = isVisible,
        label = label,
        status = status
    )
}

internal fun formatRecordingElapsed(millis: Long): String {
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%02d:%02d".format(minutes, seconds)
    }
}
