# Package 02 — Watermark Mainline Landing

## Package ID
`02-watermark-mainline`

## Goal
Make the previous watermark and watermark UI component work visible on current `main`, including preview hint, saved-photo metadata/postprocess wiring, and settings controls.

## Context
- User issue: the earlier watermark and UI component improvements do not appear on device.
- Current validation finding: external commit `ad75ca4` added `WatermarkEffect` to five non-photo mode plugins, but that commit is not an ancestor of current `HEAD`.
- Current main currently shows `WatermarkEffect` only in `PhotoModePlugin`.
- Relevant files:
  - `feature/mode-photo/src/main/kotlin/com/opencamera/feature/photo/PhotoModePlugin.kt`
  - `feature/mode-humanistic/src/main/kotlin/com/opencamera/feature/humanistic/HumanisticModePlugin.kt`
  - `feature/mode-portrait/src/main/kotlin/com/opencamera/feature/portrait/PortraitModePlugin.kt`
  - `feature/mode-night/src/main/kotlin/com/opencamera/feature/night/NightModePlugin.kt`
  - `feature/mode-pro/src/main/kotlin/com/opencamera/feature/pro/ProModePlugin.kt`
  - `feature/mode-document/src/main/kotlin/com/opencamera/feature/document/DocumentModePlugin.kt`
  - `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `core/effect/**`
  - `core/settings/**`
- Non-goals:
  - Do not reintroduce video watermark burn-in.
  - Do not edit shutter animation or zoom gesture code.

## Implementation Scope
- Rebase or reimplement the non-photo-mode `WatermarkEffect` wiring on current `main`.
- Confirm watermark style controls (`textScale`, `textOpacity`, placement, background) are rendered and dispatch setting actions.
- Ensure preview hint and saved-photo metadata/postprocess chain receive the same template/style values.
- Add focused tests covering at least one non-photo mode and settings control bindings.

## Acceptance Criteria
- [ ] Current `main` contains `WatermarkEffect` wiring for all still-photo modes that should support watermark: photo, humanistic, portrait, night, pro, document.
- [ ] The selected watermark template and per-template style are read from persisted photo settings.
- [ ] `watermarkTextScale` and `watermarkTextOpacity` reach metadata/custom tags used by the postprocessor.
- [ ] Preview watermark hint is generated from the same `WatermarkEffect`.
- [ ] Watermark settings controls render and dispatch actions.
- [ ] `rtk ./gradlew ... --tests '*Watermark*'` passes.

## Allowed Paths
- `feature/mode-photo/**`
- `feature/mode-humanistic/**`
- `feature/mode-portrait/**`
- `feature/mode-night/**`
- `feature/mode-pro/**`
- `feature/mode-document/**`
- `app/src/main/java/com/opencamera/app/SettingsPanelRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `core/effect/**`
- `core/settings/**`
- Focused tests under `app/src/test/**`, `core/effect/src/test/**`, and `core/settings/src/test/**`

## Forbidden Paths
- `app/src/main/java/com/opencamera/app/camera/CameraXCaptureAdapter.kt`
- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/gesture/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/ShutterVisualDrawable.kt`

## Dependencies
- Depends on: none

## Parallel Safety
- safe
- Reason: mode plugin/settings/effect wiring is file-disjoint from shutter data boundary and zoom gesture work.

## Verification Commands
```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Watermark*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests '*Settings*'
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:effect:test
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Expected Evidence Pack
- [ ] Worktree path and branch.
- [ ] Whether `ad75ca4` was cherry-picked, rebased, or manually reimplemented.
- [ ] Git diff stat and changed files.
- [ ] Tests proving non-photo watermark wiring and UI controls.
- [ ] Verification command results.
- [ ] Commit hash / PR link.
- [ ] Unresolved risks.
