# Session Split 05: Mode And Device Control Processor Extraction

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this plan. Use `rtk` for every command. Execute after packages 01-04.

## Goal

Move mode switching, settings refresh, mode intent bridging, device capability refresh, lens/zoom/still controls, preview ratio, and output rotation into focused internal processors, leaving `DefaultCameraSession` as a thin session shell.

## Files

Create:

- `core/session/src/main/kotlin/com/opencamera/core/session/ModeControlSessionProcessor.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/ModeControllerHost.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/ModeControlSessionProcessorTest.kt` if useful for isolated tests.

Modify:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `core/session/src/test/kotlin/com/opencamera/core/session/DefaultCameraSessionTest.kt`

## Behavior To Move

Move these current `DefaultCameraSession.kt` functions:

- `handleSwitchMode`
- `handleSettingsUpdated`
- `handleLensFacingToggled`
- `handleZoomRatioToggled`
- `handleApplyZoomRatio`
- `handleStillCaptureQualityToggled`
- `handleStillCaptureResolutionToggled`
- `handlePreviewRatioToggled`
- `handleModeIntent`
- `handleDeviceCapabilitiesUpdated`
- `handlePermissionsUpdated` only if lifecycle has already been separated cleanly; otherwise keep permissions in lifecycle and call mode-control hooks from there.
- `handleOutputRotationChanged`
- `createController`

Move these mutable fields into `ModeControllerHost`:

- `sessionDeviceCapabilities`
- `sessionLensFacing`
- `sessionStillCaptureQuality`
- `sessionStillCaptureResolutionPreset`
- `sessionPreviewRatio` if the mode-control processor owns preview ratio;
- `sessionSettingsSnapshot`
- `currentController`

Keep state flow and effect flow owned by `DefaultCameraSession` or the shared runtime context.

## ModeControllerHost Shape

Create a small internal host that owns mode controller lifecycle and mode runtime fields:

```kotlin
package com.opencamera.core.session

import com.opencamera.core.device.DeviceCapabilities
import com.opencamera.core.device.LensFacing
import com.opencamera.core.media.StillCaptureQualityPreference
import com.opencamera.core.media.StillCaptureResolutionPreset
import com.opencamera.core.mode.ModeController
import com.opencamera.core.mode.ModeContext
import com.opencamera.core.mode.ModeId
import com.opencamera.core.mode.ModeRegistry
import com.opencamera.core.mode.ModeRuntimeState
import com.opencamera.core.settings.SessionSettingsSnapshot

internal class ModeControllerHost(
    private val registry: ModeRegistry,
    private val trace: SessionTrace,
    private val effectCapabilityResolver: com.opencamera.core.effect.EffectCapabilityResolver?,
    private val capabilityGraphResolver: com.opencamera.core.effect.CapabilityGraphResolver?,
    private val capabilityRequirements: () -> List<com.opencamera.core.device.CapabilityRequirement>,
    private val updateEffectSpec: (com.opencamera.core.effect.EffectSpec) -> Unit,
    private val updateCapabilityReport: (com.opencamera.core.device.CapabilityGraphReport?) -> Unit
) {
    lateinit var currentController: ModeController
        private set

    var sessionDeviceCapabilities: DeviceCapabilities = DeviceCapabilities.DEFAULT
        private set
    var sessionLensFacing: LensFacing = LensFacing.BACK
        private set
    var sessionStillCaptureQuality: StillCaptureQualityPreference = StillCaptureQualityPreference.LATENCY
        private set
    var sessionStillCaptureResolutionPreset: StillCaptureResolutionPreset = StillCaptureResolutionPreset.LARGE_12MP
        private set
    var sessionSettingsSnapshot: SessionSettingsSnapshot = SessionSettingsSnapshot()
        private set

    fun createController(
        modeId: ModeId,
        deviceCapabilities: DeviceCapabilities,
        lensFacing: LensFacing,
        stillCaptureQuality: StillCaptureQualityPreference,
        stillCaptureResolutionPreset: StillCaptureResolutionPreset,
        settingsSnapshot: SessionSettingsSnapshot
    ): ModeController {
        val clampedStillCaptureResolutionPreset = SessionSelectionPolicy.clampStillCaptureResolutionPreset(
            current = stillCaptureResolutionPreset,
            available = deviceCapabilities.availableStillCaptureResolutionPresets
        )
        sessionDeviceCapabilities = deviceCapabilities
        sessionLensFacing = lensFacing
        sessionStillCaptureQuality = stillCaptureQuality
        sessionStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset
        sessionSettingsSnapshot = settingsSnapshot
        currentController = registry.createController(
            modeId = modeId,
            context = ModeContext(
                deviceCapabilities = deviceCapabilities,
                initialLensFacing = lensFacing,
                initialStillCaptureQuality = stillCaptureQuality,
                initialStillCaptureResolutionPreset = clampedStillCaptureResolutionPreset,
                runtimeState = {
                    ModeRuntimeState(
                        deviceCapabilities = sessionDeviceCapabilities,
                        lensFacing = sessionLensFacing,
                        stillCaptureQuality = sessionStillCaptureQuality,
                        stillCaptureResolutionPreset = sessionStillCaptureResolutionPreset
                    )
                },
                eventSink = { detail -> trace.record("mode.event", detail) },
                onEffectSpecChanged = { spec ->
                    val resolver = effectCapabilityResolver
                    if (resolver != null) {
                        val report = resolver.resolve(spec)
                        updateEffectSpec(report.effectiveSpec)
                    } else {
                        updateEffectSpec(spec)
                    }
                    val graphResolver = capabilityGraphResolver
                    if (graphResolver != null) {
                        val requirements = capabilityRequirements()
                        if (requirements.isNotEmpty()) {
                            updateCapabilityReport(
                                graphResolver.resolve(
                                    featureId = currentController.id.name.lowercase(),
                                    requirements = requirements,
                                    effectSpec = spec
                                )
                            )
                        }
                    }
                },
                settingsSnapshotProvider = { sessionSettingsSnapshot }
            )
        )
        return currentController
    }

    fun updateSettings(snapshot: SessionSettingsSnapshot) {
        sessionSettingsSnapshot = snapshot
    }

    fun updateLensFacing(lensFacing: LensFacing) {
        sessionLensFacing = lensFacing
        currentController.onLensFacingChanged(lensFacing)
    }

    fun updateStillCaptureQuality(quality: StillCaptureQualityPreference) {
        sessionStillCaptureQuality = quality
        currentController.onStillCaptureQualityChanged(quality)
    }

    fun updateStillCaptureResolution(preset: StillCaptureResolutionPreset) {
        sessionStillCaptureResolutionPreset = preset
        currentController.onStillCaptureResolutionChanged(preset)
    }

    fun updateDeviceCapabilities(deviceCapabilities: DeviceCapabilities) {
        sessionDeviceCapabilities = deviceCapabilities
        currentController.onDeviceCapabilitiesChanged(deviceCapabilities)
    }
}
```

The host exists to avoid sprinkling `currentController` and mode runtime vars across processors. It is not a second kernel because it does not own `SessionState`, effects, recovery decisions, or dispatch.

## ModeControlSessionProcessor Shape

Create:

```kotlin
internal class ModeControlSessionProcessor(
    private val state: MutableStateFlow<SessionState>,
    private val effects: MutableSharedFlow<SessionEffect>,
    private val trace: SessionTrace,
    private val modeHost: ModeControllerHost,
    private val graphResolver: SessionDeviceGraphResolver,
    private val updateState: SessionStateUpdater,
    private val capture: CaptureRecordingSessionProcessor,
    private val preview: PreviewRecoverySessionProcessor
) {
    suspend fun process(intent: SessionIntent) {
        when (intent) {
            is SessionIntent.SettingsUpdated -> handleSettingsUpdated(intent.snapshot)
            is SessionIntent.SwitchMode -> handleSwitchMode(intent.modeId)
            SessionIntent.ShutterPressed -> handleModeIntent(ModeIntent.ShutterPressed)
            SessionIntent.SecondaryActionPressed -> handleModeIntent(ModeIntent.SecondaryActionPressed)
            SessionIntent.TertiaryActionPressed -> handleModeIntent(ModeIntent.TertiaryActionPressed)
            SessionIntent.ProActionPressed -> handleModeIntent(ModeIntent.ProActionPressed)
            SessionIntent.LensFacingToggled -> handleLensFacingToggled()
            SessionIntent.ZoomRatioToggled -> handleZoomRatioToggled()
            is SessionIntent.ApplyZoomRatio -> handleApplyZoomRatio(intent.ratio)
            SessionIntent.StillCaptureQualityToggled -> handleStillCaptureQualityToggled()
            SessionIntent.StillCaptureResolutionToggled -> handleStillCaptureResolutionToggled()
            SessionIntent.PreviewRatioToggled -> handlePreviewRatioToggled()
            is SessionIntent.FrameRatioSelected -> handleModeIntent(ModeIntent.FrameRatioSelected(intent.ratio))
            is SessionIntent.OutputRotationChanged -> handleOutputRotationChanged(intent.rotation)
            else -> error("Unexpected mode/control intent: $intent")
        }
    }
}
```

If passing concrete processor instances creates constructor cycles, pass small interfaces instead:

```kotlin
internal interface CaptureControls {
    fun countdownInProgress(): Boolean
    fun cancelPendingCountdown(reason: String)
    fun startCaptureCountdown(strategy: CaptureStrategy, countdownSeconds: Int)
    suspend fun submitCaptureStrategy(strategy: CaptureStrategy)
    suspend fun requestStopActiveCapture()
}

internal interface PreviewControls {
    suspend fun requestPreviewBinding(reason: String, isRecovery: Boolean = false)
}
```

Prefer these interfaces over direct cross-processor knowledge.

## Semantics To Preserve

- Mode switch is blocked during countdown, photo capture, and active/pending recording.
- Unsupported mode switch updates last action and keeps processing later intents.
- Settings update is blocked during countdown or active shot.
- Settings update refreshes the active controller without switching modes.
- Lens toggle is blocked during countdown or active shot.
- Lens toggle updates active graph and requests preview binding.
- Zoom toggle:
  - remains available while recording once recording is active;
  - blocks photo capture and pending recording;
  - emits `SessionEffect.ApplyZoomRatio` instead of rebinding preview.
- Direct zoom apply clamps or snaps according to device capability.
- Still quality/resolution changes apply only to still capture modes and request preview rebind.
- Preview ratio changes session presentation ratio only and does not rebind CameraX.
- Mode intent bridge preserves all `ModeSignal` handling:
  - submit capture;
  - stop active recording;
  - show hint.
- Output rotation emits `SessionEffect.UpdateOutputRotation` only when changed and must not bind preview or stop recording.
- Device capability refresh updates mode controller, clamps still resolution, updates active graph, traces `device.capabilities.updated`, and requests preview binding.
- Permissions update semantics remain unchanged if moved:
  - camera denial blocks preview and clears active shot;
  - permission loss cancels countdown;
  - permission loss fails active shot trace;
  - camera grant resumes pending host recovery or requests normal bind.

## Tests To Keep Green

Run targeted mode/control cases:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.mode switch is blocked while recording" --tests "com.opencamera.core.session.DefaultCameraSessionTest.unsupported mode switch is ignored and session keeps processing intents"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.settings update refreshes active photo mode filter without switching modes" --tests "com.opencamera.core.session.DefaultCameraSessionTest.device capability update refreshes active video mode ui and graph"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.zoom toggle cycles configured preset ratios and updates active graph" --tests "com.opencamera.core.session.DefaultCameraSessionTest.apply zoom ratio sets target ratio directly"
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests "com.opencamera.core.session.DefaultCameraSessionTest.dispatching new rotation updates state and emits one update effect" --tests "com.opencamera.core.session.DefaultCameraSessionTest.rotation change while recording does not stop active shot"
```

Then run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test
rtk ./scripts/verify_stage_7_observability.sh
```

## Final Cleanup

After this package:

- Delete moved private handlers from `DefaultCameraSession.kt`.
- Keep `DefaultCameraSession.kt` focused on:
  - constructor dependency wiring;
  - initial state creation;
  - `state` and `effects` exposure;
  - intent channel;
  - `dispatch`;
  - `process` owner routing;
  - processor construction.
- Re-run `rtk wc -l core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`.
- If the file remains above 900 lines, identify remaining unrelated behavior and write a follow-up plan instead of doing a drive-by cleanup.

## Acceptance Criteria

- `DefaultCameraSession.kt` no longer contains broad mode/control handler bodies.
- `ModeControllerHost` owns mode controller construction and runtime mode fields.
- `ModeControlSessionProcessor` owns mode/control behavior but not state flow or device adapter behavior.
- No public contracts are renamed.
- Existing mode, settings, capability, zoom, rotation, and Stage 7 tests pass.
- `rtk ./scripts/verify_stage_7_observability.sh` passes.

## Non-Goals

- Do not change mode plugin contracts.
- Do not move `CameraSessionCoordinator` responsibilities.
- Do not add new product features.
- Do not rewrite `SessionContracts.kt` into smaller public files in this package.
- Do not introduce a DI framework or broad abstraction beyond internal processors.
