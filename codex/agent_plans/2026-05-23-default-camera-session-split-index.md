# 2026-05-23 DefaultCameraSession Split Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are text-only and do not require screenshots, videos, or visual judgment.

## Goal

Reduce `DefaultCameraSession.kt` from one 1998-line behavior owner into a thin `CameraSession` shell plus small internal processors, without creating a second hidden session kernel and without changing product behavior.

## Verification Verdict

The external review is substantially correct, with one important correction.

Accepted evidence:

- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt` is 1998 lines.
- Its `process(intent)` router at lines `144-205` routes the full `SessionIntent` surface.
- `SessionContracts.kt` currently defines 37 `SessionIntent` variants: lifecycle, permissions, mode actions, countdown, lens/zoom/still-quality/still-resolution, preview host/bind/recovery/events, shot events, thermal/performance, tap focus, and output rotation.
- `DefaultCameraSession.kt` owns at least these responsibilities in one class:
  - session lifecycle and permission transitions;
  - mode controller creation, switching, settings refresh, and mode intent translation;
  - preview host attach/detach, bind/unbind, recovery, first-frame metrics, preview snapshot policy, surface loss, runtime issue recovery;
  - capture countdown, shot planning, shot started/completed/failed, recording stop/watchdog;
  - lens, zoom, still quality, still resolution, preview ratio, output rotation;
  - device graph resolution, output-size resolution, zoom normalization, and mode runtime state;
  - presentation state updates, trace recording, effect emission, and thumbnail/capture feedback policy calls.
- `DefaultCameraSessionTest.kt` is 3858 lines and covers many unrelated session behaviors through the same class, which confirms broad behavioral coupling.

Correction to the external proposal:

- Do not implement a naive chain of independent managers where each manager owns state and returns `Boolean`.
- This project has a hard architecture rule: `Session Kernel` remains the only runtime owner for session state, recovery decisions, and state transitions.
- The safe target is `DefaultCameraSession` as a thin shell around internal processors that share one explicit runtime context, one state store, one effect sink, and one trace owner.
- A processor may own behavior for a subset of intents, but it must not expose its own `StateFlow`, keep shadow session state, drive CameraX, or dispatch back into session recursively except for existing countdown timer events.

## Recommended Architecture

Use a staged extraction:

```text
DefaultCameraSession
  - owns public CameraSession API
  - owns Channel<SessionIntent>
  - records intent.received
  - resolves one explicit SessionIntentOwner
  - delegates to one internal processor

SessionRuntime / ProcessorContext
  - owns MutableStateFlow<SessionState>
  - owns MutableSharedFlow<SessionEffect>
  - owns SessionTrace
  - owns ModeControllerHost and mutable session runtime fields
  - exposes small operations used by processors

Processors
  - LifecycleSessionProcessor
  - ModeControlSessionProcessor
  - PreviewRecoverySessionProcessor
  - CaptureRecordingSessionProcessor
  - DiagnosticsSessionProcessor
```

The first implementation pass should not chase the full shape immediately. It should peel the class in low-risk layers so Stage 7 verification remains meaningful after every step.

## Work Packages

1. [Pure Device Graph And Selection Extraction](./2026-05-23-session-split-01-device-graph-selection.md)
   - Lowest-risk first slice.
   - Moves pure helper logic out of `DefaultCameraSession.kt`.
   - No behavior change and no new processor abstraction yet.

2. [Intent Ownership And Processor Scaffold](./2026-05-23-session-split-02-intent-ownership-scaffold.md)
   - Introduces an exhaustive owner map for every `SessionIntent`.
   - Groups routing by ownership before moving behavior.
   - Prevents the future processor chain from becoming an untyped `|| process()` fallthrough.

3. [Preview Recovery Processor Extraction](./2026-05-23-session-split-03-preview-recovery-processor.md)
   - Highest Stage 7 value.
   - Moves preview host/bind/recovery/runtime issue/tap metering behavior into a focused internal processor.

4. [Capture And Recording Processor Extraction](./2026-05-23-session-split-04-capture-recording-processor.md)
   - Moves countdown, shot planning, shot lifecycle, recording stop/watchdog, and live bundle state handling.

5. [Mode And Device Control Processor Extraction](./2026-05-23-session-split-05-mode-device-control-processor.md)
   - Moves mode switching, settings refresh, mode intent bridge, lens/zoom/still/preview ratio controls, capability refresh, and output rotation.

## Recommended Execution Order

Run packages in order. Packages 1 and 2 are foundation and should not be parallelized with later behavior movement.

After package 2 lands, packages 3 and 4 can be worked by separate agents only if one integrator owns `DefaultCameraSession.kt` merge conflicts. Package 5 should run after packages 3 and 4 because it touches the broadest set of remaining handlers.

## Global Invariants

- `CameraSession` public API must stay unchanged unless the user explicitly approves a wider contract change.
- `SessionIntent` and `SessionEffect` names should remain stable during the split.
- `CameraSessionCoordinator` remains a bridge from `SessionEffect` to `DeviceCommand` and from `DeviceEvent` to `SessionIntent`. It must not gain session decision logic.
- UI continues to render `SessionState` and dispatch intents only.
- Mode plugins continue to describe behavior through `ModeController`, `ModeSignal`, and mode snapshots. They must not call CameraX or mutate session state directly.
- Processors must be `internal` to `core:session`.
- No processor owns a separate `MutableStateFlow`, separate effect flow, or long-lived copy of `SessionState`.
- Every behavior move must keep existing trace labels unless a test explicitly updates the expected trace.
- Every behavior move must run the focused tests first, then `rtk ./scripts/verify_stage_7_observability.sh`.

## Conflict Warnings

- `DefaultCameraSession.kt` will be touched by every package. Use small commits and avoid broad formatting.
- `DefaultCameraSessionTest.kt` is broad and brittle. Prefer moving existing tests into narrower new test classes only when a behavior has moved and the assertions become clearer.
- Preview recovery and capture countdown intersect because preview failures cancel countdowns. Do not let preview and capture processors call each other directly; use a small callback or shared runtime operation.
- Mode switching and capture/recording intersect because active shots block mode changes. Use shared guard helpers rather than duplicating active-shot checks across processors.
- Device graph resolution and still output-size policy are pure and should be extracted before any behavior processor depends on them.

## Global Verification

At minimum, after each package run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

Before declaring the full split complete:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Completion Criteria

- `DefaultCameraSession.kt` is a shell plus setup/wiring, ideally below 900 lines after all packages.
- Each processor file has one clear owner area and can be read without understanding unrelated camera behavior.
- `DefaultCameraSession.process(intent)` remains exhaustive through `SessionIntentOwnership`.
- Existing Stage 7 behavior is unchanged:
  - preview recovery;
  - runtime issue forwarding;
  - recovery failure guardrail;
  - background host recovery;
  - preview startup stall behavior;
  - zoom owner;
  - tap focus/AE metering;
  - output rotation;
  - diagnostics and trace surface.
- `rtk ./scripts/verify_stage_7_observability.sh` passes.

