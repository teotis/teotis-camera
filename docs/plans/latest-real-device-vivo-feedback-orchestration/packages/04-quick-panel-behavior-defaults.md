# Package 04 - Quick Panel Behavior Defaults

## Goal

Repair Quick panel behavior from the latest real-device test:

- Live button defaults to off.
- Watermark option in Quick must use localized user-facing copy, not English/internal template ids.
- Tapping preview/outside panel area while Quick is open dismisses the panel without triggering capture, focus, or unrelated mode actions.

## Dependencies

- Depends on `01-cockpit-bottom-layout` so bottom/panel geometry changes are known before touch/dismiss hit areas are adjusted.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/CockpitPanelRouter.kt`
- `app/src/main/java/com/opencamera/app/CockpitPanelRoute.kt`
- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` Quick sections only
- `app/src/main/java/com/opencamera/app/SessionSettingsManager.kt`
- `app/src/main/java/com/opencamera/app/i18n/AppTextResolver.kt`
- `core/settings/src/main/kotlin/com/opencamera/core/settings/**`
- focused Quick/settings/panel tests
- `docs/plans/latest-real-device-vivo-feedback-orchestration/status/04-quick-panel-behavior-defaults.md`
- package-local scratch path

## Forbidden Paths

- Do not disable Live capability globally; only make the default state off or truthful.
- Do not remove watermark controls to avoid English.
- Do not route outside taps through capture/focus side effects.
- Do not edit Style/Settings language switch scope unless needed for shared string resolver consistency.

## Tasks

1. Inspect Quick render rows, Live persisted/default state, watermark row labels, and panel dismissal scrim/hit regions.
2. Make Live default off while preserving explicit user selection and persisted settings.
3. Ensure Quick watermark labels use localized template names and never raw ids.
4. Make outside preview/scrim tap dismiss Quick reliably and safely.
5. Add focused tests for Live default, watermark copy, and outside-dismiss routing.

## Acceptance Criteria

- Fresh/default settings render Live as off.
- Quick watermark row shows localized label/value in Chinese and does not expose English template ids.
- Outside panel preview tap closes Quick and does not dispatch focus/capture/mode changes.
- Existing Quick controls remain available and no reset/regression reappears.

## Verification

```bash
rtk ./scripts/run_isolated_gradle.sh -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.CockpitPanelRouterTest --tests com.opencamera.app.SessionSettingsManagerTest
rtk ./scripts/run_isolated_gradle.sh :app:assembleDebug
```

## Evidence Required

- Default Live state evidence.
- Before/after Quick watermark label examples.
- Dismissal routing test evidence.
- Commit hash and residual real-device tap checklist.

## Unlock Condition

Mark `completed` only after focused tests/build pass and package status states any real-device tap residual risk.
