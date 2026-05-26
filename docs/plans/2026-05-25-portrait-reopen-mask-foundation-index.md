# Portrait Reopen On Scene Mask Foundation Index

日期：2026-05-25

## Goal

重新开放人像模式，但把第一性基础收敛为“人物主体和背景可识别、可拆分、可分别后处理”。后续光斑、滤镜、美颜、背景调色等产品需求都必须消费同一个 `Scene Mask / Person Subject Mask` 语义，而不是各自用椭圆焦点、硬编码中心主体或重复跑模型。

## Decision

当前不应把人像重开理解为“再加几个 UI 选项”或“调强现有模拟虚化”。仓内已经有 Scene Mask 的基础组件，但人像成片链路尚未真正消费 mask：

- `core/media/src/main/kotlin/com/opencamera/core/media/SceneMaskContracts.kt` 已定义 Scene Mask descriptor/capability/pipeline note。
- `app/src/main/java/com/opencamera/app/camera/MlKitSavedPhotoSceneMaskProvider.kt` 和 `MlKitSelfiePreviewSceneMaskSource.kt` 已存在，`app/build.gradle.kts` 已引入 ML Kit selfie segmentation。
- `AppContainer.kt` 已把 `MlKitSavedPhotoSceneMaskProvider` 接给 `PhotoAlgorithmPostProcessor`，Color Lab/滤镜侧已具备 mask-aware 入口。
- `PortraitRenderPostProcessor.kt` 仍只用椭圆焦点区域模拟主体和背景，没有接入 `SavedPhotoSceneMaskProvider` 或 `SceneMaskPayload`。
- `CameraXCaptureAdapter.kt` 的 `ImageAnalysis` analyzer 当前把同一个 `ImageProxy` 先交给 scene mask，再交给 live preview；而 `MlKitSelfiePreviewSceneMaskSource.onAnalyzeFrame(...)` 会关闭 image，存在双关闭/提前关闭/后续消费者读不到帧的风险。后续人像预览 mask 不能建立在这个入口不稳的基础上。

## Work Packages

1. [Preview Analysis Fanout Stability](./2026-05-25-portrait-preview-analysis-fanout-stability.md)
   - 先修复 `ImageAnalysis` 多消费者分发和 `ImageProxy.close()` owner。
   - 这是预览期人像 mask、Live preview ring buffer、低光/场景信号共存的基础。

2. [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md)
   - 让 `PortraitRenderPostProcessor` 使用 saved-photo subject mask。
   - 人物区域做美颜/肤色保护，背景区域做虚化/光斑/背景 bloom，mask 不可用时诚实降级回当前椭圆焦点实现。

3. [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md)
   - 在 mode/settings/effect metadata 层定义人像 profile、光斑、滤镜、美颜如何组合成前景/背景分层 spec。
   - 先做可测试的产品语义和 metadata，不在同一轮追求厂商级算法。

4. [Portrait Mask Visual QA And Acceptance](./2026-05-25-portrait-mask-visual-qa.md)
   - 给非多模态 agent 本地验证命令，也保留 Codex/user 的真机视觉验收。
   - 明确哪些东西单测可判，哪些必须看保存 JPEG 和预览录屏。

## Recommended Order

1. 先执行 preview analysis fanout stability，避免人像预览/Live/scene signal 互相抢帧。
2. 再执行 mask-aware portrait saved rendering，先把保存 JPEG 的人物/背景分层做实。
3. 然后执行 portrait effect layer contracts，把光斑、滤镜、美颜的组合语义固化到 metadata/spec/tests。
4. 最后按 visual QA 文档做真机验收，再决定是否开放更强预览效果或默认启用。

## Codex-Retained Work

- 最终视觉判断：肤色是否自然、发丝/肩膀/眼镜边缘是否有抠图感、背景虚化和光斑是否像相机产品而不是滤镜 demo。
- 人像产品取舍：哪些 profile 默认开放，mask confidence 多低时应降级，是否默认启用背景增强。
- 多模态验收：预览录屏和保存 JPEG 的一致性对比。

## Delegable Work

- `ImageAnalysis` fanout owner 与单元测试。
- `PortraitRenderPostProcessor` mask provider/cache/editor interface 的本地实现。
- synthetic mask 测试：中心人物、边缘 feather、无人物/低置信度降级。
- metadata/spec 组合测试：profile -> beauty/bokeh/filter -> processor spec。

## Blocked Or Deferred

- 自研通用人体/深度模型。
- 真深度估计和发丝级抠图。
- GPU 实时人像预览渲染。
- 视频人像和 Live 动态段逐帧人像效果。

## Global Acceptance Criteria

- 人像模式有一个明确的 person subject mask 基础；保存成片能基于 mask 区分人物和背景。
- mask 不可用、低置信度或模型失败时，capture 不失败，pipeline notes 诚实记录 degraded/unsupported/failed。
- 光斑、滤镜、美颜通过同一套 portrait effect spec 进入后处理，不各自重复解释 mode metadata。
- 不把 mask 像素放进 `SessionState`、mode state 或 persisted settings。
- `rtk ./scripts/verify_stage_7_observability.sh` 仍可作为阶段门禁。

