# Package Status: 00-mode-order-regression

- **Agent**: claude-code (background)
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree

- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/ui-v2-00-mode-order
- Branch: worktree-ui-v2-00-mode-order

## Changes (committed)

- git status: clean working tree
- git diff --stat (commit a53aac4):
  ```
  app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt    | 4 ++++
  app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt | 8 ++++----
  2 files changed, 12 insertions(+), 8 deletions(-)
  ```
- Changed files (full list):
  1. `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  2. `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`

## What Changed

**Production** (`SessionCockpitRenderModel.kt` line 428):
- `PRODUCT_MODE_ENTRY_ORDER` now includes `ModeId.HUMANISTIC` at position 2.
- Order: `PHOTO, HUMANISTIC, NIGHT, PORTRAIT, PRO, VIDEO, DOCUMENT`.

**Tests** (`SessionCockpitRenderModelTest.kt`):
- Directory test (line 213): Renamed to `includes humanistic entry and uses product order`, updated expected display names and modeId list to include HUMANISTIC.
- Track test (line 323): Renamed to `includes humanistic entry and uses product order`, updated expected size from 6 to 7, updated expected modeId list to include HUMANISTIC, changed assertion to verify HUMANISTIC is present and active.

## Verification

- `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest`: **BUILD SUCCESSFUL** (29s, 67 tasks)
- `rtk ./gradlew --no-daemon :app:assembleDebug`: **BUILD SUCCESSFUL** (1m, 81 tasks)
- Test result: **0 failures** (all tests in SessionCockpitRenderModelTest and CameraCockpitRenderModelTest pass)

## Commit

- Hash: `a53aac4`
- Message: `fix: 恢复 Humanistic 模式到产品模式目录和轨道顺序`

## Unresolved Risks

None. Package scope was limited to app-layer render model and tests. `core/mode/**` was not edited.

## Self-Certification

- [x] Only touched allowed paths: `SessionCockpitRenderModel.kt`, `SessionCockpitRenderModelTest.kt`
- [x] Did not edit `INDEX.md` or other package status files
- [x] Did not edit `core/mode/**` or `core/session/**`
- [x] No force-push, hard reset, or destructive git operations
- [x] No network access or external API calls
- [x] All tests pass, build succeeds
