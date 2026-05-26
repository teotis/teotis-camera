# 00-mode-entry-visibility — Evidence Pack

- **Status**: completed

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/pkg-00-mode-order`
- Branch: `worktree-pkg-00-mode-order`

## Git Status

```
On branch worktree-pkg-00-mode-order
nothing to commit, working tree clean (aside from untracked docs/plans/)
```

## Git Diff --stat (latest commit, against its parent)

```
 app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt   | 57 ++++++++++------------
 app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt |  3 ++
 app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt |  8 +--
 3 files changed, 32 insertions(+), 36 deletions(-)
```

## Changed Files (full list)

1. `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
2. `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
3. `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

## Root Cause Analysis

Two independent bugs prevented Humanistic, Night, Portrait, and Pro from appearing in the mode track and mode directory:

### Bug 1: `PRODUCT_MODE_ENTRY_ORDER` incomplete (SessionCockpitRenderModel.kt:428)

`PRODUCT_MODE_ENTRY_ORDER` only listed `PHOTO, HUMANISTIC, VIDEO, DOCUMENT` (4 entries). The previous fix (`a4ed2df`) only added HUMANISTIC but missed NIGHT, PORTRAIT, and PRO. The private `visibleModeEntryOrder()` function filtered this list against `state.availableModes`, so even when all 7 modes were available, only 4 appeared in both `modeDirectoryRenderModel()` and `modeTrackRenderModel()`.

### Bug 2: `CockpitSurfaceRenderer.renderModeTrack` hardcoded 3-button list (CockpitSurfaceRenderer.kt:178-209)

The renderer used an index-based `buttons` list containing only `photo, video, document`. It explicitly set `night.visibility = GONE`, `portrait.visibility = GONE`, `pro.visibility = GONE`, and `humanistic.visibility = GONE` on every render call. Even if the render model contained all 7 modes, only the first 3 would be bound to views.

## Fix Details

### SessionCockpitRenderModel.kt

Added NIGHT, PORTRAIT, PRO to `PRODUCT_MODE_ENTRY_ORDER` in the correct product display order:

```kotlin
private val PRODUCT_MODE_ENTRY_ORDER = listOf(
    ModeId.PHOTO,
    ModeId.HUMANISTIC,
    ModeId.NIGHT,
    ModeId.PORTRAIT,
    ModeId.PRO,
    ModeId.VIDEO,
    ModeId.DOCUMENT
)
```

### CockpitSurfaceRenderer.kt

Replaced the index-based 3-button list with a `ModeId`-keyed `buttonMap` covering all 7 mode buttons. The map dynamically shows/hides buttons based on the render model items. The auto-scroll logic was also updated to use the `buttonMap` directly.

### SessionCockpitRenderModelTest.kt

Updated two tests that previously asserted the buggy behavior (4 modes in product order) to now assert correct behavior (all 7 modes present in product order when available):
- `mode directory render model includes humanistic entry and uses product order`
- `mode track render model includes humanistic entry and uses product order`

## Commands Run

| Command | Result |
|---|---|
| `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest` | BUILD SUCCESSFUL |
| `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest --tests com.opencamera.core.mode.ModeProductDeclarationTest` | BUILD SUCCESSFUL |
| `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug` | BUILD SUCCESSFUL |

## Test Results Summary

- **SessionCockpitRenderModelTest**: all tests passed
- **ModeCatalogContractsTest**: passed
- **ModeProductDeclarationTest**: passed
- **assembleDebug**: successful

## Commit

- Hash: `743962ad5226975793128107097d800eb96fb394`
- Message: `fix: 恢复所有 7 个模式在底部模式栏和模式目录中的可见性`

## Acceptance Criteria Status

| Criterion | Status | Evidence |
|---|---|---|
| Humanistic and Portrait appear in `modeDirectoryRenderModel(...)` when `state.availableModes` includes them | PASS | Test `mode directory render model includes humanistic entry and uses product order` verifies 7 items in product order |
| Humanistic and Portrait appear in `modeTrackRenderModel(...)` when `state.availableModes` includes them and are bound to visible/tappable views | PASS | Test `mode track render model includes humanistic entry and uses product order` verifies 7 items; `CockpitSurfaceRenderer.renderModeTrack` now maps all 7 modes to their views via `buttonMap` |
| Selecting Humanistic or Portrait dispatches `SessionIntent.SwitchMode(...)` through existing session ownership | PASS | No change to mode switching logic; existing `handleSwitchMode` already handles all ModeIds |
| Product order is deterministic and matches the accepted UI order | PASS | `PRODUCT_MODE_ENTRY_ORDER` defines PHOTO, HUMANISTIC, NIGHT, PORTRAIT, PRO, VIDEO, DOCUMENT |
| Unsupported hardware conditions do not silently remove a degraded-but-product-visible mode | PASS | `isSupported()` in both plugins only checks `supportsStillCapture`; depth effect is a runtime fallback, not a visibility gate |
| Existing Photo/Video/Document entries remain visible | PASS | All existing tests that use default available modes continue to pass |

## Unresolved Risks

- **Real-device visibility smoke**: No emulator/device access available. Mode track rendering should be visually verified on a real device to confirm all 7 buttons render correctly with proper scrolling.

## Self-Certification

I only touched allowed paths:
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` (allowed)
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` (allowed)
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` (allowed)

No forbidden paths were edited. No INDEX.md or other package status files were modified.
