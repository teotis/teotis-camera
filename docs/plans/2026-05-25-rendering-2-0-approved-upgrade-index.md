# Rendering 2.0 Approved Upgrade Index

日期：2026-05-25

## Package Goal

把 OpenCamera 当前渲染能力升级到可进入 2.0 判断的产品链路：拍照保存必须可靠，`RenderRecipe` 必须成为预览/成片/缩略图/诊断的单一渲染真相，Color Lab V2 必须在不增加进阶工具和分享功能的前提下做到可感知、自然、可验证。

## User Request

用户认可本轮规划的三个升级方向：

- 拍照保存可靠性。
- `Render Recipe V2` 单一真相。
- `Color Lab V2` 感知式调色。

明确不纳入本轮：

- Color Lab 进阶工具。
- Color Lab 配方分享/导入/导出。

## Package Documents And Ownership

| Document | Owner | Status | Purpose |
| --- | --- | --- | --- |
| [Rendering 2.0 Capture Save Reliability](./2026-05-25-rendering-2-0-capture-save-reliability.md) | Text/code agent + Codex/user real-device smoke | accepted locally | `guardedPostProcess(...)` outer guard landed; `PostprocessOuterGuardTest` passes; real-device smoke remains pending. |
| [Rendering 2.0 Render Recipe Single Truth](./2026-05-25-rendering-2-0-render-recipe-single-truth.md) | Text/code agent | accepted locally | `RenderRecipe.from(ShotResult)` canonical fallback and recipe codec round-trip landed; real-device metadata verification remains pending. |
| [Rendering 2.0 Color Lab Perceptual Rendering](./2026-05-25-rendering-2-0-color-lab-perceptual-rendering.md) | Text/code agent + Codex/user visual QA | accepted locally (approximate preview only) | `PreviewColorTransform`/`PreviewColorFidelity` model honest approximate/degraded states; NOT real PreviewView color pipeline parity; real-device visual QA remains pending. |

## Recommended Execution Order

1. Run `Rendering 2.0 Capture Save Reliability` first. Color Lab visual work cannot be trusted until saved JPEG output is reliable.
2. Run `Rendering 2.0 Render Recipe Single Truth` second. Color Lab V2 should not add another side channel for recipe state.
3. Run `Rendering 2.0 Color Lab Perceptual Rendering` third. This consumes the recipe contract and keeps preview/saved output aligned.
4. Codex/user retain final multimodal acceptance: real-device screen recording, saved JPEG samples, thumbnail transition, gallery open, and diagnostics.

## Current Verified Facts

- `docs/plans/INDEX.md` is the canonical planning index for this project.
- Existing related plans:
  - [Color Lab Capture Save Regression](./2026-05-25-color-lab-capture-save-regression.md)
  - [Color Lab Perceptual Strength And Consistency](./2026-05-25-color-lab-perceptual-strength-and-consistency.md)
  - [Render Recipe And Preview Engine Handoff](./2026-05-22-render-recipe-preview-engine-handoff.md)
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PerceptualColorRecipe.kt` already defines `PerceptualColorRecipe` and `ColorLabSpec.toRecipe(...)`.
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` already parses recipe-like tags and applies perceptual adjustments, including a mask-aware path.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt` currently writes `filterProfile` and `filterSpec.*`, but does not write `PerceptualColorRecipe` tags.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/RenderRecipe.kt` currently covers filter/frame/watermark/selfie mirror, but not perceptual recipe, preview fidelity, or color-science provenance.
- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` still primarily derives tint overlay and mask availability; it does not yet build a real recipe-backed color transform matrix.
- `core/media/src/main/kotlin/com/opencamera/core/media/MediaPostProcessors.kt` runs processors sequentially, but does not yet provide a generic fail-soft boundary if an optional processor throws.
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt` calls `mediaPostProcessor.process(rawResult)` before emitting `ShotCompleted`; an uncaught optional postprocess exception can still turn a captured image into a failed shot path.

## Delegation Boundaries

Delegable to non-multimodal agents:

- Kotlin contract/model changes.
- Metadata codec and parser updates.
- Unit and integration tests.
- Verification script updates if needed.
- Pipeline notes and diagnostics.

Codex/user retained:

- Real-device visual judgment of Color Lab naturalness.
- Saved JPEG comparison against preview recording.
- Final 2.0 GO / NO GO judgment.

## Completion Notes

Package status is `implemented`. Three sub-plans landed on branch `feat/rendering-2-0-upgrade`.

## Implemented Commits

| Commit | Sub-Plan | Summary |
| --- | --- | --- |
| cc76237 | Capture Save Reliability | `captureStillImage` 和视频路径中 postprocess 异常不再删除已保存的照片，改为 emit ShotFailed |
| ce250f3 | Render Recipe Single Truth | `PerceptualColorRecipe` metadata 迁移到 canonical `recipe.` 前缀，兼容旧格式，添加 round-trip 测试 |
| 3612c32 | Color Lab Perceptual Rendering | 配色曲线死区+加速边缘，预览变换综合使用多参数，弱变换报告 DEGRADED，添加 corner/protection/monochrome 测试 |

## Verification Results

- `:app:assembleDebug` — BUILD SUCCESSFUL
- `:core:media:test` (CompositeMediaPostProcessorTest) — PASS
- `:core:settings:test` (PerceptualColorRecipeTest, StyleColorPipelineTest, ColorLabSpecTest) — PASS
- `:core:effect:test` (PreviewEffectAdapterTest, RenderRecipeTest, EffectBridgeTest) — PASS
- `:app:testDebugUnitTest` (PhotoAlgorithmPostProcessorTest) — PASS
- `DefaultCameraSessionTest` — pre-existing OOM (exit 143) in local environment, not caused by these changes

## Residual Device-Only Risks

- Real-device Color Lab visual acceptance (corner/edge naturalness, preview vs saved alignment) remains with Codex/user.
- DefaultCameraSessionTest needs real-device or larger-memory CI for reliable execution.
- Preview color matrix fidelity vs per-pixel saved render cannot be proven with unit tests alone.

