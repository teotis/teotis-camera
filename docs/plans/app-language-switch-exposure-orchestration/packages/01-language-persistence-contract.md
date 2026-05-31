# Package: 01-language-persistence-contract

## Mission

Make app language a real persisted setting and fix the language display contract without touching settings-panel UI wiring.

## Allowed Paths

- `core/settings/src/main/kotlin/com/opencamera/core/settings/AppLanguage.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `core/settings/src/test/kotlin/com/opencamera/core/settings/PersistedSettingsSerializerTest.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `app/src/main/res/values/strings.xml`
- `app/src/main/res/values-en/strings.xml`
- `app/src/test/java/com/opencamera/app/TestAppTextResolver.kt`

## Forbidden Paths

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `feature/**`
- Other package status files
- `INDEX.md`

## Acceptance Criteria

- Add a serializer key such as `common.appLanguage`.
- `PersistedSettingsSerializer.toMap()` writes `settings.common.appLanguage.storageKey`.
- `PersistedSettingsSerializer.fromMap()` restores `AppLanguage.fromStorageKey(...)` and falls back to the default for missing/invalid values.
- Add tests proving EN round-trips and missing/invalid values keep the default.
- Fix `AppTextResolver.languageDisplayName()` so Chinese displays as `中文` rather than `OpenCamera`, and English displays as `English`.
- Keep `AppLanguage` scoped to supported values for this release: `ZH`, `EN`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Expected Evidence

- Commit hash on `agent/app-language-switch-exposure/01-language-persistence-contract`.
- Diff summary showing serializer key/read/write and display-name fix.
- Test output for the two commands above.
- Note whether resource keys were added to both `values/strings.xml` and `values-en/strings.xml`.

## Unlock Conditions

- Mark completed only after the focused settings serializer test passes.
- If AppTextResolver changes break app tests, mark blocked with the exact failing assertion.
