package com.opencamera.app

import com.opencamera.app.i18n.AppTextResolver
import com.opencamera.core.session.SessionState

internal data class TopStatusRenderModel(
    val appName: String,
    val modeLabel: String,
    val statusText: String,
    val labEntryLabel: String
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
    val zoomButtonLabel: String,
    val zoomButtonEnabled: Boolean
)

internal data class CameraCockpitRenderModel(
    val topStatus: TopStatusRenderModel,
    val rightRail: RightRailRenderModel,
    val zoomStrip: ZoomStripRenderModel,
    val modeTrack: ModeTrackRenderModel,
    val bottomCockpit: BottomCockpitRenderModel,
    val activePanelRoute: CockpitPanelRoute = CockpitPanelRoute.None
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
            labEntryLabel = text.lensLab()
        ),
        rightRail = RightRailRenderModel(
            entries = listOf(
                RightRailEntryRenderModel(
                    route = CockpitPanelRoute.FilterLab,
                    label = text.tone(),
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
                    isActive = activeRoute is CockpitPanelRoute.DevConsole,
                    isVisible = false
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
            isShutterEnabled = true,
            isRecording = false,
            lensButtonLabel = controls.lensFacingButtonLabel,
            lensButtonEnabled = controls.lensFacingEnabled,
            zoomButtonLabel = controls.zoomButtonLabel,
            zoomButtonEnabled = controls.zoomEnabled
        ),
        activePanelRoute = activeRoute
    )
}
