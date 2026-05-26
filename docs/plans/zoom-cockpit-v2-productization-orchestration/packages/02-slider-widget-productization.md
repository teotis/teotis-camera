# Package 02 — Focal Slider Widget Productization

## Package ID

`02-slider-widget-productization`

## Goal

Upgrade `FocalLengthSliderView` from a basic draggable view into a predictable product control: continuous drag, one-decimal floating label, tappable preset dots, small snap threshold, clamped bounds, and focused tests for the ratio math.

## Context

- User request: point positions plus slider plus one-decimal focal/zoom label.
- Verified facts:
  - `FocalLengthSliderView` already draws a track, dots, thumb, and floating label.
  - Current release behavior snaps every drag to the nearest preset, so continuous drag is only transient.
  - Current ratio mapping is embedded in the View, making focused unit tests harder.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
  - optional new helper: `app/src/main/java/com/opencamera/app/FocalLengthSliderMath.kt`
  - focused app tests under `app/src/test/java/com/opencamera/app/`
- Non-goals:
  - Do not change session/device zoom ownership.
  - Do not change render model fields; package 01 owns contract shape.
  - Do not edit `CockpitSurfaceRenderer.kt`; package 04 owns integration.

## Implementation Scope

- Extract pure slider math if useful:
  - ratio to track fraction / x;
  - x to clamped ratio;
  - nearest preset;
  - snap threshold decision;
  - one-decimal formatting.
- Keep drag continuous:
  - moving thumb reports `onRatioChanged` with clamped continuous values.
  - release keeps the continuous value unless it is within a small snap threshold of a preset.
  - preset dots are tappable for explicit exact jumps.
- Preserve an explicit final callback:
  - continuous release should dispatch final value.
  - snapped/tapped preset should dispatch exact preset value.
- Ensure external session updates can sync the thumb when not dragging.

## Steps

1. Inspect current `FocalLengthSliderView` touch flow.
2. Add pure helper math if needed to avoid fragile View-only tests.
3. Change release behavior from always-snap to snap-threshold.
4. Add preset-dot hit testing for explicit exact jumps.
5. Keep floating label at exactly one decimal.
6. Add focused tests for mapping, formatting, threshold snapping, continuous release, dot tap, and external current-ratio sync.
7. Do not introduce UI-local authoritative zoom state; the view only reflects its model and emits callbacks.

## Acceptance Criteria

- Dragging reports continuous `X.Xx` values and leaves final value continuous unless within snap threshold.
- Preset dots remain clickable/tappable for exact jump.
- Floating label always uses one decimal.
- Slider clamps to min/max and never emits out-of-range ratios.
- Pinch or external session zoom updates still sync the thumb when not dragging.
- Tests cover math and callback semantics without requiring a real device.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.FocalLengthSliderMathTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
```

## Risks And Notes

- If pure View touch tests are cumbersome in this project, test the extracted math/helper thoroughly and add a light View callback smoke.
- Avoid high-frequency dispatch policy here unless it is purely widget-local and proven by tests; package 04 owns integration coalescing if needed.

## File Ownership

- `02-slider-widget-productization` owns:
  - `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
  - `app/src/main/java/com/opencamera/app/FocalLengthSliderMath.kt` if introduced
  - `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt`
  - `app/src/test/java/com/opencamera/app/FocalLengthSliderMathTest.kt` if introduced
- Other packages must not edit these files without coordination.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderMath.kt`
- `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt`
- `app/src/test/java/com/opencamera/app/FocalLengthSliderMathTest.kt`

## Forbidden Paths

- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/camera/**`

## Dependencies

- Depends on: none

## Parallel Safety

- safe with `01-product-contract-capability-boundary`
- Reason: package 02 owns widget implementation and tests; package 01 owns render/capability contract.

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
