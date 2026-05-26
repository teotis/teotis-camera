# 2026-05-23 Settings Contract Split Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing this plan. Use `rtk` for every shell command. This plan is text-only and does not require screenshots, videos, or visual judgment.

## Goal

Split `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsContracts.kt` into focused files while preserving source-level API and behavior.

## Current Evidence

`SettingsContracts.kt` is 1382 lines and currently contains value vocabulary, settings data models, feature catalog operations, action reducers, codecs, metadata helpers, and default catalog data.

Existing nearby files:

- `AppLanguage.kt`: 12 lines.
- `ColorLabSpec.kt`: 125 lines.
- `FeatureCatalogStore.kt`: 31 lines.
- `PersistedSettingsSerializer.kt`: 208 lines.
- `PersistedSettingsStore.kt`: 22 lines.
- `StyleColorPipeline.kt`: 232 lines.

The serializer for persisted settings is already separate. This plan should preserve that split and move the remaining non-contract logic out of the giant file.

## Architecture Target

All new files stay in:

```kotlin
package com.opencamera.core.settings
```

Target source files:

- `SettingsEnums.kt`: storage-key enums and small fixed vocabularies.
- `SettingsDataModels.kt`: data classes only, with minimal value-local helpers when needed for compatibility.
- `FeatureCatalogOperations.kt`: catalog merge/filter/custom-profile helpers.
- `FeatureCatalogActions.kt`: `FeatureCatalogAction` and `FeatureCatalog.reduce`.
- `SettingsActions.kt`: `PersistedSettingsAction`, `PersistedSettings.reduce`, and private reducer helpers.
- `SettingsMetadataCodecs.kt`: `FilterRenderSpec` metadata codec and `ManualCaptureParams` metadata tags.
- `SettingsShareCodecs.kt`: `FilterProfileShareCodec`, `ImportedFilterProfilesSerializer`, and `ManualCaptureDraftSerializer`.
- `SettingsDefaults.kt`: built-in filter profiles, watermark templates, and `defaultFilterRenderSpecOrNull`.

`SettingsContracts.kt` should be deleted after all declarations move and compilation is green. If deletion creates tooling churn, leave only the package line and a short comment for one pass, then delete in a cleanup commit.

## File Movement Map

Move these line ranges from `SettingsContracts.kt`:

- To `SettingsEnums.kt`:
  - `CompositionGridMode`, `CountdownDuration`, `AudioProfile`, `VideoResolution`, `VideoFrameRate`, `DynamicVideoFpsPolicy`, `FilterProfileCategory`.
  - `LiveWatermarkMotionBehavior`.
  - `WatermarkTextPlacement`, `WatermarkTextScale`, `WatermarkTextOpacity`, `WatermarkFrameBackground`.
  - `PortraitProfile`, `PortraitBeautyPreset`, `PortraitBeautyStrength`, `PortraitBokehEffect`.

- To `SettingsDataModels.kt`:
  - `VideoSpec`.
  - `FilterRenderSpec`.
  - `ManualCaptureParams`.
  - `LiveMediaBundle`.
  - `FilterProfile`.
  - `WatermarkTemplate`.
  - `WatermarkStyleSettings`.
  - `VideoSpecConstraints`.
  - `CommonSettings`, `PhotoSettings`, `VideoSettings`, `PersistedSettings`.
  - `FeatureCatalog`.
  - `SessionSettingsSnapshot`.

- To `FeatureCatalogOperations.kt`:
  - `FeatureCatalog.createCustomFilterProfile`.
  - `FeatureCatalog.updateCustomFilterProfile`.
  - `mergeCatalog`.
  - `FeatureCatalog.filterProfilesFor`.
  - private `String.slugify`.

- To `FeatureCatalogActions.kt`:
  - `FeatureCatalogAction`.
  - `fun FeatureCatalog.reduce(action: FeatureCatalogAction): FeatureCatalog`.

- To `SettingsActions.kt`:
  - `PersistedSettingsAction`.
  - `fun PersistedSettings.reduce(action: PersistedSettingsAction): PersistedSettings`.
  - `fun PhotoSettings.watermarkStyleFor(templateId: String): WatermarkStyleSettings`.
  - private `PhotoSettings.updateWatermarkStyle`.

- To `SettingsMetadataCodecs.kt`:
  - `FilterRenderSpec.toMetadataTags`.
  - `FilterRenderSpec.fromMetadataTags`.
  - `ManualCaptureParams.compactSummary`.
  - `ManualCaptureParams.toMetadataTags`.
  - private `decodeAutoInt`, `decodeAutoLong`, and `decodeAutoFloat` if they are not needed by share codecs.

- To `SettingsShareCodecs.kt`:
  - `FilterProfileShareCodec`.
  - `ImportedFilterProfilesSerializer`.
  - `ManualCaptureDraftSerializer`.

- To `SettingsDefaults.kt`:
  - `defaultFilterRenderSpecOrNull`.
  - private `builtInFilterProfile`.
  - private `renderSpec`.
  - `DEFAULT_FILTER_PROFILES`.
  - `DEFAULT_WATERMARK_TEMPLATES`.

## Compatibility Rules

- Keep every public symbol name unchanged.
- Keep enum `storageKey`, label, ID, and metadata key strings unchanged.
- Keep `FilterRenderSpec.toMetadataTags()` callable with the same syntax.
- Keep `FilterRenderSpec.fromMetadataTags()` callable with the same syntax.
- Keep `ManualCaptureParams.toMetadataTags()` callable with the same syntax.
- Keep `FeatureCatalog.reduce(...)` and `PersistedSettings.reduce(...)` callable with the same syntax.
- Do not edit `PersistedSettingsSerializer.kt` except for imports if the compiler demands it.

If `FilterRenderSpec` metadata logic is moved out of the data class, keep delegating wrappers inside `FilterRenderSpec`:

```kotlin
data class FilterRenderSpec(/* existing fields */) {
    fun toMetadataTags(prefix: String = FilterRenderSpecMetadataCodec.DEFAULT_PREFIX): Map<String, String> {
        return FilterRenderSpecMetadataCodec.toMetadataTags(this, prefix)
    }

    companion object {
        fun fromMetadataTags(
            tags: Map<String, String>,
            prefix: String = FilterRenderSpecMetadataCodec.DEFAULT_PREFIX
        ): FilterRenderSpec? {
            return FilterRenderSpecMetadataCodec.fromMetadataTags(tags, prefix)
        }
    }
}
```

Use an `object FilterRenderSpecMetadataCodec` in `SettingsMetadataCodecs.kt` so the heavy logic no longer lives inside the data model.

## Implementation Steps

1. Create the new files with only `package com.opencamera.core.settings`.
2. Move declarations by owner, preserving declaration bodies exactly on the first pass.
3. Remove duplicate declarations from `SettingsContracts.kt` after each moved group.
4. Run `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test` after moving actions/codecs/defaults.
5. If the compiler reports missing extension imports outside `core.settings`, add explicit imports instead of moving declarations back.
6. Delete or empty `SettingsContracts.kt` only after all tests compile.
7. Run the focused app tests because UI render models and settings manager are the heaviest consumers:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionUiRenderModelTest
```

8. Run the Stage 7 gate before handoff:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Tests To Watch

- `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/FilterProfileShareCodecTest.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/ColorLabSpecTest.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/StyleColorPipelineTest.kt`
- `app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

## Risk Notes

- Kotlin top-level functions moved between files keep the same package name for source consumers, but their generated JVM file-class names change. This is acceptable for this app source tree, but do not publish this as a binary-compatible library change without explicit approval.
- `FeatureCatalog.reduce` currently sits as a member function. Moving it to a top-level extension is source-compatible for existing call syntax only where `reduce` is imported or in the same package. `SessionSettingsManager.kt` already imports `com.opencamera.core.settings.reduce`; check compiler output for any other missing import.
- `FilterRenderSpec.fromMetadataTags` is a companion function. Preserve a companion wrapper if moving logic to a codec object.
- Do not normalize, rename, or reorder default filters during this pass. Reordering can change UI order and tests.

## Completion Criteria

- `SettingsContracts.kt` no longer owns mixed responsibilities.
- The largest new settings file is below roughly 450 lines.
- All public settings symbols still live in `com.opencamera.core.settings`.
- Focused settings tests pass.
- Focused app settings/render tests pass.
- Stage 7 observability gate passes before declaring completion.

