# Package 05 — Focal Slider Interaction Polish

## Package ID
`05-focal-slider-interaction`

## Problem Statement

Problem 11 is partially implemented, but `FocalLengthSliderView` snaps every release to the nearest preset. This makes continuous dragging transient only; the final value is discrete, which weakens the Apple/vivo-like continuous focal control. The new custom view also lacks focused tests.

## Acceptance Criteria

- [ ] Dragging reports continuous `X.Xx` values and leaves the final ratio at the dragged value unless the user explicitly taps a preset dot or releases within a small snap threshold.
- [ ] Preset dots remain clickable/tappable for quick jump.
- [ ] Pinch or external zoom state updates still sync the thumb.
- [ ] Floating label keeps one decimal place.
- [ ] Add focused tests for ratio mapping, one-decimal formatting, snap threshold, continuous release, and preset jump.
- [ ] No unrelated cockpit/render model refactor.

## File Ownership

Allowed paths:
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` zoom slider callback wiring only
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom slider model only
- `app/src/main/res/layout/activity_main.xml` focal slider region only
- `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` only if model expectations need updating

Forbidden paths:
- `PreviewOverlayView.kt`
- `core/effect/`
- `core/session/`
- `app/src/main/java/com/opencamera/app/camera/`

## Dependencies

None.

## Parallel Safety

Safe with package 01.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Expected Evidence Pack

- Interaction decision: continuous release vs snap threshold.
- New test list and results.
- Verification output.
