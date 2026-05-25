# Capture, Preview, And Mode Track Repair

## Goal

Fix the real-device UI regressions where the shutter appears with the wrong color/disappears while saving, the preview feels too low relative to the mode track, and Document mode is no longer reachable from the main mode switcher.

## Context

- User request: real-device testing found three issues: purple/disappearing shutter that blocks operation, preview too low, and missing Document mode.
- Verified facts:
  - `app/src/main/res/layout/activity_main.xml` uses a plain `Button` for `@id/buttonShutter` with `android:background="@drawable/bg_shutter_selector"`. Under MaterialComponents, a normal `Button` can still receive framework/material tint or ripple behavior that visually overrides the drawable.
  - `app/src/main/res/drawable/bg_shutter_selector.xml` points disabled state at `bg_shutter_photo_disabled.xml`; current disabled colors use `oc_shutter_fill_disabled` / `oc_shutter_ring_disabled`, which are accent-tinted and can read as a wrong colored shutter.
  - `app/src/main/java/com/opencamera/app/MainActivity.kt` currently calls `cockpitRenderer.renderShutter(state, controls, captureDisabledReason(state, text) == null)`. `captureDisabledReason()` disables still capture during `CaptureStatus.SAVING`, so the shutter switches to disabled visuals while the saved image is pending.
  - `app/src/main/java/com/opencamera/app/SessionCockpitRenderModel.kt` already has `shutterDisabledReason()` with shutter-specific semantics, but `MainActivity.kt` is not using it.
  - `app/src/main/res/layout/activity_main.xml` constrains `cameraPreview` and `previewOverlay` to the full parent bottom, while `modeTrackScroll` sits above `bottomSheet`. This lets bottom controls overlay the preview instead of creating a clear preview region above the mode track.
  - `modeTrackRenderModel()` emits product order `PHOTO, HUMANISTIC, VIDEO, DOCUMENT` when available.
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt` maps mode items by index into only `photo`, `video`, and `document` buttons, hides `humanistic`, and therefore drops the fourth item. This explains Document disappearing when Humanistic is reopened.
- Relevant files:
  - `app/src/main/res/layout/activity_main.xml`
  - `app/src/main/res/drawable/bg_shutter_selector.xml`
  - `app/src/main/res/drawable/bg_shutter_photo.xml`
  - `app/src/main/res/drawable/bg_shutter_photo_pressed.xml`
  - `app/src/main/res/drawable/bg_shutter_photo_disabled.xml`
  - `app/src/main/res/values/colors.xml`
  - `app/src/main/res/values/dimens.xml`
  - `app/src/main/java/com/opencamera/app/MainActivity.kt`
  - `app/src/main/java/com/opencamera/app/CockpitSurfaceRenderer.kt`
  - `app/src/test/java/com/opencamera/app/SessionCockpitRenderModelTest.kt`
  - `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- Non-goals:
  - Do not add new camera/session ownership. UI should still dispatch intents only.
  - Do not implement burst capture or queue multiple still captures unless there is already a supported session contract.
  - Do not reopen broader Stage 6/6B feature work.
  - Do not remove Humanistic to make Document visible.

## Implementation Scope

- Repair shutter visuals so the idle/pressed/disabled still shutter remains a white camera shutter control, with no purple/default Material tint on vivo X300 or local builds.
- Use the shutter-specific enabled decision for the shutter button. If duplicate photo capture remains unsupported while a still shot is active, keep the button visible and visually stable; on tap, show the existing disabled reason instead of making the button look vanished.
- Move the preview/overlay bottom constraint to a stable guide above the mode track or otherwise adjust layout so the visible capture frame sits clearly above the mode switcher and does not feel anchored too low.
- Fix mode-track rendering to bind each `ModeTrackItemRenderModel.modeId` to the matching view (`photo`, `humanistic`, `video`, `document`, etc.) rather than assigning by list index.
- Add or update tests so Humanistic and Document can both be present in the model and renderer mapping without truncation.

## Steps

1. Inspect current resource rendering of `buttonShutter`. Prefer either an `ImageButton`/`AppCompatImageButton` with explicit oval drawables, or keep `Button` only if `backgroundTint` is explicitly null/transparent and the custom drawable is proven not to be tinted.
2. Update shutter color resources/drawables so:
   - idle: white ring plus white inner fill;
   - pressed: subtle white/gray press feedback;
   - disabled/busy: visible white/gray shutter with reduced alpha or neutral ring, not accent/purple;
   - recording states stay red.
3. In `MainActivity.kt`, replace the generic `captureDisabledReason(state, text) == null` shutter enable input with the shutter-specific helper, or route through a new render model field that uses `shutterDisabledReason()`.
4. Ensure a disabled shutter tap still surfaces `showDisabledReason()` through the existing click path, and does not disappear until final media is saved.
5. Adjust `activity_main.xml` preview and overlay constraints. Recommended approach: add a horizontal guideline or constraint target above `modeTrackScroll`, then constrain `cameraPreview` and `previewOverlay` bottom to that guide/track top with a small margin. Keep tap-to-focus overlay aligned with the preview.
6. Rewrite `CockpitSurfaceRenderer.renderModeTrack()` to use a `Map<ModeId, Button>` and render/hide by mode id, including at least `PHOTO`, `HUMANISTIC`, `VIDEO`, and `DOCUMENT`. Avoid index-based assignment.
7. Add focused tests:
   - `modeTrackRenderModel()` includes `PHOTO, HUMANISTIC, VIDEO, DOCUMENT` in product order when all are available.
   - A renderer-level or Robolectric/local unit seam verifies the mode-id-to-button mapping does not drop Document when Humanistic is present. If direct Android view testing is heavy, extract a small pure function for ordered view keys and test it.
   - Shutter enable model/helper uses `shutterDisabledReason()` semantics for photo-active/saving states, or at minimum covers the chosen behavior.
8. Run focused verification, then app assemble.

## Acceptance Criteria

- Shutter is never purple in idle, pressed, or disabled still-photo states.
- After tapping shutter, the visible control remains in place through preview feedback and save completion. It may be temporarily non-interactive if the session cannot queue another still, but it must not disappear or visually collapse.
- If the user taps while a previous photo is still being processed, the app gives clear feedback using the existing disabled reason path rather than silently swallowing the tap.
- Preview content is visually above the mode switcher; the mode track no longer appears to sit inside or below the main preview framing in a way that makes the preview feel too low.
- Mode track shows `Photo`, `Humanistic`, `Video`, and `Doc`/`Document` when all four are available; Document remains clickable and dispatches `SwitchMode(ModeId.DOCUMENT)`.
- Existing hidden modes (`Night`, `Portrait`, `Pro`) are not accidentally reintroduced to the primary track unless product explicitly approves it.

## Verification Commands

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.SessionCockpitRenderModelTest --tests com.opencamera.app.CameraCockpitRenderModelTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

For full Stage 7 regression after the focused UI repair:

```bash
rtk ./scripts/verify_stage_7_observability.sh
```

## Risks And Notes

- Do not make the shutter queue duplicate still captures unless session/media layers already explicitly support it. The immediate fix is visual stability plus clear disabled feedback.
- Preview layout changes can break tap-to-focus coordinate mapping if preview and overlay constraints diverge. Keep `cameraPreview` and `previewOverlay` identical.
- If constraining the preview above the mode track changes saved-image framing expectations, verify `PreviewOverlayView.previewContentGeometry()` and existing frame-ratio tests still describe the same visible capture frame.
- This work is local UI repair; final visual judgment on real-device color and vertical placement should be done with Codex/user screenshots or on-device observation.
