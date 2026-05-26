# 2026-05-22 Real-Device Feedback And Color Lab Session Record

## 来源

- 项目：`/Volumes/Extreme_SSD/project/codex_camera`
- 应用：Android/Kotlin 相机项目 `OpenCamera`
- 记录方式：会话整理摘录，保留用户关键原话，AI 回复按决策与产出摘要记录
- 目标用途：转交外部 agent、后续实现 agent 或项目记录使用

## 用户原始反馈与需求

### 第一组最新版 APK 真机问题

用户反馈：

> 最新版apk真机实测发现的问题。1，拍照以后，缩略图无水印跳变有水印，应当一开始就有水印。2，横屏未适配（我建议UI布局不变，按钮/文字旋转，预览框横屏化）。3，构图网格效果不佳，包括UI显示效果，包括线条分布效果（针对实际成像的预览区域的x等分，参考vivo，apple的实现） 。4，面板栏目上不必展示设置状态的合集，让子项目自己展示自己的状态就行，但目前却是在二级面板头部或尾部展示状态合集。“设置，拍照/录像”中更是有三重展示。5，目前画幅系统处理不当，包括快捷中的ui展示，包括实际画幅效果，应注意长边在屏幕的竖向，才能充分利用传感器面积。6，色彩二级面板需要全面优化，目前中文翻译没做好，顶部子项高低不齐，文字杂乱，滤镜子项选中后文字复杂无用。7，我建议将左侧的色彩栏目，改名为风格，放风格模式，光斑效果之类的，顶部栏目右侧恢复曾经的“镜头实验室”（现在改名为“色彩实验室”，放置调色板等项目，调色以后对预览和成片生效）（我的理解是，风格融合了滤镜，iso，曝光，是否暗角等多种处理环节，色彩则是后处理的渲染处理）。8，拍照按钮我建议对UI图案进行优化，更有按钮效果 。分析以上问题，最终根据相近问题合并处理，出具多份的md格式的方案文档，以便直接转给其他 agent 实现落地。注意，这个其他的agent是非多模态的模型，如果优化项落地过程涉及多模态能力，则单独提出到另一个文档，择期执行

AI 已产出第三轮反馈方案文档：

- `docs/plans/2026-05-22-third-real-device-feedback-index.md`
- `docs/plans/2026-05-22-watermarked-thumbnail-first-feedback.md`
- `docs/plans/2026-05-22-landscape-grid-frame-ratio-geometry.md`
- `docs/plans/2026-05-22-panel-state-deduplication.md`
- `docs/plans/2026-05-22-style-and-color-lab-ia.md`
- `docs/plans/2026-05-22-shutter-button-visual-refresh.md`
- `docs/plans/2026-05-22-third-feedback-multimodal-visual-qa.md`

### 第二组最新版 APK 真机问题

用户反馈：

> 最新版apk真机实测发现的问题：1，横屏交互效果不好。大体上，界面UI布局不变，但按钮图案或文字进行旋转，保持横屏状态下的竖向效果。2，真实成像区域对于预览区域偏下。3，应该对模式栏文字优化，更加显著清晰。4，顶部栏目右侧的”色彩实验室“按钮，依然没有实现，反而变成了右侧侧边栏目。目前右侧侧边栏的有些混乱，需要梳理。5，快捷子界面，UI布局效果不佳，文字出现省略号。6，左侧栏目二级面板都过大，遮挡了其他组件，有些还超出了可视布局范围。7，模式栏目点击不灵敏，准确，误触频繁。8，或许有我没注意的缺陷。分析以上信息和问题，设计恰当的优化方案，根据不同处理领域恰当分类，产出不同的多个md格式的方案文档，以便直接转给其他 agent 实现落地。

AI 根据代码现状和用户截图反馈，拆成第四轮方案文档：

- `docs/plans/2026-05-22-fourth-real-device-feedback-index.md`
- `docs/plans/2026-05-22-landscape-preview-alignment-and-rotation.md`
- `docs/plans/2026-05-22-rail-and-color-lab-entry-consolidation.md`
- `docs/plans/2026-05-22-quick-and-secondary-panel-bounds.md`
- `docs/plans/2026-05-22-mode-track-legibility-and-hit-targets.md`
- `docs/plans/2026-05-22-fourth-feedback-multimodal-visual-qa.md`

核心分类：

- 横屏与预览几何：横屏控件旋转、预览成像区域对齐、grid/frame/tap 区域同源。
- 顶部/侧栏 IA：恢复实验室入口，梳理右侧栏。
- 快捷与二级面板：解决省略号、遮挡、越界。
- 模式栏：增强文字显著性、扩大命中、降低误触。
- 多模态 QA：截图、录屏、保存图对比单独延期。

## 侧边栏与顶部栏 IA 讨论

### AI 初版建议

AI 初版建议：

- 侧边栏保留：`风格 / 快捷 / Dev`
- 顶部右侧恢复：`色彩实验室` 或 `镜头实验室`
- 设置不放侧边栏
- 风格面板包含风格、滤镜、光斑、暗角、柔光、颗粒等组合
- 色彩实验室包含调色板和一组专业调整项

### 用户修正

用户提出：

> 我的建议：1，设置显示为顶部栏目最右侧的按钮。2，Dev不区分正式调试版本。3，风格面板放预设风格列表，滤镜列表，都支持强度调节。不再需要光斑、暗角、柔光、颗粒等偏“拍摄风格”的组合项（融合到风格列表中）4，快捷面板 不需要“更多”。5，就叫色彩实验室，本质是后渲染多加了一个处理模块。删除专业化的选项，仅简洁地放置一个调色板类似如图，参考apple调色盘和vivo蓝图调色板。

用户进一步解释色彩实验室理解：

> 我最初思考的效果是类似专业模式的手动调节，效果是通过综合手段，如白平衡，色温，或者类似多了一道对应颜色的光源，实现成片主要色调的改变。不过这个具体来看，各家厂商好像有多种方案，不知道能否参考。比如苹果方案基于实时语义分割，拆解为不同的“图层”，恰当处理天空，背景，人物。纵轴 - 色调（Tone）： 并非简单的亮度（Brightness），而是调整高光和阴影的对比关系。往上拉会保留更多暗部细节（更明亮扁平），往下拉会压暗阴影，形成类似胶片的强对比（更深邃）。横轴 - 色彩（Color）： 并非简单的饱和度（Saturation），更接近色温（琥珀色到冷蓝色）与自然饱和度（Vibrance）的结合。它在改变画面冷暖氛围的同时，避免了色彩溢出（断层）。vivo 蓝图调色板的底层是极其庞大的 3D LUT（三维色彩查找表）矩阵，基于实时插值。横轴 - 调色： 改变画面的色彩倾向和氛围。比如向左滑转向冷白皮、复古冷青，向右滑转向温暖的日系、胶片暖调。它调动的是色彩空间的色相偏移。纵轴 - 调影调： 控制光影的动态范围与质感。往上滑动提升中间调和暗部，让画面通透轻盈；往下滑动则压缩暗部，增加阴影的权重，营造电影戏剧感。

AI 接受并修订方案：

- 顶部栏：
  - 左侧：应用名
  - 中部靠右：色彩实验室
  - 最右：设置
- 侧边栏：
  - 风格
  - 快捷
  - Dev
- 风格二级面板：
  - 预设风格列表
  - 滤镜列表
  - 统一强度调节
  - 不单列光斑、暗角、柔光、颗粒
- 快捷二级面板：
  - 网格
  - 画质 / 闪光
  - 画幅
  - 实况
  - 定时
  - 删除 `更多`
- 色彩实验室：
  - 二维调色板
  - 横轴：色彩倾向
  - 纵轴：影调关系
  - 不暴露专业参数列表

### 用户最终补充顶部栏

用户提出：

> 色彩实验室也放在顶部栏目，位于中部靠右。顶部最右让给 设置。顶部左侧给原有的应用名UI，另外，删掉应用名UI后面跟随的“点号 模式名”

AI 已将该口径写入：

- `docs/plans/2026-05-22-rail-and-color-lab-entry-consolidation.md`
- `docs/plans/2026-05-22-fourth-real-device-feedback-index.md`
- `docs/plans/2026-05-22-quick-and-secondary-panel-bounds.md`
- `docs/plans/2026-05-22-fourth-feedback-multimodal-visual-qa.md`
- `codex/documentation.md`

最终 IA：

```text
顶部栏
  左侧：OpenCamera
    - 删除 “· 模式名”
  中部靠右：色彩实验室
  最右侧：设置

侧边栏
  风格
  快捷
  Dev
```

## 色彩实验室可行方案分析

用户要求：

> 分析适用于该应用的色彩实验室调色板的可行方案

AI 结论：

- 不建议第一阶段追苹果式实时语义分割。
- 不建议第一阶段直接做 vivo 级大型 3D LUT 矩阵。
- 最适合当前 OpenCamera 的第一阶段方案是：

```text
色彩实验室
  一个二维调色板
    横轴：色彩倾向 Color
    纵轴：影调关系 Tone
    中心：自然 / 标准
    拖动点：生成 ColorLabSpec
    强度：可选 0-100
  同一份 ColorLabSpec 同时作用于：
    预览近似效果
    最终成片精确后处理
```

横轴定义：

```text
左侧：冷、青、蓝、冷白、低暖色
中心：自然
右侧：暖、琥珀、粉橙、日系、胶片暖调
```

纵轴定义：

```text
上方：通透、轻盈、暗部抬起、中间调更亮、对比更柔
中心：自然
下方：深邃、阴影压暗、对比增强、胶片/电影感
```

实现分层：

1. 第一阶段：参数映射版
   - `x, y -> ColorLabSpec`
   - `ColorLabSpec -> FilterRenderSpec`
   - 使用现有 `FilterRenderSpec` metadata、预览适配和保存图后处理链路。

2. 第二阶段：小型 LUT 插值版
   - 轻量 `17x17x17` 或 `33x33x33` LUT。
   - 用双线性插值或网格插值提升味道。

3. 第三阶段：语义分层版
   - 类似苹果，对天空、人物、背景、肤色分层调色。
   - 暂缓，不作为当前落地前置条件。

## 用户授权第一阶段落地

用户要求：

> 同意基于第一阶段落地实现。请你分析，设计UI实现和功能实现，分析难点，你负责完成最有难度的10%工作。剩余的较为简单的90%工作，出具一份的md格式的方案文档，以便直接转给外部非多模态 agent 实现落地。

AI 执行策略：

- 负责最难的 10%：
  - 把二维坐标到后渲染参数的映射做成正式、可测试、非 UI 层的核心内核。
  - 避免后续 agent 在 UI 层重复写调色公式。

- 剩余 90%：
  - 持久化
  - 顶部栏/侧边栏接线
  - 色彩实验室面板 UI
  - palette touch
  - 预览消费
  - 成片 metadata 接线
  - 多模态 QA
  - 全部写入外部 agent 可执行 Markdown。

## 已完成的 10% 核心工作

新增：

- `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`

修改：

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `codex/documentation.md`

新增方案文档：

- `docs/plans/2026-05-22-color-lab-stage1-implementation.md`

### ColorLabSpec 设计

核心类型：

```kotlin
data class ColorLabSpec(
    val colorAxis: Float = 0f,
    val toneAxis: Float = 0f,
    val strength: Float = 1f,
    val presetId: String? = null,
    val version: Int = 1
)
```

核心行为：

- `normalized()`：夹紧坐标和强度。
- `toMapping()`：生成可解释的 `ColorLabMapping`。
- `applyTo(base: FilterRenderSpec)`：应用到现有滤镜/渲染规格。
- `FilterRenderSpec.applyColorLab(spec)`：外部统一调用入口。

映射原则：

- `colorAxis > 0`：偏暖，增加 `warmthShift / warmBoost`。
- `colorAxis < 0`：偏冷，增加 `coolBoost`，降低 warmth。
- `toneAxis > 0`：通透，提升亮度，降低对比，提升暗部。
- `toneAxis < 0`：深邃，压暗，增强对比。
- `strength = 0` 或中心点：不改变基础 `FilterRenderSpec`。
- 输出被夹紧在现有 `FilterRenderSpec` 安全范围内。

### 测试覆盖

`ColorLabSpecTest` 覆盖：

- 中心点不改变基础渲染。
- 强度 0 不改变基础渲染。
- 右侧色彩轴变暖。
- 左侧色彩轴变冷。
- 上方影调变通透。
- 下方影调变深邃。
- 输入越界时先夹紧。
- mapping 暴露 normalized 坐标，便于 UI reticle 状态使用。

### 旧 UI 逻辑委托

原 `SessionUiRenderModel.kt` 中的 `FilterRenderSpec.applyLightPalette(colorAxis, toneAxis)` 曾经直接写 UI 层公式。

现在已改为：

```kotlin
return applyColorLab(
    ColorLabSpec(
        colorAxis = colorAxis,
        toneAxis = toneAxis
    )
)
```

这样后续外部 agent 不应该再在 `MainActivity`、`FilterPaletteView` 或 `SessionUiRenderModel` 中重复实现调色公式。

## 已验证命令

曾先按 TDD 运行失败测试：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest
```

失败原因符合预期：

```text
Unresolved reference: ColorLabSpec
```

实现后通过：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest
```

受影响 app render-model 测试通过：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

联合验证通过：

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## 剩余 90% 方案文档

外部非多模态 agent 应直接阅读：

- `docs/plans/2026-05-22-color-lab-stage1-implementation.md`

该文档覆盖：

- 产品合同
- 已完成内容
- 文件地图
- Task 1：持久化 `ColorLabSpec`
- Task 2：应用到预览和保存图 metadata
- Task 3：顶部栏与侧边栏 IA
- Task 4：色彩实验室面板 render model 与 palette UI
- Task 5：删除用户可见的专业参数面板
- Task 6：最终验证
- 难点提醒
- 多模态验收交接

## 当前关键决策清单

- `色彩实验室` 是后渲染模块，不是专业参数面板。
- 第一阶段不做语义分割。
- 第一阶段不做大型 3D LUT。
- 第一阶段复用现有 `FilterRenderSpec`、预览 effect 和 `PhotoAlgorithmPostProcessor`。
- 顶部栏左侧只显示应用名，不再显示 `· 模式名`。
- 顶部中部靠右是 `色彩实验室`。
- 顶部最右是 `设置`。
- 侧边栏是 `风格 / 快捷 / Dev`。
- `Dev` 不再按正式/调试版本区分。
- `快捷` 删除 `更多`。
- `风格` 只展示预设风格、滤镜和强度调节，不单列光斑、暗角、柔光、颗粒。
- `色彩实验室` 只展示二维调色板、重置和可选强度。

## 相关文件索引

已新增或修改：

- `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `docs/plans/2026-05-22-color-lab-stage1-implementation.md`
- `codex/documentation.md`

本轮相关方案：

- `docs/plans/2026-05-22-fourth-real-device-feedback-index.md`
- `docs/plans/2026-05-22-landscape-preview-alignment-and-rotation.md`
- `docs/plans/2026-05-22-rail-and-color-lab-entry-consolidation.md`
- `docs/plans/2026-05-22-quick-and-secondary-panel-bounds.md`
- `docs/plans/2026-05-22-mode-track-legibility-and-hit-targets.md`
- `docs/plans/2026-05-22-fourth-feedback-multimodal-visual-qa.md`
- `docs/plans/2026-05-22-color-lab-stage1-implementation.md`

## 后续建议

1. 先让外部 agent 执行 `2026-05-22-color-lab-stage1-implementation.md`。
2. 执行时不要重写 `ColorLabSpec` 映射公式。
3. UI 接线优先完成顶部栏和色彩实验室入口。
4. 再完成 `ColorLabSpec` 持久化和 `PersistedSettingsAction.UpdateColorLabSpec`。
5. 再把 mode/effect 侧的 `FilterRenderSpec` 统一应用 `ColorLabSpec`。
6. 最后用多模态 QA 对比预览与保存图。

