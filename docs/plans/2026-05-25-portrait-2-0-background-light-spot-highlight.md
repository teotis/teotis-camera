# Portrait 2.0 Background Light Spot And Highlight Shape

## Goal

为人像 2.0 增加背景光斑和高光形态能力，让背景区域可以产生自然、柔和、梦幻三类可测试效果。第一版只做保存 JPEG 的保守后处理：增强背景高光、形成柔化圆形/椭圆光斑、控制 bloom 和边缘融合，不宣称复刻真实镜头光学或厂商级人像算法。

## Context

- User request: 认可人像 2.0 升级包含背景光斑/高光形态。
- Verified facts:
  - `PortraitBokehEffect.NATURAL / CREAMY / DREAMY` 已存在，当前 `resolvePortraitRenderSpec(...)` 会把它映射到 blur、edge softness、vignette、highlight bloom、background bloom。
  - [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md) 已定义背景区域做虚化/光斑/背景 bloom，subject 区域做美颜/肤色保护。
  - [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md) 已建议 `PortraitBackgroundLightSpotSpec` 和 `portrait-layer:light-spot=<none|subtle|dreamy>` notes。
  - 当前 Android renderer 仍主要是 bitmap 后处理，适合先做 saved JPEG 而不是实时预览。
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
  - `app/src/test/java/com/opencamera/app/camera/PortraitRenderPostProcessorTest.kt`
- Non-goals:
  - Do not build a GPU real-time light-spot preview.
  - Do not add named ZEISS/品牌联名/Apple styles unless licensing and product copy are explicitly approved.
  - Do not modify subject skin smoothing in this package except to protect subject edges from background bloom spill.
  - Do not run segmentation independently from the mask-aware portrait path.

## Implementation Scope

- Add a background light-spot spec inside the portrait layered resolver:
  - `NONE`: no synthetic light spot, conservative high-light preservation.
  - `SUBTLE`: lightly expands existing bright background highlights into soft disks.
  - `DREAMY`: stronger bloom and larger disks, capped by mask confidence and image highlight density.
- Reuse `PortraitBokehEffect` as the first product carrier if adding a separate enum would overcomplicate the UI:
  - `NATURAL -> NONE`
  - `CREAMY -> SUBTLE`
  - `DREAMY -> DREAMY`
- If product wants an independent control later, add `PortraitLightSpotEffect` only after profile system realignment.
- Implement saved JPEG rendering in `AndroidPortraitRenderEditor` or a helper it calls:
  - detect bright background pixels only where subject weight is low;
  - cluster or sample highlights enough to avoid applying bloom to the whole background;
  - draw soft circles/ellipses with alpha capped by strength and mask confidence;
  - feather around subject edges to prevent halos over hair, glasses, and shoulders.
- Add pipeline notes:
  - `portrait-layer:light-spot=none|subtle|dreamy`
  - `portrait-light-spot:applied`
  - `portrait-light-spot:degraded:<reason>` when no mask, no highlights, low confidence, or unsupported render target.

## Steps

1. Add pure resolver tests proving `PortraitBokehEffect` maps to light-spot spec without changing subject beauty.
2. Add synthetic bitmap tests:
   - bright background dot expands under `SUBTLE` and more under `DREAMY`;
   - bright subject area does not bloom as background light spot;
   - low mask confidence degrades or caps the effect;
   - no bright background records a no-op/degraded note rather than inventing fake light.
3. Implement helper functions in `PortraitRenderPostProcessor.kt` or a small app-layer renderer file if the existing file becomes too large.
4. Ensure existing fallback focus render remains available when mask is absent.
5. Run focused app tests and assemble.

## Acceptance Criteria

- Background-only high lights can be enhanced into soft light spots in saved portrait JPEG output.
- The effect is gated by mask availability/confidence and never applies strongly over the subject.
- `NATURAL`, `CREAMY`, and `DREAMY` have distinct, tested background behavior.
- Pipeline notes reveal whether light spot was applied, skipped, or degraded.
- Existing portrait capture still succeeds when there are no highlight candidates.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest --tests com.opencamera.app.camera.SceneMaskPayloadTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Strong light spots can quickly look artificial. Keep defaults conservative and rely on Codex/user visual QA before product pass.
- Do not use trademarked vendor style names in UI or metadata.
- This package should land after mask-aware rendering. Without a subject mask, background light spots are likely to spill over the person and create obvious artifacts.
