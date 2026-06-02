# 05-style-settings-i18n-cleanup Status

## State

`completed`

- State: completed
- Worktree: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/latest-real-device-vivo-feedback/05-style-settings-i18n-cleanup`
- Branch: `agent/latest-real-device-vivo-feedback/05-style-settings-i18n-cleanup`
- Base commit: a72d3de3
- Commit: fc2ab6a1516c79330b0c8a10441a40a09ac1dc5b

## Evidence

- Changed files: FilterLabFamily/FilterAdvancedControl/FilterAdjustmentLevel enum label 移除; AppTextResolver 新增语言/adjustment i18n 方法; SessionUiRenderModel 新增 languageControl; SettingsPanelRenderer/MainActivityViews/MainActivityActionBinder 语言按钮渲染和点击; PersistedSettingsSerializer appLanguage 序列化; activity_main.xml 语言按钮布局; strings.xml values/values-en 语言和 adjustment 字符串资源; 测试断言修复
- Verification: rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionCockpitRenderModelTest: 217 tests passed; rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:settings:test: BUILD SUCCESSFUL; rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug: BUILD SUCCESSFUL
- Acceptance notes: Style 面板英文已移除，Settings > Common 暴露语言切换，语言选择通过序列化持久化，UI 提示重启生效

## Risks / Blockers

- 语言切换需要重启才能生效（非即时），已在按钮 supportLabel 中提示用户
