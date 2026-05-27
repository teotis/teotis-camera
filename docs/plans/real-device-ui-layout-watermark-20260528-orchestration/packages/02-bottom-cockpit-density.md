# Package 02 - Bottom Cockpit Density

## Package ID

`02-bottom-cockpit-density`

## Goal

Fix issue 3 from the 2026-05-28 screenshots: the bottom controls area is too large, and the mode strip has too much vertical emptiness above and below the labels. Make the deck feel compact while keeping touch targets usable.

## User Symptom Covered

- The bottom column consumes too much screen height.
- The mode row sits in a tall empty black band; the labels look detached from the zoom slider and shutter deck.

## Branch And Worktree

- Branch: `agent/real-device-ui-layout-watermark-20260528/02-bottom-cockpit-density`
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/real-device-ui-layout-watermark-20260528-02-bottom-cockpit-density`

## Allowed Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/bg_bottom_panel.xml`
- `app/src/main/res/drawable/bg_mode_track_active_chip.xml`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- a narrowly scoped layout/renderer test if one already exists or is added for this fix

## Forbidden Paths

- Do not hide modes to make the row look compact.
- Do not reduce touch targets below practical phone use.
- Do not overlap preview, mode track, zoom slider, thumbnail, shutter, lens switch, or navigation bar.
- Do not change mode plugin ordering semantics except where renderer visibility currently drops supported modes.
- Do not edit another package status file or `INDEX.md`.

## Dependencies

- Depends on `01-preview-frame-containment`.
- Before editing layout constraints, inspect package 01 status and commit. Avoid undoing its preview/overlay containment fix.

## Required Investigation

1. Inspect `activity_main.xml` constraints for `cameraPreview`, `previewOverlay`, `modeTrackScroll`, `bottomSheet`, `focalLengthSlider`, thumbnail, shutter, and lens switch.
2. Inspect `dimens.xml` and `themes.xml` for mode-chip min height, text size, bottom panel padding, and gaps.
3. Make the mode row denser by reducing vertical padding/gaps and aligning it visually with the zoom slider, not by clipping text.
4. Ensure the bottom deck still respects system navigation and does not cause button overlap on narrow/tall phone screens.
5. Keep the top/preview balance compatible with package 01's frame containment.

## Acceptance Criteria

- Bottom deck visual height is reduced by removing unnecessary vertical gaps.
- Mode strip vertical padding is visibly tighter while labels remain readable.
- Photo, Portrait, Video, Document, and Humanistic entries remain accessible when supported; horizontal scroll remains usable.
- Zoom slider, shutter, thumbnail, and lens switch remain stable and do not overlap.
- Preview/overlay bottom constraints still match after the density changes.

## Verification Commands

Run from the assigned worktree:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

If a layout-renderer test exists or is added, run it and record the command.

## Expected Evidence

- Worktree path, branch, base commit, commit hash.
- Changed files list.
- Before/after summary of mode row and bottom deck dimensions/constraints.
- Test output summaries.
- Residual real-device screenshot checks required.

## Unlock Condition

Package `04-real-device-acceptance` may start after this package records completed status.
