# 01-cockpit-bottom-layout Status

## State

`completed`

- State: completed
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/latest-real-device-vivo-feedback/01-cockpit-bottom-layout`
- Branch: `agent/latest-real-device-vivo-feedback/01-cockpit-bottom-layout`
- Base commit: `579d2700` (initial branch head)
- Commit: `55230ce35cd540eb5bc1ec0ec4b81f16efbe0f67`

## Evidence

### Changed Files

1. `app/src/main/res/values/dimens.xml` — 新增 `bottom_cockpit_margin_bottom=8dp` 和 `cockpit_control_row_margin_top=8dp`
2. `app/src/main/res/layout/activity_main.xml` — 3 处间距调整：
   - `modeTrackScroll` paddingVertical `space_1` → `space_4`（1dp→4dp）
   - `bottomSheet` 新增 `layout_marginBottom="@dimen/bottom_cockpit_margin_bottom"`（8dp）
   - 主控件行 `layout_marginTop` `space_1` → `cockpit_control_row_margin_top`（1dp→8dp）
3. `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt` — 新增 4 个布局间距合约测试

### Layout Rationale

原始布局中 bottomSheet 紧贴屏幕底部（无 marginBottom），modeTrack 仅有 1dp 垂直内边距，
主控件行仅 1dp 顶部间距。这导致缩略图和快门按钮在视觉上锚定在屏幕物理底边，模式轨道区域
上下空间紧凑。

修正方案在三个垂直层级重新分配间距：
- bottomSheet 上移 8dp → 为系统导航手势区域留出安全距离
- modeTrack paddingVertical 增至 4dp → 模式标签不再紧贴上下控件
- 主控件行 marginTop 增至 8dp → 缩略图/快门与模式轨道之间有清晰视觉分隔

该修正保留了 ConstraintLayout 的完整约束链（preview→modeTrack→bottomSheet），
不影响面板（Settings/Filter/Dev）的定位。

### Verification Results

```
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest \
  --tests com.opencamera.app.SessionCockpitRenderModelTest \
  --tests com.opencamera.app.CameraCockpitRenderModelTest \
  --tests com.opencamera.app.SessionUiRenderModelTest
```
- 237 tests completed, 235 passed
- 2 pre-existing failures (SessionCockpitRenderModelTest: mode directory humanistic tests — unrelated to cockpit layout)
- 4 new cockpit bottom layout spacing tests: all passed

```
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```
- BUILD SUCCESSFUL

## Remaining Real-Device Checklist

- [ ] 在实际设备（尤其是 vivo 手机）上确认底部控件不再紧贴屏幕底边
- [ ] 在短屏设备上验证模式轨道和缩略图/快门不重叠
- [ ] 确认导航手势区域不会意外触发快门按钮

## Risks / Blockers

- 无阻塞问题
- 8dp marginBottom 在使用全面屏手势导航的设备上提供足够安全距离，但在带有实体导航栏的设备上可能需要进一步调整
- 两个预存在的 mode directory 测试失败与本次改动无关
