# Session UI Render Split 02: Settings, Portrait, And Watermark

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to execute this task. Use `rtk` for every command. This package is text-only and does not require screenshots.

**Goal:** Move settings root, runtime Pro controls, portrait lab, and watermark lab render-model builders into a focused settings/labs file with dedicated tests.

**Architecture:** Settings render models remain pure projections from `SessionState` plus `AppTextResolver`. They may expose `PersistedSettingsAction` and `FeatureCatalogAction`, but they must not apply settings or call session/device runtime code.

**Tech Stack:** Kotlin app module, existing `core:settings` and `core:device` contracts, JVM unit tests.

---

## Dependency

Implement this after package 1 lands. This package expects `SettingsControlRenderModel`, `SettingsControlAvailability`, and `FeatureCatalogControlRenderModel` to exist in `SessionUiRenderContracts.kt`.

## Current Code Facts

- Settings/lab declarations currently live in `SessionUiRenderModel.kt`:
  - `SessionSettingsRenderModel`
  - `CommonSettingsSectionRenderModel`
  - `PhotoSettingsSectionRenderModel`
  - `VideoSettingsSectionRenderModel`
  - `SessionSettingsPageRenderModel`
  - `RuntimeProControlsRenderModel`
  - `WatermarkLabTemplateItemRenderModel`
  - `WatermarkLabSelectorRenderModel`
  - `WatermarkLabDetailRenderModel`
  - `PortraitLabPageRenderModel`
- Main builders:
  - `sessionSettingsRenderModel`
  - `sessionSettingsPageRenderModel`
  - `runtimeProControlsRenderModel`
  - `portraitLabPageRenderModel`
  - `watermarkLabSelectorRenderModel`
  - `watermarkLabDetailRenderModel`
- Existing renderer consumer: `SettingsPanelRenderer.kt`.

## Files

Create:

- `app/src/main/java/com/opencamera/app/SessionSettingsRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionSettingsRenderModelTest.kt`

Modify:

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`

Do not modify:

- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- `core/settings/**`
- `core/session/**`

## Step 1: Baseline

Run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Expected: `BUILD SUCCESSFUL`.

## Step 2: Move Settings And Lab Declarations

Create `SessionSettingsRenderModel.kt` and move the full existing declarations for `SessionSettingsRenderModel`, `CommonSettingsSectionRenderModel`, `PhotoSettingsSectionRenderModel`, `VideoSettingsSectionRenderModel`, `SessionSettingsPageRenderModel`, `RuntimeProControlsRenderModel`, `WatermarkLabTemplateItemRenderModel`, `WatermarkLabSelectorRenderModel`, `WatermarkLabDetailRenderModel`, and `PortraitLabPageRenderModel`.

Also move the related builders and private helpers:

- `sessionSettingsRenderModel`
- `sessionSettingsPageRenderModel`
- `runtimeProControlsRenderModel`
- `portraitLabPageRenderModel`
- `watermarkLabSelectorRenderModel`
- `watermarkLabDetailRenderModel`
- `ManualControlSupport.toSettingsAvailability`
- `ManualControlSupport.manualSupportLabel`
- `manualSupportSummary`
- `onOffLabel`
- `photoFilterLabel`
- `videoFilterLabel`
- `watermarkTemplateLabel`
- `Set<String>.prettyWatermarkTokens`
- manual option lists and manual label helpers if they are only used by `runtimeProControlsRenderModel`
- `StillCaptureOutputSize.label`, `LensFacing.label`, and summary helpers only if they are used by settings/pro controls after package 1 extraction

Keep helper functions private unless another domain still uses them.

## Step 3: Keep API Names Stable

Do not rename public-in-package functions. Existing callers should still compile:

```kotlin
val settingsPage = sessionSettingsPageRenderModel(state, text)
val portraitLabPage = portraitLabPageRenderModel(state, text)
val watermarkSelectorPage = watermarkLabSelectorRenderModel(state, text)
val watermarkDetailPage = watermarkLabDetailRenderModel(state, templateId, text)
```

`SettingsPanelRenderer` should not need any source changes except possible import cleanup from the IDE/compiler.

## Step 4: Add Focused Tests

Create `SessionSettingsRenderModelTest.kt` by moving these tests from `SessionUiRenderModelTest.kt`:

- `settings render model resolves configured defaults from session snapshot`
- `runtime pro controls render model exposes editable controls for active pro variant`
- `runtime pro controls degrade to saved only on devices without manual support`
- `runtime pro controls surface unsupported controls from capability matrix`
- `settings page render model exposes section controls and catalog hints`
- `settings page section summaries are empty to avoid duplication`
- `settings page does not aggregate child states in footer`
- `settings page render model disables editing while shot is active`
- `settings page render model surfaces supported degraded and unsupported controls`
- `watermark lab selector render model exposes selection and per template style entry`
- `portrait lab render model exposes profile beauty and bokeh controls`
- `watermark lab detail render model exposes frame controls for frame templates`
- `watermark lab detail hides frame background control for classic overlay`
- `save as custom button label is localized via text resolver`
- `portrait lab page render model uses text resolver for all labels`
- `availability labels use dedicated strings not quality level labels`

Move the smallest needed test fixtures from `SessionUiRenderModelTest.kt`. If the fixture is shared by many future domain tests, create `app/src/test/java/com/opencamera/app/SessionRenderModelTestFixtures.kt` by moving the existing `defaultSessionState` helper and `strings` companion value from `SessionUiRenderModelTest.kt`. Reuse the existing `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`; do not create a second resolver type.

The moved `defaultSessionState` helper should keep the same default parameters and returned `SessionState` values so copied tests do not change expectations.

## Step 5: Verify

Run focused tests:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsRenderModelTest
```

Run legacy safety net:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

Run app compile:

```bash
rtk ./gradlew --no-daemon :app:assembleDebug
```

Expected: all commands end with `BUILD SUCCESSFUL`.

## Acceptance

- Settings, portrait, watermark, and runtime Pro render-model code no longer lives in `SessionUiRenderModel.kt`.
- `SettingsPanelRenderer.kt` behavior is unchanged.
- Settings render models remain read-only projections.
- Dedicated tests cover settings/lab behavior without requiring the whole monolithic test file.
