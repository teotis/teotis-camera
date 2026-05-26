# Portrait Effect Layer Contracts

## Goal

Define how portrait profile, bokeh/light-spot, filter/color, and beauty settings combine into a foreground/background postprocess spec. This gives agents a stable contract for future product work without scattering new meanings across mode plugin metadata, Color Lab, and portrait renderer code.

## Context

- User request: 人像和背景识别拆分后，应能分别进行光斑、滤镜、美颜等具体需求。
- Verified facts:
  - `PortraitModePlugin.kt` already writes `portraitProfile`, `portraitBeautyPreset`, `portraitBeautyStrength`, `portraitBokehEffect`, `renderPath`, `subjectTracking`, and style/filter metadata.
  - `resolvePortraitRenderSpec(...)` already maps profile/beauty/bokeh into render numeric values.
  - `EffectBridge.toMetadataTags(...)` writes filter and portrait effect tags into capture metadata.
  - `PhotoAlgorithmPostProcessor` and `PortraitRenderPostProcessor` currently interpret some overlapping style/render concepts independently.
- Relevant files:
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectSpec.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt`
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
  - `app/src/main/java/com/opencamera/app/camera/PortraitRenderPostProcessor.kt`
  - `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- Non-goals:
  - Do not add a new UI page in this package.
  - Do not implement GPU preview renderer.
  - Do not add vendor-specific beauty SDKs.
  - Do not redefine global Color Lab for all modes.

## Implementation Scope

- Add a pure mapping layer, for example:

```kotlin
internal data class PortraitLayeredEffectSpec(
    val subjectBeauty: PortraitSubjectBeautySpec,
    val subjectColorProtection: PortraitSubjectColorProtectionSpec,
    val backgroundBokeh: PortraitBackgroundBokehSpec,
    val backgroundLightSpot: PortraitBackgroundLightSpotSpec,
    val backgroundColorStyle: PortraitBackgroundColorStyleSpec,
    val degradationReason: String? = null
)
```

- Keep it pure/testable. Inputs should be current metadata/settings:
  - `PortraitProfile`
  - `PortraitBeautyPreset`
  - `PortraitBeautyStrength`
  - `PortraitBokehEffect`
  - selected portrait filter/render spec
  - optional `SceneMaskCapability`
- Output is consumed by `PortraitRenderPostProcessor`, not by UI directly.
- Establish product semantics:
  - `NATIVE`: lower beauty, lower bloom, natural background.
  - `LUMINOUS`: more subject lift, controlled highlight bloom, brighter background.
  - `NATURAL` bokeh: conservative blur and no artificial light spots.
  - `CREAMY`: stronger background separation, softer highlight disks.
  - `DREAMY`: highest bloom/light spot, still gated by mask confidence.
- Add metadata/pipeline notes:
  - `portrait-layer:subject=beauty:<preset>:<strength>`
  - `portrait-layer:background=bokeh:<effect>`
  - `portrait-layer:light-spot=<none|subtle|dreamy>`
  - `portrait-layer:mask=<applied|degraded|unsupported>`

## Steps

1. Add pure resolver tests before changing rendering:
   - profile changes subject/background spec;
   - beauty affects only subject spec;
   - bokeh/light spot affects only background spec;
   - missing/unsupported mask marks spec degraded without blocking render.
2. Move or wrap current `resolvePortraitRenderSpec(...)` calculations so they feed the new layered spec rather than duplicating product logic.
3. Ensure `PortraitModePlugin` metadata remains the single capture-time source for profile/filter/beauty/bokeh choices.
4. Update `PortraitRenderPostProcessor` to consume the layered spec.
5. Confirm `PhotoAlgorithmPostProcessor` still handles global/style Color Lab; avoid making portrait renderer also reinterpret all filter metadata unless explicitly mapped as background/subject behavior.

## Acceptance Criteria

- There is one tested resolver for portrait foreground/background effect meaning.
- Beauty settings do not accidentally alter background-only bokeh behavior.
- Bokeh/light spot settings do not accidentally over-smooth the subject.
- Existing portrait profile metadata remains compatible.
- Pipeline notes make the final applied/fallback portrait path debuggable.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PortraitRenderPostProcessorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest --tests com.opencamera.core.effect.EffectSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Risks And Notes

- Keep the first implementation conservative. Strong bokeh/light-spot values can look worse than the current fallback if mask edges are weak.
- Do not introduce another hidden mode kernel. The mode plugin declares choices; media postprocessors render choices.
- If new enum values are added to settings, update serializer tests and migration/default behavior in the same package.

