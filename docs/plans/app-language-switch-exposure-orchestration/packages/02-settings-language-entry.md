# Package: 02-settings-language-entry

## Mission

Expose a visible language control in the Common settings page and wire it to `PersistedSettingsAction.UpdateAppLanguage`.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderContracts.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderContractsTest.kt`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/AppLanguage.kt` (read-only unless needed for next-language helper)

## Forbidden Paths

- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
- `feature/**`
- Other package status files
- `INDEX.md`

## Acceptance Criteria

- Common settings render model has a `language` control alongside grid, shutter sound, and selfie mirror.
- The language button label is user-facing, compact, and displays current value (`中文` or `English`).
- Clicking the button dispatches `PersistedSettingsAction.UpdateAppLanguage(nextLanguage)`.
- `SettingsPanelRenderer.renderPage()` renders and enables/disables the language control consistently with other Common controls.
- `MainActivityViews` binds the new button from `activity_main.xml`.
- `MainActivityActionBinder` applies the language control action.
- Existing tab behavior remains unchanged: clicking Common tab immediately shows updated Common content.
- Add/update focused app render-model/contract tests for the language action and button label.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionUiRenderContractsTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence

- Commit hash on `agent/app-language-switch-exposure/02-settings-language-entry`.
- Diff summary showing render model, layout, view binding, renderer, and action binder changes.
- Test/build output for the commands above.
- Screenshot is optional; do not mark real-device visual QA as complete from local tests.

## Unlock Conditions

- Mark completed only when the focused app tests and assemble pass.
- If layout text clips or a new Common control makes the panel unusable in code review, mark blocked with exact files and recommended UI fallback.
