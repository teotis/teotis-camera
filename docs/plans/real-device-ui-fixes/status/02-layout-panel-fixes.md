# Package Status: 02-layout-panel-fixes

- **Agent**: claude-code-bg
- **Status**: completed
- **Started**: 2026-05-26
- **Completed**: 2026-05-26

## Worktree
- Path: /Volumes/Extreme_SSD/project/open_camera/.claude/worktrees/layout-panel-fixes
- Branch: worktree-layout-panel-fixes

## Changes
- git status: clean
- git diff --stat: 4 files changed, 9 insertions(+), 6 deletions(-)
- Changed files:
  - `app/src/main/res/layout/activity_main.xml` — guideline 0.6→0.7, preview -20dp margin
  - `app/src/main/res/values/themes.xml` — RightRailDevButton: parent→Button, textColor→oc_text_primary, removed bold/strokeColor
  - `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt` — entry alpha 0.78→0.86
  - `app/src/main/java/com/opencamera/app/MainActivity.kt` — renderAfterPanelChange() 调用 renderLatestSettingsSurfaces()

## Verification
- Commands run:
  - `OPENCAMERA_BUILD_ROOT=... ./gradlew --no-daemon :app:assembleDebug` → BUILD SUCCESSFUL (35s)
  - `OPENCAMERA_BUILD_ROOT=... ./gradlew --no-daemon :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest` → BUILD SUCCESSFUL (32s)
- Test results: 全部通过，无失败

## Delivery
- Commit hash: 6096ab1
- PR link: (worktree branch, not yet merged)

## Issue Fixes

### #4: secondaryPanelBottomGuide 0.6 → 0.7
- 修改 `activity_main.xml` L58: `constraintGuide_percent="0.7"`
- 影响：所有二级面板底部边界下移至屏幕 70%

### #5: 预览画面上移
- 给 `cameraPreview` 和 `previewOverlay` 添加 `layout_marginTop="-20dp"`
- 模式名进入黑色背景区域，提升可读性

### #6: 开发按钮样式对齐
- `themes.xml`: `RightRailDevButton` parent 从 `OutlinedButton` 改为 `Button`（filled），textColor 从 `oc_accent` 改为 `oc_text_primary`，移除 `textStyle=bold` 和 `strokeColor`
- `DevConsoleRenderer.kt`: 未选中 alpha 从 0.78f 改为 0.86f（与其他右侧栏按钮一致）
- 效果：开发按钮现在与其他 RightRailButton 视觉风格一致

### #16: 设置标签切换后内容不更新
- 根因：`renderAfterPanelChange()` 不调用 `renderPage()`/`renderTabs()`，导致 section visibility 不切换
- 修复：在 `renderAfterPanelChange()` 末尾，当 `activePanelRoute.isSettingsOpen` 时调用 `renderLatestSettingsSurfaces()`
- 效果：标签切换后立即触发完整 render pipeline（renderPage + renderTabs）

## Self-Certification
- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit INDEX.md or other status files

## Unresolved Risks
- Issue #5 的 -20dp 偏移值需真机验证，可能需要调整为 -16dp 或 -24dp
- Issue #6 的 `backgroundTint` 保留了 `oc_surface_scrim_light`（与 RightRailButton 的 `oc_surface_scrim` 不同），作为开发按钮的轻微视觉区分
