# 02-cockpit-layout-mode-entry-density

## Goal

Repair the real-device cockpit composition shown in the screenshots: the bottom area should be compact and intentional, the preview should be positioned so top and bottom black/scrim zones feel balanced, and the mode track should expose Portrait mode.

## User Symptoms Covered

- Issue 6: current mode switch bar should expose Portrait.
- Issue 8: bottom area is too tall and empty; preview should move down enough to make bottom compact while the top bar has black/scrimmed background.

## Allowed Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/*`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CockpitSurfaceRendererTest.kt` if present or newly needed

## Forbidden Paths

- Do not change mode plugin business behavior beyond making an already-supported Portrait entry visible.
- Do not hide Humanistic, Document, Photo, or Video to make room for Portrait.
- Do not solve density by overlapping text/buttons with preview or navigation bars.
- Do not edit coordinator files except `status/02-cockpit-layout-mode-entry-density.md` and the matching `state.tsv` row.

## Required Investigation

1. Verify whether `ModeTrackRenderModel` already contains Portrait and whether `CockpitSurfaceRenderer` drops it.
2. Inspect current constraints for `cameraPreview`, `previewOverlay`, `topPanel`, `modeTrackScroll`, `bottomSheet`, and `focalLengthSlider`.
3. Decide a compact layout that:
   - keeps mode track readable;
   - keeps zoom slider and shutter reachable;
   - reduces empty vertical black area under the preview;
   - gives the top bar a black/scrimmed background instead of floating only over preview;
   - does not cover system navigation.

## Acceptance Criteria

- Portrait is visible in the bottom mode track when supported by the mode catalog.
- Mode track remains horizontally scrollable and auto-scrolls to the active mode.
- Bottom deck visual height is reduced by removing unnecessary vertical gaps, not by clipping controls.
- Preview/top/bottom constraints match the screenshot need: top bar reads against black/scrim and bottom controls feel compact.
- Layout remains stable on narrow phone widths and does not overlap top actions, right rail, mode track, slider, shutter, thumbnail, or lens button.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Before/after explanation of constraints and dimensions.
- Test output summaries.
- Residual real-device screenshot checks required.

## Unlock Condition

Package 04 may start after this package records completed status or records a branch/base that package 04 can build on.
