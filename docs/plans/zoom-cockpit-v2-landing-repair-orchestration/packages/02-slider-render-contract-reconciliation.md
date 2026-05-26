# Package 02 - Slider Render Contract Reconciliation

## Package ID

`02-slider-render-contract-reconciliation`

## Goal

Align app-side render model, slider behavior, and evidence with the final Session recording policy and actual landed files.

## Current Evidence

- App focused zoom/slider/render tests pass.
- `FocalLengthSliderView.kt` has inline math helpers; `FocalLengthSliderMath.kt` is not present on main despite prior status claiming it.
- `SessionCockpitRenderModel` currently leaves focal slider enabled during active recording for all zoom capabilities, which conflicts with the intended "recording restriction" if Session package blocks discrete preset recording zoom.
- `CockpitSurfaceRenderer` gates `slider.isInteractive` from render model and dispatches `ApplyZoomRatio`, which is the correct direction.

## Implementation Scope

- Align render model with package 01 policy:
  - continuous capability may remain interactive during active recording;
  - discrete preset capability must be disabled/read-only during active recording if Session blocks it;
  - requesting/stopping remain disabled.
- Decide and document one implementation truth for slider math:
  - either introduce `FocalLengthSliderMath.kt` plus tests, or update evidence/docs to stop claiming it exists;
  - do not leave status saying a file exists when it does not.
- Keep exact preset callbacks dispatching `ApplyZoomRatio`, never `ZoomRatioToggled`.
- Add/update app focused tests for continuous vs discrete recording states.

## Acceptance Criteria

- UI render model and Session policy agree for active recording.
- `FocalLengthSliderViewTest` reflects the actual implementation shape.
- If a pure math helper is introduced, it is tested and present on main; if not, status/evidence explicitly says math stays inline.
- App focused command passes.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Branch And Worktree Policy

- Branch: `agent/zoom-cockpit-v2-landing-repair/02-slider-render-contract-reconciliation`
- Worktree: `.agent-worktrees/zoom-cockpit-v2-landing-repair/02-slider-render-contract-reconciliation`

## Unlock Conditions

- Initial ready wave, but before final commit re-check package 01's final Session policy if both run in parallel.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderMath.kt` if introduced
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` zoom/focal slider sections only
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` focal slider render method only
- `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt`
- `app/src/test/java/com/opencamera/app/FocalLengthSliderMathTest.kt` if introduced
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` zoom/focal sections only

## Forbidden Paths

- `core/session/**`
- `app/src/main/java/com/opencamera/app/camera/**`
- shutter, quick panel, focus/EV, color lab, and unrelated UI files

## Expected Evidence Pack

- [ ] coordinator status updated
- [ ] `state.tsv` row updated
- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] base commit recorded
- [ ] commit hash recorded
- [ ] changed files listed
- [ ] verification commands/results recorded
- [ ] unresolved risks noted
- [ ] only allowed paths touched

