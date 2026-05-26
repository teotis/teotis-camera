# 04 Persistence Reset Unification

## Package ID

`04-persistence-reset-unification`

## Goal

Unify automatic memory and Reset behavior for user adjustments in Settings, Style, Color Lab, and Quick. This package should prevent scattered one-off reset logic and make default restoration testable.

## Problem Statement

Real-device feedback asks that user adjustments in “设置”“风格”“色彩实验室”“快捷” auto-remember and expose a bottom Reset button. The existing code already persists many values through `PersistedSettingsAction`, `PersistedSettingsSerializer`, `SharedPreferencesPersistedSettingsStore`, and catalog stores, but reset affordances are inconsistent and some Quick/session adjustments may be runtime-only.

## Allowed Paths

- `core/settings/**`
- `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- `app/src/main/java/com/opencamera/app/SharedPreferencesPersistedSettingsStore.kt`
- settings/style/color/quick reset render-model sections in `SessionUiRenderModel.kt` and `SessionCockpitRenderModel.kt`
- `MainActivityActionBinder.kt` reset button wiring
- relevant layout/views for reset controls
- `core/settings/src/test/**`
- `app/src/test/java/com/opencamera/app/SessionSettingsManagerTest.kt`
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- package status file `status/04-persistence-reset-unification.md`

## Forbidden Paths

- core session/device ownership changes unless a Quick setting currently has no persisted representation and the package explicitly documents why
- effect/preview conflict files
- unrelated UI animation/visual polish
- any other package status file

## Dependencies

Run after packages 01, 02, and 03 settle. This package spans all affected surfaces and should integrate their final labels/routes/dismiss behavior.

## Parallel Safety

Not parallel safe. Run alone.

## Design Contract

- Persisted settings remain the source of truth for remembered user preferences.
- UI temporary state may exist only for active drag/gesture feedback and must commit to persisted/session-owned state at a clear point.
- Reset restores a defined default snapshot, not arbitrary hard-coded scattered values.
- Reset controls should live at the bottom of the relevant panel/sheet and be disabled or hidden when already at defaults.
- Do not create a second hidden settings owner in `MainActivity`.

## Suggested Scope Split Inside The Package

1. Inventory existing persisted controls across Settings, Style, Color Lab, and Quick.
2. Define default snapshot/reset actions in `core:settings`.
3. Add render-model flags for `hasUserAdjustments` and reset action per surface.
4. Wire bottom Reset controls in app UI.
5. Add serializer/reducer/manager/render-model tests.

## Acceptance Criteria

- Settings, Style, Color Lab, and Quick all remember supported user adjustments across app/session recreation through existing stores.
- Each surface exposes a bottom Reset control when any value differs from defaults.
- Reset restores the documented default values and updates visible UI immediately.
- Quick adjustments that are intentionally runtime-only are either persisted or explicitly labeled as session-only with user-approved rationale.
- Tests cover reducer defaults, serialization, render-model reset visibility, and action dispatch.
- No duplicate default constants are scattered across app and core.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:settings:test --tests com.opencamera.core.settings.PersistedSettingsSerializerTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionSettingsManagerTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.SessionCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

In an isolated worktree, replace `rtk ./gradlew` with `rtk ./scripts/run_isolated_gradle.sh`.

## Expected Evidence Pack

- Inventory table: surface, current state owner, persisted key/action, reset default.
- Tests proving reset and persistence.
- Residual risk list for any real-device-only settings recreation behavior.
