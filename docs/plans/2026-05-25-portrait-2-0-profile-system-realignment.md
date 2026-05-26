# Portrait 2.0 Profile System Realignment

## Goal

重整人像 2.0 的 Profile 体系，让 `PortraitProfile`、美颜、景深/虚化、背景光斑、高光、滤镜/风格各自拥有清晰职责。最终用户看到的是少量稳定产品入口，代码里则通过单一 layered portrait spec 解释这些入口，避免 `PortraitModePlugin`、`EffectBridge`、`PhotoAlgorithmPostProcessor`、`PortraitRenderPostProcessor` 分别重复解释同一语义。

## Context

- User request: 认可人像 2.0 升级包含人像 Profile 体系重整。
- Verified facts:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt` 当前只有 `PortraitProfile.NATIVE / LUMINOUS`、`PortraitBeautyPreset`、`PortraitBeautyStrength`、`PortraitBokehEffect.NATURAL / CREAMY / DREAMY`。
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt` 已写入 `portraitProfile`、`portraitBeautyPreset`、`portraitBeautyStrength`、`portraitBokehEffect`、`renderPath`、`subjectTracking` 和 selected style/filter metadata。
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt` 当前直接把 profile/beauty/bokeh 映射成数值效果。
  - [Portrait Effect Layer Contracts](./2026-05-25-portrait-effect-layer-contracts.md) 已建议引入 `PortraitLayeredEffectSpec`。
- Relevant files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
- Non-goals:
  - Do not add a vendor beauty SDK.
  - Do not migrate all `FilterProfile` into a new global style system in this package.
  - Do not add real-time preview portrait rendering in this package.
  - Do not remove existing storage keys without compatibility parsing.

## Implementation Scope

- Define product responsibilities:
  - `PortraitProfile`: overall portrait recipe such as `native`, `luminous`, future `studio` or `cinematic`, controlling subject lift, skin protection, background tone, and default bokeh bias.
  - `PortraitBeautyPreset` and `PortraitBeautyStrength`: subject-only smoothing/lift/skin behavior.
  - `PortraitBokehEffect`: background-only blur, bloom, and light-spot bias.
  - `FilterProfileCategory.PORTRAIT`: color/style selection that can affect whole image or be explicitly mapped into subject/background style by the layered resolver.
- Add a pure resolver such as `PortraitLayeredEffectResolver` if not already created by the effect-layer package.
- Keep capture-time source of truth in `PortraitModePlugin` metadata and `EffectBridge.toMetadataTags(...)`.
- Update Portrait Lab render model copy and control grouping only as needed to express the clearer model. Avoid broad UI redesign.
- Add compatibility tests for existing serialized values: `native`, `luminous`, `authentic`, `clear`, `radiant`, `off`, `soft`, `balanced`, `elevated`, `natural`, `creamy`, `dreamy`.

## Steps

1. Inspect current enum serialization and reducer tests in `PersistedSettingsSerializerTest`.
2. Add pure tests for profile responsibility:
   - changing `PortraitProfile` changes subject/background recipe defaults;
   - changing beauty affects only subject beauty fields;
   - changing bokeh affects only background blur/light-spot fields;
   - changing portrait filter does not silently overwrite beauty or bokeh choices.
3. Introduce or reuse `PortraitLayeredEffectSpec` as the single output of profile/profile-like decisions.
4. Update `PortraitRenderPostProcessor` to consume the layered resolver rather than keeping independent profile math spread across helper functions.
5. Update `SessionUiRenderModel.portraitLabPageRenderModel(...)` only if current labels no longer describe the model. Keep commands as `PersistedSettingsAction` updates.
6. Run settings/effect/app tests listed below.

## Acceptance Criteria

- There is one tested resolver for profile, beauty, bokeh/light-spot, and selected portrait style interactions.
- Existing persisted settings deserialize to the same user-visible selections.
- Beauty settings do not alter background-only behavior.
- Bokeh/light-spot settings do not alter subject smoothing.
- `PortraitModePlugin` remains the capture-time source of metadata; media postprocessors do not invent new user choices.
- No mask bitmap or preview frame data is stored in settings, mode state, or session state.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest
```

## Risks And Notes

- The existing names are already user-facing in Portrait Lab. If labels change, route them through `AppTextResolver` and `R.string`, not hard-coded Kotlin strings.
- Keep profile count small for 2.0. More presets are cheap to add but expensive to make visually credible.
- This package should precede the slider and light-spot packages if they need new spec fields.
