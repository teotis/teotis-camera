package com.opencamera.app

import com.opencamera.core.session.SessionState
import com.opencamera.core.mode.ModeId

internal data class ModeActionRenderModel(
    val isVisible: Boolean,
    val label: String,
    val isActive: Boolean
)

internal fun modeActionRenderModel(state: SessionState): ModeActionRenderModel {
    val snapshot = state.modeSnapshot
    val isScopedModeAction = state.activeMode == ModeId.HUMANISTIC ||
        state.activeMode == ModeId.CHECK_IN
    val isEnabled = isScopedModeAction && snapshot.id == state.activeMode &&
        snapshot.state.isProActionEnabled
    val label = when (state.activeMode) {
        ModeId.HUMANISTIC -> "Pro"
        ModeId.CHECK_IN -> snapshot.uiSpec.proActionLabel ?: ""
        else -> ""
    }
    return ModeActionRenderModel(
        isVisible = isEnabled,
        label = label,
        isActive = snapshot.state.isProVariantActive
    )
}
