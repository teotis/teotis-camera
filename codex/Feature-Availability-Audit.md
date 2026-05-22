# Feature Availability Audit

> 并行任务 C：功能可触达与有效可用审查
> 审查日期：2026-05-22
> 审查范围：codex_camera 2.0 全部可见功能

## Summary

| Result | Count |
| --- | --- |
| Pass | 32 |
| Risk | 3 |
| Fail | 2 |

## Feature Matrix

### 基础功能

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 拍照 | PhotoModePlugin shutter | ModeSnapshot | CaptureStrategy.SingleFrame → DeviceGraphSpec.stillCapture | ShotExecutorTest, PhotoAlgorithmPostProcessorTest | SUPPORTED | Pass |
| 录像 | VideoModePlugin shutter | ModeSnapshot | CaptureStrategy.VideoRecording → VideoCapture+Recorder | VideoSpecSelectionTest, CameraXCaptureAdapterRecordingQualityTest | SUPPORTED with degradation | Pass |
| 缩略图回看 | CameraCockpit bottomCockpit | ThumbnailRenderCommand | MediaStore query + decode | ThumbnailRenderCommandTest | SUPPORTED | Pass |
| 前后镜头 | CameraCockpit lens button | availableLensFacings | CameraX bindToLifecycle per lens | CameraXCaptureAdapterCapabilityDetectionTest | SUPPORTED | Pass |
| 变焦(离散) | ZoomStripRenderModel | ZoomRatioCapability.DISCRETE_PRESET | Camera2 CONTROL_ZOOM_RATIO_RANGE detection, normalized 0.1 steps | CameraXCaptureAdapterCapabilityDetectionTest | SUPPORTED | Pass |
| 变焦(连续) | ZoomControlSupport.CONTINUOUS | enum defined but detection never returns it | N/A | N/A | UNSUPPORTED (not detected) | Risk |

### 模式

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| Photo | ModeTrackRenderModel | PhotoModePlugin | Full pipeline: SingleFrame/LivePhoto + FilterEffect + WatermarkEffect + FrameEffect | ModeProductDeclarationTest, ModeCaptureStrategyGraphTest | SUPPORTED | Pass |
| Video | ModeTrackRenderModel | VideoModePlugin | Full pipeline: VideoRecording + FilterEffect + torch/resolution/fps degradation | ModeProductDeclarationTest, VideoSpecSelectionTest | SUPPORTED with degradation | Pass |
| Night/Scenery | ModeTrackRenderModel | NightModePlugin | Multi-frame (6/8/12 frames) or Single-frame fallback + FrameEffect | ModeCapabilityDegradationTest, ModeCaptureStrategyGraphTest | SUPPORTED/DEGRADED | Pass |
| Portrait | ModeTrackRenderModel | PortraitModePlugin | Depth/Focus pipeline + FilterEffect + PortraitEffect + FrameEffect | ModeCapabilityDegradationTest, PortraitRenderPostProcessorTest | SUPPORTED/DEGRADED | Pass |
| Document | ModeTrackRenderModel | DocumentModePlugin | Enhanced/Basic pipeline + DocumentEffect (autoCrop + contrast) | ModeCapabilityDegradationTest, DocumentAutoCropPostProcessorTest | SUPPORTED/DEGRADED | Pass |
| Humanistic | 内部保留(隐藏于 UI) | HumanisticModePlugin | Full pipeline: SingleFrame/LivePhoto + FilterEffect + FrameEffect | ModeProductDeclarationTest | SUPPORTED (hidden) | Pass |
| Pro | ModeTrackRenderModel | ProModePlugin | Manual/Assisted pipeline + FrameEffect + Camera2 interop | ModeCapabilityDegradationTest, CameraXCaptureAdapterManualRequestTest | SUPPORTED/DEGRADED | Pass |

### 快捷控制

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 网格 | PreviewOverlayRenderModel | CompositionGridMode (OFF/THIRDS/GOLDEN) | Preview overlay line rendering | PreviewOverlayGeometryTest | SUPPORTED | Pass |
| 画幅 | FrameRatioControlRenderModel | FrameRatio (4:3/16:9/1:1) → PhotoFrameRatioPostProcessor | Center-crop post-processing + preview geometry | PhotoFrameRatioPostProcessorTest, PreviewOverlayGeometryTest | SUPPORTED | Pass |
| 闪光 | PhotoModePlugin secondary action | supportsFlashControl gate | CameraX ImageCapture.flashMode + torch control | CameraXCaptureAdapterCapabilityDetectionTest, DefaultDeviceShotRequestTranslatorTest | SUPPORTED/UNSUPPORTED | Pass |
| 定时 | PreviewOverlayRenderModel countdown | CountdownDuration (OFF/3s/5s/10s) | Mode countdown display + delayed capture | ModeCaptureStrategyGraphTest | SUPPORTED | Pass |
| Live Photo | PhotoModePlugin livePhotoEnabled | LiveMediaBundle → LiveTemporalAssemblyPlanner | Temporal planner exists; adapter hardcodes METADATA_ONLY → STILL_ONLY_FALLBACK | ShotExecutorTest, LiveTemporalAssemblyPlannerTest | DEGRADED (STILL_ONLY_FALLBACK) | Risk |
| 质量/分辨率 | SessionSettingsPageRenderModel | stillCaptureQuality + resolutionPreset | CameraX binding with closest native output size | CameraXCaptureAdapterStillCaptureQualityTest | SUPPORTED | Pass |

### 风格

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 预设风格 | FilterLabPageRenderModel | FilterProfile (18 built-in, 3 categories) | StyleColorPipeline.render() two-stage blending | StyleColorPipelineTest, FilterProfileShareCodecTest | SUPPORTED | Pass |
| 滤镜 | FilterLabPageRenderModel | FilterRenderSpec (15 params) | PhotoAlgorithmPostProcessor per-pixel CPU rendering (24+ profiles) | PhotoAlgorithmPostProcessorTest | SUPPORTED | Pass |
| 强度 | FilterLabPageRenderModel | styleStrength (0..1) | StyleColorPipeline base blending | StyleColorPipelineTest | SUPPORTED | Pass |
| 自定义滤镜 | FilterLabPageRenderModel | createCustomFilterProfile + FilterProfileShareCodec | Export/import round-trip | FilterProfileShareCodecTest | SUPPORTED | Pass |

### 色彩实验室

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 二维调色板 | ColorLabPanelRenderModel | ColorLabSpec (colorAxis -1..1, toneAxis -1..1) | StyleColorPipeline COLOR_LAB_SECONDARY stage | ColorLabSpecTest, StyleColorPipelineTest | SUPPORTED | Pass |
| 重置 | ColorLabPanelRenderModel | ColorLabSpec neutral (0, 0, 1) | Reset to neutral palette | ColorLabSpecTest | SUPPORTED | Pass |
| 强度 | ColorLabPanelRenderModel | ColorLabSpec.strength (0..1) | Apply weight in pipeline | ColorLabSpecTest | SUPPORTED | Pass |
| 预览/成片接线 | FilterLabPageRenderModel | renderStyleColorSpec() | EffectSpec → PostProcessSpec metadata bridge; 4 color science modes (NATURAL/TEXTURE/VIVID/MONOCHROME) | StyleColorPipelineTest | SUPPORTED | Pass |

### 专业控制

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| ISO | RuntimeProControlsRenderModel | ManualControlSupport.APPLY | Camera2Interop SENSOR_SENSITIVITY + CONTROL_AE_MODE_OFF | CameraXCaptureAdapterManualRequestTest, DefaultDeviceShotRequestTranslatorTest | SUPPORTED | Pass |
| 快门速度 | RuntimeProControlsRenderModel | ManualControlSupport.APPLY | Camera2Interop SENSOR_EXPOSURE_TIME | 同上 | SUPPORTED | Pass |
| EV | RuntimeProControlsRenderModel | ManualControlSupport.APPLY | Camera2Interop exposure compensation | 同上 | SUPPORTED | Pass |
| AF | RuntimeProControlsRenderModel | ManualControlSupport.APPLY | Camera2Interop CONTROL_AF_MODE_OFF + LENS_FOCUS_DISTANCE | 同上 | SUPPORTED | Pass |
| AWB/WB | RuntimeProControlsRenderModel | ManualControlSupport.SAVED_ONLY | Saved to metadata only, not enforced at capture | CameraXCaptureAdapterManualRequestTest | SAVED_ONLY (DEGRADED) | Pass |
| RAW | RuntimeProControlsRenderModel | rawEnabled flag | No DNG output; flag persisted to metadata only | CameraXCaptureAdapterManualRequestTest | FLAG ONLY (UNSUPPORTED) | Fail |
| 光圈 | RuntimeProControlsRenderModel | ManualControlSupport.APPLY | Camera2Interop LENS_APERTURE | CameraXCaptureAdapterManualRequestTest | SUPPORTED | Pass |

### 媒体后处理

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| 水印 | WatermarkLabSelectorRenderModel | WatermarkTemplate (3 templates: classic-overlay, travel-polaroid, retro-frame) | PhotoWatermarkPostProcessor Canvas bitmap rendering with EXIF restore | PhotoWatermarkPostProcessorTest, PhotoWatermarkTemplateResolverTest | SUPPORTED | Pass |
| 边框 | WatermarkLabDetailRenderModel | supportsFrameBorder flag | drawExpandedFrame in watermark templates (polaroid/retro) | PhotoWatermarkPostProcessorTest | SUPPORTED | Pass |
| 自拍镜像 | SessionSettingsPageRenderModel | selfieMirrorEnabled | PhotoSelfieMirrorPostProcessor Matrix preScale horizontal flip | PhotoSelfieMirrorPostProcessorTest | SUPPORTED | Pass |
| 文档裁切 | DocumentModePlugin | DocumentEffect.autoCrop | DocumentAutoCropPostProcessor luminance-based edge detection | DocumentAutoCropPostProcessorTest | SUPPORTED | Pass |
| 人像渲染 | PortraitLabPageRenderModel | PortraitEffect (depth/focus renderPath + beauty + bokeh) | PortraitRenderPostProcessor scale-blur + elliptical focus blending; fallback to focus when segmentation unavailable | PortraitRenderPostProcessorTest | SUPPORTED/DEGRADED | Pass |
| 多帧合并 | NightModePlugin | MultiFrameMergeStrategy | MultiFrameMergePlaceholderPostProcessor (logs inputs + deletes temps, no real merge) | MultiFrameMergeAlgorithmProcessorTest | PLACEHOLDER (UNSUPPORTED) | Fail |
| 视频水印字幕 | VideoModePlugin | VideoWatermarkSubtitlePostProcessor | SRT file generation via MediaMetadataRetriever | VideoWatermarkSubtitlePostProcessorTest | SUPPORTED (sidecar only) | Pass |

### 诊断

| Feature | Entry | State owner | Execution owner | Result evidence | Support semantics | Risk |
| --- | --- | --- | --- | --- | --- | --- |
| Dev log | DevConsole route | DevLogRenderModel | Debug/release tabs, export content, timing events, resource diagnostics | DevLogRenderModelTest | SUPPORTED | Pass |
| Runtime issues | DevConsole route | RuntimeIssue forwarding | CameraSessionCoordinator event forwarding, bind failure classification, thermal monitoring | CameraSessionCoordinatorTest, CameraXCaptureAdapterRuntimeIssueTest | SUPPORTED | Pass |

## P0/P1 Feature Defects

### P0 — 假入口（入口可见但无真实执行）

#### 1. RAW 输出是假入口

- **现象**: `RuntimeProControlsRenderModel` 中 RAW 有 on/off toggle，`rawEnabled` 持久化到 `ManualCaptureParams`，序列化到 metadata tag `manualDraftRaw`。
- **实际**: `ManualControlCapabilityMatrix.raw` 默认 `SAVED_ONLY`，`FilteredManualCaptureParams` 在 capability 非 `APPLY` 时 strip 为 `false`。CameraXCaptureAdapter 从未配置 RAW stream 或 `CAPTURE_MODE_MAXIMIZE_QUALITY` + DNG 输出。
- **影响**: 用户开启 RAW 后得到的仍是 JPEG，无 DNG 文件生成。UI 未标注当前状态为 "SAVED_ONLY"。
- **建议**: 要么实现 Camera2 RAW capture (ImageReader + DNG)，要么在 UI 中明确标注 "仅记录设置" 或 "即将支持"。
- **代码位置**: `DeviceContracts.kt:61-68` (CAMERA2_INTEROP_DEFAULT), `CameraXCaptureAdapter.kt:304-340` (applyCamera2ManualCaptureConfig), `SessionUiRenderModel.kt:1211-1333` (RuntimeProControlsRenderModel)

#### 2. 多帧合并是占位符

- **现象**: Night 模式在 `supportsNightMultiFrame=true` 时发射 `CaptureStrategy.MultiFrame` (6/8/12 frames)，UI 展示帧数和三脚架检测。
- **实际**: `MultiFrameMergePlaceholderPostProcessor` 只记录输入帧路径并删除临时文件，无真实合并算法。效果等同于单帧。
- **影响**: Night 模式多帧拍摄的降噪/HDR 效果不存在。用户期望的"夜景增强"实际为单帧亮度提升。
- **建议**: 集成真实多帧合并算法（如 Google HDR+ 或自研对齐+融合），或在 UI 降级提示"算法集成中"。
- **代码位置**: `MediaPipelineContracts.kt` (MultiFrameMergePlaceholderPostProcessor), `NightModePlugin.kt` (multi-frame profiles)

### P1 — 功能降级未告知

#### 3. Live Photo 始终降级为静态图

- **现象**: Photo/Portrait/Humanistic 模式支持 Live Photo toggle，`LiveTemporalAssemblyPlanner` 有完整的 4 种 motion source 规划。
- **实际**: `CameraXCaptureAdapter.captureLivePhoto` 硬编码 `availableSource = METADATA_ONLY`，`ringBufferDepthMillis = 0`，`postShutterBudgetMillis = 0`。temporal plan 始终降级为 `STILL_ONLY_FALLBACK`，`frameCount = 0`。sidecar JSON 有 temporal window 结构但无实际运动帧。
- **影响**: 用户开启 Live Photo 后得到的是静态图 + 空 sidecar。无任何 UI 提示当前为 still-only。
- **建议**: 实现 preview ring buffer 采集，或在 UI 标注"Live Photo 暂为静态模式"。
- **代码位置**: `CameraXCaptureAdapter.kt:1258-1317` (captureLivePhoto), `LiveTemporalAssemblyPlanner.kt`

#### 4. 连续变焦已定义但未实现

- **现象**: `ZoomControlSupport.CONTINUOUS` 枚举值已定义，UI 有 `ZoomStripRenderModel` 展示 zoom chips。
- **实际**: `detectZoomRatioCapability` 始终返回 `DISCRETE_PRESET`，从未检测到 CONTINUOUS。捏合缩放依赖 CONTINUOUS 语义可能无法工作。
- **影响**: 如果 UI 层依赖 CONTINUOUS 做捏合缩放，该交互将失败。
- **建议**: 确认 UI 是否依赖 CONTINUOUS；如果是，则需实现或回退到 discrete 步进。
- **代码位置**: `DeviceContracts.kt:261-284` (ZoomRatioCapability), `CameraXCaptureAdapter.kt:898-943` (detectZoomRatioCapability)

## Unsupported/Degraded Semantics Gaps

| 功能 | 当前语义 | 期望语义 | Gap |
| --- | --- | --- | --- |
| RAW | `SAVED_ONLY` — flag 保存到 metadata，无 DNG | `UNSUPPORTED` 或真实 RAW 输出 | 用户无法区分 "RAW 已启用" 和 "RAW 未生效" |
| Live Photo | `STILL_ONLY_FALLBACK` — sidecar 有结构但 frameCount=0 | `DEGRADED_MOTION` 或 `COMPLETE` | 用户无法得知 Live Photo 未录制运动 |
| 多帧合并 | Placeholder — 记录输入后删除 | 真实合并或 `UNSUPPORTED` | Night 模式展示帧数但实际无合并效果 |
| 连续变焦 | `CONTINUOUS` 定义但检测永不返回 | 检测返回 `CONTINUOUS` 或移除枚举 | 潜在的死代码和未定义行为 |
| WB | `SAVED_ONLY` — 保存到 EXIF 但不应用 | `APPLY` 或 UI 标注 "仅记录" | 用户设置 WB 后预览和成片色温不变 |
| 视频水印 | Sidecar SRT 字幕文件 | 帧级渲染或 sidecar | 视频无视觉水印，仅有字幕 sidecar |

## Recommended Regression Tests

基于审查发现，建议补充以下回归测试：

### 优先级 P0

1. **RAW 输出语义测试**: 验证 `ManualControlCapabilityMatrix.raw` 为 `SAVED_ONLY` 时，capture pipeline 不产生 DNG 文件，且 `FilteredManualCaptureParams.rawEnabled` 被 strip。
2. **多帧合并占位符行为测试**: 验证 `MultiFrameMergePlaceholderPostProcessor` 在多帧输入下只删除临时文件、不修改输出，与单帧行为一致。
3. **Live Photo 降级测试**: 验证 `captureLivePhoto` 在 `METADATA_ONLY` source 下产生 `STILL_ONLY_FALLBACK` bundle，且 `frameCount = 0`。

### 优先级 P1

4. **连续变焦检测测试**: 验证 `detectZoomRatioCapability` 在所有设备上返回 `DISCRETE_PRESET`，不返回 `CONTINUOUS`。
5. **WB SAVED_ONLY 测试**: 验证 white balance 参数写入 EXIF 但不影响 Camera2 CaptureRequest。
6. **色彩实验室端到端测试**: 验证 `ColorLabSpec` 调整通过 `StyleColorPipeline.render()` 影响 `FilterRenderSpec`，并正确序列化到 `PostProcessSpec` metadata。
7. **画幅预览一致性测试**: 验证 `FrameRatio` 切换同时更新 preview overlay geometry 和 capture post-processing crop bounds。

## Handoff To IO / Multimodal

### 不在本次审查范围

- 成片画质判断（色彩准确度、锐度、噪点水平）
- 厂商私有能力（特定 ISP 特性、硬件 HDR）
- 实际设备上的 Camera2 interop 兼容性
- 多帧合并算法的实际效果评估

### 交接项

1. **RAW capture 实现**: 需要 Camera2 ImageReader + DNG writer 集成，涉及 `CameraXCaptureAdapter` 和 `DeviceShotRequestTranslator`。
2. **Live Photo ring buffer**: 需要 CameraX Preview frame stream 回调 + 临时文件管理，涉及 `LiveTemporalAssemblyPlanner` 和 adapter 层。
3. **多帧合并算法**: 需要图像对齐 + 融合算法集成，涉及 `MultiFrameMergePlaceholderPostProcessor` 替换。
4. **视频帧级水印**: 需要 MediaCodec 或 FFmpeg 帧级处理，当前仅有 SRT sidecar。

### 测试证据汇总

| 模块 | 测试类数 | 测试用例数 | 状态 |
| --- | --- | --- | --- |
| core/mode | 4 | ~38 | 全部通过 |
| core/settings | 4 | ~26 | 全部通过 |
| core/device | 4 | ~33 | 全部通过 |
| core/media | 10 | ~97 | 全部通过 |
| app/camera | 16 | ~133 | 全部通过 |
| app/ui | 9 | ~60+ | 全部通过 |
