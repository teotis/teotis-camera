# Package 01 - Zoom Slider Render Latch

## Package ID

`01-zoom-slider-render-latch`

## Goal

Make the current `FocalLengthSliderView` usable on real device fast drags: preset dots must have persistent node labels, and active drag must not be overwritten by stale `model.currentRatio` render echoes.

## Current Evidence

- `FocalLengthSliderView.kt` exists and already supports:
  - one-decimal floating label;
  - continuous move callback;
  - tap near preset dot;
  - snap threshold on release;
  - `isInteractive`.
- It still draws only circles for preset dots; no persistent dot labels.
- `setCurrentRatio(ratio)` still changes `currentRatio` while `isDragging`, then skips only `invalidate()`. This is unsafe because `ACTION_UP` uses `currentRatio` to decide final release/snap.
- `CockpitSurfaceRenderer.renderFocalLengthSlider()` always calls `slider.setCurrentRatio(model.currentRatio)`.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` focal slider method only
- `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` zoom/focal sections only if needed

## Forbidden Paths

- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/camera/**`
- quick panel / brightness code
- `docs/plans/zoom-brightness-rollback-implementation-orchestration/INDEX.md`
- another package's status file

## Dependencies

- Depends on: none

## Implementation Requirements

1. Add persistent node labels for every preset dot in `FocalLengthSliderView`.
   - Labels should use compact zoom text: `1x`, `2`, `5`, `0.6`, not always `1.0x`.
   - Labels must not overlap the floating drag label.
   - Active/current preset label may be highlighted, but continuous in-between value must still be shown by the floating label.
2. Add active-drag render echo suppression.
   - While `isDragging == true`, external `setCurrentRatio(...)` must not mutate the internal drag value.
   - After drag ends, the next render may reconcile to session state.
   - Keep `setPresetRatios(...)` safe during drag; do not crash if capabilities refresh.
3. Keep UI-local drag state ephemeral.
   - Do not make `FocalLengthSliderView` own committed camera state.
   - It emits callbacks only; Session Kernel remains committed runtime owner.
4. Update tests.
   - Cover compact node label formatting helper if introduced.
   - Cover `setCurrentRatio` suppression during active drag with a testable helper or Robolectric-free pure hook.
   - Preserve existing snap threshold tests.

## Acceptance Criteria

- Preset dot labels are visible and formatted compactly.
- Drag release final value cannot be overwritten by an external render echo that arrives during active drag.
- Exact dot tap still snaps to preset.
- Continuous release still remains continuous unless within snap threshold.
- Existing `FocalLengthSliderViewTest` passes, with new tests for labels/latch.

## Verification Commands

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FocalLengthSliderViewTest --tests com.opencamera.app.SessionCockpitRenderModelTest
```

## Expected Evidence

- worktree path
- branch
- base commit
- commit hash
- changed files
- diff stat
- focused test output summary
- any residual real-device visual risk

