# Fifth Feedback: Bottom Cockpit Zoom And Mode Track Integration

> **For text-only agents:** This is a layout/render-model task. Keep changes scoped and coordinate with any agent touching `activity_main.xml`. Use `rtk` for every command.

## Goal

Make the bottom operation area feel like one coherent camera cockpit by merging zoom into the translucent shutter area and improving mode-track readability.

## Problems To Fix

- Zoom row, mode row, shutter area, thumbnail, and lens button feel visually split.
- Zoom chips float above the bottom panel and compete with mode labels.
- Mode-track text is hard to read over live preview.
- Mode track needs stronger contrast, semi-transparent backing, and clearer active state.
- Edge labels can be clipped on narrow screens.

## Required Behavior

- Bottom cockpit is one rounded translucent region containing:
  - latest thumbnail,
  - shutter,
  - true lens-switch button,
  - zoom chips,
  - mode track or a visually attached mode strip.
- Zoom should appear as part of the capture controls, not an unrelated floating row.
- Mode labels must be readable over bright/complex preview.
- Active mode must be obvious.
- Horizontal mode scroll should keep active mode centered enough that `视频/文档` are not clipped during use.

## Suggested Layout Direction

Recommended low-risk structure:

1. Keep `bottomSheet` as the main rounded cockpit.
2. Move `zoomCapsuleScroll` inside or immediately attached to `bottomSheet`.
3. Add a semi-transparent backing behind `modeTrackScroll`, or include mode track as the upper band of the cockpit.
4. Use stronger text color and active chip background for active mode.
5. Reduce vertical gaps between mode, zoom, and shutter so they read as one module.

Avoid:

- Giant floating zoom chips.
- Negative margins that depend on one screen size.
- Rebuilding the screen in Compose.

## Tests To Add Or Update

- `CameraCockpitRenderModelTest` should verify zoom and mode track both exist in cockpit render model.
- Add a layout-independent helper test if there is a pure spacing/visibility model.
- Existing `ModeTrackTouchPolicy` tests should continue to pass.

## Files To Inspect Or Modify

- `app/src/main/res/layout/activity_main.xml`
- `app/src/main/res/values/dimens.xml`
- `app/src/main/res/values/themes.xml`
- `app/src/main/res/drawable/bg_bottom_panel.xml`
- `app/src/main/java/com/opencamera/app/MainActivity.kt`
- `app/src/main/java/com/opencamera/app/SessionUiRenderModel.kt`
- `app/src/main/java/com/opencamera/app/CameraCockpitRenderModel.kt`
- `app/src/test/java/com/opencamera/app/CameraCockpitRenderModelTest.kt`
- `app/src/test/java/com/opencamera/app/ModeTrackTouchPolicyTest.kt`

## Acceptance Criteria

- Zoom chips visually belong to the shutter cockpit.
- Mode track text remains readable over the preview.
- Active mode is clear at a glance.
- `视频` and `文档` are not clipped in normal portrait use.
- Shutter remains visually dominant.
- No camera/session ownership changes.

## Verification

```bash
rtk ./gradlew --no-daemon -Pkotlin.incremental=false :app:testDebugUnitTest --tests com.opencamera.app.CameraCockpitRenderModelTest --tests com.opencamera.app.ModeTrackTouchPolicyTest
rtk ./gradlew --no-daemon :app:assembleDebug
```

Manual/multimodal follow-up: compare before/after vivo X300 portrait recording for bottom cockpit coherence.
