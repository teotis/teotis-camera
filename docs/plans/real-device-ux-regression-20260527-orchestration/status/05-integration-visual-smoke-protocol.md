# 05-integration-visual-smoke-protocol Status

**Status**: completed

## Worktree And Branch

- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-regression-20260527/05-integration-visual-smoke-protocol`
- Branch: `agent/real-device-ux-regression-20260527/05-integration-visual-smoke-protocol`
- Base commit: `36abefa`
- Commit hash: `f6651e4` (rebased onto main, no additional implementation commit — verification only package)

## Verification

All three verification commands passed after rebasing the worktree onto main (the original base `36abefa` was behind the `2e6a94e` session fix on main, causing 19 pre-existing `DefaultCameraSessionTest` failures that are not regressions from the merged packages).

### Command 1 — App unit tests (8 test classes)

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.FocalLengthSliderViewTest \
  --tests com.opencamera.app.SessionUiRenderModelTest \
  --tests com.opencamera.app.SessionCockpitRenderModelTest \
  --tests com.opencamera.app.DevLogRenderModelTest \
  --tests com.opencamera.app.DevLogExporterTest \
  --tests com.opencamera.app.CockpitPanelRouterTest \
  --tests com.opencamera.app.DocumentBatchOrganizerRenderModelTest \
  --tests com.opencamera.app.DocumentBatchRailRenderModelTest
```

**Result**: BUILD SUCCESSFUL — all 8 test classes passed.

### Command 2 — Core session test

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:session:test \
  --tests com.opencamera.core.session.DefaultCameraSessionTest
```

**Result**: BUILD SUCCESSFUL — 151 tests completed, 0 failed.

### Command 3 — Assemble

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:assembleDebug
```

**Result**: BUILD SUCCESSFUL. APK at `~/.codex-build/OpenCamera-1ea7e11c/app/outputs/apk/debug/app-debug.apk`.

## Real-Device Smoke Protocol

**Device**: Android device with Camera2 provider (test on the device used for the original 2026-05-27 screenshots)

**APK**: `~/.codex-build/OpenCamera-1ea7e11c/app/outputs/apk/debug/app-debug.apk`

### Smoke Checklist

| # | Area | What to check | Expected result |
|---|---|---|---|
| 1 | Zoom slider | Drag zoom slider across `2x` threshold | Preview/lens switches to 2x lens node; UI shows 2x label |
| 2 | Zoom slider | Drag zoom slider across `5x` threshold | Preview/lens switches to 5x lens node; UI shows 5x label |
| 3 | Zoom slider | Drag back below `2x` from above | Lens switches back to wide; smooth transition |
| 4 | Dev console | Open Dev console, select a log tab, tap `清理` | Clears only the current log category (not a global close) |
| 5 | Quick panel | Check Quick reset button style | Uses `QuickBubbleButton` style matching other Quick panel buttons |
| 6 | Style/Color Lab | Open Style/Color Lab | No bottom `关闭色调` action visible |
| 7 | Mode track | Swipe mode track to find Portrait | Portrait mode appears in the track and can be selected |
| 8 | Right rail | Check `开发` button in the right rail | Uses the same visual style as `风格` and `快捷` buttons |
| 9 | Cockpit density | Observe bottom deck area | Bottom deck is compact; top bar sits on a black/scrimmed background |
| 10 | Document organizer | Open Document organizer panel | Compact layout; one tap on close dismisses the panel |

### Residual Risk

- All checks above are **visual/interactive** and require real-device verification.
- Local unit tests and build passed; this protocol does not replace device-level visual acceptance.
- The zoom lens node switch (items 1-3) should be tested with a device that has multiple lens nodes (e.g., wide + telephoto).

## Risks / Residual Device QA

- Real-device visual acceptance is **pending** — the smoke protocol above must be executed on device.
- The zoom threshold lens switch behavior depends on device hardware supporting multiple lens nodes.
- Cockpit density visual check depends on device screen size and density.
