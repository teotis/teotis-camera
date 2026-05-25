# Scene Mask Segmentation For Color And Portrait Index

日期：2026-05-25

## Goal

把用户提出的“体块识别能力”收敛为 OpenCamera 可落地的 `Scene Mask` 能力：先用成熟开源/公开 SDK 做人像主体 mask，在预览期和成片后处理期分别提供近似/高质量 mask，从而支撑 Color Lab 的主体保护、背景调色、人像前后景处理和后续景深/语义扩展。

## Decision

推荐第一阶段采用 **人像/主体分割 mask**，不要先做通用深度估计、自研模型或全场景语义分割。

原因：

- 当前用户痛点是 Color Lab、人像、前后景、自然色彩优化；先知道“人/主体 vs 背景”已经能解决最大一块体验问题。
- 本仓已有 `ImageAnalysis` 预览帧入口和 `CompositeMediaPostProcessor` 保存后处理入口，适合接入 mask pipeline。
- 自研模型、通用语义分割、深度估计都需要质量、性能、模型包体、设备兼容和视觉 QA 的长期投入，不适合作为第一闭环。

## External Options

### ML Kit Selfie Segmentation

推荐作为第一后端。

- 官方文档说明该能力可对自拍/人像视频或照片分割主体，输出每个像素的 mask，可用于背景替换、背景虚化等场景。
- Android API 支持 `STREAM_MODE` / `SINGLE_IMAGE_MODE`，适合预览期和成片期分别配置。
- 文档明确该 API 仍处于 beta，因此必须用 `supported / degraded / unsupported` 语义包住，不能作为不可降级的核心路径。

Sources:

- https://developers.google.com/ml-kit/vision/selfie-segmentation
- https://developers.google.cn/ml-kit/vision/selfie-segmentation/android?hl=en

### MediaPipe Image Segmenter

推荐作为第二阶段或可替换后端。

- 官方 MediaPipe Tasks 提供 Android Image Segmenter，可输出 category mask 或 confidence mask。
- 更适合未来扩展到人/天空/物体等多类别语义，但第一版接入成本和模型选择成本高于 ML Kit selfie。

Sources:

- https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter
- https://ai.google.dev/edge/mediapipe/solutions/vision/image_segmenter/android

### TensorFlow Lite / ONNX Runtime 自管模型

建议暂缓。

- 优点：模型可控、可离线、可替换，长期自主性更好。
- 缺点：模型选择、量化、输入输出格式、NNAPI/GPU delegate、性能与质量验收都要自己承担。
- 可作为后续 `SceneMaskBackend.TFLITE` / `SceneMaskBackend.ONNX`，不作为第一阶段默认路径。

Sources:

- https://www.tensorflow.org/lite/examples/segmentation/overview
- https://onnxruntime.ai/docs/tutorials/mobile/

### CameraX Extensions / Camera2 Depth

只作为能力增强，不作为通用基础。

- CameraX Extensions 可以利用设备厂商效果，但黑盒且设备差异大。
- Camera2 `DEPTH_OUTPUT` / `DEPTH16` 依赖硬件能力，覆盖率和帧率都不可假设。

Sources:

- https://developer.android.com/media/camera/camerax/extensions
- https://developer.android.com/reference/android/hardware/camera2/CameraMetadata#REQUEST_AVAILABLE_CAPABILITIES_DEPTH_OUTPUT

## Work Packages

1. [Scene Mask Contracts And Capability](./2026-05-25-scene-mask-contracts-capability.md)
   - Pure Kotlin first. Defines `SceneMask`, transform metadata, capability/degradation semantics, and capture metadata tags.
   - Can be implemented before adding any external ML dependency.

2. [Preview Subject Mask Pipeline](./2026-05-25-preview-subject-mask-pipeline.md)
   - Android app layer. Adds low-resolution preview subject mask source through `ImageAnalysis`, initially ML Kit selfie segmentation behind an interface.
   - Must not put mask bitmaps into `SessionState`.

3. [Saved Photo Mask Rendering](./2026-05-25-saved-photo-mask-rendering.md)
   - App/media layer. Re-runs segmentation on saved JPEG or decoded bitmap for higher-quality postprocessing, then applies Color Lab/person/background-aware render logic.
   - Keeps EXIF/output handle behavior unchanged.

4. [Scene Mask Verification And Visual QA](./2026-05-25-scene-mask-verification-qa.md)
   - Local tests plus real-device acceptance protocol.
   - Explicitly reserves final “自然但明显” judgment for Codex/user with saved JPEG and screen recording.

## Recommended Order

1. Implement contracts and fake backend first.
2. Implement saved-photo postprocess using synthetic masks and tests.
3. Add preview subject mask source with ML Kit behind `SceneMaskBackend`.
4. Integrate preview render approximation.
5. Run local gates.
6. Codex/user performs real-device visual QA.

## Codex-Retained Work

- Final choice between ML Kit and MediaPipe after dependency/package-size build evidence.
- Multimodal QA: preview recording vs saved JPEG comparison.
- Product call on whether mask-based Color Lab should be enabled by default or only when confidence is high.

## Delegable Work

- Pure contracts, serializers, metadata tags, fake mask source tests.
- ML Kit adapter implementation behind an interface.
- Saved-photo renderer tests with synthetic masks.
- Verification script updates.

## Blocked Or Deferred

- Self-trained model.
- Full semantic scene segmentation.
- Real hardware depth map pipeline.
- Pixel-perfect live GPU preview renderer.

## Global Acceptance Criteria

- No visible feature becomes fake: if segmentation is unavailable, UI/output metadata must say degraded or unsupported.
- Preview mask and saved-photo mask share coordinate/crop metadata, even if they are produced at different resolutions.
- Color Lab gains subject/background-aware behavior without breaking existing no-mask behavior.
- Final JPEG remains editable through existing `MediaOutputHandle` path and does not skip downstream watermark/selfie mirror processors.
- Stage 7 observability gate remains passable after integration.
