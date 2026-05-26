# Package 01 — Product Contract And Capability Boundary

## Package ID

`01-product-contract-capability-boundary`

## Goal

Define the Zoom Cockpit V2 product contract so the UI can show preset dots, a continuous focal slider, one-decimal label, capability boundaries, and recording restrictions without inventing UI-local camera runtime state.

## Context

- User request: Zoom Cockpit V2 / focal slider productization, inspired by vivo lens feel, OPPO-style continuous composition, and Apple Camera Control, but not chasing 100x.
- Verified facts:
  - `ZoomRatioCapability` already distinguishes `UNSUPPORTED`, `DISCRETE_PRESET`, and `CONTINUOUS`.
  - `SessionCockpitRenderModel` already exposes `zoomCapsules` and `FocalLengthSliderRenderModel`.
  - Current focal slider model only has `presetRatios`, `currentRatio`, and `isVisible`, so it cannot express disabled/degraded/recording states honestly.
- Relevant files:
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- Non-goals:
  - Do not claim optical focal length, 35mm-equivalent lens values, dual-telephoto hardware, or 100x zoom without explicit device capability evidence.
  - Do not move zoom runtime ownership into UI.
  - Do not edit `FocalLengthSliderView.kt`; package 02 owns widget behavior.

## Implementation Scope

- Extend the focal/zoom render contract with explicit product semantics:
  - current one-decimal display label, e.g. `1.3x`;
  - min/max boundary labels;
  - support state: unsupported, discrete preset, continuous;
  - interaction state: hidden, read-only, preset-only, continuous;
  - disabled/degraded reason, including recording restrictions;
  - preset dot model with active/snap-threshold semantics if needed.
- Derive all fields from `SessionState.activeDeviceCapabilities.zoomRatioCapability`, `SessionState.activeDeviceGraph.preview.zoomRatio`, and `RecordingStatus`.
- Decide the conservative V2 recording policy in the render model:
  - `REQUESTING` and `STOPPING`: zoom controls read-only.
  - `RECORDING` plus `CONTINUOUS`: show current ratio and allow only continuous in-range changes if package 03 confirms session/device can apply without rebind.
  - `RECORDING` plus `DISCRETE_PRESET`: read-only or preset-only disabled with a visible reason; do not present lens jumps as safe.
- Keep preset capsules and slider semantics aligned so exact dots and the floating label never disagree.

## Steps

1. Inspect existing zoom render tests in `SessionCockpitRenderModelTest` and `CameraCockpitRenderModelTest`.
2. Add or refine data classes for the focal slider / zoom cockpit render model.
3. Derive support, current label, bounds, and disabled reason from session state.
4. Add tests for:
   - unsupported zoom is hidden or clearly disabled;
   - discrete preset support is not advertised as continuous;
   - continuous support exposes min/max and one-decimal current label;
   - recording states produce explicit interaction restrictions;
   - exact preset models preserve active state at one-decimal normalization.
5. Do not wire UI callbacks here; package 04 consumes the contract.

## Acceptance Criteria

- Render model can represent unsupported, discrete-only, continuous, and recording-limited zoom states.
- Current zoom label always uses one decimal for slider/cockpit display.
- Preset dots/capsules remain exact, capability-derived ratios.
- During recording, the render model exposes a clear allowed/disabled policy instead of leaving UI to guess.
- Tests prove UI cannot look fully continuous when only discrete preset support is available.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:device:test
```

## Risks And Notes

- Device capability currently describes ratio support, not physical focal length. Treat the V2 label as ratio/focal-feel, not optical focal-length equivalence.
- If the agent believes new core capability fields are required, keep them narrow and testable; do not add broad hardware abstractions.

## File Ownership

- `01-product-contract-capability-boundary` owns:
  - `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt` zoom capability fields/helpers only
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom/focal render models only
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` zoom/focal sections only
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt` zoom/focal sections only
- Other packages must not edit these files without coordination.

## Allowed Paths

- `core/device/src/main/kotlin/com/opencamera/core/device/DeviceContracts.kt`
- `core/device/src/test/kotlin/com/opencamera/core/device/**`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

## Forbidden Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `core/session/src/main/kotlin/com/opencamera/core/session/DefaultCameraSession.kt`
- `app/src/main/java/com/opencamera/app/camera/**`

## Dependencies

- Depends on: none

## Parallel Safety

- safe with `02-slider-widget-productization`
- Reason: package 01 owns render/capability contract; package 02 owns widget math and drawing.

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status clean or explained
- [ ] git diff --stat captured
- [ ] changed files listed
- [ ] verification commands run
- [ ] test results summarized
- [ ] commit hash / PR link
- [ ] unresolved risks noted
- [ ] only allowed paths touched
