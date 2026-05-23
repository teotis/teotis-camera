# vivo X300 Dev 控制台信息架构修复方案

日期：2026-05-24

覆盖用户问题：5，“开发选项中，核心和关键重复，应该设计更有差异化的区分，或删去选项。”

## 目标

Dev 控制台保留工程诊断价值，但用户可见 tab 必须有清晰差异。`关键` 和 `核心` 不能像同义词一样让人困惑。

## 现有代码入口

重点文件：

- `app/src/main/java/com/opencamera/app/DevLogRenderModel.kt`
- `app/src/main/java/com/opencamera/app/DevConsoleRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/main/res/layout/activity_main.xml`

当前分桶：

- `DevLogTab.KEY`
- `DevLogTab.CORE`
- `DevLogTab.ERROR`
- `DevLogTab.ALL`

当前文案：

- 中文：`关键` / `核心` / `错误` / `全部`
- 英文：`Key` / `Core` / `Error` / `All`

当前事件分组在 `SessionUiRenderModel.kt`：

- `KEY_EVENT_NAMES` 包含 session/mode/lens/zoom/preview first frame/capture saved/recording/permissions/device capabilities/settings 等用户路径关键事件。
- `CORE_EVENT_NAMES` 包含 preview binding/recovery/countdown/saving/feedback snapshot/recording requested/shot plan/mode signal/intent 等内部链路事件。

## 推荐方案

保留两个 tab，但改名并强化语义：

- `关键` 改为 `摘要`
- `核心` 改为 `链路`
- `错误` 保持
- `全部` 保持

解释：

- `摘要` 面向真机验收和现场排查，显示最少但最关键的用户路径事件。
- `链路` 面向工程排障，显示预览绑定、恢复、shot plan、intent 等内部 pipeline。
- 这样比删掉 `核心` 更稳，因为 Stage 7 重视诊断和可观测性，保留内部链路有价值。

英文建议：

- `Key` 改为 `Summary`
- `Core` 改为 `Pipeline`
- `Error`
- `All`

## 具体改法

1. 修改字符串资源：
   - `app/src/main/res/values/strings.xml`
     - `dev_tab_key`: `摘要`
     - `dev_tab_core`: `链路`
   - `app/src/main/res/values-en/strings.xml`
     - `dev_tab_key`: `Summary`
     - `dev_tab_core`: `Pipeline`
2. 修改标题资源，如果存在：
   - `dev_log_title_key`: `摘要日志 (%d)`
   - `dev_log_title_core`: `链路日志 (%d)`
   - 英文对应 `Summary Log (%d)` / `Pipeline Log (%d)`
3. 修改 `AppTextResolver.devLogTitleKey()` / `devLogTitleCore()` fallback：
   - fallback 从 `Key Log` / `Core Log` 改为 `Summary Log` / `Pipeline Log`
4. 不必修改 enum 名称 `DevLogTab.KEY/CORE`，除非 agent 有充足测试覆盖。为了降低风险，本轮只改用户可见文案。
5. 如果希望进一步拉开内容差异，可把 `device.capabilities.updated` 和 `settings.updated` 留在 `KEY_EVENT_NAMES`，把 `intent.received`、`mode.signal`、`mode.event` 继续留在 `CORE_EVENT_NAMES`。不要把同一事件同时放入两个集合。

二次审查补充：

- 建议同步更新导出文案的 section header，避免 UI 已改成 `摘要/链路`，导出文件仍是 `KEY EVENTS / CORE EVENTS`。如果担心破坏外部脚本，可保留英文 machine header，但在 header 后加一行中文说明，例如 `# 摘要事件` / `# 链路事件`。
- `KEY_EVENT_NAMES` 和 `CORE_EVENT_NAMES` 当前没有明显重复，但下游 agent 修改分桶后应增加测试，断言两组集合交集为空，避免“摘要/链路”再次内容趋同。
- 如果只改 tab 文案，用户仍可能觉得内容重复。建议至少把标题、summary 和 tab label 一起改，形成完整语义闭环。

## 不建议方案

不建议直接删掉 `核心` tab。原因：

- Stage 7 仍依赖 runtime/recovery/preview 诊断。
- 真机问题里也有 provider、recovery、性能相关残余风险，内部链路 tab 对排障有用。
- 删除 tab 会牵涉布局、绑定、导出格式和测试，收益不如改名清晰。

## 验收标准

1. Dev 控制台 tab 显示 `摘要 / 链路 / 错误 / 全部`。
2. `摘要` 标题和内容偏用户路径：模式、镜头、变焦、首帧、拍照保存、录像保存、权限、设置。
3. `链路` 标题和内容偏工程链路：preview binding/recovery、countdown、saving、feedback snapshot、shot plan、intent 等。
4. 导出日志仍包含 `KEY EVENTS` / `CORE EVENTS` 可以暂不改；若要改，需同步测试和下游脚本。用户可见 UI 是本轮重点。

## 测试建议

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

真机手动验收：

1. Debug 包打开相机。
2. 点右侧 `开发`。
3. 确认 tab 不再出现 `关键 / 核心` 这种重复语义，而是 `摘要 / 链路`。
4. 分别点两个 tab，确认内容不完全相同。

自动验收建议补充：

- 在 `SessionUiRenderModelTest` 增加 dev log render case：构造 key event 和 core event，断言 `DevLogTab.KEY` 与 `DevLogTab.CORE` 标题不同、内容不同。
- 增加资源字符串断言不是必须，但可用普通 render test 间接覆盖 `AppTextResolver.devLogTitleKey/Core` fallback。
