# 2026-05-23 Mode Controller Refactor V2 Index

> **For agentic workers:** This is a handoff index for text-only implementation agents. Use `rtk` for every shell command. Keep each work package small, testable, and reversible. Do not introduce a `BaseModeController` in this loop.

## Verification Judgment

External review conclusion accepted with corrections.

The duplicated mode-controller patterns described in `.tmp/mode-refactor/v2-*.md` still exist in the current repository:

- Six still modes still build the same `DeviceGraphSpec.stillCapture(...)`.
- Five still modes still duplicate frame-ratio list/index/cycle/select behavior.
- Three modes still duplicate Pro variant state, manual draft tags, and manual-control fallback logic.
- Six still modes still duplicate the `ShotStarted / ShotCompleted / ShotFailed` session-event branch shape.

The recommended direction is also correct: extract small pure helpers/delegates in `core/mode`, then migrate feature controllers to them. A base controller should remain out of scope because capture strategy, effect spec, metadata, and post-process behavior are mode-specific and currently tested through session behavior.

## Corrections To The External V2 Docs

- Step 1 is valid as written in spirit, but tests should use `kotlin.test` rather than JUnit because `core/mode` already uses `testImplementation(kotlin("test"))`.
- Step 2 must preserve mode-specific cycle headlines such as `Portrait frame updated`, `Scenery assist frame updated`, and `Assist frame ratio updated`. A delegate that hard-codes one headline would regress product text.
- Step 3 should not force all inactive headlines into a shared helper. Portrait and Night have capability-dependent inactive headlines, so `ProVariantState` should own shared Pro/manual semantics while controllers keep their mode-specific headline choices.
- Step 4 should use a real reducer, not a headline-only object. The headline-only version leaves almost all branching duplication in place and is too small to justify the churn.
- The four packages should not be implemented in parallel in the same worktree because they touch overlapping mode files. Multiple agents may work on separate branches, but one integrator should merge them in the order below.

## Current Code Facts

Mode contracts and helper boundary:

- `core/mode/src/main/kotlin/com/opencamera/core/mode/ModeContracts.kt` defines `ModeContext`, `ModeRuntimeState`, `ModeIntent.FrameRatioSelected`, `ModeSessionEvent`, `ModeSignal`, and `ModeController`.
- `core/mode/build.gradle.kts` depends on `:core:device`, `:core:media`, `:core:settings`, and `:core:effect`, so helpers in `core/mode` may reference `DeviceGraphSpec`, `FrameRatio`, `EffectSpec`, and `ManualCaptureParams`.
- Current `rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test` baseline passed before writing these handoff docs.

Feature controllers that repeat still graph construction:

- `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
- `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
- `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
- `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
- `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
- `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`

Feature controllers that repeat frame-ratio behavior:

- `PhotoModePlugin.kt`
- `PortraitModePlugin.kt`
- `ProModePlugin.kt`
- `NightModePlugin.kt`
- `HumanisticModePlugin.kt`

Feature controllers that repeat Pro variant/manual draft behavior:

- `PortraitModePlugin.kt`
- `NightModePlugin.kt`
- `HumanisticModePlugin.kt`

Feature controllers that repeat still shot session events:

- `PhotoModePlugin.kt`
- `PortraitModePlugin.kt`
- `ProModePlugin.kt`
- `NightModePlugin.kt`
- `DocumentModePlugin.kt`
- `HumanisticModePlugin.kt`

`feature/mode-video/src/main/kotlin/com/opencamera/feature/video/VideoModePlugin.kt` is intentionally excluded from all still-photo helpers because it uses `DeviceGraphSpec.videoRecording(...)` and has recording-specific session-event state.

## Work Packages

1. [Still Capture Graph Helper](./2026-05-23-mode-refactor-still-capture-graph-helper.md)
   - Add `stillCaptureDeviceGraph(runtimeState)`.
   - Replace six duplicated `currentDeviceGraph()` bodies with one-line helper calls.
   - Lowest-risk package; implement first.

2. [Frame Ratio Delegate](./2026-05-23-mode-refactor-frame-ratio-delegate.md)
   - Add `FrameRatioDelegate`.
   - Migrate five still modes while preserving their current labels, event prefixes, and effect-spec updates.
   - Run existing session tests for frame-ratio behavior.

3. [Pro Variant State](./2026-05-23-mode-refactor-pro-variant-state.md)
   - Add `ProVariantState`.
   - Migrate Portrait, Night, and Humanistic Pro/manual draft semantics.
   - Keep capability-dependent headlines inside each controller.

4. [Still Shot Session Event Reducer](./2026-05-23-mode-refactor-still-shot-session-event-reducer.md)
   - Add `reduceStillShotSessionEvent(...)`.
   - Replace six still-mode `onSessionEvent` branch bodies with reducer calls.
   - Keep Video untouched.

## Recommended Order

Run these serially in one worktree:

1. Still capture graph helper.
2. Frame ratio delegate.
3. Pro variant state.
4. Still shot session event reducer.

If multiple agents execute in parallel, give each agent one package on its own branch or worktree. Expect conflicts in the mode plugin files and resolve them by preserving all helper imports and the latest wrapper method bodies.

## Global Verification

After each package, run the focused verification listed in that package document.

After all four packages are merged, run:

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:mode:test :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./scripts/verify_stage_7_observability.sh
```

If Gradle reports a transient build-directory or Kotlin daemon issue under `~/.codex-build/OpenCamera`, rerun the smallest failed command serially before treating it as a product regression.

