# Package 02 — Quick Pixel Surface Design

## Package ID

`02-quick-pixel-surface-design`

## Purpose

Design the quick pixel control behavior once package 01 establishes a truthful native still-output capability contract. The goal is that a vivo X300 class device does not show a misleading 13MP ceiling when a higher still output is actually supported, and does not expose a fake switch when high-pixel capture is only degraded or unsupported.

## Dependencies

- Wait for `status/01-pixel-capability-enumeration.md`.

## Current Evidence To Re-read

- `app/src/main/java/com/opencamera/app/SessionStateRender.kt`
  - `displayedStillCaptureOutputSize(...)`
  - `selectedNativeStillCaptureOutputSizeOrNull(...)`
  - `isStillResolutionToggleEnabled(...)`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - native output labels and quick megapixel display
  - quick/settings panel text around still resolution
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `StillCaptureResolutionToggled`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
  - `handleStillCaptureResolutionToggled(...)`
  - `nextStillCaptureOutputSize(...)`
  - `resolutionPresetForOutputSize(...)`
- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- `docs/plans/2026-05-25-quick-panel-regression-repair.md`
- `docs/plans/2026-05-25-quick-panel-semantic-controls-v2.md`

## Allowed Paths

- Read-only: app render/action code, core session still-resolution code, feature mode plugin code, relevant tests and prior plans.
- Writable: `docs/plans/vivo-x300-pixel-shutter-lifecycle-orchestration/status/02-quick-pixel-surface-design.md` only.

## Forbidden Paths

- Do not edit runtime code or tests.
- Do not edit `INDEX.md`.
- Do not edit another package's status file.

## Required Design Questions

1. What should the quick row label show when:
   - native output sizes include 50MP and lower modes,
   - only 13MP is bindable,
   - sensor suggests 50MP but CameraX cannot bind it,
   - high-pixel capture is supported only for some modes/lenses?
2. What should tapping the quick pixel row do:
   - cycle native output sizes directly,
   - cycle presets plus native size,
   - open a small options surface,
   - disable with a degraded explanation?
3. What state should be persisted:
   - native output size,
   - preset,
   - best-effort high-pixel preference,
   - per-lens/per-mode selection?
4. What UI truthfulness rules prevent false claims:
   - no `50MP` label unless it is selected/bindable or explicitly marked unavailable,
   - no switch affordance when only one output is supported,
   - no hiding 50MP behind `Large 12MP` fallback labels.

## Verification Commands

Use read-only inspection first:

```bash
rtk rg -n "displayedStillCaptureOutputSize|isStillResolutionToggleEnabled|StillCaptureResolutionToggled|quickMegapixelLabel|availableStillCaptureOutputSizes" app/src/main/java app/src/test core/session/src/main -g '*.kt'
```

If running focused tests, use:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionStateRenderTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

In a worktree, use `rtk ./scripts/run_isolated_gradle.sh` for the same Gradle args.

## Acceptance Criteria

- [ ] Status file consumes package 01 evidence instead of guessing the high-pixel source.
- [ ] Status file defines quick pixel labels, enabled/disabled states, and cycle/open behavior for supported/degraded/unsupported cases.
- [ ] Status file names the future code touchpoints and tests needed to implement the design.
- [ ] Status file preserves architecture boundaries: UI dispatches intents; session owns state; device adapter owns hardware capability truth.
- [ ] Status file includes a real-device acceptance checklist for the pixel row but does not claim completion.

## Expected Evidence Pack

Write to `status/02-quick-pixel-surface-design.md`:
- decision table for labels/affordance,
- proposed state and persistence semantics,
- future test list,
- risks and product questions that need user/Codex decision.
