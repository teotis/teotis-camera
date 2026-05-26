# 2026-05-23 MainActivity Thin Shell Refactor Index

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` if executing one of these plans. Use `rtk` for every shell command. These plans are text-only and do not require screenshot or video analysis.

## Goal

Verify and address the external review finding that `MainActivity.kt` has absorbed too many UI-shell responsibilities. The end state is a thinner Android `Activity` that wires lifecycle, dependency access, state collection, and high-level callbacks, while view binding, panel route transitions, rendering, and input binding live in focused app-layer collaborators.

## Audit Verdict

The external finding is accepted with one correction: `MainActivity` is not only three hidden objects. It currently acts as at least these app-shell collaborators:

1. `ViewBinder`: 138 `findViewById` calls and over 100 view fields.
2. `StateRenderer`: `render(state)` constructs and applies settings, portrait, watermark, filter, overlay, mode-track, quick-panel, dev-log, thumbnail, and shutter models.
3. `PanelRouter`: `activePanelRoute`, `selectedSettingsTab`, `selectedWatermarkDetailTemplateId`, `selectedFilterLabFamilyOverride`, `isFilterAdjustmentVisible`, `filterAdjustmentMode`, and `lightPaletteBaseSpec` are mutated from click handlers, back navigation, and render refresh helpers.
4. `ActionBinder`: 84 `setOnClickListener` occurrences bind UI events to session intents, settings actions, route changes, gallery launch, dev-log export, and filter preparation.
5. `GestureBridge`: preview touch routing reads panel state and latest session state, maps gestures, normalizes focus taps, and dispatches session intents.
6. `PlatformShell`: permission launcher, locale update, orientation monitor, shutter sound, FileProvider/gallery `Intent`, and toasts.

This confirms the architectural risk: new UI changes must understand too much state and too many Android side effects in one 1974-line file.

## Evidence

- `app/src/main/java/com/opencamera/app/MainActivity.kt` has 1974 lines.
- Lines 75-232 are a large mixed field block: view references, route state, render-model caches, filter edit state, gesture objects, and platform resources.
- Lines 252-410 perform manual view lookup and setup, with 138 `findViewById` occurrences in the file.
- Lines 452-765 bind most UI actions directly in the Activity; the file contains 84 `setOnClickListener` occurrences.
- Lines 767-810 bridge gestures, route guards, latest session state, preview geometry, and session dispatch.
- Lines 828-930 are the main render fan-out and side-effect area.
- Lines 1339-1413 mutate and render panel route visibility.
- Lines 1635-1673 duplicate panel route transition logic in `onBackPressed`.
- Lines 1703-1912 mix settings application, filter preparation, custom filter editing, color-lab palette updates, and render refreshes.
- `CockpitPanelRoute.kt` already defines the route type, but route transition policy still lives in `MainActivity`.
- Existing pure helpers such as `GalleryOpenTarget.kt`, `PreviewTapFocusGeometry.kt`, `ModeTrackTouchPolicy.kt`, and render-model tests show the project already accepts extracting Activity logic into small testable app-layer units.

## Architectural Decision

Do not introduce a large `CameraViewModel` as the first step. The current project already has a `Session Kernel` and `SessionState`; adding a broad ViewModel before the Activity is untangled risks creating a second UI/session coordinator. Start by extracting passive app-shell collaborators around the existing kernel:

```text
MainActivity
  owns Android lifecycle and coroutine scope
  collects container.cameraSession.state
  delegates route transitions to CockpitPanelRouter
  delegates view lookup to MainActivityViews
  delegates rendering to MainActivityRenderer
  delegates click/touch setup to MainActivityActionBinder
  launches Android-only effects such as permission, gallery, toast, and external settings
```

Only after these seams are stable should a narrow `ActivityViewModel` be considered, and only if it owns UI-only route state rather than session runtime state.

## Work Packages

1. [Panel Router And UI State Extraction](./2026-05-23-mainactivity-panel-router-plan.md)
   - First package.
   - Pure Kotlin, low Android risk, creates tests for route/back/close transitions.

2. [View Binder And Renderer Surface Extraction](./2026-05-23-mainactivity-view-renderer-plan.md)
   - Depends on package 1 only for route state names.
   - Moves view references into grouped binding classes and moves view-application code into renderer classes.

3. [Action Binder And Thin Activity Integration](./2026-05-23-mainactivity-action-binder-integration-plan.md)
   - Depends on packages 1 and 2.
   - Moves click/touch binding out of `MainActivity`, defines callback interfaces, and leaves Activity as composition shell.

## Recommended Execution Order

1. Implement package 1 and run pure app tests plus `:app:assembleDebug`.
2. Implement package 2 in small sub-steps: bind views first, then settings renderer, filter renderer, dev renderer, cockpit renderer.
3. Implement package 3 after package 2 lands, because action binding needs the grouped view container.
4. Run the full Stage 7 verification script after all packages land.

## Parallelization Guidance

- Package 1 can be implemented independently.
- Package 2 should have one owner because it touches many lines in `MainActivity.kt`.
- Package 3 should start after package 2 to avoid merge churn.
- Multiple agents may split package 2 by renderer only if one integrator owns `MainActivity.kt` and `MainActivityViews.kt`.

## Conflict Warnings

- Do not move camera runtime decisions into UI collaborators. UI collaborators may dispatch `SessionIntent` or apply `PersistedSettingsAction`; they must not call CameraX, adapters, or recovery paths.
- Do not create a new hidden session kernel in a ViewModel, router, renderer, binder, coordinator, or manager.
- Do not rewrite `activity_main.xml` as part of this refactor unless a compile error forces a local ID fix.
- Do not change product behavior, visible text, route semantics, or panel IA in this refactor.
- Do not delete `CockpitPanelRoute` or existing render models.
- Avoid enabling Android ViewBinding as a first step. A manual grouped binder is lower risk and avoids Gradle/config churn.

## Global Acceptance

- `MainActivity.kt` is materially smaller and no longer owns bulk view fields, panel transition policy, renderer implementation, or click binding implementation.
- Panel navigation behavior remains unchanged, including scrim close, settings back, Android back, style/color lab close, quick bubble, and dev console.
- Existing render model tests still pass.
- New pure tests cover panel route transitions and any extracted mapper logic.
- Stage 7 verification remains green:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

