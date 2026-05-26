# Package Status: 00-mode-order-regression

- **Agent**: claude-code (background)
- **Status**: in-progress (interrupted by user)
- **Started**: 2026-05-26
- **Completed**: —

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/ui-v2-00-mode-order
- Branch: worktree-ui-v2-00-mode-order

## Changes (uncommitted)

- git status: 2 modified files, not staged
- git diff --stat:
  ```
  app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt   | 4 ++++
  app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt | 6 +++---
  2 files changed, 7 insertions(+), 3 deletions(-)
  ```
- Changed files (full list):
  1. `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  2. `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

### What changed so far

**Production** (`SessionCockpitRenderModel.kt` line 428):
- Added `ModeId.HUMANISTIC`, `ModeId.NIGHT`, `ModeId.PORTRAIT`, `ModeId.PRO` to `PRODUCT_MODE_ENTRY_ORDER`.
- New order: `PHOTO, HUMANISTIC, NIGHT, PORTRAIT, PRO, VIDEO, DOCUMENT`.

**Tests** (`SessionCockpitRenderModelTest.kt`):
- Updated test name from `hides humanistic` to `includes humanistic` (line 213).
- Updated expected display names to include "Humanistic" at position 2 (line 230).
- Updated expected modeId list to include `ModeId.HUMANISTIC` at position 2 (line 234).

## Verification

- First test run (before test updates): **36 tests completed, 4 failed**
  - `mode directory render model hides humanistic entry` — expected order mismatch
  - `mode directory render model degrades scenery/portrait features` — NoSuchElementException (NIGHT/PORTRAIT not in order)
  - `mode track render model hides humanistic entry` — expected order mismatch
  - `active mode track item has distinct visual state` — NoSuchElementException (NIGHT not in order)
- **Tests have NOT been re-run after the latest changes.** Need to re-verify.

## Unfinished Items

1. **Mode track test not updated**: Test at line 323 (`mode track render model hides humanistic entry and uses product order`) still expects old order without HUMANISTIC. Needs same treatment as the directory test — update name, expected display names, and expected modeId list to include HUMANISTIC.
2. **No commit made**: Changes are uncommitted.
3. **Tests not re-run**: Need to run `bash /Volumes/Extreme_SSD/project/open_camera/scripts/run_isolated_gradle.sh --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest` and confirm 0 failures.
4. **assembleDebug not run**: Need `bash /Volumes/Extreme_SSD/project/open_camera/scripts/run_isolated_gradle.sh --no-daemon :app:assembleDebug`.
5. **local.properties**: Was copied to worktree (`sdk.dir=/Users/dingren/Library/Android/sdk`) for build to work.

## Stop Gate Notes

- `core/mode/**` was NOT edited (only app-layer render model and tests).
- `INDEX.md` and other package status files were NOT edited.
- No network access, no force-push, no destructive git operations.
