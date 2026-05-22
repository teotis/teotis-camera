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

internal data class BottomCockpitRenderModel(
    val captureOutputText: String,
    val shutterLabel: String,
    val isShutterEnabled: Boolean,
    val isRecording: Boolean,
    val lensButtonLabel: String,
    val lensButtonEnabled: Boolean,
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
    val bottomCockpit: BottomCockpitRenderModel,
    val previewRatioChip: PreviewRatioChipRenderModel,
    val activePanelRoute: CockpitPanelRoute = CockpitPanelRoute.None,
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
    val controls = sessionControlsRenderModel(state, strings)
    val primary = primaryStatusRenderModel(state, text)
    val modeTrack = modeTrackRenderModel(state, text)
    val captureOutput = sessionCaptureOutputText(state, strings)

    return CameraCockpitRenderModel(
        topStatus = TopStatusRenderModel(
            appName = "OpenCamera",
            modeLabel = primary.modeLabel,
            statusText = primary.statusText,
            labEntryLabel = text.colorLabEntry(),
            settingsEntryLabel = text.settingsEntry()
        ),
        rightRail = RightRailRenderModel(
            entries = listOf(
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.FilterLab,
                    label = text.styleEntry(),
                    isActive = activeRoute is CockpitPanelRoute.FilterLab
                ),
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.QuickBubble,
                    label = text.quickLauncher(),
                    isActive = activeRoute is CockpitPanelRoute.QuickBubble
                ),
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.DevConsole,
                    label = text.devEntry(),
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
        bottomCockpit = BottomCockpitRenderModel(
            captureOutputText = captureOutput,
            shutterLabel = text.shutterShort(),
            isShutterEnabled = state.modeSnapshot.state.isShutterEnabled,
            isRecording = state.recordingStatus != com.opencamera.core.session.RecordingStatus.IDLE,
            lensButtonLabel = controls.lensFacingButtonLabel,
            lensButtonEnabled = controls.lensFacingEnabled,
            disabledReason = captureDisabledReason(state, text)
        ),
        previewRatioChip = PreviewRatioChipRenderModel(
            label = state.previewRatio.label,
            ratio = state.previewRatio,
            isActive = true
        ),
        activePanelRoute = activeRoute
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
