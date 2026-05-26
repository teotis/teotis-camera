# Package Status: 04-persistence-reset-unification

- **Agent**: Claude Code
- **Status**: completed
- **Started**: 2026-05-27
- **Completed**: 2026-05-27

## Worktree

- Path: `/Volumes/Extreme_SSD/project/open_camera/.worktrees/real-device-ux-polish/04-persistence-reset-unification`
- Branch: `agent/real-device-ux-polish/04-persistence-reset-unification`

## Changes

- git status: clean
- git diff --stat: 17 files changed, 458 insertions(+), 15 deletions(-)
- Changed files:
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsEnums.kt` — added `ResetTarget` enum
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt` — added `ResetToDefaults` action + reduce handler
  - `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt` — added `hasUserAdjustments()` utility
  - `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt` — added `resetToDefaults()` method
  - `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` — added `hasSettingsUserAdjustments`/`resetSettingsAction` to settings page, `hasStyleUserAdjustments`/`resetStyleAction` to filter lab, `hasUserAdjustments` to color lab
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` — added `hasQuickUserAdjustments`/`resetQuickAction` to quick panel
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt` — added `resetDefaults` button to QuickPanelViews, SettingsPanelViews, FilterLabViews
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt` — wired reset button click listeners
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` — quick panel reset button visibility
  - `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt` — settings reset button visibility
  - `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt` — filter lab reset button visibility
  - `app/src/main/res/layout/activity_main.xml` — added reset buttons to 3 panels
  - `app/src/main/res/values/strings.xml` — added `button_reset_defaults` string
  - `core/settings/src/test/.../PersistedSettingsSerializerTest.kt` — 10 new tests for reset + hasUserAdjustments
  - `app/src/test/.../SessionSettingsManagerTest.kt` — 4 new tests for resetToDefaults
  - `app/src/test/.../SessionUiRenderModelTest.kt` — 6 new tests for render model reset fields
  - `app/src/test/.../SessionCockpitRenderModelTest.kt` — 2 new tests for quick panel reset

## Verification

- Commands run:
  - `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest` — PASSED
  - `rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest` — 184 tests, 183 passed, 1 pre-existing failure
  - `rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug` — BUILD SUCCESSFUL
- Test results:
  - 22 new tests added, all passing
  - 1 pre-existing failure: `prepare filter for adjustment clones built in filter into editable custom default` (text encoding issue in filter profile slug, unrelated to this package)

## Delivery

- Commit hash: `f842b1f`
- PR link: (local branch, not pushed)

## Inventory Table

| Surface | Current State Owner | Persisted Key/Action | Reset Default |
|---|---|---|---|
| Settings (Common) | `PersistedSettings.common` | `common.gridMode`, `common.shutterSoundEnabled`, `common.selfieMirrorEnabled` | `CommonSettings()` |
| Style | `PersistedSettings.photo` | `defaultFilterProfileId`, `defaultHumanisticFilterProfileId`, `defaultPortraitFilterProfileId`, `styleStrength`, `colorLabSpec` | `PhotoSettings()` defaults for those fields |
| Color Lab | `PersistedSettings.photo.colorLabSpec` | `colorLab.colorAxis`, `colorLab.toneAxis`, `colorLab.strength` | `ColorLabSpec()` |
| Quick | `PersistedSettings.common` + `photo` + `video` | `common.gridMode`, `photo.livePhotoEnabledByDefault`, `photo.countdownDuration`, `video.defaultVideoSpec` | `PersistedSettings()` defaults for those fields |

## Acceptance Criteria Status

- [x] Settings, Style, Color Lab, and Quick all remember supported user adjustments across app/session recreation through existing stores.
- [x] Each surface exposes a bottom Reset control when any value differs from defaults.
- [x] Reset restores the documented default values and updates visible UI immediately (via `SessionSettingsManager.apply()` which saves + dispatches).
- [x] Quick adjustments that are intentionally runtime-only: brightness is session-only (not persisted), already labeled as such via `QuickBrightnessRenderModel.canReset`. Grid, live, timer, and video spec are persisted.
- [x] Tests cover reducer defaults, serialization, render-model reset visibility, and action dispatch.
- [x] No duplicate default constants — all defaults come from `PersistedSettings()` constructor.

## Self-Certification

- [x] Only touched allowed paths
- [x] Did not edit forbidden paths
- [x] Did not edit `INDEX.md` or other status files

## Unresolved Risks

- Real-device behavior: the reset buttons use `visibility=GONE` by default and are shown via render model flags. Actual tap behavior depends on `MainActivityActionCallbacks.applySettingsAction()` being wired to the session manager — which it already is for all other settings controls.
- The `brightness` quick control is session-only (not persisted) and intentionally excluded from the Quick reset target. This matches the existing architecture where `QuickBrightnessRenderModel.canReset` handles brightness reset separately.
- Pre-existing test failure `prepare filter for adjustment clones built in filter into editable custom default` is unrelated to this package (text encoding issue in slug generation).
