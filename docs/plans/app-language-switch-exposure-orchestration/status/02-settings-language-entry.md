# 02-settings-language-entry

## State

`completed`

## Evidence

- **Branch**: `agent/app-language-switch-exposure/02-settings-language-entry`
- **Base commit**: `65367bf9`
- **Commit hash**: `7ca59f3c`
- **Worktree**: `.claude/worktrees/app-language-switch-exposure-02`

### Changed Files

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` — `CommonSettingsSectionRenderModel` 新增 `language` 字段；`sessionSettingsRenderModel()` 和 `sessionSettingsPageRenderModel()` 中构建语言控制
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt` — `renderPage()` 中渲染语言控制按钮
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt` — `SettingsPanelViews` 新增 `language: Button` 字段并在 `bind()` 中绑定
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` — 绑定 `buttonLanguage` 点击事件
- `app/src/main/res/layout/activity_main.xml` — Common section 中 `buttonSelfieMirror` 后新增 `buttonLanguage`
- `app/src/main/res/values/strings.xml` — 新增 `label_language` 字符串资源
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt` — 新增 `languageLabel()` 和 `languageDisplayValue(AppLanguage)` 方法
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt` — 更新 commonSummary 断言，新增 2 个语言控制测试
- `app/src/test/java/com/opencamera/app/SessionUiRenderContractsTest.kt` — 新增 3 个语言控制交互测试
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt` — 新增 `languageLabel()` 覆写

### Verification

```
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionUiRenderContractsTest
```
结果: 127 tests completed, 1 failed（1 个为预存的无关测试失败：`session summary includes native output and action context`，Zoom 断言问题）

```
rtk ./gradlew --no-daemon :app:assembleDebug
```
结果: BUILD SUCCESSFUL

### Risks

- 预存测试 `session summary includes native output and action context` 失败（Zoom 相关断言），与本次变更无关
