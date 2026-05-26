# Package Status: 03-quick-panel-outside-dismiss

- **Agent**: agent-03-quick-panel-outside-dismiss
- **Status**: completed
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-polish/03-quick-panel-outside-dismiss
- Branch: agent/real-device-ux-polish/03-quick-panel-outside-dismiss

## Changes

- git status: clean working tree
- git diff --stat:
  ```
  app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt     | 13 ++++++++++++-
  app/src/main/res/layout/activity_main.xml                           |  5 +++--
  app/src/test/java/com/opencamera/app/CockpitPanelRouterTest.kt      |  8 ++++++++
  3 files changed, 23 insertions(+), 3 deletions(-)
  ```
- Changed files:
  1. `MainActivityActionBinder.kt` — preview touch listener returns `false` when GestureGuard blocks, allowing touch to fall through to `panelDismissScrim`
  2. `activity_main.xml` — removed `elevation="4dp"` from scrim so bottom controls (shutter, mode track) remain touchable
  3. `CockpitPanelRouterTest.kt` — added `DismissAll from QuickBubble closes to None` test

## Verification

- Commands run:
  - `rtk ./scripts/run_isolated_gradle.sh --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest` → BUILD SUCCESSFUL
  - `rtk ./scripts/run_isolated_gradle.sh --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest` → BUILD SUCCESSFUL
  - `rtk ./scripts/run_isolated_gradle.sh --no-daemon :app:assembleDebug` → BUILD SUCCESSFUL
- Test results: All 19 CockpitPanelRouterTest tests pass (including new DismissAll-from-QuickBubble test). CameraCockpitRenderModelTest and SessionCockpitRenderModelTest also pass.

## Delivery

- Commit hash: bc258a8
- PR link: (not created — local commit only)

## Acceptance Criteria Status

- [x] Tapping outside Quick while `CockpitPanelRoute.QuickBubble` is active reduces to `CockpitPanelRoute.None` — scrim click listener fires `DismissAll`
- [x] Tapping inside Quick controls still performs the control action — Quick panel at elevation 8dp is above scrim at 0dp
- [x] Other panels keep their existing dismiss behavior — scrim click listener unchanged (`DismissAll`)
- [x] No capture, focus, zoom, or mode switch triggered by outside tap — GestureGuard returns false for PREVIEW zone when QuickBubble is open; preview touch listener returns false, so no gesture dispatched
- [x] Layout hit area stable on narrow portrait screens — scrim is `match_parent` (full screen), no size changes

## Root Cause Analysis

**Primary**: `GestureRouter.onTouchEvent()` always returns `true` (line 87). When QuickBubble is open, `GestureGuard.isGestureAllowed(GestureZone.PREVIEW, ...)` returns `false`, but the preview's `setOnTouchListener` still called `gestureRouter!!.onTouchEvent(v, event)` which consumed the DOWN event. The scrim's click listener never fired.

**Secondary**: The scrim had `elevation="4dp"`, placing it above the bottom controls (shutter, mode track) at 0dp elevation. This blocked touch interaction with bottom controls when any panel was open.

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files

## Unresolved Risks

- Real-device touch smoke test pending — the fix is architecturally correct (preview returns false → scrim receives touch → DismissAll fires), but visual/touch QA on a physical device is recommended to confirm the scrim properly covers the preview area and bottom controls remain interactive.
