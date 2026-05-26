# 00 Mode Entry Visibility

## Package ID

`00-mode-entry-visibility`

## Goal

Restore visible `Humanistic` and `Portrait` mode entrances in the bottom mode bar and mode directory when the mode catalog and device capability chain say they are available. This is the highest-priority package because hidden modes make shipped functionality unreachable.

## Problem Statement

Real-device testing reports that “人文” and “人像” do not display. The likely break is not a missing enum, because `ModeId.HUMANISTIC` and `ModeId.PORTRAIT` exist. The fix must trace the full chain:

- mode plugin registration
- `ModeRegistry.availableModes` and `supportedModes(...)`
- `SessionState.availableModes`
- app render model filtering/order
- layout/view binding for the bottom mode track
- tests that assert product order and visibility

## Allowed Paths

- `core/mode/**`
- `feature/mode-humanistic/**`
- `feature/mode-portrait/**`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/opencamera/app/i18n/**`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `core/mode/src/test/**`

## Forbidden Paths

- `core/effect/**`
- preview color transform conflict files
- settings/style/quick/dev log package files unless the mode entry fix cannot be verified otherwise
- any package status file except `status/00-mode-entry-visibility.md`

## Dependencies

None.

## Parallel Safety

Run first, alone. Later UI packages depend on app focused tests not being blocked by mode entry regressions.

## Implementation Notes

- Prefer fixing the existing availability/filtering chain over adding a second UI-only mode list.
- Preserve supported/degraded/unsupported semantics: if `Portrait` is degraded because depth is unavailable, the mode entrance should still be visible when still capture is supported and the product contract says degraded is acceptable.
- Do not reintroduce hidden mode kernels in UI/coordinator code.
- Check whether existing `ui-animation-v2-fix-orchestration/packages/00-mode-order-regression.md` has already been applied in the target branch; avoid duplicating or reverting it.

## Acceptance Criteria

- Humanistic and Portrait appear in `modeDirectoryRenderModel(...)` when `state.availableModes` includes them.
- Humanistic and Portrait appear in `modeTrackRenderModel(...)` when `state.availableModes` includes them and are bound to visible/tappable views.
- Selecting Humanistic or Portrait dispatches `SessionIntent.SwitchMode(...)` through existing session ownership, not direct CameraX/device calls.
- Product order is deterministic and matches the accepted UI order.
- Unsupported hardware conditions do not silently remove a degraded-but-product-visible mode unless the core mode plugin explicitly reports unsupported.
- Existing Photo/Video/Document entries remain visible.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest --tests com.opencamera.core.mode.ModeProductDeclarationTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

In an isolated worktree, use:

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest
```

## Expected Evidence Pack

- File references proving where visibility was broken.
- Test output summary for the focused core/app commands.
- Screenshot or UI-tree note if the agent has emulator/device access; otherwise mark real-device visibility smoke as pending.
- Self-certification that only allowed paths were touched.
