# Style And Color Lab 2.0 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Build `风格 + 色彩实验室` 2.0: style is the base color-science layer, and Color Lab is a secondary XY render on top of the selected style.

**Architecture:** The hardest 30% is already implemented in `core/settings`: `StyleColorPipeline` and `ColorLabSpec` define order, inheritance, and style-specific palette response. Remaining agents should wire this core into persistent settings, mode/effect creation, UI panels, resources, and preview/saved output without inventing new color math.

**Tech Stack:** Kotlin core settings, Android Views/XML, existing filter profile catalog, existing `FilterRenderSpec`, existing preview effect adapter, existing `PhotoAlgorithmPostProcessor`, Markdown handoff docs.

---

## Core Product Model

2.0 has two layers:

```text
Layer 1: 风格
  - user chooses exactly one base style
  - style owns base contrast, saturation, tone curve direction, optional LUT id
  - style supports strength

Layer 2: 色彩实验室
  - user drags a two-dimensional palette point
  - horizontal axis is color tendency
  - vertical axis is tone relationship
  - the same XY coordinate behaves differently depending on the selected style base
```

The order is mandatory:

```text
Style base -> inherited base render spec -> Color Lab secondary mapping -> final FilterRenderSpec
```

Do not apply Color Lab before style. Do not treat Color Lab as a global brightness/saturation slider.

## Already Completed By Codex

Core files:

- `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/StyleColorPipeline.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/StyleColorPipelineTest.kt`

Core APIs:

```kotlin
val result = StyleColorPipeline.render(
    StyleColorPipelineRequest(
        styleProfileId = selectedStyleId,
        baseRenderSpec = selectedStyle.renderSpec ?: FilterRenderSpec(),
        colorLabSpec = colorLabSpec,
        colorScience = StyleColorPipeline.resolveColorScience(
            selectedStyleId,
            selectedStyle.renderSpec ?: FilterRenderSpec()
        ),
        styleStrength = styleStrength
    )
)

val finalRenderSpec: FilterRenderSpec = result.finalRenderSpec
```

Verified:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.StyleColorPipelineTest --tests com.opencamera.core.settings.ColorLabSpecTest
```

Do not duplicate the pipeline in app/UI code.

## Parallel Workstreams

Recommended owners:

1. Core Persistence Agent
2. Catalog And Style Strength Agent
3. Mode/Effect Pipeline Agent
4. UI/Interaction Agent
5. Preview/Media Verification Agent
These can run in parallel after agreeing on model names. One integrator should merge `MainActivity.kt` changes.

## Workstream 1: Core Persistence

**Goal:** Persist selected style strength and Color Lab state.

**Files:**

- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- Test settings serializer/reducer tests

### Tasks

- [ ] Add `ColorLabSpec` to `PhotoSettings`:

```kotlin
val colorLabSpec: ColorLabSpec = ColorLabSpec()
```

- [ ] Add a style strength model. Keep it simple:

```kotlin
data class StyleStrengthSettings(
    val photo: Float = 1f,
    val humanistic: Float = 1f,
    val portrait: Float = 1f,
    val video: Float = 1f
)
```

or if scope must stay photo-only:

```kotlin
val styleStrength: Float = 1f
```

- [ ] Add actions:

```kotlin
data class UpdateColorLabSpec(val spec: ColorLabSpec) : PersistedSettingsAction
data class UpdatePhotoStyleStrength(val strength: Float) : PersistedSettingsAction
```

- [ ] Reducer must clamp:

```kotlin
photo = photo.copy(colorLabSpec = action.spec.normalized())
photo = photo.copy(styleStrength = action.strength.coerceIn(0f, 1f))
```

- [ ] Serializer keys:

```kotlin
photo.colorLab.colorAxis
photo.colorLab.toneAxis
photo.colorLab.strength
photo.colorLab.presetId
photo.styleStrength
```

- [ ] Tests:

```kotlin
@Test
fun `serializer round trips style strength and color lab spec`() {
    val settings = PersistedSettings(
        photo = PhotoSettings(
            colorLabSpec = ColorLabSpec(colorAxis = 0.4f, toneAxis = -0.3f, strength = 0.7f),
            styleStrength = 0.6f
        )
    )

    val decoded = PersistedSettingsSerializer.fromMap(
        PersistedSettingsSerializer.toMap(settings)
    )

    assertEquals(settings.photo.colorLabSpec, decoded.photo.colorLabSpec)
    assertEquals(0.6f, decoded.photo.styleStrength)
}
```

Verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test
```

## Workstream 2: Style Catalog And User-Facing Style List

**Goal:** Make `风格` a single style list with strength, not a mixed advanced parameter panel.

**Files:**

- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
- Modify `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- Modify strings
- Test `SessionUiRenderModelTest`

### Tasks

- [ ] Keep using `FilterProfile` as the first 2.0 style carrier unless a new `StyleProfile` is introduced by a dedicated refactor.
- [ ] Rename UI language from filter-first to style-first:
  - `风格`
  - `自然`
  - `黑白`
  - `人文`
  - `浓郁`
  - `质感`
  - `鲜明`
- [ ] Add style profiles if missing:

```kotlin
builtInFilterProfile(
    id = "photo-texture",
    label = "Texture",
    category = FilterProfileCategory.PHOTO,
    renderSpec = renderSpec(
        brightnessShift = -3,
        contrast = 1.18f,
        saturation = 0.82f,
        warmthShift = 2,
        grainStrength = 0.08f,
        vignetteStrength = 0.08f
    )
)

builtInFilterProfile(
    id = "photo-bw",
    label = "B&W",
    category = FilterProfileCategory.PHOTO,
    renderSpec = renderSpec(
        contrast = 1.12f,
        saturation = 0f,
        monochromeMix = 1f
    )
)
```

- [ ] In `风格` panel:
  - show only style list;
  - show selected style strength;
  - do not show advanced controls;
  - do not show independent bokeh/light spot/vignette/grain rows.

- [ ] Tests:
  - style panel contains style rows;
  - style panel exposes strength;
  - style panel does not expose advanced filter controls.

Verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Workstream 3: Mode And Effect Pipeline Integration

**Goal:** Use `StyleColorPipeline` wherever active mode builds preview/capture effect specs.

**Files to inspect:**

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
- `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
- relevant mode/plugin tests

### Tasks

- [ ] Add a shared helper:

```kotlin
fun renderStyleColorSpec(
    profileId: String,
    baseRenderSpec: FilterRenderSpec?,
    colorLabSpec: ColorLabSpec,
    styleStrength: Float
): FilterRenderSpec? {
    val base = baseRenderSpec ?: return null
    return StyleColorPipeline.render(
        StyleColorPipelineRequest(
            styleProfileId = profileId,
            baseRenderSpec = base,
            colorLabSpec = colorLabSpec,
            styleStrength = styleStrength
        )
    ).finalRenderSpec
}
```

- [ ] Replace direct `FilterEffect(profile.id, profile.renderSpec)` with pipeline output.
- [ ] Ensure capture metadata still receives `filterSpec.*`.
- [ ] Add diagnostics tags if easy:

```text
styleColor.profileId
styleColor.colorScience
styleColor.baseLutId
```

- [ ] Tests:
  - selected style + colorLabSpec produces adjusted metadata;
  - neutral color lab preserves style base;
  - different style IDs produce different final specs for same XY.

Verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.EffectBridgeTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
```

## Workstream 4: Top Bar, Side Rail, And Panels

**Goal:** Implement the final IA and make `风格` / `色彩实验室` visually separate.

**Files:**

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/FilterPaletteView.kt`
- `app/src/test/java/com/opencamera/app/FilterPaletteViewTest.kt`
- strings/resources
- tests

### Tasks

- [ ] Top bar:
  - left title only app name;
  - middle-right `色彩实验室`;
  - far-right `设置`;
  - remove `· 模式名`.

- [ ] Side rail:
  - `风格`;
  - `快捷`;
  - `Dev`;
  - no `设置`;
  - no `色彩实验室`.

- [ ] `风格` panel:
  - list styles;
  - strength slider;
  - no advanced controls.

- [ ] `色彩实验室` panel:
  - one XY palette;
  - reticle;
  - reset;
  - optional strength slider;
  - no professional parameter list.

- [ ] Keep the improved palette implementation:
  - horizontal gradient expresses cool-to-warm color tendency;
  - vertical overlay expresses airy-to-deep tone;
  - dot grid and center axes show two-dimensional control;
  - reticle remains visible on light and dark areas.

- [ ] Keep palette vertical axis:

```kotlin
val toneAxis = (1f - event.y / height * 2f).coerceIn(-1f, 1f)
reticleY = (1f - toneAxis) / 2f
```

Verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.FilterPaletteViewTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Workstream 5: Preview And Saved Output Consistency

**Goal:** Confirm preview and saved JPEG consume the same final `FilterRenderSpec`.

**Files:**

- `core/effect/src/main/kotlin/com/opencamera/core/effect/PreviewEffectAdapter.kt`
- `app/src/main/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessor.kt`
- tests in `core/effect` and `app/src/test`

### Tasks

- [ ] Preview test:
  - build final spec through `StyleColorPipeline`;
  - pass to `PreviewEffectAdapter`;
  - assert overlay warmth/vignette/alpha changes.

- [ ] Saved output test:
  - write final spec to metadata tags;
  - ensure `PhotoAlgorithmPostProcessor` receives adjusted values.

- [ ] Keep first-stage limitation explicit:
  - preview may be approximate;
  - saved output is canonical;
  - both must share final spec.

Verification:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

## Non-Goals For 2.0 First Implementation

- Do not implement full Apple-style semantic segmentation in this pass.
- Do not implement a large production 3D LUT matrix in this pass.
- Do not add separate professional controls to Color Lab.
- Do not create a second effect engine outside `StyleColorPipeline`.
- Do not remove existing `FilterProfile` storage until a larger migration is planned.

## Final Verification

Run focused tests first, then:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

If the change touches Stage 7 owners, also run:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```
