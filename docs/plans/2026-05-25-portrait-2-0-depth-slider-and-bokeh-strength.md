# Portrait 2.0 Depth Slider And Bokeh Strength

## Goal

把人像模式的景深/虚化从当前离散 preset 和 style 内部 `bokehStrength`，升级为用户可调、可持久化、可诊断的强度控制。这个控制应影响保存成片的人像背景虚化，并在 mask 不可用或设备不支持时诚实降级。

## Context

- User request: 认可人像 2.0 升级包含景深/虚化强度滑杆。
- Verified facts:
  - `PortraitModePlugin.kt` 当前从 selected portrait style 写入 `bokehStrength` metadata。
  - `SettingsEnums.kt` 当前有离散 `PortraitBokehEffect.NATURAL / CREAMY / DREAMY`。
  - `PortraitRenderPostProcessor.resolvePortraitRenderSpec(...)` 当前用 `bokehStrength` 和 `PortraitBokehEffect` 计算 blur scale、focus radius、edge softness、vignette、bloom。
  - `SessionUiRenderModel` 和 `SettingsPanelRenderer` 已有 Portrait Lab 控件入口，但没有连续强度滑杆。
  - [Mask Aware Portrait Saved Rendering](./2026-05-25-mask-aware-portrait-saved-rendering.md) 应先落地或至少并行提供 subject/background 语义。
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
- Non-goals:
  - Do not claim true optical aperture or hardware depth unless device capability explicitly supports it.
  - Do not implement post-capture editing UI in this package.
  - Do not add real-time GPU blur preview in this package.
  - Do not bypass `ModeIntent -> ModeSignal -> CaptureStrategy -> Media Pipeline`.

## Implementation Scope

- Add a persisted portrait depth setting, for example `portraitDepthStrength: Int` in range `0..100` or a small value class if the settings package has a precedent.
- Add `PersistedSettingsAction.UpdatePortraitDepthStrength`.
- Serialize with a clear key such as `photo.portrait.depthStrength`.
- Add the value to `PortraitEffect` and `EffectBridge.toMetadataTags(...)`, for example `portraitDepthStrength`.
- In `PortraitModePlugin.buildEffectSpec()` and capture metadata, combine:
  - selected style default `bokehStrength`;
  - selected `PortraitBokehEffect`;
  - user `portraitDepthStrength`;
  - device/render path `depth` versus `focus`.
- In `PortraitRenderPostProcessor`, map the slider through the layered resolver:
  - low values preserve more background detail;
  - mid values match current behavior;
  - high values increase background blur and edge softness conservatively;
  - mask low confidence caps effective strength to avoid halos.
- Add render model support for a slider/stepper in Portrait Lab. If current renderer only supports button-like controls, add a narrow render-model capability instead of hard-coding view behavior.

## Steps

1. Add settings serializer tests for default, min, mid, max, invalid low, and invalid high values.
2. Add effect bridge tests proving `portraitDepthStrength` enters metadata.
3. Add mode plugin tests or existing session tests proving portrait capture metadata includes the selected strength.
4. Add render model tests proving Portrait Lab exposes the control only when settings editing is enabled.
5. Update `PortraitRenderPostProcessorTest`:
   - lower strength produces less background blur/bloom;
   - higher strength produces stronger background blur/bloom;
   - weak/missing mask caps or degrades the effective strength;
   - strength does not change subject beauty values.
6. Run focused verification and `:app:assembleDebug`.

## Acceptance Criteria

- User depth strength is persisted and survives settings serialization.
- Capture metadata and effect metadata include the selected strength.
- Saved portrait rendering consumes the strength through the portrait layered spec.
- Mask unavailable or low confidence does not fail capture and records degraded behavior.
- Existing `PortraitBokehEffect` remains compatible and acts as a preset/bias rather than being removed.
- The control is not wired directly to CameraX or app-local hidden state.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- A 2D person mask is not true depth. Use product wording like `Depth` only for UI if the project already accepts that convention; pipeline notes should say `subject-mask` and `background-blur`.
- High strength values can make edge artifacts obvious. Cap strength when mask confidence is low.
- This package may touch the same files as Profile System Realignment. Prefer executing profile work first or assigning a single integrator.
