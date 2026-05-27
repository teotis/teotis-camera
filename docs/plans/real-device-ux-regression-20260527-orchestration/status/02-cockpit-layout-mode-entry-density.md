# 02-cockpit-layout-mode-entry-density Status

**Status**: completed

## Worktree And Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-regression-20260527/02-cockpit-layout-mode-entry-density`
- Branch: `agent/real-device-ux-regression-20260527/02-cockpit-layout-mode-entry-density`
- Base commit: `36abefa`
- Commit hash: `3b8b3e4`

## Changed Files

- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` — Portrait added to buttonMap, removed hardcoded View.GONE
- `app/src/main/res/drawable/bg_top_scrim.xml` — Top scrim gradient from #99000000 to transparent (was fully transparent)
- `app/src/main/res/drawable/bg_bottom_panel.xml` — Bottom panel solid color changed to #CC000000 (was transparent)
- `app/src/main/res/layout/activity_main.xml` — Tightened bottomSheet padding (4dp→2dp top, 12dp→8dp bottom) and control row margin (4dp→2dp)

## Before/After Explanation

**Portrait visibility (Issue 6):**
- Before: `CockpitSurfaceRenderer.renderModeTrack()` hardcoded `modeTrack.portrait.visibility = View.GONE` and excluded PORTRAIT from the `buttonMap`. The render model correctly included Portrait in `PRODUCT_MODE_ENTRY_ORDER`, but the renderer never showed it.
- After: PORTRAIT is included in `buttonMap` alongside PHOTO, HUMANISTIC, VIDEO, and DOCUMENT. The renderer now shows Portrait when the device supports it. Night and Pro remain hidden per scope.

**Cockpit density (Issue 8):**
- Before: `topScrim` was fully transparent (#00000000 to #00000000), serving only as a layout spacer. `bottomSheet` background was transparent. Bottom padding was 4dp top / 12dp bottom.
- After: `topScrim` gradient goes from 60% black (#99000000) to transparent, giving the top bar a visible black/scrimmed background. `bottomSheet` has a solid 80% black background (#CC000000). Bottom padding reduced to 2dp/8dp and control row margin reduced from 4dp to 2dp, making the bottom deck more compact.

## Verification

- `SessionUiRenderModelTest`: BUILD SUCCESSFUL
- `SessionCockpitRenderModelTest`: BUILD SUCCESSFUL
- `assembleDebug`: BUILD SUCCESSFUL

## Evidence

- All three verification commands passed in the assigned worktree
- No forbidden paths were modified
- Mode plugin business behavior unchanged beyond making Portrait visible

## Risks / Residual Device QA

- Real-device screenshot verification needed to confirm:
  - Portrait appears in mode track when device supports it
  - Top scrim gradient reads as black/scrimmed background behind top bar
  - Bottom deck feels compact and intentional
  - Layout stable on narrow phone widths
  - No overlap between top actions, right rail, mode track, slider, shutter, thumbnail, or lens button
