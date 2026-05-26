# 00-mode-entry-visibility — Evidence Pack

## Status: DONE

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/pkg-00-mode-entry-visibility`
- Branch: `worktree-pkg-00-mode-entry-visibility`

## Git Status

```
On branch worktree-pkg-00-mode-entry-visibility
nothing to commit, working tree clean
```

## Git Diff --stat (against main)

```
 app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt   | 57 ++++++++++------------
 app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt |  4 ++
 app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt | 15 +++---
 3 files changed, 36 insertions(+), 40 deletions(-)
```

## Changed Files (full list)

1. `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
2. `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
3. `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

## Root Cause Analysis

Two independent bugs prevented Humanistic, Night, Portrait, and Pro from appearing in the mode track and mode directory:

### Bug 1: `PRODUCT_MODE_ENTRY_ORDER` incomplete (SessionCockpitRenderModel.kt:428)

`PRODUCT_MODE_ENTRY_ORDER` only listed `PHOTO, VIDEO, DOCUMENT`. The private `visibleModeEntryOrder()` function filtered this list against `state.availableModes`, so even when all 7 modes were available, only 3 appeared in both `modeDirectoryRenderModel()` and `modeTrackRenderModel()`.

### Bug 2: `CockpitSurfaceRenderer.renderModeTrack` hardcoded 3-button list (CockpitSurfaceRenderer.kt:178-209)

The renderer used an index-based `buttons` list containing only `photo, video, document`. It explicitly set `night.visibility = GONE`, `portrait.visibility = GONE`, `pro.visibility = GONE`, and `humanistic.visibility = GONE` on every render call. Even if the render model contained all 7 modes, only the first 3 would be bound to views.

## Fix Details

### SessionCockpitRenderModel.kt

Added HUMANISTIC, NIGHT, PORTRAIT, PRO to `PRODUCT_MODE_ENTRY_ORDER` in the correct product display order:

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

Updated two tests that previously asserted the buggy behavior (Humanistic excluded from mode directory and mode track) to now assert correct behavior (all 7 modes present in product order when available).

## Commands Run

| Command | Result |
|---|---|
| `rtk ./gradlew :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest` | 36 tests, 0 failures |
| `rtk ./gradlew :core:mode:test --tests com.opencamera.core.mode.ModeCatalogContractsTest --tests com.opencamera.core.mode.ModeProductDeclarationTest` | BUILD SUCCESSFUL |
| `rtk ./gradlew :app:assembleDebug` | BUILD SUCCESSFUL |

## Test Results Summary

- **SessionCockpitRenderModelTest**: 36/36 passed
- **ModeCatalogContractsTest**: passed
- **ModeProductDeclarationTest**: passed
- **assembleDebug**: successful

## Commit

- Hash: `ec28489a44ce17dc4069d9bd5651ce51d1b2e8d9`
- Message: `fix: 恢复 Humanistic/Portrait 模式在底部模式栏和模式目录中的可见性`

## Acceptance Criteria Status

| Criterion | Status | Evidence |
|---|---|---|
| Humanistic and Portrait appear in `modeDirectoryRenderModel(...)` when `state.availableModes` includes them | PASS | Test `mode directory render model includes all available modes in product order` verifies 7 items |
| Humanistic and Portrait appear in `modeTrackRenderModel(...)` when `state.availableModes` includes them and are bound to visible/tappable views | PASS | Test `mode track render model includes all available modes in product order` verifies 7 items; `CockpitSurfaceRenderer.renderModeTrack` now maps all 7 modes to their views |
| Selecting Humanistic or Portrait dispatches `SessionIntent.SwitchMode(...)` through existing session ownership | PASS | No change to mode switching logic; existing `handleSwitchMode` already handles all ModeIds |
| Product order is deterministic and matches the accepted UI order | PASS | `PRODUCT_MODE_ENTRY_ORDER` defines PHOTO, HUMANISTIC, NIGHT, PORTRAIT, PRO, VIDEO, DOCUMENT |
| Unsupported hardware conditions do not silently remove a degraded-but-product-visible mode | PASS | `isSupported()` in both plugins only checks `supportsStillCapture`; depth effect is a runtime fallback, not a visibility gate |
| Existing Photo/Video/Document entries remain visible | PASS | All 3 existing tests that use default available modes continue to pass |

## Unresolved Risks

- **Real-device visibility smoke**: No emulator/device access available. Mode track rendering should be visually verified on a real device to confirm all 7 buttons render correctly with proper scrolling.
- **Night and Pro mode buttons**: These were also hidden before the fix. They are now visible when available. If product intent was to hide them behind a different UX path, this change surfaces them in the mode track.

## Self-Certification

I only touched allowed paths:
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` (allowed)
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` (allowed)
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt` (allowed)

No forbidden paths were edited. No INDEX.md or other package status files were modified.
