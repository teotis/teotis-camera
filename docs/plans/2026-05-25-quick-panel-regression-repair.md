# Quick Panel Regression Repair

## Goal

Restore the newer `快捷` quick-panel interaction: brightness is a draggable slider, frame ratio is a single large cycle row, `画质` displays the active quality instead of stale or unrelated text, and `像素` remains a first-class photo quick row.

## Context

- User request: previous optimizations for `快捷` brightness, frame-ratio UI, pixel, and quality have reverted to the old version.
- Verified facts:
  - `activity_main.xml` still has the old brightness row with three buttons: `buttonBrightnessMinus`, `buttonBrightnessValue`, and `buttonBrightnessPlus`.
  - `activity_main.xml` still has a vertical frame-ratio row with three mini chips: `buttonFrameRatio43`, `buttonFrameRatio169`, and `buttonFrameRatio11`.
  - `MainActivityViews.QuickPanelViews` and `MainActivityActionBinder` still bind those old brightness and frame buttons.
  - `CockpitSurfaceRenderer.renderQuickBubble()` still renders the old brightness buttons and ratio chips.
  - `QuickPanelSheetRenderModel` already has `brightnessRow.steps/minSteps/maxSteps/isInteractive` and `frameRatioNext`, so the model is partly ready for a slider and cycle row.
  - `SessionCockpitRenderModel.kt` currently sets `qualityRow.value = grid.value`; this is a clear regression for quick quality.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/java/com/opencamera/app/MainActivityViews.kt`
  - `app/src/main/java/com/opencamera/app/MainActivityActionBinder.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/SessionUiRenderModelTest.kt`
- Non-goals:
  - Do not change saved JPEG frame-ratio crop behavior.
  - Do not make quick brightness affect Color Lab or post-processing brightness.
  - Do not remove video quick combined-spec behavior.

## Implementation Scope

- Replace old brightness `- / value / +` controls with a row containing label, `SeekBar`, and compact value text.
- Replace three frame-ratio chips with one full-width button such as `画幅 4:3`; click dispatches `FrameRatioSelected(frameRatioNext)`.
- Fix quick quality label and enabled state for photo and video modes.
- Preserve and verify the `像素` row as a photo quick row.
- Update rotation handling in `MainActivity.applyControlRotationForDisplay()` so it no longer references removed frame chip views.

## Steps

1. Update `activity_main.xml`.
   - Replace `buttonBrightnessMinus / buttonBrightnessValue / buttonBrightnessPlus` with `SeekBar` plus value `TextView`.
   - Replace `frameRatioOptionRow` and three chip buttons with one `buttonQuickFrameRatio`.
2. Update `QuickPanelViews`.
   - Remove old brightness/frame chip fields.
   - Add `brightnessSlider`, `brightnessValue`, and `frameRatio`.
3. Update `CockpitSurfaceRenderer.renderQuickBubble()`.
   - Render brightness slider min/max/progress from `QuickBrightnessRenderModel`.
   - Render frame row text from `sheet.frameRatioRow`.
   - Use a guard or `fromUser` check to prevent renderer-driven slider progress updates from dispatching session intents.
4. Update `MainActivityActionBinder`.
   - Bind slider user changes to `SessionIntent.ApplyPreviewBrightness(targetSteps)`.
   - Bind frame row to `snapshot().quickPanelSheet?.frameRatioNext` or equivalent current snapshot source.
   - Keep quality dispatch through session/mode owner; do not call CameraX.
5. Fix `SessionCockpitRenderModel`.
   - Replace `qualityRow.value = grid.value` with active still/video quality label logic.
   - Keep `resolutionRow` label and disabled semantics truthful.
6. Update tests.
   - Assert quick quality reflects active still quality and video combined spec.
   - Assert quick brightness exposes slider numeric fields and disabled states.
   - Assert frame-ratio next cycles `4:3 -> 16:9 -> 1:1 -> 4:3`.
   - Remove or rewrite tests that require three visible ratio chips.

## Acceptance Criteria

- `快捷` no longer shows three brightness buttons.
- `快捷` no longer shows three small frame-ratio chips.
- Dragging brightness dispatches `ApplyPreviewBrightness` with mapped steps and does not create render loops.
- Frame-ratio row is a single large hit target and cycles to the next ratio.
- `画质` never displays grid state.
- `像素` row remains visible and functional in photo mode and disabled/hidden honestly outside still capture contexts.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.SessionUiRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- The older chip UI has multiple references across XML, view binding, renderer, action binder, and rotation code. Remove the old ids completely in one pass to avoid compile-time drift.
- If slider dispatch is too noisy on device, add lightweight UI-side coalescing while keeping session/device ownership unchanged.
