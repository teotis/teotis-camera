# Session Split 02: Intent Ownership And Processor Scaffold

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan. Use `rtk` for every command. This package introduces routing structure before moving behavior.

## Goal

Replace the single large `when (intent)` router with an explicit, exhaustive ownership map and grouped dispatch methods. This creates a safe path for later processor extraction while preserving the single session kernel.

## Why This Is Needed

The external proposal suggested:

```kotlin
val handled = lifecycle.process(intent) ||
    modeSwitch.process(intent) ||
    capture.process(intent) ||
    recording.process(intent) ||
    preview.process(intent)
```

Do not implement that shape directly. A boolean chain hides ownership gaps and makes fallthrough order part of behavior.

Instead, introduce one exhaustive ownership function:

```kotlin
internal fun SessionIntent.owner(): SessionIntentOwner = when (this) {
    // Every current SessionIntent branch is listed in the ownership shape below.
}
```

This keeps Kotlin sealed-class exhaustiveness as the guardrail.

## Files

Create:

- `core/session/src/main/kotlin/com/opencamera/core/session/SessionIntentOwnership.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/SessionIntentOwnershipTest.kt`

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt` only if routing test setup needs imports.

## Ownership Shape

Create:

```kotlin
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
    is SessionIntent.OutputRotationChanged -> SessionIntentOwner.MODE_CONTROL

    SessionIntent.PreviewHostAttached,
    is SessionIntent.PreviewHostDetached,
    is SessionIntent.PreviewBindingStarted,
    is SessionIntent.PreviewFirstFrameAvailable,
    is SessionIntent.PreviewSnapshotUpdated,
    is SessionIntent.CaptureFeedbackSnapshotUpdated,
    is SessionIntent.PreviewSurfaceLost,
    is SessionIntent.PreviewError,
    is SessionIntent.PreviewRuntimeIssue,
    is SessionIntent.PreviewStopped,
    is SessionIntent.PreviewTapToFocus,
    is SessionIntent.PreviewMeteringCompleted -> SessionIntentOwner.PREVIEW_RECOVERY

    is SessionIntent.CountdownTick,
    SessionIntent.CountdownCompleted,
    is SessionIntent.ShotStarted,
    is SessionIntent.ShotCompleted,
    is SessionIntent.ShotFailed -> SessionIntentOwner.CAPTURE_RECORDING

    is SessionIntent.ThermalStateChanged,
    is SessionIntent.PerformanceClassChanged -> SessionIntentOwner.DIAGNOSTICS
}
```

If the compiler flags any branch as invalid because a data class spans multiple lines in `SessionContracts.kt`, keep the same mapping and adjust syntax only. Do not change semantic ownership.

## DefaultCameraSession Routing

Change:

```kotlin
private suspend fun process(intent: SessionIntent) {
    trace.record("intent.received", intent.toString())
    when (intent) {
        // Existing direct intent routing is replaced by owner-based routing.
    }
}
```

To:

```kotlin
private suspend fun process(intent: SessionIntent) {
    trace.record("intent.received", intent.toString())
    when (intent.owner()) {
        SessionIntentOwner.LIFECYCLE -> processLifecycleIntent(intent)
        SessionIntentOwner.MODE_CONTROL -> processModeControlIntent(intent)
        SessionIntentOwner.PREVIEW_RECOVERY -> processPreviewRecoveryIntent(intent)
        SessionIntentOwner.CAPTURE_RECORDING -> processCaptureRecordingIntent(intent)
        SessionIntentOwner.DIAGNOSTICS -> processDiagnosticsIntent(intent)
    }
}
```

Add grouped private methods in `DefaultCameraSession.kt`. In this package, they may still call the existing private handlers:

```kotlin
private suspend fun processLifecycleIntent(intent: SessionIntent) {
    when (intent) {
        SessionIntent.Boot -> handleBoot()
        SessionIntent.Shutdown -> handleShutdown()
        is SessionIntent.PermissionsUpdated -> handlePermissionsUpdated(
            cameraGranted = intent.cameraGranted,
            microphoneGranted = intent.microphoneGranted
        )
        is SessionIntent.DeviceCapabilitiesUpdated -> handleDeviceCapabilitiesUpdated(intent.capabilities)
        else -> error("Unexpected lifecycle intent: $intent")
    }
}
```

Use the same pattern for the other owners. This is intentionally a scaffold: later packages move the handler bodies out.

## Diagnostics Routing

For now, preserve current no-op trace behavior:

```kotlin
private fun processDiagnosticsIntent(intent: SessionIntent) {
    when (intent) {
        is SessionIntent.ThermalStateChanged ->
            trace.record("intent.thermal", intent.thermalState.toString())
        is SessionIntent.PerformanceClassChanged ->
            trace.record("intent.performance", intent.performanceClass.toString())
        else -> error("Unexpected diagnostics intent: $intent")
    }
}
```

## Tests To Add

`SessionIntentOwnershipTest` should assert representative mapping:

- `Boot` -> `LIFECYCLE`
- `PermissionsUpdated` -> `LIFECYCLE`
- `SwitchMode` -> `MODE_CONTROL`
- `ShutterPressed` -> `MODE_CONTROL`
- `PreviewRuntimeIssue` -> `PREVIEW_RECOVERY`
- `PreviewTapToFocus` -> `PREVIEW_RECOVERY`
- `CountdownTick` -> `CAPTURE_RECORDING`
- `ShotCompleted` -> `CAPTURE_RECORDING`
- `ThermalStateChanged` -> `DIAGNOSTICS`
- `OutputRotationChanged` -> `MODE_CONTROL`

The main compile-time safety is the exhaustive `when` in `owner()`. The test is a smoke guard against accidental remapping.

## Focused Verification

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.SessionIntentOwnershipTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Acceptance Criteria

- `DefaultCameraSession.process()` is short and delegates by `SessionIntentOwner`.
- All original handler behavior remains in `DefaultCameraSession.kt` for now.
- `DefaultCameraSessionTest` passes unchanged.
- No new manager owns state.
- No boolean processor chain is introduced.
- Adding a new `SessionIntent` in the future requires updating `owner()` at compile time.

## Non-Goals

- Do not move preview, capture, mode, or lifecycle handler bodies yet.
- Do not introduce a public middleware API.
- Do not split `SessionContracts.kt`.
- Do not change `CameraSessionCoordinator`.
