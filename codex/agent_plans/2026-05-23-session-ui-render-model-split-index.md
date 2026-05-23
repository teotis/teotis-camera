# 2026-05-23 SessionUiRenderModel Domain Split Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are text-only and do not require screenshot or video analysis.

## Goal

Address the external review finding that `SessionUiRenderModel.kt` has become a large cross-domain render-model hub. The end state is a set of domain-owned render model files and tests that can evolve independently while preserving existing app behavior.

## Audit Verdict

The external finding is accepted with two corrections:

1. The view-application renderer split has already started. Files such as `SettingsPanelRenderer.kt`, `FilterLabPanelRenderer.kt`, `CockpitSurfaceRenderer.kt`, `DevConsoleRenderer.kt`, `MainActivityRenderer.kt`, and `MainActivityViews.kt` already exist. This R1 package should not duplicate the earlier `MainActivity` thin-shell work.
2. A single broad `SessionUiRenderContracts.kt` must stay small. Moving all data classes into one new contract file would only rename the current giant file. Shared contracts should include only types genuinely reused across domains; domain-specific render models should live beside their domain builders.

## Evidence

- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt` has 2730 lines.
- `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt` has 1929 lines.
- `SessionUiRenderModel.kt` currently contains 40+ top-level declarations before helper functions, including controls, preview overlay, quick panel, settings page, portrait lab, watermark lab, filter lab, mode directory, diagnostics, and adjustment helpers.
- Top-level render model functions include `sessionControlsRenderModel`, `focusReticleRenderModel`, `previewOverlayRenderModel`, `frameRatioControlRenderModel`, `quickPanelSheetRenderModel`, `sessionSettingsPageRenderModel`, `runtimeProControlsRenderModel`, `portraitLabPageRenderModel`, `watermarkLabSelectorRenderModel`, `watermarkLabDetailRenderModel`, `filterLabPageRenderModel`, `devLogRenderModel`, `modeDirectoryRenderModel`, `modeTrackRenderModel`, `primaryStatusRenderModel`, and related text helpers.
- `MainActivity.render(state)` constructs all panel/domain models in one method, then fans them out to existing renderers and views.
- `SessionUiRenderModel.kt` imports app i18n plus `core:device`, `core:effect`, `core:media`, `core:mode`, `core:session`, and `core:settings` types, making every UI render change compile against many core domains.
- `SessionUiRenderModelTest.kt` covers unrelated areas in one suite: capture output, diagnostics, settings, runtime Pro controls, preview overlay, watermark, portrait, filter/style/color lab, mode directory/track, quick panel, primary status, and capture disabled reasons.
- Baseline verification passed:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

## Architectural Decision

Split by render-model domain, not by technical type category. Keep the existing package `com.opencamera.app`, existing `internal` visibility, and existing top-level function names where possible so call sites change minimally.

Target shape:

```text
app/src/main/java/com/opencamera/app/
  SessionUiRenderContracts.kt
    only shared UI contract types used by more than one domain

  SessionCockpitRenderModel.kt
    capture/output/status/controls/zoom/quick panel/mode track/frame ratio

  SessionPreviewRenderModel.kt
    overlay, preview frame, focus reticle render model mapping

  SessionSettingsRenderModel.kt
    settings root page, runtime Pro controls, portrait lab, watermark lab

  SessionStyleColorRenderModel.kt
    style lab, color lab, filter adjustment, light palette helpers

  SessionDiagnosticsRenderModel.kt
    diagnostics text and dev-log render model
```

`MainActivity.render(state)` may still orchestrate domains, but it should call small domain facades instead of directly knowing every render-model builder. Do not introduce a ViewModel, new session state owner, or second session kernel.

## Work Packages

1. [Shared Contracts, Cockpit, And Preview Split](./2026-05-23-session-ui-render-split-01-contracts-cockpit-preview.md)
   - First package.
   - Mechanical extraction with the lowest product risk.
   - Moves shared contracts plus cockpit/preview builders out of the giant file.

2. [Settings, Portrait, And Watermark Split](./2026-05-23-session-ui-render-split-02-settings-labs.md)
   - Depends on package 1 because it reuses shared settings controls.
   - Moves settings root, runtime Pro controls, portrait lab, and watermark lab.

3. [Style, Color, Diagnostics, And MainActivity Composition Split](./2026-05-23-session-ui-render-split-03-style-diagnostics-composer.md)
   - Depends on packages 1 and 2.
   - Moves style/color lab and diagnostics, then adds a small composition facade for `MainActivity.render(state)`.

## Recommended Execution Order

1. Run the baseline `SessionUiRenderModelTest`.
2. Implement package 1 and run the new cockpit/preview tests plus the old monolithic test.
3. Implement package 2 and run settings/watermark/portrait tests plus the old monolithic test.
4. Implement package 3 and remove or shrink the old monolithic test after all replacement tests exist.
5. Run the full Stage 7 gate.

## Parallelization Guidance

- Do not run all packages in parallel against the same branch. They touch the same source file and will create heavy merge churn.
- If multiple agents are used, assign package 1 first. After it lands, package 2 and package 3 can be prepared by separate agents only if one integrator owns final conflict resolution.
- Test-file splitting is best done in the same package as each source extraction so behavior coverage follows the moved code.

## Conflict Warnings

- Do not change visible text, labels, availability semantics, mode ordering, filter adjustment behavior, diagnostics formatting, or thumbnail behavior as part of this refactor.
- Do not move settings mutation into render models. Render models may expose `PersistedSettingsAction` or `FeatureCatalogAction`; they must not apply the action.
- Do not call CameraX, device adapters, media pipeline, or session recovery code from these render files.
- Do not create a broad `RenderModelRegistry`, `CameraViewModel`, or global UI state owner.
- Do not collapse existing `CameraCockpitRenderModel.kt` into this split. It is already a separate cockpit-specific model and should be left alone unless a compile error requires import cleanup.

## Global Acceptance

- `SessionUiRenderModel.kt` is deleted or reduced to a tiny compatibility facade with no domain implementation.
- Domain render-model files are each focused and materially smaller than the current 2730-line file.
- `SessionUiRenderModelTest.kt` is deleted or reduced to compatibility smoke tests; domain tests live in dedicated files.
- Existing renderers and `MainActivity` still receive equivalent models.
- Focused verification passes:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionPreviewRenderModelTest --tests com.opencamera.app.SessionSettingsRenderModelTest --tests com.opencamera.app.SessionStyleColorRenderModelTest --tests com.opencamera.app.SessionDiagnosticsRenderModelTest
```

- Legacy safety net passes until it is intentionally removed:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionUiRenderModelTest
```

- Stage 7 verification passes:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```
