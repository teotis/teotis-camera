package com.opencamera.core.session

internal enum class SessionIntentOwner {
    LIFECYCLE,
    MODE_CONTROL,
    PREVIEW_RECOVERY,
    CAPTURE_RECORDING,
    DIAGNOSTICS
}

internal fun SessionIntent.owner(): SessionIntentOwner = when (this) {
    SessionIntent.Boot,
    SessionIntent.Shutdown,
    is SessionIntent.PermissionsUpdated,
    is SessionIntent.DeviceCapabilitiesUpdated -> SessionIntentOwner.LIFECYCLE

    is SessionIntent.SettingsUpdated,
    is SessionIntent.SwitchMode,
    SessionIntent.ShutterPressed,
    SessionIntent.SecondaryActionPressed,
    SessionIntent.TertiaryActionPressed,
    SessionIntent.ProActionPressed,
    SessionIntent.LensFacingToggled,
    SessionIntent.ZoomRatioToggled,
    is SessionIntent.ApplyZoomRatio,
    SessionIntent.StillCaptureQualityToggled,
    SessionIntent.StillCaptureResolutionToggled,
    SessionIntent.PreviewRatioToggled,
    is SessionIntent.FrameRatioSelected,
    is SessionIntent.OutputRotationChanged,
    is SessionIntent.ApplyPreviewBrightness,
    SessionIntent.IncreasePreviewBrightness,
    SessionIntent.DecreasePreviewBrightness,
    SessionIntent.ResetPreviewBrightness,
    is SessionIntent.PreviewBrightnessApplied,
    SessionIntent.DocumentBatchClear,
    is SessionIntent.DocumentBatchRemoveItem,
    is SessionIntent.DocumentBatchMoveItem,
    is SessionIntent.DocumentBatchReorder,
    is SessionIntent.DocumentBatchUpdateCropStatus,
    SessionIntent.DocumentBatchFinish -> SessionIntentOwner.MODE_CONTROL

    SessionIntent.PreviewHostAttached,
    is SessionIntent.PreviewHostDetached,
    is SessionIntent.PreviewBindingStarted,
    is SessionIntent.PreviewFirstFrameAvailable,
    is SessionIntent.PreviewSnapshotUpdated,
    is SessionIntent.LatestGalleryImageLoaded,
    is SessionIntent.LatestGalleryMediaLoaded,
    is SessionIntent.CaptureFeedbackSnapshotUpdated,
    is SessionIntent.PreviewSurfaceLost,
    is SessionIntent.PreviewError,
    is SessionIntent.PreviewRuntimeIssue,
    is SessionIntent.PreviewStopped,
    is SessionIntent.PreviewTapToFocus,
    is SessionIntent.PreviewMeteringCompleted,
    is SessionIntent.PhotoSceneSignalUpdated,
    SessionIntent.PhotoLowLightPromptExpired,
    is SessionIntent.PreviewMeteringFeedbackExpired -> SessionIntentOwner.PREVIEW_RECOVERY

    is SessionIntent.CountdownTick,
    SessionIntent.CountdownCompleted,
    is SessionIntent.ShotStarted,
    is SessionIntent.CaptureCommitted,
    is SessionIntent.DataReceived,
    is SessionIntent.ShotCompleted,
    is SessionIntent.ShotFailed -> SessionIntentOwner.CAPTURE_RECORDING

    is SessionIntent.ThermalStateChanged,
    is SessionIntent.PerformanceClassChanged -> SessionIntentOwner.DIAGNOSTICS
}
