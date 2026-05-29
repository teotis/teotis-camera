# Package: 01-i18n-core-infrastructure

## Mission

修复语言设定的持久化缺陷、在设置面板中添加语言切换控件、修复语言显示名称。

## Allowed Paths

- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/AppLanguage.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderContracts.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/res/layout/activity_main.xml`

## Forbidden Paths

- All feature module directories (`feature/`)
- All other core module directories outside `core/settings/`
- `res/values/strings.xml`
- `res/values-en/strings.xml`
- `INDEX.md`
- Other package status files

## Acceptance Criteria

### A1. 语言设定持久化

- [ ] `PersistedSettingsSerializer.toMap()` 包含 `appLanguage` 字段
- [ ] `PersistedSettingsSerializer.fromMap()` 正确恢复 `appLanguage`
- [ ] App 重启后语言设定保持为上次用户选择的值（而非总是回退到 `ZH`）

实现方式：
1. 在 `PersistedSettingsSerializer.kt` 添加 `KEY_APP_LANGUAGE = "common.appLanguage"`
2. 在 `toMap()` 中加入 `KEY_APP_LANGUAGE to settings.common.appLanguage.storageKey`
3. 在 `fromMap()` 中从 map 读取并恢复 `appLanguage`：`AppLanguage.fromStorageKey(values[KEY_APP_LANGUAGE]) ?: defaults.common.appLanguage`

### A2. 语言切换控件

- [ ] 设置面板 Common 分区中显示语言切换按钮
- [ ] 按钮显示当前语言名称（中文 / English）
- [ ] 点击按钮循环切换到下一个可用语言
- [ ] 切换后 `applyLocale()` 被触发，全应用 UI 文字即时更新（通过 `AppCompatDelegate.setApplicationLocales()` 实现热切换）

实现方式：
1. 在 `AppTextResolver` 中添加 `languagePickerLabel()` 和 `languagePickerValue()` 方法（或增强现有 `languageDisplayName()`）
2. 在 `CommonSettingsSectionRenderModel` 中添加 `language: SettingsControlRenderModel` 字段
3. 在 `sessionSettingsPageRenderModel()` 中构建 language 控件，使用 `PersistedSettingsAction.UpdateAppLanguage`
4. 在 `SettingsPanelRenderer.renderPage()` 中渲染该控件
5. 在 `MainActivityViews.kt` 中添加对应按钮视图 binding（如 `language`）

### A3. 语言显示名称修复

- [ ] `AppTextResolver.languageDisplayName()` 中 `AppLanguage.ZH` 返回对应字符串资源（"中文"），而非 `app_name`
- [ ] `AppLanguage.EN` 返回 "English"

实现方式：直接修改 `AppTextResolver.kt` 的 `languageDisplayName()` 方法，ZH 分支返回 "中文"（硬编码或通过新 string resource）。

### A4. 热切换测试

- [ ] 切换语言后，已渲染的设置面板文字即时更新
- [ ] `applyLocale()` 在设置变更时被正确调用（当前已在 `render()` 中每次调用，无需额外修改）

## Verification Commands

```bash
# 1. 编译验证
./gradlew --no-daemon :app:assembleDebug

# 2. settings 模块测试
./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test

# 3. app 模块测试（如果有相关 UI 测试）
./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest
```

## Expected Evidence

- Git diff 展示序列化器新增 `appLanguage` 的读写
- Git diff 展示 `CommonSettingsSectionRenderModel` 新增 `language` 字段
- Git diff 展示 `SettingsPanelRenderer` 新增语言按钮渲染
- Git diff 展示 `AppTextResolver.languageDisplayName()` 修正
- 构建成功输出（`:app:assembleDebug` 通过）
- 测试通过输出

## Notes

1. 语言热切换依赖于 `AppCompatDelegate.setApplicationLocales()`，AndroidX 官方支持此 API（需 `appcompat >= 1.6.0`）。切换后 Activity 会收到 `onConfigurationChanged` 回调，所有通过 `context.getString()` 获取的字符串会自动更新。当前项目已在 `render()` 中每次调用 `applyLocale()`，切换后即时生效。

2. 当前 `CommonSettings` data class 中 `appLanguage` 默认值为 `AppLanguage.ZH`。若未来默认语言需改为跟随系统语言，需额外修改 `CommonSettings` 的默认值逻辑。

3. `MainActivityViews.kt` 中可能需要新增一个 `Button` 的 view binding。查看现有布局和 views 定义，遵循现有的 `SettingsPanelViews` 模式。
