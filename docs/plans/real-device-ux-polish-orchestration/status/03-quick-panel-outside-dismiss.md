# Package Status: 03-quick-panel-outside-dismiss

- **Agent**: Claude Code
- **Status**: DONE
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-polish/03-quick-panel-outside-dismiss`
- Branch: `agent/real-device-ux-polish/03-quick-panel-outside-dismiss`

## Changes

- git status: clean (3 commits ahead of main)
- git diff --stat (app only):
  ```
  app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt     | 13 ++++-
  app/src/main/res/layout/activity_main.xml                            |  4 +-
  app/src/test/java/com/opencamera/app/CockpitPanelRouterTest.kt       | 84 ++++++++++++
  3 files changed, 99 insertions(+), 2 deletions(-)
  ```
- Changed files:
  1. `MainActivityActionBinder.kt` — preview touch listener returns `false` when GestureGuard blocks preview gestures (panel open), letting the touch fall through to `panelDismissScrim`
  2. `activity_main.xml` — restored `elevation="4dp"` on `panelDismissScrim` so bottom controls (shutter, mode track) at 0dp cannot be accidentally tapped while a panel is open
  3. `CockpitPanelRouterTest.kt` — added 8 tests covering DismissAll from every panel type and the full scrim dismiss path

## Root Cause Analysis

**Primary issue**: `GestureRouter.onTouchEvent()` unconditionally returns `true` (line 87 of `GestureRouter.kt`). The preview's `setOnTouchListener` called `gestureRouter!!.onTouchEvent(v, event)` for every touch, consuming the ACTION_DOWN event. The scrim's `OnClickListener` never fired because the preview intercepted the touch first.

**Fix (part 1)**: `MainActivityActionBinder.kt:408-411` — when `GestureGuard.isGestureAllowed(GestureZone.PREVIEW, ...)` returns `false` (which happens when QuickBubble, Settings, StyleLab, ColorLab, or DevConsole is open), the preview's touch listener returns `false` instead of routing to the GestureRouter. This lets the touch propagate to the scrim.

**Fix (part 2)**: Restored `elevation="4dp"` on `panelDismissScrim`. A previous commit had removed this elevation, but without it the scrim sits at 0dp — same as the `bottomSheet` (shutter button, mode track). Since `bottomSheet` is declared later in XML (higher child index), it is checked first in touch dispatch and its buttons consume the touch, violating acceptance criterion "No capture triggered by outside tap."

**Why the scrim receives the touch**: In ConstraintLayout's ViewGroup touch dispatch, children are iterated in reverse XML index order. The scrim (XML index 2) is checked after `quickBubblePanel` (XML index 9). For taps outside the quickBubblePanel bounds, the panel returns false; the scrim — full-screen, clickable, at 4dp elevation — receives the touch and fires its `OnClickListener` → `DismissAll`.

## Verification

- Commands run:
  - `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest` → BUILD SUCCESSFUL
  - `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug` → BUILD SUCCESSFUL
- Test results:
  - **CockpitPanelRouterTest**: 29/29 passed (21 existing + 8 new)
  - **CameraCockpitRenderModelTest**: passed
  - **SessionCockpitRenderModelTest**: passed
  - **assembleDebug**: successful

## Delivery

- Commit hashes: `bc258a8`, `86f74f1`, `54d8eb3`
- PR link: pending (will be merged via integration branch)

## Acceptance Criteria Status

| Criterion | Status | Evidence |
|---|---|---|
| Tapping outside Quick while QuickBubble is active reduces to None | PASS | Scrim OnClickListener fires `DismissAll` → `nextState()` returns `CockpitPanelRoute.None`. Scrim is full-screen at 4dp elevation. Preview touch listener returns false when panel is open (lines 408-411). Test `DismissAll from QuickBubble closes to None` and `scrim dismiss path ToggleQuickBubble then DismissAll returns to None`. |
| Tapping inside Quick controls performs action and does not close | PASS | `quickBubblePanel` at 8dp elevation is above `panelDismissScrim` at 4dp. Touch dispatch delivers to the panel first; child controls consume the touch. |
| Other panels keep existing dismiss behavior | PASS | `DismissAll` resets to `CockpitPanelUiState()` default for all panel types. Tests verify StyleLab, ColorLab, DevConsole, DocumentBatchOrganizer, and Settings all close correctly. |
| No capture/focus/zoom/mode switch triggered by outside tap | PASS | When QuickBubble is open, `GestureGuard.isGestureAllowed(PREVIEW, ...)` returns `false`. Preview touch listener returns `false` (not routing to GestureRouter), so no session intents dispatched. Scrim at 4dp elevation blocks bottomSheet touches. |
| Layout hit area stable on narrow portrait screens | PASS | Scrim uses `0dp` constraints to all four parent edges (match_constraint), adapts to any screen size. quickBubblePanel uses 260dp width with marginEnd=74dp. |

## Unresolved Risks

- **Real-device visual/touch QA**: The mechanism was verified through code analysis and unit tests. A real-device tap smoke test should confirm the scrim intercepts touches correctly on physical hardware. No emulator/device access was available.
- **GestureRouter unconditional return true**: `GestureRouter.onTouchEvent()` returns `true` unconditionally when enabled. The preview's touch listener works around this by returning `false` before reaching the router when a panel is open. If future code changes the preview touch listener without this guard, the scrim would stop receiving touches.

## Self-Certification

- [x] Only touched allowed paths: `MainActivityActionBinder.kt`, `activity_main.xml`, `CockpitPanelRouterTest.kt` (all allowed)
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files
- [x] Did not force-push, hard reset, delete branches/worktrees, use network, or add secrets
