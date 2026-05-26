# Color Lab Perceptual Strength And Consistency Plan

日期：2026-05-25

## Goal

解决真机反馈：“色彩实验室中调色板拉满，画面变动幅度仍然较小”。目标不是简单做成重口滤镜，而是在保持两个产品限制的前提下，让调色板四边和四角有明确、可感知、可验证的视觉变化：

- 预览和成片效果近似。
- 色彩改变自然，不生硬、不塑料、不明显污染肤色和中性灰。

Latest real-device follow-up on 2026-05-25 says the maximum Color Lab effect is still too pale. Treat this document as the active implementation package for issue 7, but execute it after the Color Lab capture/save regression is fixed so saved JPEG samples are trustworthy.

## Context

- User request: 真机实测发现 Color Lab 调色板拉满效果仍然偏弱，希望参考 vivo 蓝图调色板、Apple 调色盘，或为 OpenCamera 设计新思路。
- Verified facts:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt` 已有一轮增强：边界亮度、对比、饱和、暖冷、shadow/highlight、warm/cool boost 都比早期方案更强。
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt` 已通过 `renderStyleColorSpec(...)` 把 `photo.colorLabSpec` 合成进 `FilterEffect`，预览和拍照元数据使用同一个 `FilterRenderSpec`。
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt` 当前预览只生成低透明度 tint overlay 与 vignette。它不真正模拟饱和度、tone curve、highlight/shadow、局部保护或分区色彩，所以真机预览容易“看起来没怎么变”。
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt` 当前成片后处理主要是全局 RGB 偏移、对比、饱和与简单高光/阴影压缩。它能改变照片，但缺少感知型 tone/chroma 曲线、肤色/中性灰保护和色相分区，强行加系数容易变生硬。
  - `StyleColorPipeline.kt` 中 `TEXTURE / VIVID` 的 Color Lab 二次响应比 `ColorLabSpec.applyTo()` 保守；某些 style 下，即使 palette 拉满，视觉差异可能仍被 style-specific guard 压住。
- References:
  - Apple 官方说明 Photographic Styles 可在相机里细调 tone、color、intensity，并且会“intelligently adjust specific colors in different parts of your photos”，这说明参考重点应是“分区/感知式调整”，不是普通全局滤镜：https://support.apple.com/en-euro/guide/iphone/iph629d2cd37/ios
  - Apple 旧版官方说明里，Photographic Styles 也以 Rich Contrast、Vibrant、Warm、Cool 加 Tone/Warmth 的方式表达，适合借鉴“少控件、强语义、每个方向有明确效果”的产品模型：https://support.apple.com/en-mide/guide/iphone/-iph939c00e95/ios
  - vivo X300 官方页强调自然人像、真实色彩、蓝图影像芯片与影像 NPU；可借鉴的是“自然、真实、场景化”的色彩目标，而不是承诺 OpenCamera 具备同等 ISP/芯片能力：https://www.vivo.com.cn/vivo/x300/
  - 非官方快讯称 vivo 蓝图调色盘支持自定义色彩影调及配方分享；该点只作为产品方向参考，不作为工程事实源：https://www.donews.com/news/detail/8/6504195.html
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectModel.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/SessionPreviewRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- Non-goals:
  - 不新增一堆专业参数，不把 Color Lab 退化成曝光/饱和/色温列表。
  - 不只提高 `PreviewEffectAdapter` 的 tintAlpha 来假装有效。
  - 不引入 vendor 私有 ISP / vivo 蓝图 / Apple Photographic Styles 的能力宣称。
  - 不让预览和成片各用一套不可对齐的色彩算法。

## Product Direction

推荐把 OpenCamera Color Lab V2 定义为“三层语义、一张调色盘”：

1. `Style base`：风格仍是第一层，负责基础审美，如原色、鲜明、人文、浓郁。
2. `Palette direction`：Color Lab 的二维点负责方向。横轴是冷暖/综合色彩倾向，纵轴是明快/深邃影调。
3. `Perceptual intensity`：强度不是简单饱和度，而是由 tone curve、chroma、split tint、highlight/shadow 和保护 mask 共同形成。

四角应有明确语义：

- 右上：暖亮，阳光、通透，但高光不过曝。
- 右下：暖深，胶片、夕阳、深对比，但肤色不过橙。
- 左上：冷亮，清爽、空气感，但中性灰不发蓝。
- 左下：冷深，城市、夜感、浓郁，但暗部不死黑。

关键判断：现在不是要再开放更多控件，而是要让现有二维控件每个方向都有“产品上读得出来”的结果。

## Implementation Scope

- 新增或重构一个共享的 `ColorLabRenderRecipe` / `PerceptualColorRecipe`，由 `ColorLabSpec + style profile + styleStrength` 生成。
- 让 `StyleColorPipeline` 不只返回低维 `FilterRenderSpec`，而能输出或附带可供预览与成片共同消费的 recipe 语义。
- 把 `PreviewEffectAdapter` 从“低透明度颜色蒙层”升级为“同源 recipe 的预览近似”。API 31+ 可优先考虑对 `PreviewView` 使用 `RenderEffect + ColorMatrixColorFilter`；不支持时才退化为增强 overlay，并在 render model 标记 `previewColorFidelity=DEGRADED`。
- 把 `PhotoAlgorithmPostProcessor` 的成片处理从全局 RGB offset 提升到感知型处理：luma-aware tone curve、chroma curve、warm/cool split grade、中性灰保护、肤色保护、highlight/shadow guard。
- 增加可量化的最低感知差异测试，防止未来又被“自然”名义改回几乎无变化。

## Steps

1. Inspect current effect chain:
   - Confirm all active photo/humanistic/portrait/video mode plugin paths call `renderStyleColorSpec(...)`.
   - Confirm capture metadata includes the final color lab render values through `EffectBridge.toMetadataTags(...)`.
   - Confirm preview model is built from the same `EffectSpec`.

2. Add core recipe model:
   - In `core/settings` or `core/effect`, introduce a compact recipe model, for example:

```kotlin
data class PerceptualColorRecipe(
    val toneLift: Float,
    val toneDepth: Float,
    val chromaBoost: Float,
    val warmthBias: Float,
    val tintBias: Float,
    val shadowTint: Float,
    val highlightTint: Float,
    val neutralProtection: Float,
    val skinProtection: Float,
    val previewFidelity: PreviewColorFidelity = PreviewColorFidelity.APPROXIMATE
)
```

   - Keep existing `FilterRenderSpec` metadata for compatibility, but treat it as a fallback/export surface, not the full internal color model.

3. Build perceptual mapping:
   - Keep center area gentle with a small dead zone around `0f`.
   - Use a curve that preserves mid-control subtlety but accelerates near edges.
   - Guarantee edge response per style: even `TEXTURE` and `VIVID` must hit a minimum perceptual delta at full palette strength.
   - Add style-specific caps only after the minimum visible response is satisfied.

4. Upgrade preview approximation:
   - Extend `PreviewEffectRenderModel.FilterOverlaySpec` or add a sibling `ColorTransformPreviewSpec`.
   - Apply a color matrix/render effect to the preview surface when available.
   - Overlay fallback may remain, but it must be visually stronger and explicitly tested as a degraded approximation.
   - Do not draw an unrelated color wash over controls or letterboxed areas; apply to the actual preview content rect when possible.

5. Upgrade saved-photo renderer:
   - In `PhotoAlgorithmPostProcessor`, implement recipe-driven per-pixel adjustment:
     - Convert RGB to luma/chroma-style intermediates.
     - Apply tone lift/depth by luminance zone.
     - Increase or reduce chroma with clipping guards.
     - Apply warm/cool and tint bias with reduced effect on low-chroma neutrals.
     - Reduce hue shift on likely skin hue ranges while preserving gentle warmth/luma.
   - Keep JPEG/EXIF preservation behavior unchanged.

6. Add regression tests:
   - Core recipe tests:
     - All four corners produce non-trivial recipe values.
     - Center and zero strength remain neutral.
     - `TEXTURE / VIVID / NATURAL / MONOCHROME` behavior is intentional; monochrome may ignore color axis but must keep tone axis.
   - Preview tests:
     - Edge palette produces a preview transform stronger than the current low-alpha tint-only path.
     - Unsupported render-effect path reports degraded preview fidelity.
   - Pixel tests:
     - Synthetic warm/cool/color patches show measurable channel/luma movement at full strength.
     - Neutral gray remains close enough to gray.
     - Skin-like patch does not swing into obvious orange/blue/green.
     - Highlights and shadows avoid hard clipping.

7. Run verification.

## Acceptance Criteria

- At palette edges and corners, a user can clearly see the direction change on a real device without needing side-by-side forensic comparison.
- Preview and saved JPEG move in the same direction: warm/cool, airy/deep, saturation/chroma, and highlight/shadow behavior should match qualitatively.
- The effect remains natural: no posterization, no heavy uniform color veil, no neon saturation, no destroyed skin tones, no gray objects turning obviously colored.
- Existing `ColorLabSpec` persisted settings remain readable. Old settings becoming more visible is acceptable and should be documented.
- If a device/API cannot support stronger preview transform, UI/diagnostics treat preview as degraded approximation, not as exact final render.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.StyleColorPipelineTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

After implementation, run the Stage 7 gate if this lands in the current stabilization branch:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Real Device Acceptance

Use the same target device and one stable scene with skin/neutral/green/sky or colored objects if possible.

1. Open Photo mode, default style `Original`, Color Lab center.
2. Drag to each edge and corner, record screen.
3. Capture one photo per corner.
4. Compare preview recording and saved JPEG:
   - direction must match;
   - saved JPEG can be slightly cleaner/stronger than preview, but not a different look;
   - center reset must return close to neutral.
5. Repeat quickly under one non-default style such as `Vivid` or `Texture` to ensure style-specific guards no longer erase the palette.

Codex/user should retain final multimodal acceptance because only screenshots/saved photos can judge “明显但自然” honestly.

## Risks And Notes

- A pure overlay preview will never fully match saved-photo tone and chroma transforms. If exact preview-output parity becomes a hard product requirement, the project needs a real preview render path, likely GPU shader / surface transform / CameraX effect pipeline, rather than only `PreviewOverlayView`.
- Increasing all scalar values again is risky: it may make some scenes vivid but will break skin/gray naturalness. The safer path is minimum perceptual delta plus protection masks.
- Apple/vivo references should guide product semantics only. OpenCamera must stay honest about public Android/CameraX/app-side processing limits.
- This work is feature-quality repair from real-device feedback, not a new stage transition.
