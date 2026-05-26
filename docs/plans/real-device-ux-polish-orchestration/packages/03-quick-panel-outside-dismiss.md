# 03 Quick Panel Outside Dismiss

## Package ID

`03-quick-panel-outside-dismiss`

## Goal

Make the expanded Quick panel close when the user taps unrelated preview/cockpit/lower-screen areas, while preserving taps inside the Quick panel controls.

## Problem Statement

Quick already has `CockpitPanelRoute.QuickBubble`, a launcher, and `panelDismissScrim`. Real-device testing reports that after “快捷” expands, tapping the lower half or unrelated areas does not auto-dismiss. The likely issue is scrim size, z-order, touch pass-through, or routing of preview/cockpit clicks while Quick is open.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/MainActivityRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/res/layout/activity_main.xml`
- Quick/cockpit render tests in `app/src/test/java/com/opencamera/app/**`
- package status file `status/03-quick-panel-outside-dismiss.md`

## Forbidden Paths

- Quick content semantics owned by existing UI Animation V2 package unless required only to test dismiss
- core session/device code
- style/settings/dev log package files
- any other package status file

## Dependencies

Package 00 should be complete or clearly not blocking app UI tests.

## Parallel Safety

Can run with package 05. Coordinate with package 04 before package 04 starts.

## Implementation Notes

- Prefer a single outside-dismiss owner through `CockpitPanelRouter` and `panelDismissScrim`.
- Ensure the scrim covers all unrelated regions when Quick is open but does not block Quick child controls.
- If preview gestures are intentionally disabled while Quick is open through `GestureGuard`, outside tap should still dismiss instead of doing nothing.
- Avoid special-casing only the lower half; the rule should be “outside active panel closes overlay”.

## Acceptance Criteria

- Tapping outside Quick while `CockpitPanelRoute.QuickBubble` is active reduces to `CockpitPanelRoute.None`.
- Tapping inside Quick controls still performs the control action and does not close unless that is the intended control behavior.
- Other panels keep their existing dismiss behavior.
- No capture, focus, zoom, or mode switch is triggered by the same outside tap used to dismiss Quick.
- Layout hit area is stable on narrow portrait screens.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

In an isolated worktree, replace `rtk ./gradlew` with `rtk ./scripts/run_isolated_gradle.sh`.

## Expected Evidence Pack

- Explanation of hit target / z-order fix.
- Focused test summaries.
- Real-device or emulator tap smoke evidence if available; otherwise mark as pending visual/touch QA.
