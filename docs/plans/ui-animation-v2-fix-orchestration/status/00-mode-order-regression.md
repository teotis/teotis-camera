# Package Status: 00-mode-order-regression

- **Agent**: claude-code (background)
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/pkg-00-mode-order
- Branch: worktree-pkg-00-mode-order

## Changes

- git status: clean (all committed)
- git diff --stat (2 commits, 2 files):
  ```
  app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt  | 1 +
  app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt | 34 ++++++++++++----------
  2 files changed, 19 insertions(+), 16 deletions(-)
  ```
- Changed files (full list):
  1. `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  2. `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

### What changed

**Production**: Added `ModeId.HUMANISTIC` to `PRODUCT_MODE_ENTRY_ORDER` between PHOTO and VIDEO, so both `modeDirectoryRenderModel()` and `modeTrackRenderModel()` now include Humanistic in the correct product position: `PHOTO, HUMANISTIC, VIDEO, DOCUMENT`.

**Tests** (4 tests updated):
- `mode directory render model includes humanistic entry and uses product order` — expects 4 items: PHOTO, HUMANISTIC, VIDEO, DOCUMENT
- `mode track render model includes humanistic entry and uses product order` — expects 4 items with HUMANISTIC as active
- `mode directory render model degrades features with capability fallback` — uses product-ordered modes (PHOTO, HUMANISTIC, VIDEO, DOCUMENT) instead of NIGHT/PORTRAIT
- `active mode track item has distinct visual state` — uses HUMANISTIC as active mode instead of NIGHT

## Verification

- Commands run:
  1. `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest`
  2. `rtk ./gradlew --no-daemon :app:assembleDebug`
- Test results: **66 tests completed, 0 failed** (BUILD SUCCESSFUL)
- assembleDebug: **BUILD SUCCESSFUL**

## Delivery

- Commit hashes: `a4ed2df`, `e3407e9`
- PR link: (local only, not pushed)

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files
- [x] Used `rtk` for shell commands
- [x] Used isolated Gradle build wrapper if running from a worktree

## Unresolved Risks

- None. This package is a prerequisite; dependent packages can now proceed.
