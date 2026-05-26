# Package 04 — Cockpit Wiring And UX Integration

## Package ID

`04-cockpit-wiring-and-ux-integration`

## Goal

Wire the finalized Zoom Cockpit V2 render model and focal slider widget into the actual cockpit so users get exact preset dots, continuous one-decimal slider feedback, honest disabled/degraded states, and recording restrictions in the visible UI.

## Context

- User request: productize the existing zoom foundation at relatively low landing cost.
- Verified facts:
  - `CockpitSurfaceRenderer.renderFocalLengthSlider()` currently initializes callbacks and passes `presetRatios/currentRatio/isVisible`.
  - `MainActivity` currently dispatches `SessionIntent.ApplyZoomRatio(ratio)` for both selected and changed slider callbacks.
  - Existing old zoom capsules may still exist alongside the slider; V2 should avoid conflicting controls.
- Relevant files:
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/res/layout/activity_main.xml`
  - app render/cockpit tests
- Non-goals:
  - Do not redefine capability policy; package 01 owns it.
  - Do not change widget internals; package 02 owns it.
  - Do not change session recording policy; package 03 owns it.

## Implementation Scope

- Consume the render model from package 01 and the widget behavior from package 02.
- Ensure exact preset taps/dots dispatch `SessionIntent.ApplyZoomRatio(exactRatio)`, not `ZoomRatioToggled`.
- Ensure continuous slider changes dispatch clamped one-decimal ratios at a safe cadence and a final release value.
- Reflect disabled/degraded/recording restriction state visibly:
  - disabled alpha/clickability;
  - accessible content description or text status;
  - no enabled-looking continuous control for unsupported/discrete-only state.
- Decide whether old capsule row remains as dot labels, collapses into the slider, or is hidden to avoid duplicate zoom controls.
- Add tests for callback mapping and render integration where local tests can cover it.

## Steps

1. Re-read packages 01, 02, and 03 evidence before editing.
2. Update `CockpitSurfaceRenderer.renderFocalLengthSlider()` to consume the full V2 render model.
3. Update `MainActivity` callback wiring only if the callback semantics changed.
4. Update `activity_main.xml` only in the focal slider / zoom cockpit region.
5. Add focused app tests for:
   - exact preset dispatch;
   - disabled state does not dispatch;
   - recording-limited state displays reason;
   - continuous slider dispatch is not duplicated by a separate toggle path.
6. Run focused app verification and assemble.

## Acceptance Criteria

- Visible cockpit has one coherent zoom control language: preset points plus slider plus one-decimal current value.
- Exact preset selection dispatches exact ratios through `ApplyZoomRatio`.
- Continuous drag dispatches bounded continuous values and a final release value.
- Unsupported/discrete/recording-limited states are visible and not misleading.
- UI does not directly drive camera runtime; it only dispatches session intents.
- Existing pinch zoom remains compatible with slider state.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.gesture.GesturePolicyTest
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :core:session:test --tests com.opencamera.core.session.DefaultCameraSessionTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

## Risks And Notes

- Shared cockpit files are conflict-prone. Keep edits tightly scoped to focal/zoom methods and layout region.
- Real-device QA should include slow drag, fast drag, preset dot tap, pinch, recording start/stop, mode switch after zoom, and preview recovery after zoom.

## File Ownership

- `04-cockpit-wiring-and-ux-integration` owns:
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` focal/zoom render methods only
  - `app/src/main/java/com/opencamera/app/MainActivity.kt` zoom callback wiring only
  - `app/src/main/res/layout/activity_main.xml` focal slider and zoom cockpit region only
  - focused app render/cockpit tests needed for integration
- Other packages must not edit these files without coordination.

## Allowed Paths

- `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/res/layout/activity_main.xml`
- `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/FocalLengthSliderViewTest.kt` only to update integration expectations after package 02

## Forbidden Paths

- `core/session/**`
- `core/device/**`
- `app/src/main/java/com/opencamera/app/FocalLengthSliderView.kt`
- `app/src/main/java/com/opencamera/app/camera/**`
- unrelated shutter, quick panel, focus/EV, and color lab files

## Dependencies

- Depends on: `01-product-contract-capability-boundary`, `02-slider-widget-productization`, `03-session-recording-zoom-policy`

## Parallel Safety

- unsafe
- Reason: final integration package touches shared cockpit renderer/layout and must run after contract, widget, and session packages settle.

## Expected Evidence Pack

- [ ] worktree path recorded
- [ ] branch name recorded
- [ ] git status clean or explained
- [ ] git diff --stat captured
- [ ] changed files listed
- [ ] verification commands run
- [ ] test results summarized
- [ ] commit hash / PR link
- [ ] unresolved risks noted
- [ ] only allowed paths touched
