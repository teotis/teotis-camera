# Color Lab Real-Device Follow-Up Index

日期：2026-05-25

## Goal

把最新真机反馈中的两个 Color Lab 问题拆成可执行闭环：一条处理“最大效果依然偏淡”，另一条处理“选择色彩实验室后点击拍照有动画但没有图片拍摄”。两条都必须保持项目四层边界：UI 只分发意图，Session Kernel 拥有拍摄状态，Device Adapter 执行 CameraX，Media Pipeline 负责后处理和保存。

## Context

- User request:
  - `7，色彩实验室最大效果依然偏淡。`
  - `8，选择了色彩实验室以后，点击拍照虽然有动画，但没有图片拍摄。`
- Verified facts:
  - `ColorLabSpec.toMapping()` 已做过一轮数值增强，说明继续只加全局 scalar 很可能收益递减。
  - `StyleColorPipeline` 的 `TEXTURE / VIVID / MONOCHROME` 分支仍有 style-specific guard，部分风格可能把 Color Lab 边界响应压得不明显。
  - 预览 overlay 类近似无法真正表达 tone curve、chroma、highlight/shadow 和保护区，所以真机预览可能比 metadata/成片更淡。
  - `PhotoModePlugin.buildEffectSpec()` 会把 `photo.colorLabSpec` 合成进 `FilterEffect`，拍照 metadata 会包含 `filterSpec.*`。
  - `captureFeedbackPolicyFor()` 对 Color Lab/filter shot 会抑制 raw preview feedback，必须等最终 saved media；如果后处理失败、超时或没有回灌 `ShotCompleted`，用户会看到“有动画但没有图片”。
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/PreviewRecoverySessionProcessor.kt`
  - `core/session/src/main/kotlin/com/opencamera/core/session/CaptureRecordingSessionProcessor.kt`
- Non-goals:
  - 不新增专业参数列表来替代二维 Color Lab。
  - 不把拍照失败伪装为 UI 动效问题。
  - 不让非多模态 agent 负责判断“明显但自然”的最终视觉质量。

## Work Packages

1. [Color Lab Capture Save Regression Plan](2026-05-25-color-lab-capture-save-regression.md)
   - Purpose: 解决选择 Color Lab 后拍照没有最终图片。
   - Status: blocked after validation.
   - Owner: non-multimodal implementation agent for code/tests/logging; Codex/user for real-device smoke.

2. [Color Lab Perceptual Strength And Consistency Plan](2026-05-25-color-lab-perceptual-strength-and-consistency.md)
   - Purpose: 解决最大效果偏淡。
   - Status: blocked after validation.
   - Owner: non-multimodal implementation agent for code/tests; Codex/user for visual QA.

## Validation Result After External-Agent Delivery

当前仓内落地效果未通过原计划验收。已确认的阻断点：

- capture/save 方向：`PhotoAlgorithmPostProcessorTest` 仍有多项失败，mask source/decode 路径仍可能在 fallback 前抛出异常。
- recipe bridge 方向：`PerceptualColorRecipe` 已在 mode plugin 侧生成，但没有通过 `EffectBridge` 写入 capture metadata，saved-photo 后处理仍可能读到中性 recipe。
- preview 方向：`PreviewEffectAdapter` 未消费 `FilterEffect.recipe`，Color Lab 最大效果仍无法在预览中得到足够可信的近似。
- app coordinator 方向：`CameraSessionCoordinatorTest` 中 still quality/resolution bind rebind 失败，需要在同一修复轮确认是否为行为回归。

Follow-up package:

- [Color Lab Validation Blocker Follow-Up Index](2026-05-25-color-lab-validation-blocker-followup-index.md)
  - Status: planned.
  - Purpose: 把上述验收失败拆成 postprocess fail-soft、recipe metadata bridge、preview recipe transform 三个阻断修复包。

## Recommended Order

1. 先执行 follow-up 中的 postprocess fail-soft/test repair。保存链路不可靠时，任何“效果强度”验收都没有可靠样张。
2. 再执行 recipe metadata bridge repair。保存 JPEG 必须消费同一非中性 recipe。
3. 再执行 preview recipe transform repair。预览应消费同一 recipe 并诚实标记 approximate/degraded。
4. 最后由 Codex/user 做多模态验收：确认保存媒体存在、缩略图更新、预览和成片方向一致、效果明显但自然。

## Global Acceptance Criteria

- Color Lab 打开或关闭时，Photo mode shutter 仍能发起真实 still capture。
- Color Lab 活跃时，保存链路要么成功产出 saved JPEG，要么明确显示 `ShotFailed` / degraded notes；不允许只有动画、无图片、无错误。
- Color Lab 四角和边缘在真机预览与保存 JPEG 中都有明确方向变化。
- 中心点和 reset 能回到接近中性。
- 失败诊断必须能从 session trace / pipeline notes / logcat 中定位到 UI dispatch、device capture、postprocess、MediaStore 中的具体阶段。

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- 真机反馈已经说明纯本地 unit/assemble 不足以判定产品 pass。实现 agent 可以完成 deterministic 修复，但最终必须复测保存媒体和视觉效果。
- 这属于 Stage 7 中由真机反馈驱动的 feature-quality/stability repair，不是进入新 stage 的授权。
