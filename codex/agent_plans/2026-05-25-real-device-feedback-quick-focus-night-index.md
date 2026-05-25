# Real Device Feedback: Quick Panel, Tap Feedback, Night Prompt

## Goal

Turn the latest real-device findings into executable work packages: restore the optimized `快捷` controls, fix tap focus / AE feedback geometry and lifetime, and make the low-light night prompt actually surface on device.

## Context

- User request:
  - Issue 4: previous optimizations for `快捷` brightness adjustment, frame-ratio UI, pixel, and quality have regressed to the old version.
  - Issue 5: tap focus / brightness feedback animation is offset, may appear outside the preview window, and does not disappear.
  - Issue 6: no automatic night icon appears in dark scenes.
- Verified facts:
  - `codex/agent_plans/2026-05-25-quick-panel-frame-cycle-brightness-slider.md` already defines the newer product direction, but current `activity_main.xml` still uses `buttonBrightnessMinus / buttonBrightnessValue / buttonBrightnessPlus` and `buttonFrameRatio43 / buttonFrameRatio169 / buttonFrameRatio11`.
  - `SessionCockpitRenderModel.kt` currently sets quick `qualityRow.value = grid.value`, so quality can display the grid value rather than active still/video quality.
  - `PreviewTapFocusGeometry.kt` normalizes taps relative to `activeFrameRect` when a frame is active, while `PreviewOverlayView.drawFocusReticle()` converts the returned values against full view width/height. This coordinate-system mismatch explains the observed offset.
  - `PreviewMeteringFeedback` and `PreviewBrightnessFeedback` have no visible expiry path comparable to `PhotoLowLightPromptExpired`, so feedback can remain indefinitely.
  - `PreviewSceneBrightnessMonitor` exists, but `AppContainer` does not instantiate it or pass a `sceneBrightnessSource` into `CameraSessionCoordinator`; its default `bitmapProvider` returns `null`.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/main/java/com/opencamera/app/PreviewTapFocusGeometry.kt`
  - `app/src/main/java/com/opencamera/app/PreviewOverlayView.kt`
  - `app/src/main/java/com/opencamera/app/camera/PreviewSceneBrightnessMonitor.kt`
  - `app/src/main/java/com/opencamera/app/camera/CameraSessionCoordinator.kt`
  - `app/src/main/java/com/opencamera/app/AppContainer.kt`
- Non-goals:
  - Do not change stage ownership or enter a new stage.
  - Do not let UI directly call CameraX for quick controls, focus, brightness, or night assist.
  - Do not tune final low-light thresholds without real-device evidence.

## Work Packages

1. [Quick Panel Regression Repair](./2026-05-25-quick-panel-regression-repair.md)
2. [Preview Tap Feedback Geometry And Lifetime](./2026-05-25-preview-tap-feedback-geometry-and-lifetime.md)
3. [Low-Light Night Prompt Real-Device Repair](./2026-05-25-low-light-night-prompt-real-device-repair.md)
4. [Real Device Feedback Acceptance QA](./2026-05-25-real-device-feedback-acceptance-qa.md)

## Execution Order

1. Run package 1 first if only one agent is available, because it restores the most visible `快捷` regression and updates several shared UI files.
2. Run package 2 next; it touches overlay/session feedback rather than quick-panel layout.
3. Run package 3 after or in parallel with package 2, but avoid simultaneous edits to `CameraSessionCoordinator.kt`.
4. Codex/user performs package 4 after a debug APK is built.

## Global Acceptance Criteria

- `快捷` shows one brightness slider, one frame-ratio cycle row, truthful `画质`, and a usable `像素` row.
- Tapping inside the preview produces focus/AE feedback at the touched location, never outside the active preview/capture area, and it disappears automatically.
- Dark scenes in photo mode dispatch low-light scene signals and show the floating night prompt for about 3 seconds.
- All shell commands use `rtk`.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest --tests com.opencamera.app.PreviewTapFocusGeometryTest --tests com.opencamera.app.PreviewOverlayGeometryTest --tests com.opencamera.app.camera.CameraSessionCoordinatorTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- The first three packages are suitable for text/code agents. Final visual feel, “does it land under my finger”, and actual low-light prompting remain Codex/user real-device acceptance work.
- Current workspace is not reporting usable git status, so agents must inspect target files before editing and avoid relying on git as the only source of changed-file truth.
