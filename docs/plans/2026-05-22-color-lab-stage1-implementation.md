# Color Lab Stage 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking. Use `rtk` for every shell command.

**Goal:** Finish the first-stage `色彩实验室` feature as a simple two-dimensional palette that updates preview and saved photos through one shared render spec.

**Architecture:** The hardest 10% is already done: `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt` maps palette coordinates to `FilterRenderSpec`, and `ColorLabSpecTest` locks the math. The remaining work should wire this core into persisted state, UI routes, render models, preview effect metadata, and media postprocessing without creating another effect engine.

**Tech Stack:** Android Views/XML, Kotlin render models, existing `SessionSettingsManager`, existing `FilterRenderSpec` metadata, existing `PreviewEffectAdapter`, existing `PhotoAlgorithmPostProcessor`.

---

## Product Contract

Top bar:

- left: app name only, no `· 模式名`;
- middle-right: `色彩实验室`;
- far-right: `设置`.

Side rail:

- `风格`;
- `快捷`;
- `Dev`.

`色彩实验室` panel:

- one two-dimensional palette;
- horizontal axis: `色彩`, from cool/blue/cyan through neutral to warm/amber;
- vertical axis: `影调`, top is airy/lifted/soft contrast, bottom is deep/strong contrast;
- optional strength slider may be deferred if the first UI pass keeps strength at `1f`;
- reset returns to center `(0f, 0f)`;
- no exposed professional parameter list.

Important boundary:

- `风格` owns preset style/filter selection and strength.
- `色彩实验室` owns the global post-render palette.
- Do not show `白平衡 / 色温 / 高光 / 阴影 / 饱和 / 锐化 / 暗角 / 颗粒` as independent user-facing controls.

## Already Completed By Codex

Files already added/modified:

- `core/settings/src/main/kotlin/com/opencamera/core/settings/ColorLabSpec.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`

Existing behavior now available:

```kotlin
ColorLabSpec(
    colorAxis = -1f..1f,
    toneAxis = -1f..1f,
    strength = 0f..1f
).applyTo(baseFilterRenderSpec)
```

Verified:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Do not rewrite the mapping formula in UI code. Use `ColorLabSpec` or `FilterRenderSpec.applyColorLab()`.

## Target File Map

Core settings:

- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
  - Add `colorLabSpec` to `PhotoSettings`.
  - Add `PersistedSettingsAction.UpdateColorLabSpec`.
  - Reduce action into `photo.colorLabSpec`.
- Modify `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
  - Persist `colorAxis`, `toneAxis`, `strength`, and `presetId`.
- Add tests to `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt` or existing settings serializer tests.

Session/effect bridge:

- Modify mode plugins or shared effect construction path so active `ColorLabSpec` is applied to the active `FilterRenderSpec` before metadata reaches capture.
- Prefer one shared helper in `core/settings` or `core/effect`, for example:
  `fun FilterRenderSpec.withColorLab(settings: PersistedSettings): FilterRenderSpec`.
- Ensure saved photo metadata still contains `filterSpec.*`, because `PhotoAlgorithmPostProcessor` already consumes it.

App UI:

- Modify `app/src/main/res/layout/activity_main.xml`
  - Add `buttonColorLabEntry` to top bar.
  - Keep `buttonSettingsEntry` as far-right settings.
  - Remove/hide `buttonLensLabEntry` from side rail.
  - Keep `buttonDevEntry` visible.
- Modify `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
  - Add or repurpose a `ColorLab` route. Low-churn path: use `LensLab` internally but visible label must be `色彩实验室`.
- Modify `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
  - Top actions include `色彩实验室` and `设置`.
  - Right rail entries are `风格`, `快捷`, `Dev`.
- Modify `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - Bind `buttonColorLabEntry` to `ColorLab/LensLab`.
  - Bind `buttonSettingsEntry` to settings.
  - Set `titleText.text = getString(R.string.app_name)`.
  - Remove `buttonSettingsEntry.visibility = View.GONE`.
  - Make `buttonDevEntry` visible according to current product policy, not `BuildConfig.DEBUG` only.
  - Route palette touches to `PersistedSettingsAction.UpdateColorLabSpec(...)` or a small manager helper.
- Modify `app/src/main/java/com/opencamera/app/FilterPaletteView.kt`
  - Rename only if safe; otherwise reuse it as the Color Lab palette view.
  - Fix vertical mapping so top means positive `toneAxis` and bottom means negative `toneAxis`.
- Modify `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
  - Add a `ColorLabPanelRenderModel`.
  - Hide advanced filter controls from color lab.
  - Use existing style/filter render model for `风格`.

Tests:

- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- settings serializer tests
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `core/effect/src/test/kotlin/com/opencamera/core/effect/PreviewEffectAdapterTest.kt`
- `app/src/test/java/com/opencamera/app/camera/PhotoAlgorithmPostProcessorTest.kt`

## Task 1: Persist Color Lab State

**Files:**

- Modify: `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt`
- Modify: `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- Test: existing settings serializer test file, or create `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`

- [ ] **Step 1: Write failing reducer test**

Add:

```kotlin
@Test
fun `persisted settings reducer updates color lab spec`() {
    val spec = ColorLabSpec(colorAxis = 0.5f, toneAxis = -0.25f, strength = 0.8f)

    val settings = PersistedSettings().reduce(
        PersistedSettingsAction.UpdateColorLabSpec(spec)
    )

    assertEquals(spec.normalized(), settings.photo.colorLabSpec)
}
```

- [ ] **Step 2: Run test and verify failure**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

Expected: compile failure or unresolved `UpdateColorLabSpec`.

- [ ] **Step 3: Implement model and reducer**

Add to `PhotoSettings`:

```kotlin
val colorLabSpec: ColorLabSpec = ColorLabSpec()
```

Add to `PersistedSettingsAction`:

```kotlin
data class UpdateColorLabSpec(val spec: ColorLabSpec) : PersistedSettingsAction
```

Add to `reduce`:

```kotlin
is PersistedSettingsAction.UpdateColorLabSpec -> copy(
    photo = photo.copy(colorLabSpec = action.spec.normalized())
)
```

- [ ] **Step 4: Add serializer round-trip test**

```kotlin
@Test
fun `serializer round trips color lab spec`() {
    val settings = PersistedSettings(
        photo = PhotoSettings(
            colorLabSpec = ColorLabSpec(
                colorAxis = -0.75f,
                toneAxis = 0.5f,
                strength = 0.6f,
                presetId = "cool-air"
            )
        )
    )

    val decoded = PersistedSettingsSerializer.fromMap(
        PersistedSettingsSerializer.toMap(settings)
    )

    assertEquals(settings.photo.colorLabSpec, decoded.photo.colorLabSpec)
}
```

- [ ] **Step 5: Implement serializer keys**

Use keys:

```kotlin
private const val KEY_COLOR_LAB_COLOR_AXIS = "photo.colorLab.colorAxis"
private const val KEY_COLOR_LAB_TONE_AXIS = "photo.colorLab.toneAxis"
private const val KEY_COLOR_LAB_STRENGTH = "photo.colorLab.strength"
private const val KEY_COLOR_LAB_PRESET_ID = "photo.colorLab.presetId"
```

Add values in `toMap()`. In `fromMap()`, parse floats with safe fallback and call `.normalized()`.

- [ ] **Step 6: Run focused tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
```

Expected: PASS.

## Task 2: Apply Color Lab To Preview And Saved Output

**Files:**

- Modify: whichever shared mode/effect builder currently creates `FilterEffect`.
- Likely inspect:
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `core/effect/src/main/kotlin/com/opencamera/core/effect/EffectBridge.kt`
- Tests:
  - `core/effect/src/test/kotlin/com/opencamera/core/effect/EffectBridgeTest.kt`
  - existing mode plugin tests if present.

- [ ] **Step 1: Write failing test for metadata composition**

Add a test in the layer that converts active mode settings/effect into capture metadata. The expected metadata must include adjusted `filterSpec.*` values after applying `settings.photo.colorLabSpec`.

Example assertion:

```kotlin
assertEquals("1", tags["filterSpec.version"])
assertTrue(tags["filterSpec.warmthShift"]!!.toInt() > baseWarmth)
assertTrue(tags["filterSpec.contrast"]!!.toFloat() != baseContrast)
```

- [ ] **Step 2: Implement one shared helper**

Prefer in `core/settings`:

```kotlin
fun FilterRenderSpec.withColorLab(spec: ColorLabSpec): FilterRenderSpec {
    return applyColorLab(spec)
}
```

Then use it wherever `FilterEffect(profile.id, profile.renderSpec)` is built:

```kotlin
val renderSpec = profile.renderSpec
    ?.applyColorLab(settings.photo.colorLabSpec)
FilterEffect(profile.id, renderSpec)
```

If video should support color lab too, decide explicitly. For stage 1, safest scope is photo-facing modes first unless product requires video.

- [ ] **Step 3: Verify saved output path**

`PhotoAlgorithmPostProcessor` already reads `FilterRenderSpec.fromMetadataTags(result.metadata.customTags)`. Add or update a test showing adjusted tags become `PhotoAlgorithmSpec` fields.

Command:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

- [ ] **Step 4: Verify preview path**

`PreviewEffectAdapter` already maps `FilterRenderSpec` to preview overlay. Add a test with `ColorLabSpec(colorAxis = 1f, toneAxis = -1f).applyTo(FilterRenderSpec())` and assert preview warmth/tint alpha changes.

Command:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
```

## Task 3: Build Top Bar And Side Rail IA

**Files:**

- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Modify: `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- Modify: `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-en/strings.xml`
- Test: `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`

- [ ] **Step 1: Write failing cockpit render-model test**

Expected:

- title is app name only;
- top actions include `色彩实验室` and `设置`;
- right rail contains `风格`, `快捷`, `Dev`;
- right rail does not contain `设置` or `色彩实验室`.

- [ ] **Step 2: Update strings**

Chinese:

```xml
<string name="button_color_lab_entry">色彩实验室</string>
<string name="button_style_entry">风格</string>
<string name="button_quick_launcher">快捷</string>
<string name="button_settings_entry">设置</string>
```

English:

```xml
<string name="button_color_lab_entry">Color Lab</string>
<string name="button_style_entry">Style</string>
<string name="button_quick_launcher">Quick</string>
<string name="button_settings_entry">Settings</string>
```

- [ ] **Step 3: Update XML**

Add `buttonColorLabEntry` to top panel. Keep `buttonSettingsEntry` as far-right. Remove or hide `buttonLensLabEntry` from `floatingUtilityGroup`. Keep `buttonDevEntry` visible.

- [ ] **Step 4: Update `MainActivity` binding**

Bind:

```kotlin
buttonColorLabEntry.setOnClickListener { activePanelRoute = CockpitPanelRoute.LensLab /* or ColorLab */ }
buttonSettingsEntry.setOnClickListener { toggleSettingsPanel() }
titleText.text = getString(R.string.app_name)
```

Remove:

```kotlin
buttonSettingsEntry.visibility = View.GONE
buttonDevEntry.isVisible = BuildConfig.DEBUG
```

- [ ] **Step 5: Run focused tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

## Task 4: Color Lab Panel Render Model And Palette UI

**Files:**

- Modify: `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Modify: `app/src/main/java/com/opencamera/app/FilterPaletteView.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Test: `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

- [ ] **Step 1: Write failing render-model test**

Expected:

- `ColorLabPanelRenderModel` title is `色彩实验室`;
- palette reticle reflects `settings.photo.colorLabSpec`;
- panel exposes reset action;
- panel does not expose advanced controls.

- [ ] **Step 2: Add render model**

Example:

```kotlin
internal data class ColorLabPanelRenderModel(
    val title: String,
    val colorAxis: Float,
    val toneAxis: Float,
    val strength: Float,
    val summary: String,
    val resetAction: PersistedSettingsAction
)
```

- [ ] **Step 3: Fix palette coordinate mapping**

In `FilterPaletteView.onTouchEvent`, top should be positive tone:

```kotlin
val colorAxis = (event.x / width * 2f - 1f).coerceIn(-1f, 1f)
val toneAxis = (1f - event.y / height * 2f).coerceIn(-1f, 1f)
```

In `updateReticle`:

```kotlin
reticleX = (colorAxis + 1f) / 2f
reticleY = (1f - toneAxis) / 2f
```

- [ ] **Step 4: Route palette touches to persisted settings**

Use:

```kotlin
PersistedSettingsAction.UpdateColorLabSpec(
    ColorLabSpec(colorAxis = colorAxis, toneAxis = toneAxis, strength = currentStrength)
)
```

Reset:

```kotlin
PersistedSettingsAction.UpdateColorLabSpec(ColorLabSpec())
```

- [ ] **Step 5: Run focused tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Task 5: Remove Advanced Color Lab UI From User Surface

**Files:**

- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/java/com/opencamera/app/MainActivity.kt`
- Modify: `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- Test: `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

- [ ] **Step 1: Write failing test**

Assert color lab render model does not include:

- exposure;
- soft glow;
- halo;
- grain;
- sharpness;
- vignette;
- highlights;
- shadows;
- warm/cool boost;
- temperature/tint shift.

- [ ] **Step 2: Hide advanced controls for Color Lab route**

Keep advanced controls only if still needed in style/filter adjustment internals. Do not show them under `色彩实验室`.

- [ ] **Step 3: Verify UI route separation**

Acceptance:

- `风格` opens preset/filter list and strength.
- `色彩实验室` opens only palette/reset/optional strength.
- `设置` opens settings.
- `快捷` opens quick controls.

## Task 6: Final Verification

- [ ] **Step 1: Run focused settings tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.ColorLabSpecTest
```

- [ ] **Step 2: Run focused app tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
```

- [ ] **Step 3: Run preview/media tests**

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test --tests com.opencamera.core.effect.PreviewEffectAdapterTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.camera.PhotoAlgorithmPostProcessorTest
```

- [ ] **Step 4: Build APK**

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

- [ ] **Step 5: Optional stage verification**

After a meaningful closed loop:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Known Hard Parts And Guidance

- The palette math is intentionally already centralized in `ColorLabSpec`. Do not fork it into `MainActivity`, `FilterPaletteView`, or `PhotoAlgorithmPostProcessor`.
- The first-stage palette is global, not semantic. Do not add Apple-style subject/sky/background segmentation.
- Do not add a large LUT system in this pass.
- Saved output is expected to work through existing `filterSpec.*` metadata and `PhotoAlgorithmPostProcessor`.
- Preview is allowed to be approximate but must consume the same adjusted `FilterRenderSpec`.
- If a route name remains `LensLab` internally for low churn, visible text must still be `色彩实验室`.
- If implementing video support expands scope too much, keep stage 1 to photo-facing modes and document video as a follow-up.

## Multimodal Follow-Up

After implementation, hand to `2026-05-22-fourth-feedback-multimodal-visual-qa.md` for visual verification:

- palette appearance against real preview,
- top bar layout,
- side rail contents,
- preview vs saved photo color consistency,
- landscape usability.
