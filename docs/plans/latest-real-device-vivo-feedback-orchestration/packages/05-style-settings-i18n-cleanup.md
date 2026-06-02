# Package 05 - Style Settings i18n Cleanup

## Goal

Remove remaining English from Style-related UI and expose a language switch under Settings > Common.

## Dependencies

- Depends on `04-quick-panel-behavior-defaults` because both may touch i18n/text resolver and settings render models.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/FilterLabPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/SettingsTab.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` Style/Settings/Common sections only
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/AppLanguage.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsActions.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/SettingsDataModels.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/PersistedSettingsSerializer.kt`
- `app/src/main/res/values*/**`
- focused i18n/settings/style tests
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/05-style-settings-i18n-cleanup.md`
- package-local scratch path

## Forbidden Paths

- Do not hide untranslated controls by removing feature access.
- Do not add a second settings owner outside persisted settings.
- Do not claim full language visual QA without screenshots/device evidence.
- Do not rewrite unrelated settings panel structure.

## Tasks

1. Search current Style/Filter/Color/Settings UI strings for English/internal ids that can surface to the user.
2. Add missing localized labels through existing resource/resolver patterns.
3. Expose a Settings > Common language switch for existing `AppLanguage` semantics.
4. Persist language selection and make render model/text resolver reflect the selected language where existing architecture supports it.
5. Add tests for language switch rendering, persistence, and Style copy cleanup.

## Acceptance Criteria

- Style panel does not show English/internal ids in Chinese UI.
- Settings > Common exposes a language control with Chinese/English options or cycle behavior.
- Language choice persists through settings serialization.
- If immediate locale re-render is not locally provable, UI truthfully documents/requires restart instead of pretending.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :core:settings:test
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Resource keys/strings added or changed.
- Settings Common language switch render evidence.
- Style English audit summary.
- Tests/build and commit hash.

## Unlock Condition

Mark `completed` only after tests/build pass and residual real-device language visual QA is recorded.
