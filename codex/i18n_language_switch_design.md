# OpenCamera 中英文切换方案

> 目标：让当前英文用户交互界面支持中文 / 英文切换，并默认使用中文。本文是交给后续 agent 落地实现的设计方案，不直接修改业务代码。

## 1. 背景判断

当前项目是 Android / Kotlin 相机工程，UI 主要在 `app` 模块：

- 布局入口：`app/src/main/res/layout/activity_main.xml`
- 静态资源：`app/src/main/res/values/strings.xml`
- 运行态文案：`app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- 绑定渲染：`app/src/main/java/com/opencamera/app/MainActivity.kt`
- 模式文案来源：`core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt` 与各 `feature/mode-*/*ModePlugin.kt`
- 设置持久化：`core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`、`PersistedSettingsSerializer.kt`，Android 落盘在 `SharedPreferencesPersistedSettingsStore.kt`

现状不是单纯 XML 文案未翻译。仓内已有 `strings.xml`，但大量用户可见文案仍硬编码在 `SessionUiRenderModel.kt`、`ModeContracts.kt`、各 mode plugin 的 `ModeUiSpec / ModeState / ShowHint`、settings enum 的 `label` 字段中。

因此推荐方案不是只新增 `values-zh/strings.xml`，而是建立一条小而清晰的本地化边界：

```text
Core / Mode / Settings 只暴露稳定 id、storageKey、状态和能力语义
App UI 层负责把这些语义解析成当前语言下的用户文案
```

这样不会因为“切换语言”污染 `Session Kernel`，也不会让模式插件继续扩散英文字符串。

## 2. 产品目标

首轮目标：

- App 默认显示中文。
- 用户可在 App 内设置中文 / English。
- 切换语言后，当前界面、设置面板、模式轨道、按钮、状态提示、用户可见错误前缀立即刷新。
- 语言选择持久化，下次打开继续使用用户选择。
- 不因切换语言重启 camera session、重绑 preview、清空当前模式、清空设置面板状态或触发恢复流程。

非首轮目标：

- 不做多语言动态下载。
- 不做系统语言自动跟随作为默认策略；本需求明确默认中文。
- 不要求开发者调试日志、trace event name、metadata tag、EXIF tag 全部翻译。它们是诊断数据或机器语义，首轮保留英文更稳定。
- 不引入 Compose 或大规模 UI 重写。

## 3. 推荐方案

采用“Android resources + app 层 text resolver + 持久化语言偏好”的混合方案。

### 3.1 语言模型

在 `core/settings` 新增语言偏好：

```kotlin
enum class AppLanguage(
    val storageKey: String
) {
    ZH("zh"),
    EN("en");

    companion object {
        fun fromStorageKey(value: String?): AppLanguage? {
            return entries.firstOrNull { it.storageKey == value }
        }
    }
}
```

在 `CommonSettings` 增加字段：

```kotlin
val appLanguage: AppLanguage = AppLanguage.ZH
```

序列化新增 key：

```kotlin
private const val KEY_APP_LANGUAGE = "common.appLanguage"
```

默认值必须是 `ZH`，满足“默认设置是中文”。

### 3.2 Android locale 应用方式

优先使用 AndroidX AppCompat 的 per-app language API：

```kotlin
AppCompatDelegate.setApplicationLocales(
    LocaleListCompat.forLanguageTags(language.storageKey)
)
```

如果当前 `app` 仍继承 `ComponentActivity` 且未引入 AppCompat，可以选择两种落地路径：

1. 推荐：把 `MainActivity` 改为 `AppCompatActivity`，加入 `androidx.appcompat:appcompat`，使用官方 per-app language API。
2. 保守：保留 `ComponentActivity`，在 app 层用 `Context.createConfigurationContext()` 为 `getString()` 提供 localized context。

推荐路径是 1，因为系统资源、布局 inflation、无障碍 contentDescription、后续 Activity 级刷新都会更符合 Android 平台行为。切换语言可能触发 Activity recreation，但不能触发 session kernel 的语义重置；现有 `container.cameraSession` 在 `Application` 的 `AppContainer` 中，后续 agent 需要确认 recreation 后不会创建第二个 kernel。

### 3.3 文案资源结构

把默认资源改成中文：

- `app/src/main/res/values/strings.xml`：中文默认文案
- `app/src/main/res/values-en/strings.xml`：英文文案

不建议使用 `values-zh` 承载中文、`values` 继续英文，因为需求是默认中文；Android fallback 应当落到中文。

首轮至少覆盖这些资源：

- 顶部入口：设置、滤镜、快捷、DEV 可保留英文或改成开发入口
- 模式轨道：拍照、文档、风光、人文、人像、专业、录像
- 快门与运行控制：快门、切镜头、变焦、画质、尺寸
- 权限与输出：权限提示、最近照片 / 视频 / Live / 错误前缀
- 设置面板：通用、拍照、录像、滤镜、水印、人像、手动控制
- 快捷控制：闪光、比例、Live、倒计时、更多
- 无障碍描述：settings panel、filter panel、preview thumbnail 等

### 3.4 App 层本地化解析器

新增一个 app 层 resolver，避免把 `Context.getString()` 散进所有 render 函数。

建议文件：

```text
app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt
app/src/main/java/com/opencamera/app/i18n/LocalizedSessionUiStrings.kt
```

职责：

- 将 `ModeId` 转成当前语言下的 displayName / shortLabel。
- 将 `CompositionGridMode`、`CountdownDuration`、`AudioProfile`、`DynamicVideoFpsPolicy`、`PortraitProfile` 等稳定 enum 转成当前语言下的 label。
- 将 `SettingsControlAvailability` 转成“支持 / 降级 / 不支持”或 “Supported / Degraded / Unsupported”。
- 提供组合文案方法，比如“默认滤镜 %s”“当前设备不支持录像”等。

示例接口：

```kotlin
class AppTextResolver(
    private val context: Context
) {
    fun modeDisplayName(modeId: ModeId): String
    fun modeTrackLabel(modeId: ModeId): String
    fun gridModeLabel(value: CompositionGridMode): String
    fun countdownLabel(value: CountdownDuration): String
    fun availabilityLabel(value: SettingsControlAvailability): String
    fun onOff(enabled: Boolean): String
    fun sessionUiStrings(): SessionUiStrings
}
```

注意：resolver 只放在 `app` 模块。`core/session`、`core/device`、`core/media`、`core/mode`、`core/settings` 不应依赖 Android `Context` 或 `R.string`。

### 3.5 Render model 改造边界

`SessionUiRenderModel.kt` 当前已经有局部 `SessionUiStrings` 注入，这是好的起点。后续应把它扩大成完整的 `AppTextResolver` 注入。

改造方向：

```kotlin
internal fun sessionSettingsPageRenderModel(
    state: SessionState,
    text: AppTextResolver
): SessionSettingsPageRenderModel
```

类似需要增加 resolver 参数的函数：

- `sessionSettingsRenderModel`
- `sessionSettingsPageRenderModel`
- `portraitLabPageRenderModel`
- `watermarkLabSelectorRenderModel`
- `watermarkLabDetailRenderModel`
- `filterLabPageRenderModel`
- `modeTrackRenderModel`
- `primaryStatusRenderModel`
- `modeDirectoryRenderModel`
- `runtimeProControlsRenderModel`
- `sessionCaptureOutputText`
- `sessionControlsRenderModel`

`MainActivity.render()` 统一创建：

```kotlin
val text = AppTextResolver(this)
```

然后传入所有 render model builder。

不要让 `ModeController` 或 `DefaultCameraSession` 因语言变化重新生成业务状态。用户可见的 mode title/headline/detail 应逐步迁移为 app 层基于 mode id、profile id、capability 语义生成。

## 4. Mode / Core 文案去硬编码策略

首轮不要一次性重写所有模式契约，风险太大。按三类处理。

### 4.1 必须首轮迁移的用户主界面文案

这些文案用户每天会看到，必须进入 resolver：

- 模式名称与模式轨道短标签
- 快门按钮、二级/三级模式按钮、Pro 入口
- 设置面板标题、说明、按钮 label、支持状态
- 滤镜面板标题、tab、保存自定义、调整按钮
- 人像、水印、录像设置按钮
- 权限提示与输出前缀

### 4.2 可以首轮保留英文但需标记的诊断文案

这些可以保留英文，避免影响 stage 7 diagnostics：

- `SessionTrace` event name
- `sessionDiagnosticsText`
- `DevLogRenderModel`
- pipeline notes
- metadata tag、EXIF override key
- device runtime issue kind 的机器 tag

如果用户界面展示 dev console，可以给入口中文化，但 console 内容可继续英文。

### 4.3 Mode plugin 的迁移节奏

各 mode plugin 目前返回 `ModeUiSpec(title = "Photo", shutterLabel = "...")`、`ModeState(headline/detail)`、`ModeSignal.ShowHint("...")`。

推荐首轮做兼容层，而不是立刻改 mode contract：

- UI 顶部标题、mode track 不再直接信任 `state.modeSnapshot.uiSpec.title`，而用 `text.modeDisplayName(state.activeMode)`。
- shutter label 若能由 `ModeId + capture capability` 推导，则 app 层覆盖。
- `ModeState.headline/detail` 暂时保留，但在中文首轮中只作为 fallback；高频可见 summary 在 `SessionUiRenderModel` 中本地化生成。

第二轮再考虑把 `ModeUiSpec` 改为结构化 key：

```kotlin
data class ModeUiSpec(
    val titleKey: ModeTextKey,
    val shutterLabelKey: ModeTextKey,
    val secondaryActionKey: ModeTextKey? = null
)
```

这一步会触及所有 mode plugin 和大量测试，不建议和首轮语言切换混在一起。

## 5. 设置入口设计

在现有 Lens Lab / 设置面板的 General 区域增加一个语言控制。

中文默认界面：

```text
语言
中文
支持
```

英文界面：

```text
Language
English
Supported
```

点击行为：

- 中文 -> English
- English -> 中文

落地方式：

- `PersistedSettingsAction` 增加 `UpdateAppLanguage(AppLanguage)`
- `SessionSettingsManager.apply()` 处理该 action
- `SessionIntent.SettingsUpdated` 继续作为设置刷新路径
- `MainActivity` 观察 settings 后应用 app locale，并重新渲染当前界面

要避免的行为：

- 不要把语言切换建成新的 session intent，如 `SessionIntent.LanguageChanged`。
- 不要在 `DefaultCameraSession` 内调用 Android locale API。
- 不要因为语言变化重启 preview 或 camera provider。

## 6. 文件级实施建议

### Task A：持久化语言偏好

修改：

- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`

要点：

- 新增 `AppLanguage`，默认 `ZH`。
- `CommonSettings` 增加 `appLanguage`。
- serializer 写入 / 读取 `common.appLanguage`。
- 无旧 key 时回退中文。
- 非法值回退中文。

### Task B：语言 action 和 SettingsManager

修改：

- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
- `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- 相关 settings manager 测试，如果已有。

要点：

- `PersistedSettingsAction.UpdateAppLanguage(language)`。
- 点击设置后保存到 persisted settings。
- action 不影响 feature catalog。

### Task C：资源文件默认中文

修改 / 新增：

- 修改 `app/src/main/res/values/strings.xml` 为中文。
- 新增 `app/src/main/res/values-en/strings.xml` 保存英文。

要点：

- 所有现有 string name 保持不变，降低 XML 影响面。
- 新增 resolver 需要的 string key 时，中英文必须同时添加。
- 中文按钮要短，避免相机控制区溢出。例如：拍照、文档、风光、人文、人像、专业、录像。

### Task D：AppTextResolver 与 render model 注入

新增：

- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`

修改：

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

要点：

- 先把 `SessionUiStrings` 扩展为 resolver，而不是一次性删除。
- 先覆盖高频主界面和设置面板。
- 对 debug/diagnostics 保持英文。
- 单测用 fake resolver，分别断言中文默认和英文切换。

### Task E：应用 locale

推荐修改：

- `app/build.gradle.kts`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/OpenCameraApplication.kt` 或 `AppContainer.kt`

要点：

- 引入 AppCompat per-app language。
- App 启动时先读取 persisted language 并应用。
- Settings 更新后如果语言变化，调用 locale API 并刷新当前 Activity。
- 确认 Activity recreation 不创建第二个隐藏 session kernel；如 `AppContainer` 已在 `Application` 级别持有，保持该模式。

### Task F：模式主文案覆盖

修改：

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- 必要时少量调整 `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt`

要点：

- `primaryStatusRenderModel` 使用 resolver 的 mode name。
- `modeTrackRenderModel` 使用 resolver 的 track label。
- `titleText` 不再拼接英文 `uiSpec.title`。
- `shutterButton.text` 对常见模式用 resolver 覆盖：拍照 / 录像 / 停止 / 处理中等。

## 7. 测试策略

优先单测，不依赖真机。

### core/settings

命令：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

覆盖：

- 默认 settings 的 `common.appLanguage == AppLanguage.ZH`
- serializer 输出 `common.appLanguage=zh`
- 读取 `en` 得到英文
- 缺失 / 非法值回退中文

### app render model

命令：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

覆盖：

- 中文 resolver 下 mode track 输出“拍照 / 风光 / 录像”
- 英文 resolver 下 mode track 输出“Photo / Scenery / Video”
- 设置面板语言控制中文显示“语言 / 中文”
- 英文显示“Language / English”
- capture output 前缀随语言变化
- diagnostics 文本仍保留核心 trace 字段，不被翻译破坏

### assemble

命令：

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

覆盖：

- `values` 与 `values-en` key 完整。
- XML 引用未缺失。

### stage 7 回归

本功能触达 app UI 与 settings，最终仍应跑 stage 7 脚本：

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

原因：

- 语言切换不应破坏 diagnostics owner。
- 语言切换不应破坏 `SessionUiRenderModelTest`、`CameraSessionCoordinatorTest`、`assembleDebug`。

## 8. 验收标准

手工验收：

- 首次安装启动显示中文。
- 点击设置面板可以看到“语言 / 中文”。
- 切换到 English 后，主界面、设置面板、模式轨道、权限提示、输出前缀变为英文。
- 杀进程重开后仍保持上次选择。
- 切换语言时预览不应进入错误态；如果 Activity recreation 导致 preview 重新 attach，应走现有生命周期路径，而不是出现第二套 session owner。
- Debug console 可以继续显示英文 trace，但入口和关闭按钮应按当前语言显示。

自动化验收：

- `PersistedSettingsSerializerTest` 通过。
- `SessionUiRenderModelTest` 增加中英文断言并通过。
- `:app:assembleDebug` 通过。
- `scripts/verify_stage_7_observability.sh` 通过。

## 9. 风险与规避

### 风险 1：只翻译 XML，运行态仍大量英文

规避：必须把 `SessionUiRenderModel.kt` 的高频文案纳入 resolver；否则主界面仍会混合中英。

### 风险 2：把语言切换做进 Session Kernel

规避：语言属于持久化设置和 UI rendering，不属于 camera runtime。`DefaultCameraSession` 只能接收 settings snapshot 更新，不直接处理 Android locale。

### 风险 3：切换语言触发 Activity recreation 后出现第二个 session

规避：确认 `AppContainer` 生命周期仍在 `Application`，不要在 `Activity` 内 new session kernel。必要时增加一次测试或手工日志确认 session owner 唯一。

### 风险 4：硬改 mode plugin 文案导致测试大面积震荡

规避：首轮在 app render model 覆盖主界面文案；mode plugin 的英文 `ModeState.detail` 暂时作为 fallback，第二轮再结构化。

### 风险 5：中文文案过长挤压相机按钮

规避：模式轨道和主按钮使用短中文：拍照、文档、风光、人文、人像、专业、录像、快门、切镜头、变焦。设置面板内部可以使用完整文案。

## 10. 推荐落地顺序

1. 先做 `AppLanguage` 持久化与 serializer 测试，确保默认中文。
2. 新增 `values-en/strings.xml`，把 `values/strings.xml` 改为中文默认。
3. 新增 `AppTextResolver`，先接入 `SessionUiStrings` 已覆盖的控制和输出文案。
4. 扩展 resolver 到 mode track、primary status、settings page、filter lab、watermark lab。
5. 在设置面板 General 区域加入语言切换控制。
6. 接入 Android per-app language API，确认切换后立即刷新。
7. 跑 focused tests、`:app:assembleDebug`、`verify_stage_7_observability.sh`。
8. 更新 `codex/documentation.md`，记录“中英文切换方案/实现闭环”和剩余未本地化 debug 文案范围。

## 11. 首轮中英文术语建议

| English | 中文 |
| --- | --- |
| Photo | 拍照 |
| Document | 文档 |
| Scenery | 风光 |
| Humanistic | 人文 |
| Portrait | 人像 |
| Pro | 专业 |
| Video | 录像 |
| Shutter | 快门 |
| Switch Lens | 切镜头 |
| Zoom | 变焦 |
| Lens Lab | 设置 |
| Filter Lab | 滤镜 |
| Quick | 快捷 |
| General | 通用 |
| Still Quality | 照片画质 |
| Still Size | 照片尺寸 |
| Watermark Lab | 水印 |
| Portrait Lab | 人像调节 |
| Supported | 支持 |
| Degraded | 降级 |
| Unsupported | 不支持 |
| On | 开 |
| Off | 关 |
| Auto | 自动 |
| Language | 语言 |
| Chinese | 中文 |
| English | English |

## 12. 一句话结论

这次要做的是“把用户文案从运行态英文字符串迁移到 app 层本地化解析”，不是简单翻译资源文件。默认中文由 `PersistedSettings.common.appLanguage = ZH` 和中文 `values/strings.xml` 双重保证；中英文切换由 app 层 locale API 与 render model resolver 完成，Session Kernel 保持只管相机运行态。
