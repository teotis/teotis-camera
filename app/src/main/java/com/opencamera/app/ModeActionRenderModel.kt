package com.opencamera.app

import com.opencamera.core.session.SessionState

internal data class ModeActionRenderModel(
    val isVisible: Boolean,
    val label: String,
    val isActive: Boolean
)

internal fun modeActionRenderModel(state: SessionState): ModeActionRenderModel {
    val snapshot = state.modeSnapshot
    val isEnabled = snapshot.state.isProActionEnabled
    return ModeActionRenderModel(
        isVisible = isEnabled,
        label = snapshot.uiSpec.proActionLabel ?: "",
        isActive = snapshot.state.isProVariantActive
    )
}
